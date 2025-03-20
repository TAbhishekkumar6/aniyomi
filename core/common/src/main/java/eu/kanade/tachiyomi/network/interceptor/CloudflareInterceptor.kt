package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
) {
    private var timeout: Int = 30
    private var useEnhancedEvasions: Boolean = false

    fun setTimeout(seconds: Int) {
        timeout = seconds
    }

    fun setEvasions(enabled: Boolean) {
        useEnhancedEvasions = enabled
    }

    fun intercept(client: OkHttpClient, request: Request): Response {
        val origRequest = request
        val origRequestUrl = origRequest.url.toString()

        val oldCookie = cookieJar.get(origRequest.url.toHttpUrl())
            .firstOrNull { it.name == "cf_clearance" }

        val latch = CountDownLatch(1)
        var webview: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val headers = request.headers.toMultimap()
        val executor = ContextCompat.getMainExecutor(context)

        executor.execute {
            webview = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = defaultUserAgentProvider()
                }
            }

            webview?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Cloudflare check
                    fun isCloudFlareBypassed(): Boolean {
                        val newCookie = cookieJar.get(origRequest.url.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                        return newCookie != null && newCookie != oldCookie
                    }

                    // Inject our evasion scripts and checks
                    view.evaluateJavascript(getEvasionScript()) { }
                    if (useEnhancedEvasions) {
                        view.evaluateJavascript(getAdvancedEvasionScript()) { }
                    }

                    // Check if bypassed
                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        latch.countDown()
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        if (error.errorCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            webview?.loadUrl(origRequestUrl, headers.mapValues { it.value.first() })
        }

        // Wait with timeout
        if (!latch.await(timeout.toLong(), TimeUnit.SECONDS)) {
            throw CloudflareBypassException("Timeout after ${timeout}s")
        }

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (isWebViewOutdated) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            }
            throw CloudflareBypassException()
        }

        // Create new request with bypass cookies
        val newRequest = origRequest.newBuilder()
            .apply {
                cookieJar.get(origRequest.url.toHttpUrl()).forEach {
                    addHeader("Cookie", "${it.name}=${it.value}")
                }
            }
            .build()

        return client.newCall(newRequest).execute()
    }

    private fun getEvasionScript(): String = """
        // Basic evasions
        Object.defineProperty(navigator, 'webdriver', { get: () => false });
        Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5].map(() => ({
            name: ['Chrome PDF Plugin', 'Chrome PDF Viewer', 'Native Client'][Math.floor(Math.random() * 3)]
        }))});
    """.trimIndent()

    private fun getAdvancedEvasionScript(): String = """
        // Advanced evasions for aggressive mode
        const originalFunction = document.createElement;
        document.createElement = function(...args) {
            const element = originalFunction.apply(this, args);
            if (element.tagName === 'CANVAS') {
                const originalToDataURL = element.toDataURL;
                element.toDataURL = function(...args) {
                    return originalToDataURL.apply(this, args)
                }
            }
            return element;
        }

        // Add WebGL evasions
        const webglVendors = [
            'Google Inc.', 'Apple Computer, Inc.', 'Intel Inc.', 'NVIDIA Corporation'
        ];
        const getParameterProxy = WebGLRenderingContext.prototype.getParameter;
        WebGLRenderingContext.prototype.getParameter = function(parameter) {
            if (parameter === 37445) { // UNMASKED_VENDOR_WEBGL
                return webglVendors[Math.floor(Math.random() * webglVendors.length)];
            }
            return getParameterProxy.apply(this, arguments);
        };
    """.trimIndent()

    companion object {
        private val ERROR_CODES = listOf(403, 503, 429, 529, 520, 521, 522)
    }
}

class CloudflareBypassException(message: String? = null) : IOException(message ?: "Failed to bypass Cloudflare")
