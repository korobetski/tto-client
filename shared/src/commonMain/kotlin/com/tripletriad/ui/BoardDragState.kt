package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import com.tripletriad.model.Card

internal class BoardDragState {
    var card: Card? by mutableStateOf(null)
        private set

    var pointer: Offset by mutableStateOf(Offset.Unspecified)
        private set

    var origin: Offset by mutableStateOf(Offset.Zero)

    private val cells = mutableStateMapOf<Int, Rect>()

    val isDragging: Boolean get() = card != null

    /**
     * The cell a mouse is resting on, or null on a device that has no pointer to rest.
     *
     * Separate from the drag, because they are two ways of aiming at the same cell and only one of
     * them exists on a phone. A drag is how a finger aims; hovering is how a mouse does, and a
     * desktop player who taps a card and then reads the board is aiming with neither until they
     * move the pointer over a cell.
     */
    var hover: Int? by mutableStateOf(null)
        private set

    fun enter(position: Int) {
        hover = position
    }

    fun leave(position: Int) {
        if (hover == position) hover = null
    }

    fun registerCell(position: Int, bounds: Rect) {
        cells[position] = bounds
    }

    fun unregisterCell(position: Int) {
        cells.remove(position)
    }

    fun start(card: Card, at: Offset) {
        this.card = card
        pointer = at
    }

    fun moveTo(at: Offset) {
        if (isDragging) pointer = at
    }

    fun hovered(): Int? {
        if (!isDragging || !pointer.isSpecified) return null
        return cells.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    /**
     * The cell being aimed at by whichever gesture is in play — the drag first.
     *
     * A drag wins over a hover because a dragged card is under the pointer: the mouse is over the
     * cell it is carrying the card to, and answering with the hover would be answering with the
     * same cell by the longer route. On a phone there is never a hover to fall back to.
     */
    fun aimed(): Int? = hovered() ?: hover

    fun drop(): Pair<Card, Int>? {
        val dropped = card
        val target = hovered()
        cancel()
        return if (dropped != null && target != null) dropped to target else null
    }

    fun cancel() {
        card = null
        pointer = Offset.Unspecified
    }
}

@Composable
internal fun rememberBoardDragState(): BoardDragState = remember { BoardDragState() }
