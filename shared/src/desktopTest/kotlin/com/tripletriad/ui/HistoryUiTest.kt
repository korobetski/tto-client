package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.storage.InMemoryDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The whole path the record takes: a match the referee settles, a document on this device, and a
 * row under the profile.
 *
 * `MatchJournalTest` owns the conversions. What is here is the wiring that was missing — the
 * repository and its tests have been in the tree since before this screen existed and nothing
 * called them.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryUiTest {
    private val stub = PveStubServer()
    private val history = InMemoryDocumentStore()

    @Test
    fun aCharacterWhoHasPlayedNothingIsToldSoRatherThanShownAnEmptyList() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                history = history,
                server = stub.connection,
            )
        }
        openDashboard()
        openHistory()

        onNodeWithTag(HISTORY_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(HISTORY_LIST_TEST_TAG))
        assertEquals(0, history.writes, "an unplayed character wrote a history document")
    }

    @Test
    fun aMatchPlayedThroughTheUiBecomesARowUnderTheProfile() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                history = history,
                server = stub.connection,
            )
        }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_RESULT_TEST_TAG) }
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()

        backToDashboard()
        openHistory()

        onNodeWithTag(HISTORY_LIST_TEST_TAG).assertExists()
        // Named as the roster names them, not by the `iconID` the row actually stores.
        assertTrue(isVisible("Triple Triad Master"), "the row did not resolve the opponent")
        // And the summary above it, which is the profile's own counters read over the rows.
        onNodeWithTag(HISTORY_TALLY_TEST_TAG).assertExists()
    }

    @Test
    fun theRowSurvivesLeavingTheScreenBecauseItIsOnTheDevice() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                history = history,
                server = stub.connection,
            )
        }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_RESULT_TEST_TAG) }
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()
        backToDashboard()

        openHistory()
        onNodeWithTag(HISTORY_LIST_TEST_TAG).assertExists()

        // Out to the lobby and back in. The list is read from the document rather than held by the
        // screen, which is the whole reason it is worth writing one.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(STATS_TABLE_TEST_TAG) }
        onNodeWithTag(STATS_HISTORY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(HISTORY_LIST_TEST_TAG) }
        assertTrue(history.writes > 0, "nothing was written to the history store")
    }

    @Test
    fun theFormStripStaysAwayUntilThereIsAFormToShow() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                history = history,
                server = stub.connection,
            )
        }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_RESULT_TEST_TAG) }
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()
        backToDashboard()
        openHistory()

        // One match is not a trend, and three pips would claim to be one.
        assertFalse(exists(HISTORY_FORM_TEST_TAG), "a single result drew a form strip")
    }
}
