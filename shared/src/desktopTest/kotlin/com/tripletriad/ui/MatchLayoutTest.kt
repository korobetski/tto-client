package com.tripletriad.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchLayoutTest {
    @Test
    fun landscapePutsTheHandsBesideTheBoardInTwoColumns() {
        val layout = matchLayout(width = 914.dp, height = 385.dp)

        assertTrue(layout.landscape, "wider than tall is landscape")
        assertEquals(2, layout.handColumns, "a landscape hand is a block, not a strip")
        assertEquals(
            LANDSCAPE_HAND_ROWS,
            layout.handRows,
            "five cards over two columns needs three rows",
        )
    }

    @Test
    fun portraitPutsTheHandsAboveAndBelowInOneStrip() {
        val layout = matchLayout(width = 411.dp, height = 890.dp)

        assertTrue(!layout.landscape, "taller than wide is portrait")
        assertEquals(HAND_SIZE, layout.handColumns, "a portrait hand is one row of five")
        assertEquals(1, layout.handRows)
    }

    @Test
    fun aSquareViewportCountsAsLandscape() {
        // Not an important choice, but it has to be *a* choice: `width >= height`.
        assertTrue(matchLayout(600.dp, 600.dp).landscape)
    }

    @Test
    fun theArrangementAlwaysFitsInTheSpaceItWasGiven() {
        for ((width, height) in VIEWPORTS) {
            assertFits(width, height, matchLayout(width, height))
        }
    }

    /** The same, with the seat headers counted — they are fixed height, and cards are not. */
    @Test
    fun theArenaAlwaysFitsInTheSpaceItWasGivenSeatsIncluded() {
        for ((width, height) in VIEWPORTS) {
            val layout = matchLayout(width, height, arena = true)
            assertFits(width, height, layout)
            assertTrue(
                layout.boardScale >= layout.scale,
                "$width x $height: board tiles must never be smaller than hand cards",
            )
        }
    }

    @Test
    fun theArenaDrawsPastTheAuthoredSizeWhereTheWindowHasRoom() {
        val plain = matchLayout(FULL_HD_PLAY_WIDTH, FULL_HD_PLAY_HEIGHT)
        val arena = matchLayout(FULL_HD_PLAY_WIDTH, FULL_HD_PLAY_HEIGHT, arena = true)

        assertEquals(1f, plain.boardScale, "outside the arena the cards stop at the authored size")
        assertTrue(arena.scale > 1f, "a 1080p arena should draw hand cards past it: ${arena.scale}")
        assertTrue(
            arena.boardScale > arena.scale,
            "and the board larger again: ${arena.boardScale} vs ${arena.scale}",
        )
    }

    @Test
    fun theArenaStopsAtTheHighDefinitionArtWithTheHandsBelowIt() {
        val huge = matchLayout(4000.dp, 2000.dp, arena = true)

        assertEquals(2f, huge.boardScale, "208x256 is the largest art there is to draw")
        assertTrue(huge.scale < huge.boardScale, "the hands stay smaller than the board")
    }

    @Test
    fun onlyTheArenaReservesRoomForSeats() {
        assertEquals(0.dp, matchLayout(FULL_HD_PLAY_WIDTH, FULL_HD_PLAY_HEIGHT).seatHeight)
        assertEquals(
            SeatHeaderHeight + SeatGap,
            matchLayout(FULL_HD_PLAY_WIDTH, FULL_HD_PLAY_HEIGHT, arena = true).seatHeight,
        )
    }

    @Test
    fun theChromeFollowsTheWindowsSizeAndShape() {
        assertEquals(MatchChrome.ARENA, matchChrome(wide = true, 1920.dp, 1080.dp))
        assertEquals(
            MatchChrome.PANEL,
            matchChrome(wide = true, 1100.dp, 768.dp),
            "every UI test's window keeps the panel",
        )
        assertEquals(
            MatchChrome.PANEL,
            matchChrome(wide = true, 1920.dp, 600.dp),
            "too short for seats over the hands",
        )
        assertEquals(
            MatchChrome.PANEL,
            matchChrome(wide = true, 1280.dp, 1920.dp),
            "large but upright would stack the seats",
        )
        assertEquals(
            MatchChrome.COMPACT,
            matchChrome(wide = true, 890.dp, 411.dp),
            "a phone on its side has height for neither",
        )
        assertEquals(MatchChrome.COMPACT, matchChrome(wide = false, 1920.dp, 1080.dp))
    }

    private fun assertFits(width: Dp, height: Dp, layout: MatchLayout) {
        // Only meaningful above the floor: below it the cards are already as small as they
        // are allowed to get and overflow is preferred to illegible cards.
        if (layout.scale <= MIN_TESTED_SCALE) return

        val (usedWidth, usedHeight) = footprint(layout)
        assertTrue(
            usedWidth <= width + TOLERANCE,
            "$width x $height: needs $usedWidth across, has $width",
        )
        assertTrue(
            usedHeight <= height + TOLERANCE,
            "$width x $height: needs $usedHeight down, has $height",
        )
    }

    @Test
    fun theScaleGrowsWithTheViewportUntilItReachesTheAuthoredSize() {
        val small = matchLayout(400.dp, 200.dp).scale
        val medium = matchLayout(800.dp, 400.dp).scale
        val huge = matchLayout(4000.dp, 2000.dp).scale

        assertTrue(small < medium, "a bigger viewport should draw bigger cards")
        assertEquals(1f, huge, "cards never exceed the 88x118 they were authored at")
    }

    @Test
    fun theBoardIsNeverSmallerThanTheHandsAndFillsWhatTheyLeave() {
        // Portrait: a five-card strip is width-bound, so the board — three across — has height
        // to spare and must use it. This is the case that left a third of the screen empty.
        val portrait = matchLayout(411.dp, 890.dp)
        assertTrue(
            portrait.boardScale > portrait.scale,
            "portrait board ${portrait.boardScale} should exceed hand ${portrait.scale}",
        )

        for ((width, height) in VIEWPORTS) {
            val layout = matchLayout(width, height)
            assertTrue(
                layout.boardScale >= layout.scale,
                "$width x $height: board tiles must never be smaller than hand cards",
            )
        }
    }

    private fun footprint(layout: MatchLayout): Pair<Dp, Dp> {
        val boardWidth = (CardSpriteWidth * BOARD_SIDE + BoardGapTotal) * layout.boardScale
        val boardHeight = (CardSpriteHeight * BOARD_SIDE + BoardGapTotal) * layout.boardScale
        val breaks = HandBoardGap * 2
        val seated = layout.handHeight + layout.seatHeight
        return if (layout.landscape) {
            (layout.handWidth * 2 + boardWidth + breaks) to maxOf(seated, boardHeight)
        } else {
            maxOf(layout.handWidth, boardWidth) to (seated * 2 + boardHeight + breaks)
        }
    }

    private companion object {
        const val BOARD_SIDE = 3
        const val LANDSCAPE_HAND_ROWS = 3

        val BoardGapTotal = 16.dp

        val TOLERANCE = 0.5.dp

        const val MIN_TESTED_SCALE = 0.23f

        /** What a 1920x1080 window leaves the cards once the arena's header is drawn, roughly. */
        val FULL_HD_PLAY_WIDTH = 1904.dp
        val FULL_HD_PLAY_HEIGHT = 880.dp

        val VIEWPORTS = listOf(
            411.dp to 890.dp,
            914.dp to 385.dp,
            360.dp to 640.dp,
            640.dp to 360.dp,
            800.dp to 1280.dp,
            1280.dp to 800.dp,
            1920.dp to 1080.dp,
            200.dp to 200.dp,
            100.dp to 900.dp,
        )
    }
}
