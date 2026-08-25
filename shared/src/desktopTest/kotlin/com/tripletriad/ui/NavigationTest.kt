package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    private val stub = PveStubServer()

    @Test
    fun theSplashHoldsWhileStartupIsUnfinishedAndNamesItsPhase() = runComposeUiTest {
        setContent { TestApp(store = NeverAnswers) }
        waitForIdle()

        assertFalse(isVisible("Play"), "the menu was up although startup never finished")
        onNodeWithTag(SPLASH_PHASE_TEST_TAG).assertTextEquals(SPLASH_LINES.first())
    }

    @Test
    fun everyPhaseHasAStringRatherThanItsKey() {
        val strings = runBlocking { loadStrings(AppLocale.EN_US) }
        assertEquals(SPLASH_LINES.size, StartupPhase.entries.size, "one line per phase")
        for (phase in StartupPhase.entries) {
            val line = strings[phase.labelKey]
            assertTrue(line in SPLASH_LINES, "$phase resolved to \"$line\"")
        }
    }

    @Test
    fun theSplashGivesWayToTheMenuOnItsOwn() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).assertTextEquals("Options")
        onNodeWithTag(MENU_QUIT_TEST_TAG).assertTextEquals("Quit")
    }

    @Test
    fun playWithNoCharacterAsksForOne() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        onNodeWithTag(
            MENU_PROFILE_TEST_TAG,
        ).assertTextEquals("No character yet — create one to play.")

        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        onNodeWithTag(PROFILE_NEW_TEST_TAG).assertTextEquals("New Game")
    }

    @Test
    fun playReachesABoardAndTheChevronComesBack() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        awaitOpponents()

        backToDashboard()

        // The account screen, not the local profile list: with a server signed in, "choose a
        // character" is answered by the account, and that is where stepping back from the
        // dashboard lands.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    @Test
    fun playWithACharacterLoadedGoesStraightToItsDashboard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        awaitDashboard()
    }

    @Test
    fun everyDashboardEntryOpensItsScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        val entries = listOf(
            DASHBOARD_PLAY_TEST_TAG to OPPONENT_LIST_TEST_TAG,
            DASHBOARD_STATS_TEST_TAG to STATS_TABLE_TEST_TAG,
            DASHBOARD_DECKS_TEST_TAG to DECK_LIST_TEST_TAG,
            DASHBOARD_INVENTORY_TEST_TAG to INVENTORY_EMPTY_TEST_TAG,
            DASHBOARD_HELP_TEST_TAG to HELP_LIST_TEST_TAG,
        )
        for ((entry, landmark) in entries) {
            openFromDashboard(entry, landmark)
            backToDashboard()
        }
    }

    @Test
    fun everyNavigationBarEntryOpensItsScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        val entries = listOf(
            "play" to OPPONENT_LIST_TEST_TAG,
            "cards" to CARD_GRID_TEST_TAG,
            "store" to SHOP_LIST_TEST_TAG,
        )
        for ((tab, landmark) in entries) {
            openFromBar(tab, landmark)
            backToDashboard()
        }

        // And Home is on the bar too, from wherever the player happens to be.
        openFromBar("store", SHOP_LIST_TEST_TAG)
        openFromBar("home", DASHBOARD_PLAY_TEST_TAG)
    }

    @Test
    fun theNavigationBarIsAbsentOnTheMenuAndDuringAMatch() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        awaitMenu()
        assertFalse(exists(navTestTag("home")), "the menu has no character and so no bar")

        startMatch()
        assertFalse(exists(navTestTag("home")), "a match should not be leavable by a bar entry")
    }

    @Test
    fun logoutReturnsToTheCharacterList() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_LOGOUT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
    }

    @Test
    fun optionsOpensAndBackReturnsToTheMenu() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()

        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()
        assertTrue(isVisible("Language"), "the options screen did not open")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    @Test
    fun quitCallsTheHostRatherThanDoingAnythingItself() = runComposeUiTest {
        var quits = 0
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), onQuit = { quits++ }) }
        awaitMenu()

        onNodeWithTag(MENU_QUIT_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(1, quits)
        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    @Test
    fun theMenuIsInTheStoredLanguage() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.FR_FR)) }
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Jouer")
        onNodeWithTag(MENU_QUIT_TEST_TAG).assertTextEquals("Quitter")
    }

    @Test
    fun aFirstRunWithNoSettingsFileStillStarts() = runComposeUiTest {
        val store = InMemorySettingsStore()
        setContent { TestApp(store = store) }

        awaitMenu()

        assertEquals(1, store.writes, "the first run should have persisted a file")
    }

    @Test
    fun aStoreThatThrowsDoesNotStrandTheSplash() = runComposeUiTest {
        setContent {
            TestApp(store = InMemorySettingsStore(failure = IllegalStateException("no permission")))
        }

        awaitMenu()
    }

    private object NeverAnswers : SettingsStore {
        override suspend fun read(): String? = awaitCancellation()

        override suspend fun write(text: String) = awaitCancellation()
    }

    private companion object {
        val SPLASH_LINES = listOf(
            "reading settings…",
            "loading cards…",
            "loading artwork…",
            "loading opponents…",
            "ready",
        )
    }
}
