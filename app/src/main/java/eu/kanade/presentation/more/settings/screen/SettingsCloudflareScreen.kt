package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsCloudflareScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_cloudflare

    @Composable
    override fun getPreferences(): List<Preference> {
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val context = LocalContext.current

        val bypassStrategy by networkPreferences.bypassStrategy().collectAsState()
        val proxyEnabled by networkPreferences.proxyEnabled().collectAsState()
        val maxRetries by networkPreferences.maxRetries().collectAsState()
        val cacheEnabled by networkPreferences.cacheEnabled().collectAsState()
        val cacheDuration by networkPreferences.cacheDuration().collectAsState()
        val customUserAgentEnabled by networkPreferences.customUserAgentEnabled().collectAsState()
        val randomizeFingerprint by networkPreferences.randomizeFingerprint().collectAsState()
        val aggressiveMode by networkPreferences.aggressiveModeEnabled().collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_cloudflare),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        pref = networkPreferences.bypassStrategy(),
                        title = stringResource(MR.strings.pref_cloudflare_bypass_mode),
                        entries = NetworkPreferences.BypassStrategy.entries
                            .associateWith { it.name }
                            .toImmutableMap(),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = networkPreferences.proxyEnabled(),
                        title = stringResource(MR.strings.pref_cloudflare_proxy_enabled),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = networkPreferences.maxRetries(),
                        title = stringResource(MR.strings.pref_cloudflare_max_retries),
                        entries = (1..5).associateWith { it.toString() }.toImmutableMap(),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = networkPreferences.cacheEnabled(),
                        title = stringResource(MR.strings.pref_cloudflare_cache_enabled),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = networkPreferences.cacheDuration(),
                        title = stringResource(MR.strings.pref_cloudflare_cache_duration),
                        entries = mapOf(
                            15L * 60L * 1000L to "15 minutes",
                            30L * 60L * 1000L to "30 minutes",
                            60L * 60L * 1000L to "1 hour",
                            2L * 60L * 60L * 1000L to "2 hours",
                        ).toImmutableMap(),
                        enabled = cacheEnabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = networkPreferences.customUserAgentEnabled(),
                        title = stringResource(MR.strings.pref_cloudflare_custom_ua),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = networkPreferences.randomizeFingerprint(),
                        title = stringResource(MR.strings.pref_cloudflare_random_fingerprint),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = networkPreferences.aggressiveModeEnabled(),
                        title = stringResource(MR.strings.pref_cloudflare_aggressive_mode),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_cloudflare_clear_cache),
                        onClick = {
                            networkPreferences.clearBypassCache()
                            context.toast(MR.strings.cookies_cleared)
                        },
                    ),
                ),
            ),
        )
    }
}
