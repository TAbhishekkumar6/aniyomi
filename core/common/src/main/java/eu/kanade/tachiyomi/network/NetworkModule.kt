package eu.kanade.tachiyomi.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCloudflareMonitor(): CloudflareMonitor {
        return CloudflareMonitor()
    }

    @Provides
    @Singleton
    fun provideProxyManager(): ProxyManager {
        return ProxyManager()
    }

    @Provides
    @Singleton
    fun provideCloudflareBypassManager(
        @ApplicationContext context: Context,
        proxyManager: ProxyManager,
    ): CloudflareBypassManager {
        return CloudflareBypassManager(context, proxyManager)
    }

    @Provides
    @Singleton
    fun provideBrowserFingerprintGenerator(): BrowserFingerprintGenerator {
        return BrowserFingerprintGenerator()
    }
}
