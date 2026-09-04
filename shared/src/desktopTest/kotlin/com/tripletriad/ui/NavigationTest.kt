package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.CLIENT_VERSION
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.storage.InMemoryDocumentStore
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

        assertFalse(
            exists(TITLE_SCREEN_TEST_TAG),
            "the title screen was up although startup never finished",
        )
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
    fun theSplashGivesWayToTheTitleScreenOnItsOwn() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        awaitTitleChoice("new")

        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals(NO_CHARACTER)
        onNodeWithTag(titleChoiceTestTag("new")).assertTextEquals("New Game")
        // Discreet, in the corner, and on the first screen — which is where a player who
        // is about to report something goes looking for it.
        onNodeWithTag(TITLE_VERSION_TEST_TAG).assertTextEquals("v$CLIENT_VERSION")
    }

    @Test
    fun withNoCharacterTheScreenIsNotATapTargetAndSaysWhatToDoInstead() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        awaitTitleChoice("new")

        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals(NO_CHARACTER)
        assertFalse(exists(TITLE_CONTINUE_TEST_TAG), "a tap had nowhere to go")

        // Straight to creation, not to a list with nothing in it.
        onNodeWithTag(titleChoiceTestTag("new")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }
    }

    @Test
    fun playReachesABoardAndTheChevronComesBack() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        leaveMatch()
        awaitOpponents()

        backToDashboard()

        // And the lobby has no chevron of its own: it is the root of a session, and the way
        // out of one is to end it rather than to step back out of it.
        assertFalse(exists(SCREEN_BACK_TEST_TAG), "the lobby offered a way back")

        // The account screen, not the local profile list: with a server signed in, "choose a
        // character" is answered by the account, and that is where signing out lands.
        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitTitle()
    }

    @Test
    fun aTitleScreenWithSomebodyToOfferOffersThemRatherThanCreation() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()

        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()

        // "New Game" was the only thing on offer on the way in. There is a `.sav` now, so
        // the same screen offers the list instead — and it leads back to the same lobby.
        loadCharacter(documents)
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
            DASHBOARD_QUESTS_TEST_TAG to QUESTS_LIST_TEST_TAG,
            DASHBOARD_AUCTION_TEST_TAG to AUCTION_SCREEN_TEST_TAG,
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
    fun theNavigationBarIsAbsentOnTheTitleScreenAndDuringAMatch() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        awaitTitle()
        assertFalse(exists(navTestTag("home")), "the title screen has no bar to leave by")

        startMatch()
        assertFalse(exists(navTestTag("home")), "a match should not be leavable by a bar entry")
    }

    @Test
    fun logoutReturnsToTheCharacterList() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        signOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
    }

    @Test
    fun theSettingsAreASheetOverTheTitleScreenRatherThanAScreenPastIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        awaitTitleChoice("new")

        onNodeWithTag(TITLE_OPTIONS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_SHEET_TEST_TAG) }
        assertTrue(isVisible("Language"), "the settings did not open")

        onNodeWithTag(OPTIONS_CLOSE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(OPTIONS_SHEET_TEST_TAG) }

        // Never left, which is the whole point of a sheet here: the language picker has to
        // be reachable before there is an account, and reaching it must not cost the
        // screen it was reached from. See [OptionsSheet].
        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals(NO_CHARACTER)
    }

    @Test
    fun theSettingsAreReachableFromTheLobbyTooAndTheSessionSurvivesThem() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_MENU_TEST_TAG).performClick()
        onNodeWithTag(DASHBOARD_OPTIONS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_SHEET_TEST_TAG) }

        onNodeWithTag(OPTIONS_CLOSE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(OPTIONS_SHEET_TEST_TAG) }
        // A `Screen.OPTIONS` could not have promised this: its `up` was one destination
        // whichever door had been used, so one of the two callers was always sent
        // somewhere it had not come from.
        awaitDashboard()
    }

    @Test
    fun quitCallsTheHostRatherThanDoingAnythingItself() = runComposeUiTest {
        var quits = 0
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), onQuit = { quits++ }) }
        awaitTitleChoice("new")

        onNodeWithTag(TITLE_QUIT_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(1, quits)
        onNodeWithTag(TITLE_PROMPT_TEST_TAG).assertTextEquals(NO_CHARACTER)
    }

    @Test
    fun theTitleScreenIsInTheStoredLanguage() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.FR_FR)) }
        awaitTitleChoice("new")

        onNodeWithTag(TITLE_PROMPT_TEST_TAG)
            .assertTextEquals("Aucun personnage — créez-en un pour jouer.")
        onNodeWithTag(titleChoiceTestTag("new")).assertTextEquals("Nouvelle Partie")
    }

    @Test
    fun aFirstRunWithNoSettingsFileStillStarts() = runComposeUiTest {
        val store = InMemorySettingsStore()
        setContent { TestApp(store = store) }

        awaitTitle()

        assertEquals(1, store.writes, "the first run should have persisted a file")
    }

    @Test
    fun aStoreThatThrowsDoesNotStrandTheSplash() = runComposeUiTest {
        setContent {
            TestApp(store = InMemorySettingsStore(failure = IllegalStateException("no permission")))
        }

        awaitTitle()
    }

    private object NeverAnswers : SettingsStore {
        override suspend fun read(): String? = awaitCancellation()

        override suspend fun write(text: String) = awaitCancellation()
    }

    private companion object {
        const val NO_CHARACTER = "No character yet — create one to play."

        val SPLASH_LINES = listOf(
            "reading settings…",
            "loading cards…",
            "loading artwork…",
            "loading opponents…",
            "ready",
        )
    }
}
