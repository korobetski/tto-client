package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class TurnTimerTest {
    private val cards = runBlocking { loadCardCatalog() }

    private val formats = runBlocking { loadFormatCatalog() }
    private val format = formats[FF14_FORMAT]!!

    private val npcs = runBlocking { loadNpcCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private val opponent = npcs
        .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    private fun ComposeUiTest.openMatch(limit: kotlin.time.Duration) {
        setContent {
            TripleTriadTheme {
                CompositionLocalProvider(LocalStrings provides english) {
                    MatchScreen(
                        catalog = cards,
                        profile = freshSave(),
                        npc = opponent,
                        format = format,
                        // The bare script a local match now needs. Every field is a default but
                        // `counted`, which is what makes this a match `MatchScreen` will still
                        // play: an ordinary one is refereed and its clock lives on
                        // `PveMatchScreen`. The clock itself is `turnClock`, shared by both, so
                        // this exercises the same code either way.
                        script = MatchScript(
                            speakerKey = opponent.nameKey,
                            counted = false,
                        ),
                        onExit = {},
                        turnLimit = limit,
                    )
                }
            }
        }
        awaitPlayer()
    }

    @Test
    fun aTurnThatRunsOutPlaysACardByItself() = runComposeUiTest {
        openMatch(limit = SHORT)
        val before = handSize(CardColor.BLUE)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.BLUE) < before }

        assertEquals(before - 1, handSize(CardColor.BLUE), "one card, not the whole hand")
    }

    @Test
    fun theBarIsUpOnlyOnThePlayersTurn() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        onNodeWithTag(TURN_TIMER_TEST_TAG).assertExists()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            exists(TURN_TIMER_FILL_TEST_TAG) || !isPlayerTurn() || isFinished()
        }
        assertTrue(exists(TURN_TIMER_FILL_TEST_TAG), "the player is to move, so it should run")

        // Play, and the turn passes to an opponent that thinks for `OPPONENT_PAUSE_MS`.
        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            !isPlayerTurn() || isFinished() || !exists(TURN_TIMER_FILL_TEST_TAG)
        }

        if (!isPlayerTurn() && !isFinished()) {
            assertFalse(exists(TURN_TIMER_FILL_TEST_TAG), "the clock should not run on red's turn")
        }
        onNodeWithTag(TURN_TIMER_TEST_TAG).assertExists("the track stays, so the bar does not jump")
    }

    @Test
    fun aPlayerWhoMovesInTimeKeepsTheirChoice() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        val played = playOneCard()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "exactly the card the player chose")
        assertTrue(played in 0 until com.tripletriad.model.Board.SIZE)
    }

    @Test
    fun aFinishedMatchHasNoClockRunning() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        playOut()

        assertTrue(isFinished())
        assertFalse(exists(TURN_TIMER_FILL_TEST_TAG), "the clock should be stopped")
    }

    private companion object {
        val SHORT = 300.milliseconds
    }
}
