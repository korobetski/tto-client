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
