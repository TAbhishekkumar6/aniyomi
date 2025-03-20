package eu.kanade.tachiyomi.di

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.BrowserFingerprintGenerator
import eu.kanade.tachiyomi.network.CloudflareBypassManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get

class NetworkModule : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(NetworkPreferences(get()))
        addSingleton(AndroidCookieJar())
        addSingleton(BrowserFingerprintGenerator())
        addSingleton {
            CloudflareBypassManager(
                context = get<Context>(),
                networkPreferences = get(),
                cookieManager = get(),
                fingerprintGenerator = get()
            )
        }
        addSingleton {
            NetworkHelper(
                context = get(),
                preferences = get(),
                cloudflareBypassManager = get(),
                browserFingerprintGenerator = get()
            )
        }
    }
}