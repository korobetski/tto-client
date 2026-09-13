package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The match on a 1920x1080 window at density 1 — the size a desktop or a browser tab gives it, and
 * the one [AdaptiveUiTest]'s 1024x768 window never reaches.
 */
@OptIn(ExperimentalTestApi::class)
class MatchArenaUiTest {
    private val stub = PveStubServer()

    private fun arena(block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT), density = Density(1f)) {
            setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
            startMatch()
            block()
        }

    @Test
    fun aLargeLandscapeWindowTradesThePanelForLargerCards() = arena {
        assertFalse(exists(MATCH_SIDE_TEST_TAG), "the arena has no side panel")
        assertTrue(exists(MATCH_RULES_TEST_TAG), "so the rules go under the score instead")

        val tile = onNodeWithTag(tileTestTag(0)).getUnclippedBoundsInRoot()
        val width = tile.right - tile.left
        assertTrue(
            width > CardSpriteWidth * MIN_GROWTH,
            "a 1080p board should draw its tiles well past the authored width: $width",
        )
    }

    @Test
    fun theOpponentIsNamedOnceOverTheirOwnHand() = arena {
        val names = onAllNodesWithTag(MATCH_OPPONENT_TEST_TAG).fetchSemanticsNodes()
        assertEquals(1, names.size, "one name, not the header's and the seat's")

        val name = onNodeWithTag(MATCH_OPPONENT_TEST_TAG).getUnclippedBoundsInRoot()
        val card = onNodeWithTag(handCardTestTag(CardColor.RED, 0)).getUnclippedBoundsInRoot()
        val board = onNodeWithTag(tileTestTag(0)).getUnclippedBoundsInRoot()
        assertTrue(name.bottom <= card.top, "the name sits over the hand: $name vs $card")
        assertTrue(name.right <= board.left, "and beside the board, not over it: $name vs $board")
        onNodeWithText("Your hand").assertExists("the player's own hand is labelled too")
    }

    @Test
    fun theHeaderStillReadsAsTheScoreAndTheClock() = arena {
        val (blue, red) = score()
        assertEquals(TOTAL_CARDS, blue + red, "a score of $blue — $red")
        assertTrue(exists(TURN_TIMER_TEST_TAG), "the ring's track is always drawn")

        awaitPlayer()
        assertTrue(exists(TURN_TIMER_FILL_TEST_TAG), "and its fill while the player's clock runs")
    }

    @Test
    fun theHeadersLogKeepsOnlyTheLatestMoves() = arena {
        repeat(2) { playOneCard() }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.RED) <= HAND_SIZE - 2 }

        val entries = onAllNodesWithTag(MATCH_LOG_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
            .children
            .count { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .any { "→" in it.text }
            }
        assertEquals(LOG_LINES, entries, "four placements made, three shown")
    }

    private companion object {
        const val WIDTH = 1920f
        const val HEIGHT = 1080f

        const val MIN_GROWTH = 1.5f

        const val TOTAL_CARDS = 10

        const val LOG_LINES = 3
    }
}
