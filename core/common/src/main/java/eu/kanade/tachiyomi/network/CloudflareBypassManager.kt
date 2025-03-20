package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebView
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CloudflareBypassManager(
    private val context: Context,
    private val networkPreferences: NetworkPreferences,
    private val cookieManager: AndroidCookieJar,
    private val fingerprintGenerator: BrowserFingerprintGenerator,
) {

    private val cache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(
        val cookies: List<Cookie>,
        val timestamp: Long,
        val userAgent: String
    )

    fun attemptBypass(client: OkHttpClient, request: Request): Response? {
        val url = request.url.toString()
        
        // Check cache first if enabled
        if (networkPreferences.cacheEnabled().get()) {
            val cached = cache[url]
            if (cached != null) {
                val now = System.currentTimeMillis()
                val cacheDuration = networkPreferences.cacheDuration().get()
                if (now - cached.timestamp < cacheDuration) {
                    // Apply cached cookies and UA
                    val newRequest = request.newBuilder()
                        .header("User-Agent", cached.userAgent)
                        .build()
                    cached.cookies.forEach { cookie ->
                        cookieManager.saveFromResponse(request.url, listOf(cookie))
                    }
                    return client.newCall(newRequest).execute()
                } else {
                    cache.remove(url)
                }
            }
        }

        // Generate browser fingerprint
        val fingerprint = if (networkPreferences.randomizeFingerprint().get()) {
            fingerprintGenerator.generateFingerprint()
        } else {
            fingerprintGenerator.getDefaultFingerprint()
        }

        // Determine retry strategy
        val maxRetries = networkPreferences.maxRetries().get()
        val strategy = NetworkPreferences.BypassStrategy.valueOf(
            networkPreferences.bypassStrategy().get()
        )

        var lastError: Exception? = null
        for (i in 1..maxRetries) {
            try {
                val interceptor = CloudflareInterceptor(
                    context = context, 
                    cookieJar = cookieManager,
                    defaultUserAgentProvider = { fingerprint["User-Agent"] ?: "" }
                )

                // Configure based on strategy
                when (strategy) {
                    NetworkPreferences.BypassStrategy.FAST -> {
                        interceptor.setTimeout(15) // Shorter timeout
                    }
                    NetworkPreferences.BypassStrategy.AGGRESSIVE -> {
                        interceptor.setEvasions(true) // More evasions
                        interceptor.setTimeout(45) // Longer timeout
                    }
                    else -> { } // Default settings
                }

                val response = interceptor.intercept(client, request)
                
                // Cache successful result
                if (networkPreferences.cacheEnabled().get()) {
                    cache[url] = CacheEntry(
                        cookies = cookieManager.get(url.toHttpUrl()),
                        timestamp = System.currentTimeMillis(),
                        userAgent = fingerprint["User-Agent"] ?: ""
                    )
                }

                return response

            } catch (e: Exception) {
                lastError = e
                // Exponential backoff between retries
                if (i < maxRetries) {
                    Thread.sleep(1000L * i * i)
                }
            }
        }

        throw lastError ?: Exception("Failed to bypass Cloudflare")
    }

    fun clearCache() {
        cache.clear()
    }
}