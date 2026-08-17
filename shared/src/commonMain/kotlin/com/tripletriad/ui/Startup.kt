package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.tripletriad.data.CampaignCatalog
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.FormatCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.StarterCatalog
import com.tripletriad.data.loadCampaignCatalog
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.data.loadStarterCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.rememberDeviceLocale
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository

enum class StartupPhase(val labelKey: String) {
    SETTINGS(StringKeys.STARTUP_SETTINGS),

    CARDS(StringKeys.LOADING_CARDS),

    ART(StringKeys.STARTUP_ART),

    OPPONENTS(StringKeys.STARTUP_OPPONENTS),

    READY(StringKeys.STARTUP_READY),
    ;

    val progress: Float get() = ordinal / (entries.size - 1f)
}

data class StartupState(
    val phase: StartupPhase = StartupPhase.SETTINGS,
    val settings: UserSettings? = null,
    val catalog: CardCatalog? = null,
    val starters: StarterCatalog? = null,
    val formats: FormatCatalog? = null,
    val art: CardArt? = null,
    val ui: UiArt? = null,
    val opponents: NpcCatalog? = null,
    val campaigns: CampaignCatalog? = null,
) {
    val isReady: Boolean get() = phase == StartupPhase.READY
}

@Composable
fun rememberStartup(store: SettingsStore): StartupState {
    // Read here rather than inside the producer: `Locale.current` is a composition-local read and
    // the producer's body is a coroutine, not a composable.
    val device = rememberDeviceLocale()
    val state by produceState(StartupState(), store, device) {
        val settings = UserSettingsRepository(store).load(device)
        value = StartupState(StartupPhase.CARDS, settings)

        val catalog = loadCardCatalog()
        val starters = loadStarterCatalog()
        val formats = loadFormatCatalog()
        value = StartupState(StartupPhase.ART, settings, catalog, starters, formats)

        val art = loadCardArt()
        val ui = loadUiArt()
        value = StartupState(
            StartupPhase.OPPONENTS, settings, catalog, starters, formats, art, ui,
        )

        val opponents = loadNpcCatalog()
        val campaigns = loadCampaignCatalog()
        value = StartupState(
            phase = StartupPhase.READY,
            settings = settings,
            catalog = catalog,
            starters = starters,
            formats = formats,
            art = art,
            ui = ui,
            opponents = opponents,
            campaigns = campaigns,
        )
    }
    return state
}

class SettingsHolder internal constructor(
    initial: UserSettings,
    private val save: (UserSettings) -> Unit,
) {
    var value: UserSettings by mutableStateOf(initial)
        private set

    fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(value).sane()
        if (next != value) {
            value = next
            save(next)
        }
    }
}
