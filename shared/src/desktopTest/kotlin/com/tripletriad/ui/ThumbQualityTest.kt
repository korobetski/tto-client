package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.tripletriad.data.loadCardCatalog
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a thumbnail is sampled once it is not drawn at its authored size — see `AUTHORED_THUMB_PX`.
 *
 * Drawn off screen, pixel against pixel: nothing in a `BitmapPainter` says which filter it was
 * built with, and a capture of the grid would put the frame and the badges in the way.
 */
class ThumbQualityTest {
    private val art = runBlocking { loadUiArt() }
    private val cards = runBlocking { loadCardCatalog() }.all

    @Test
    fun theFfxivThumbnailsAreTheGamesDoubleSizeIconsAndTheFf8OnesAreNot() {
        val sizes = cards.groupingBy { art.thumb(it)?.intrinsicSize?.width?.toInt() }.eachCount()

        assertEquals(mapOf<Int?, Int>(HQ_PX to FFXIV_CARDS, AUTHORED_THUMB_PX to FF8_CARDS), sizes)
    }

    /** Point sampling would answer every halved pixel with one of the four it stands for. */
    @Test
    fun anFfxivThumbnailIsSmoothedWhenItIsHalved() {
        val thumb = thumbOfWidth(HQ_PX)
        val source = thumb.render(HQ_PX)
        val halved = thumb.render(AUTHORED_THUMB_PX)

        var mixed = 0
        var picked = 0
        for (y in 0 until AUTHORED_THUMB_PX) {
            for (x in 0 until AUTHORED_THUMB_PX) {
                val block = listOf(
                    source[2 * x, 2 * y],
                    source[2 * x + 1, 2 * y],
                    source[2 * x, 2 * y + 1],
                    source[2 * x + 1, 2 * y + 1],
                )
                if (block.distinct().size == 1) continue
                mixed++
                if (halved[x, y] in block) picked++
            }
        }

        assertTrue(mixed > 0, "the icon has no detail to sample")
        assertTrue(picked * 2 < mixed, "$picked of $mixed halved pixels were one source pixel")
    }

    /** Smoothing would put colours between two pixels that the artist never drew. */
    @Test
    fun anFf8ThumbnailStaysPixelArtWhenItIsEnlarged() {
        val thumb = thumbOfWidth(AUTHORED_THUMB_PX)
        val source = thumb.render(AUTHORED_THUMB_PX)
        val doubled = thumb.render(HQ_PX)

        val blurred = (0 until HQ_PX).sumOf { y ->
            (0 until HQ_PX).count { x -> doubled[x, y] != source[x / 2, y / 2] }
        }

        assertEquals(0, blurred, "enlarged pixels that are not the source pixel under them")
    }

    private fun thumbOfWidth(px: Int): Painter = cards.firstNotNullOf { card ->
        art.thumb(card)?.takeIf { it.intrinsicSize.width == px.toFloat() }
    }

    private fun Painter.render(side: Int): PixelMap {
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            with(this@render) { draw(size) }
        }
        return bitmap.toPixelMap()
    }

    private companion object {
        /** The game's own icon size, twice [AUTHORED_THUMB_PX]. */
        const val HQ_PX = 80

        /** Measured on 2026-09-15 from `cards.json`: blocks 1 and 2, then the FF8 set. */
        const val FFXIV_CARDS = 475
        const val FF8_CARDS = 111
    }
}
