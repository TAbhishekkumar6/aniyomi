package eu.kanade.tachiyomi.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class CloudflareMonitor {
    private val mutex = Mutex()
    private val hostPatterns = ConcurrentHashMap<String, HostStats>()
    
    data class HostStats(
        var successfulBypassCount: Int = 0,
        var failedBypassCount: Int = 0,
        var lastSuccessfulUserAgent: String? = null,
        var lastSuccessfulCookies: Map<String, String>? = null,
        var averageBypassTime: Long = 0,
        var lastChallengeType: String? = null,
        var consecutiveFailures: Int = 0,
        val bypassTimings: MutableList<Long> = mutableListOf()
    )

    suspend fun recordSuccess(
        url: HttpUrl,
        userAgent: String,
        cookies: Map<String, String>,
        bypassTime: Long,
        challengeType: String
    ) = mutex.withLock {
        val host = url.host
        val stats = hostPatterns.getOrPut(host) { HostStats() }
        
        stats.apply {
            successfulBypassCount++
            consecutiveFailures = 0
            lastSuccessfulUserAgent = userAgent
            lastSuccessfulCookies = cookies
            lastChallengeType = challengeType
            
            // Update bypass timing statistics
            bypassTimings.add(bypassTime)
            if (bypassTimings.size > 10) bypassTimings.removeAt(0)
            averageBypassTime = bypassTimings.average().toLong()
        }
    }

    suspend fun recordFailure(url: HttpUrl) = mutex.withLock {
        val host = url.host
        val stats = hostPatterns.getOrPut(host) { HostStats() }
        
        stats.apply {
            failedBypassCount++
            consecutiveFailures++
        }
    }

    suspend fun getOptimalStrategy(url: HttpUrl): BypassStrategy = mutex.withLock {
        val stats = hostPatterns[url.host]
        
        return when {
            stats == null -> BypassStrategy.DEFAULT
            stats.consecutiveFailures >= 3 -> BypassStrategy.AGGRESSIVE
            stats.successfulBypassCount > 5 && stats.averageBypassTime < 10000 -> BypassStrategy.FAST
            else -> BypassStrategy.DEFAULT
        }
    }

    suspend fun getSuggestedWaitTime(url: HttpUrl): Long = mutex.withLock {
        val stats = hostPatterns[url.host]
        if (stats == null) return 0L

        return when {
            stats.consecutiveFailures == 0 -> 0L
            stats.consecutiveFailures == 1 -> 1000L
            else -> min(stats.consecutiveFailures * 2000L, 10000L)
        }
    }

    suspend fun getPreviousSuccess(url: HttpUrl): SuccessfulBypass? = mutex.withLock {
        val stats = hostPatterns[url.host] ?: return null
        
        if (stats.lastSuccessfulUserAgent != null && stats.lastSuccessfulCookies != null) {
            return SuccessfulBypass(
                userAgent = stats.lastSuccessfulUserAgent!!,
                cookies = stats.lastSuccessfulCookies!!
            )
        }
        return null
    }

    data class SuccessfulBypass(
        val userAgent: String,
        val cookies: Map<String, String>
    )

    enum class BypassStrategy {
        DEFAULT,    // Standard approach
        FAST,       // Optimized for speed, less evasion
        AGGRESSIVE  // Maximum evasion, slower
    }

    suspend fun clearStats() = mutex.withLock {
        hostPatterns.clear()
    }
}