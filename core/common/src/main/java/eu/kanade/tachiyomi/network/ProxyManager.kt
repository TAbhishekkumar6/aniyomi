package eu.kanade.tachiyomi.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Proxy
import java.net.InetSocketAddress
import java.net.Proxy.Type
import java.util.concurrent.atomic.AtomicInteger

class ProxyManager {
    private val mutex = Mutex()
    private val currentIndex = AtomicInteger(0)
    private var workingProxies = mutableListOf<ProxyConfig>()

    data class ProxyConfig(
        val type: Type,
        val host: String,
        val port: Int,
        var failCount: Int = 0,
    ) {
        fun toProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))
    }

    init {
        // Initialize with some default proxies
        // In a production app, these should be loaded from a secure config or service
        workingProxies.addAll(
            listOf(
                ProxyConfig(Type.SOCKS, "127.0.0.1", 9050), // Tor proxy if available
                ProxyConfig(Type.HTTP, "127.0.0.1", 8118), // Privoxy if available
            ),
        )
    }

    suspend fun getNextProxy(): Proxy? = mutex.withLock {
        if (workingProxies.isEmpty()) return null

        val index = currentIndex.getAndIncrement() % workingProxies.size
        return workingProxies[index].toProxy()
    }

    suspend fun markProxyAsFailed(proxy: Proxy) = mutex.withLock {
        workingProxies.find { it.toProxy() == proxy }?.let { config ->
            config.failCount++
            if (config.failCount >= MAX_FAILURES) {
                workingProxies.remove(config)
            }
        }
    }

    suspend fun addProxy(type: Type, host: String, port: Int) = mutex.withLock {
        val config = ProxyConfig(type, host, port)
        if (!workingProxies.contains(config)) {
            workingProxies.add(config)
        }
    }

    companion object {
        private const val MAX_FAILURES = 3
    }
}
