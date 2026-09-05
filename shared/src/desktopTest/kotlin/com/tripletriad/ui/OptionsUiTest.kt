package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.MatchSpeed
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OptionsUiTest {
    @Test
    fun pickingALanguageRedrawsTheAppInItImmediately() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.FR_FR)).performClick()
        waitForIdle()

        // Same screen, now in French — the confirmation is the screen itself, which is why there
        // is no "settings saved" toast.
        assertTrue(isVisible("Langue"), "the language label did not change")
        // And the shell around it, which is the half a screen-local assertion would have missed.
        assertTrue(isVisible("Options"), "the scaffold title did not change")
    }

    @Test
    fun aLanguageChangeIsWrittenToTheStore() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US"}""")
        setContent { TestApp(store = store) }
        openOptions()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.JA_JA)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        assertTrue(
            store.stored.orEmpty().contains("\"ja_JA\""),
            "the store still holds: ${store.stored.orEmpty()}",
        )
    }

    @Test
    fun theChangeSurvivesLeavingAndReopeningTheScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.DE_DE)).performClick()
        waitForIdle()

        closeOptions()
        openOptions()

        assertTrue(isVisible("Sprache"), "the German label is gone, so the choice was lost")
    }

    @Test
    fun theScreenUnderTheSheetFollowsTheLanguageChosenInIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.FR_FR)).performClick()
        waitForIdle()
        closeOptions()

        onNodeWithTag(titleChoiceTestTag("new")).assertTextEquals("Nouvelle Partie")
    }

    @Test
    fun aVolumeChangeIsClampedPersistedAndShownAsAPercentage() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US","background_volume":1.0}""")
        setContent { TestApp(store = store) }
        openOptions()

        // Dragged to the far left, which is 0. Using the semantics action rather than a swipe
        // keeps this independent of where the slider ended up being laid out.
        onNodeWithTag(OPTIONS_BACKGROUND_VOLUME_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        assertTrue(isVisible("0%"), "the percentage did not follow the slider")
        assertTrue(
            store.stored.orEmpty().contains("\"background_volume\": 0.0"),
            "the store holds: ${store.stored.orEmpty()}",
        )
    }

    @Test
    fun theAudioSectionAdmitsNothingPlaysYet() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        assertTrue(isVisible("saved, but nothing plays yet"))
    }

    @Test
    fun bothVolumesAreOnTheScreenWithTheirAs3Labels() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        assertTrue(isVisible("Background Volume"))
        assertTrue(isVisible("Noise Volume"))
    }

    @Test
    fun theShippedPaceIsWhatIsSelectedOnAFileThatHasNeverBeenAsked() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        onNodeWithTag(optionsSpeedTestTag(MatchSpeed.NORMAL)).assertIsSelected()
        onNodeWithTag(optionsSpeedTestTag(MatchSpeed.INSTANT)).assertIsNotSelected()
    }

    @Test
    fun pickingASpeedWritesItAndMovesTheSelection() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US"}""")
        setContent { TestApp(store = store) }
        openOptions()

        onNodeWithTag(optionsSpeedTestTag(MatchSpeed.INSTANT)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        onNodeWithTag(optionsSpeedTestTag(MatchSpeed.INSTANT)).assertIsSelected()
        onNodeWithTag(optionsSpeedTestTag(MatchSpeed.NORMAL)).assertIsNotSelected()
        assertTrue(
            store.stored.orEmpty().contains("\"${MatchSpeed.INSTANT.tag}\""),
            "the store still holds: ${store.stored.orEmpty()}",
        )
    }

    @Test
    fun theCransAreNamedInTheLanguageOnScreen() = runComposeUiTest {
        // The four labels are reached through `MatchSpeed.labelKey` rather than `StringKeys`, so
        // this is the only place the *drawn* word is asserted against a real bundle.
        setContent { TestApp(store = settingsFor(AppLocale.FR_FR)) }
        openOptions()

        assertTrue(isVisible("Vitesse d'animation"), "the label is not in French")
        assertTrue(isVisible("Instantanée"), "the crans are not in French")
    }

    @Test
    fun theBoardsAidIsOnUntilItIsTurnedOff() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US"}""")
        setContent { TestApp(store = store) }
        openOptions()

        // On by default: the digits are on screen either way, and the rule that combines them is
        // what a new player is still learning. See `LocalCaptureHints`.
        onNodeWithTag(OPTIONS_CAPTURE_HINTS_TEST_TAG).assertIsOn()

        onNodeWithTag(OPTIONS_CAPTURE_HINTS_TEST_TAG).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        onNodeWithTag(OPTIONS_CAPTURE_HINTS_TEST_TAG).assertIsOff()
        assertTrue(
            store.stored.orEmpty().contains("\"capture_hints\": false"),
            "the store still holds: ${store.stored.orEmpty()}",
        )
    }

    @Test
    fun theAidSaysWhatItDoesAndWhereItStops() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        // The note is not decoration: the label alone reads as "every capturing cell", which is
        // the thing this deliberately is not.
        assertTrue(isVisible("aiming at"), "the note does not say which cell it answers about")
        assertTrue(isVisible("wager"), "the note does not say where it stops")
    }

    private fun ComposeUiTest.openOptions() {
        awaitTitleChoice("new")
        onNodeWithTag(TITLE_OPTIONS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_SHEET_TEST_TAG) }
    }

    private fun ComposeUiTest.closeOptions() {
        onNodeWithTag(OPTIONS_CLOSE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(OPTIONS_SHEET_TEST_TAG) }
    }
}
