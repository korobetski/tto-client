package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coming back to a match the server still has open, from the roster rather than by guessing which
 * opponent it was against.
 *
 * The gap this closes: `PveSession.resume` takes an opponent, and every caller but one passes it —
 * so a match interrupted by a closed app existed on the server and had no door on this side. The
 * roster asks the question with no opponent in it.
 */
@OptIn(ExperimentalTestApi::class)
class ResumeMatchUiTest {
    private val stub = PveStubServer()

    @Test
    fun aMatchLeftInProgressIsOfferedOnTheRosterAndComesBackWhereItWas() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOneCard()
        val handWhenLeft = handSize(CardColor.BLUE)
        assertTrue(handWhenLeft < HAND_SIZE, "the fixture should leave a card on the board")

        leaveMatch()
        awaitOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPPONENT_RESUME_TEST_TAG) }
        onNodeWithTag(OPPONENT_RESUME_TEST_TAG).performClick()

        awaitBoard()
        awaitPlayer()
        assertEquals(
            handWhenLeft,
            handSize(CardColor.BLUE),
            "resuming should return the board that was left, not deal a new one",
        )
    }

    /** The button names who is waiting: a roster of ninety rows is no place for "Resume". */
    @Test
    fun theOfferNamesTheOpponentItWouldGoBackTo() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOneCard()

        leaveMatch()
        awaitOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPPONENT_RESUME_TEST_TAG) }
        onNodeWithTag(
            OPPONENT_RESUME_TEST_TAG,
        ).assertTextEquals("Resume against Triple Triad Master")
    }

    @Test
    fun aRosterWithNothingInProgressOffersNoWayBack() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openDashboard()

        openOpponents()

        // The roster has been drawn and the question has been asked — waiting on the list rather
        // than on the answer would pass before the request had been made, whatever the answer was.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RANDOM_OPPONENT_TEST_TAG) }
        waitForIdle()
        assertFalse(exists(OPPONENT_RESUME_TEST_TAG), "nothing has been started to come back to")
    }

    /**
     * A settled match is not a match in progress, and the two are told apart by status rather than
     * by absence: the server keeps a finished match findable for a couple of minutes so a player
     * killed between the last card and the result still sees it — `PveStore.recentFor` — and the
     * stub answers `/pve/matches/active` with whatever it last held, exactly as that window does.
     * Without the `isOver` guard this offers to "resume" a match into its own result panel.
     */
    @Test
    fun aMatchAlreadyOverIsNotOfferedAsSomethingToResume() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isFinished() }

        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RANDOM_OPPONENT_TEST_TAG) }
        waitForIdle()
        assertFalse(exists(OPPONENT_RESUME_TEST_TAG), "the match is over, not paused")
    }

    /**
     * Without a server there is no match to be in the middle of, and no request to make. The
     * roster is otherwise itself — this is the `server == null` path CLAUDE.md calls a supported
     * configuration rather than a degraded one.
     */
    @Test
    fun anOfflineRosterAsksNothingAndOffersNothing() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(RANDOM_OPPONENT_TEST_TAG) }
        waitForIdle()
        assertFalse(exists(OPPONENT_RESUME_TEST_TAG), "there is no server holding a match")
    }

    @Test
    fun theExitArrowAsksBeforeItLetsGoOfALiveBoard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_LEAVE_TEST_TAG) }

        // The sentence is the point of the sheet: a match begun is counted, and this is the only
        // place the player is told that walking off it is not the same as finishing it.
        assertTrue(isVisible("counts as a forfeit"), "the sheet does not say what leaving costs")
        assertTrue(isVisible("opponent list"), "the sheet does not say where the board went")
        assertTrue(exists(BOARD_TEST_TAG), "the board should still be there behind the question")
    }

    @Test
    fun answeringNoKeepsThePlayerOnTheBoard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOneCard()
        val handWhenAsked = handSize(CardColor.BLUE)

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_LEAVE_CANCEL_TEST_TAG) }
        onNodeWithTag(MATCH_LEAVE_CANCEL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_LEAVE_TEST_TAG) }

        awaitPlayer()
        assertEquals(
            handWhenAsked,
            handSize(CardColor.BLUE),
            "cancelling the question should change nothing at all",
        )
    }

    @Test
    fun theQuestionIsNotAskedOnceTheMatchIsOver() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_RESULT_TEST_TAG) }

        // `endedMatches` has moved and the result is paid, so the arrow is plain navigation again.
        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        awaitOpponents()

        assertFalse(exists(MATCH_LEAVE_TEST_TAG), "a settled match has nothing to warn about")
    }
}
