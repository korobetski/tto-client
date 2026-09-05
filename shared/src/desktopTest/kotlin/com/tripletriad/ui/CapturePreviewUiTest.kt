package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchView
import com.tripletriad.model.TurnOrder
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The aid on a board this test composed itself, so that what it should ring is not a matter of
 * which cards the referee happened to deal.
 *
 * `CapturePreviewTest` owns *which* cells are correct. What is here is the wiring — that the answer
 * reaches the tile, that it arrives while the card is still in the air rather than after it lands,
 * and that it is said in words as well as drawn as a colour.
 */
@OptIn(ExperimentalTestApi::class)
class CapturePreviewUiTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun draggingOverACellRingsTheCardItWouldTake() = runComposeUiTest {
        setContent { Board(hints = true) }

        assertFalse(previewed(ENEMY), "nothing is aimed yet")

        holdOver(CENTRE)

        assertTrue(previewed(ENEMY), "the card it would take was not ringed")
        assertFalse(previewed(FRIEND), "it ringed a card of the player's own")
        assertFalse(previewed(CENTRE), "it ringed the empty cell it is being aimed at")
    }

    @Test
    fun theRingIsSaidAsWellAsDrawn() = runComposeUiTest {
        setContent { Board(hints = true) }
        holdOver(CENTRE)

        // A colour is the ring's only other channel, so this sentence is the aid for anybody
        // reading the board through a screen reader rather than looking at it.
        assertTrue(
            stateOf(ENEMY)?.contains("captured") == true,
            "the ringed cell says nothing: ${stateOf(ENEMY)}",
        )
    }

    @Test
    fun theAidStaysAwayWhenItIsTurnedOff() = runComposeUiTest {
        setContent { Board(hints = false) }

        holdOver(CENTRE)

        assertFalse(previewed(ENEMY), "the setting is off and the board answered anyway")
    }

    /** Presses the hand card and holds it over [cell] without letting go. */
    private fun ComposeUiTest.holdOver(cell: Int) {
        val hand = handCardTestTag(CardColor.BLUE, 0)
        val from = centreOf(hand)
        val to = centreOf(tileTestTag(cell))

        onNodeWithTag(hand).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + (to - from) * (step.toFloat() / DRAG_STEPS))
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.centreOf(tag: String): Offset =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    private fun ComposeUiTest.stateOf(position: Int): String? =
        onAllNodesWithTag(tileTestTag(position), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.StateDescription) }

    private fun ComposeUiTest.previewed(position: Int): Boolean = stateOf(position) != null

    /**
     * One enemy card the hand card beats, one of the player's own that it also out-numbers, and an
     * empty centre between them. The friendly card is the case a preview must never ring.
     */
    @Composable
    private fun Board(hints: Boolean) {
        val board = Board()
            .place(ENEMY, weak.copy(owner = CardColor.RED), CardColor.RED)
            .place(FRIEND, weak, CardColor.BLUE)
        val view = MatchView(
            side = CardColor.BLUE,
            rules = GameRules(),
            board = board,
            ownHand = listOf(strong),
            opponentHand = emptyList(),
            order = TurnOrder(CardColor.BLUE),
            placement = 2,
            playableHandIndices = listOf(0),
        )

        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalCaptureHints provides hints,
        ) {
            TripleTriadTheme {
                PlayArea(
                    view = view,
                    selected = null,
                    layout = matchLayout(400.dp, 700.dp),
                    highlights = emptyMap(),
                    waves = emptyMap(),
                    onSelect = {},
                    onPlace = {},
                    onDrop = { _, _ -> },
                )
            }
        }
    }

    private val strong = card(1, power = 9)
    private val weak = card(2, power = 2)

    private fun card(number: Int, power: Int) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = "Test $number",
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = 1,
    )

    private companion object {
        const val ENEMY = 1
        const val CENTRE = 4
        const val FRIEND = 7
        const val DRAG_STEPS = 6
    }
}
