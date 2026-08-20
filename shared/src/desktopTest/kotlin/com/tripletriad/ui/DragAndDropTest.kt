package com.tripletriad.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DragAndDropTest {
    private val stub = PveStubServer()

    private fun ComposeUiTest.centreOf(tag: String): Offset =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    private fun ComposeUiTest.topLeftOf(tag: String): Offset =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.topLeft

    private fun ComposeUiTest.dragHandCard(slot: Int, target: Offset) {
        val tag = handCardTestTag(CardColor.BLUE, slot)
        val from = topLeftOf(tag)
        val to = target - from
        onNodeWithTag(tag).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + (to - center) * (step.toFloat() / DRAG_STEPS))
            }
            up()
        }
        waitForIdle()
    }

    @Test
    fun aCardDraggedOntoACellIsPlayed() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = handSize(CardColor.BLUE)

        dragHandCard(slot = 0, target = centreOf(tileTestTag(CENTRE_CELL)))

        assertEquals(before - 1, handSize(CardColor.BLUE), "the card should have left the hand")
        assertTrue(placementsMade() >= 1, "and should be on the board")
    }

    @Test
    fun aCardDroppedOffTheBoardStaysInTheHand() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = handSize(CardColor.BLUE)
        // Counted rather than assumed to be zero: red plays first whenever it wins the coin flip,
        // and `startMatch` returns once it is *blue's* turn — which can be one placement in.
        val onBoard = placementsMade()

        // The top-left corner of the window: outside every cell, and reachable in one gesture.
        dragHandCard(slot = 0, target = Offset.Zero)

        assertEquals(before, handSize(CardColor.BLUE), "nothing should have been played")
        assertEquals(onBoard, placementsMade(), "and the board should be untouched")
    }

    @Test
    fun aCellThatIsAlreadyTakenRefusesTheDrop() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        // Fill a cell first, by dragging onto it.
        dragHandCard(slot = 0, target = centreOf(tileTestTag(CENTRE_CELL)))
        awaitPlayer()
        val taken = centreOf(tileTestTag(CENTRE_CELL))
        val before = handSize(CardColor.BLUE)

        dragHandCard(slot = 0, target = taken)

        assertEquals(before, handSize(CardColor.BLUE), "a taken cell should refuse a second card")
    }

    @Test
    fun theOpponentsHandCannotBeDragged() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = handSize(CardColor.RED)
        val onBoard = placementsMade()

        val tag = handCardTestTag(CardColor.RED, 0)
        val from = topLeftOf(tag)
        val to = centreOf(tileTestTag(CENTRE_CELL)) - from
        onNodeWithTag(tag).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + (to - center) * (step.toFloat() / DRAG_STEPS))
            }
            up()
        }
        waitForIdle()

        assertEquals(before, handSize(CardColor.RED), "red's hand is not the player's to move")
        assertEquals(onBoard, placementsMade(), "and nothing should have reached the board")
    }

    @Test
    fun tappingStillPlaysACardAlongsideTheDrag() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        val played = playOneCard()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "the tap path should still work")
        assertTrue(played in 0 until Board.SIZE)
    }

    private companion object {
        const val CENTRE_CELL = 4

        const val DRAG_STEPS = 8
    }
}
