package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MatchUiTest {
    private val stub = PveStubServer()

    @Test
    fun theBoardHasNineCellsAndAFullPlayerHand() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(BOARD_TEST_TAG).assertExists()
        repeat(Board.SIZE) { onNodeWithTag(tileTestTag(it)).assertExists() }
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player should hold a full hand")
        assertTrue(
            handSize(CardColor.RED) >= HAND_SIZE - 1,
            "the opponent should have played at most once",
        )
        for (owner in CardColor.entries) {
            onNodeWithTag(handCardTestTag(owner, HAND_SIZE)).assertDoesNotExist()
        }
    }

    @Test
    fun theScoreStartsFiveFive() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals(LEVEL_SCORE)
    }

    @Test
    fun theBoardNamesTheOpponent() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(MATCH_OPPONENT_TEST_TAG).assertTextEquals("Triple Triad Master")
    }

    @Test
    fun theRulesInForceAreNamedOnTheBoard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("All Open")
    }

    @Test
    fun theRuleStripOpensToExplainWhatItNames() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        // Read unmerged: the strip is `clickable`, so it absorbs its children's semantics.
        val help = ruleHelpTestTag("RULE_ALL_OPEN")
        assertFalse(existsUnmerged(help), "the strip should start closed")

        onNodeWithTag(MATCH_RULES_TEST_TAG).performClick()
        assertTrue(existsUnmerged(help), "tapping the strip should explain its rules")
        onNodeWithTag(help, useUnmergedTree = true)
            .assertTextEquals("All Open — Both decks are placed face up.")

        onNodeWithTag(MATCH_RULES_TEST_TAG).performClick()
        assertFalse(existsUnmerged(help), "tapping it again should close it")
    }

    @Test
    fun theBoardShowsTheOpponentsPortrait() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(portraitTestTag(TEST_OPPONENT)).assertExists()
    }

    @Test
    fun pickingACardThenACellPlacesItAndPassesTheTurn() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        assertVisible("pick a cell", "selecting a card should prompt for a cell")

        onNodeWithTag(tileTestTag(CENTRE)).performClick()
        waitForIdle()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "the card should have left the hand")
    }

    @Test
    fun theOpponentPlaysByItself() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        // Whoever the flip favours, red will have played once the turn is back with blue and blue
        // has moved at least once — so drive one blue turn and wait.
        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.RED) < HAND_SIZE }

        assertTrue(handSize(CardColor.RED) < HAND_SIZE, "red never played")
    }

    @Test
    fun theOpponentsHandIsNeverSelectable() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        onNodeWithTag(handCardTestTag(CardColor.RED, 0)).performClick()
        waitForIdle()

        assertFalse(isVisible("pick a cell"), "clicking the opponent's hand selected something")
    }

    @Test
    fun placingOnATakenCellIsIgnored() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        val taken = playOneCard()
        awaitPlayer()
        val before = handSize(CardColor.BLUE)
        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        onNodeWithTag(tileTestTag(taken)).performClick()
        waitForIdle()

        // The click was swallowed rather than throwing, and the selection survived it.
        assertEquals(before, handSize(CardColor.BLUE), "an occupied cell accepted a card")
        assertVisible("pick a cell", "the selection should survive an illegal placement")
    }

    @Test
    fun capturesMoveTheScoreAndItAlwaysTotalsTen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        val seen = mutableListOf(score())
        while (!isFinished()) {
            playOneCard()
            assertTrue(totalIsTen(), "the two scores must always total 10, read ${score()}")
            seen += score()
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
            seen += score()
        }

        assertTrue(
            seen.any { it != HAND_SIZE to HAND_SIZE },
            "the score never left 5-5, so nothing was ever captured: $seen",
        )
    }

    @Test
    fun playingOutTheMatchProducesAResultAndAPayout() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        playOut()

        onNodeWithTag(MATCH_RESULT_TEST_TAG).assertExists()
        // `You win !` / `You lose...` / `Draw` — the bundle has no neutral "red wins", so the
        // result is phrased from blue's side. See `TurnLine`.
        assertTrue(
            isVisible("You win") || isVisible("You lose") || isVisible("Draw"),
            "a finished match must announce a result",
        )
        // Every result pays in this game, so the payout line is always there and always positive.
        assertVisible("+", "the payout should be shown")
        assertVisible("MGP", "the payout should name MGP")
    }

    @Test
    fun theRematchControlDealsAgain() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        playOut()
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        // A rematch is a fresh match, dealt by the referee — deck and all, which is why the deck
        // question comes back first. `OpponentUiTest` is where that is the assertion rather than
        // a step; here it is just the way to the second board.
        settleDeck()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player should be dealt again")
        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals(LEVEL_SCORE)
    }

    /**
     * **The same control, against an opponent that is never asked which deck to bring.**
     *
     * [theRematchControlDealsAgain] passes for a reason that is not the one it looks like: the deck
     * question comes back, so `deck` goes from an answer to null and back, and that movement is
     * what `MatchDestination`'s opening effect was keyed on. Twenty-eight of the roster's
     * opponents declare Random and are never asked — `deck` is null for the whole life of the
     * screen — so tapping Rematch cleared the match, moved nothing the effect was watching, and
     * left the board on "Loading" waiting for a deal nobody had asked the referee for.
     *
     * The opponent here is chosen for exactly that property and for costing a novice nothing to
     * challenge; what is being tested is the absence of the deck question, not who asks it.
     */
    @Test
    fun theRematchControlDealsAgainWhenNoDeckIsAsked() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch(RANDOM_OPPONENT)

        assertFalse(
            exists(DECK_SELECT_CHOOSE_TEST_TAG),
            "the fixture is pointless unless this opponent skips the deck question",
        )
        playOut()
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()

        // Straight to a board: there is no deck question to answer on the way.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() && exists(BOARD_TEST_TAG) }
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player should be dealt again")
        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals(LEVEL_SCORE)
    }

    @Test
    fun leavingTheResultPanelReturnsToTheOpponentList() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        playOut()
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()

        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()
    }

    @Test
    fun theUiIsInTheChosenLanguage() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.FR_FR), server = stub.connection) }
        startMatch()

        assertTrue(
            isVisible("choisissez une carte") || isVisible("joue"),
            "the turn line should be French",
        )
        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("Toutes sur table")
        assertFalse(isVisible("pick a card"), "no English should be left on the board screen")
    }

    @Test
    fun aMissingStringFallsBackToEnglishWithoutDisturbingTheRest() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.DE_DE), server = stub.connection) }
        startMatch()

        // `RULE_ALL_OPEN` resolves in German — to the English words, because the imported de_DE
        // bundle really does say "All Open" there. That is the shipped data, not a fallback.
        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("All Open")
        playOut()
        assertTrue(
            isVisible("Du hast gewonnen") || isVisible("Sie verlieren") || isVisible("Zeichnen"),
            "the outcome is one of the keys German does define",
        )
    }

    private companion object {
        const val CENTRE = 4

        /**
         * An opponent whose declared rules include Random, so the deck question is never asked.
         *
         * Named here rather than picked at random from the roster because it also has to be one a
         * novice can afford to challenge — see `PveStubServer.undealtReason`.
         */
        const val RANDOM_OPPONENT = "maisenta"

        const val LEVEL_SCORE = "5 — 5"
    }
}
