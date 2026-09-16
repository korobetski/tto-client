package com.tripletriad.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a card's hover line lands — see [BesideAnchor]. Pixels, as the provider is handed them. */
class BesideAnchorTest {
    private val beside = BesideAnchor(gap = GAP)

    private fun place(
        anchor: IntRect,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): IntOffset = beside.calculatePosition(anchor, WINDOW, direction, POPUP)

    @Test
    fun aFirstRowCardIsNamedBesideItAndNotOverTheMenusAboveIt() {
        // The collection's first row: the Type menu is the 40 px over this cell.
        val cell = IntRect(left = 200, top = 480, right = 288, bottom = 568)

        assertEquals(IntOffset(288 + GAP, 480), place(cell))
    }

    @Test
    fun aCardInTheLastColumnIsNamedOnItsOtherSide() {
        val cell = IntRect(left = 1200, top = 480, right = 1288, bottom = 568)

        assertEquals(IntOffset(1200 - GAP - POPUP.width, 480), place(cell))
    }

    @Test
    fun aCardAtTheFootOfTheWindowPushesItsLineUpOnlyAsFarAsTheEdge() {
        val cell = IntRect(left = 200, top = 860, right = 288, bottom = 900)

        assertEquals(IntOffset(288 + GAP, WINDOW.height - POPUP.height), place(cell))
    }

    @Test
    fun rightToLeftPrefersTheLeftSide() {
        val cell = IntRect(left = 600, top = 480, right = 688, bottom = 568)

        assertEquals(IntOffset(600 - GAP - POPUP.width, 480), place(cell, LayoutDirection.Rtl))
    }

    private companion object {
        const val GAP = 4
        val WINDOW = IntSize(1400, 900)
        val POPUP = IntSize(280, 80)
    }
}
