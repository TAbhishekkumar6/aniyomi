package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    private val cloudflareBypassManager: CloudflareBypassManager,
    private val browserFingerprintGenerator: BrowserFingerprintGenerator,
) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

        if (preferences.verboseLogging().get()) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        // Enhanced Cloudflare interceptor with bypass manager
        builder.addInterceptor { chain ->
            val request = chain.request()
            try {
                val response = chain.proceed(request)
                if (response.code in listOf(403, 503, 429, 520, 521, 522) &&
                    response.header("Server")?.contains("cloudflare", ignoreCase = true) == true) {
                    response.close()

                    // Try bypass with our enhanced manager
                    val bypassResponse = cloudflareBypassManager.attemptBypass(builder.build(), request)
                    if (bypassResponse != null) {
                        return@addInterceptor bypassResponse
                    }

                    // If bypass manager failed, fall back to standard interceptor
                    return@addInterceptor CloudflareInterceptor(
                        context,
                        cookieJar,
                        ::defaultUserAgentProvider
                    ).intercept(chain, request, response)
                }
                response
            } catch (e: Exception) {
                throw IOException(context.getString(R.string.information_cloudflare_bypass_failure), e)
            }
        }

        // Configure DNS-over-HTTPS
        when (preferences.dohProvider().get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            PREF_DOH_LIBREDNS -> builder.dohLibreDNS()
        }

        builder.build()
    }

    fun defaultUserAgentProvider(): String {
        val defaultUA = preferences.defaultUserAgent().get().trim()
        return if (defaultUA.isNotBlank()) {
            defaultUA
        } else {
            browserFingerprintGenerator.generateFingerprint()["sec-ch-ua"]?.let { ua ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${ua.substringAfter("v=\"").substringBefore("\"")} Safari/537.36"
            } ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        }
    }
}
