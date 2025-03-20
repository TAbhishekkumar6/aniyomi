package eu.kanade.tachiyomi.network

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NetworkPreferences(
    private val preferenceStore: PreferenceStore,
    private val verboseLogging: Boolean = false,
) {

    fun verboseLogging(): Preference<Boolean> {
        return preferenceStore.getBoolean("verbose_logging", verboseLogging)
    }

    fun dohProvider(): Preference<Int> {
        return preferenceStore.getInt("doh_provider", -1)
    }

    fun defaultUserAgent(): Preference<String> {
        return preferenceStore.getString(
            "default_user_agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        )
    }

    // Enhanced Cloudflare bypass preferences
    fun bypassStrategy() = preferenceStore.getString(
        "cf_bypass_strategy",
        BypassStrategy.DEFAULT.name,
    )

    fun proxyEnabled() = preferenceStore.getBoolean("cf_proxy_enabled", false)

    fun maxRetries() = preferenceStore.getInt("cf_max_retries", 3)

    fun cacheEnabled() = preferenceStore.getBoolean("cf_cache_enabled", true)

    fun cacheDuration() = preferenceStore.getLong("cf_cache_duration", 30L * 60L * 1000L) // 30 minutes

    fun customUserAgentEnabled() = preferenceStore.getBoolean("cf_custom_ua_enabled", false)

    fun randomizeFingerprint() = preferenceStore.getBoolean("cf_randomize_fingerprint", true)

    fun aggressiveModeEnabled() = preferenceStore.getBoolean("cf_aggressive_mode", false)

    fun clearBypassCache() {
        preferenceStore.getBoolean("cf_cache_enabled", true).delete()
    }

    enum class BypassStrategy {
        DEFAULT, // Standard approach
        FAST, // Optimized for speed, less evasion
        AGGRESSIVE, // Maximum evasion, slower
    }
}
