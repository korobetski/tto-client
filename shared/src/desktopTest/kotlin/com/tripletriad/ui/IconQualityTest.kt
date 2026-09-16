package com.tripletriad.ui

import androidx.compose.ui.graphics.FilterQuality
import com.tripletriad.model.BoosterType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which bag icons are the game's 80x80 and which are still the AS3's, and that the sampling rule
 * tells them apart — see `iconFilter`.
 *
 * The pack icons are read through [BoosterType.iconId] rather than typed, so a pack that names an
 * icon nobody shipped fails here instead of drawing the vector in the shop.
 */
class IconQualityTest {
    private val art = runBlocking { loadUiArt() }

    @Test
    fun theCardAndPackIconsAreTheGamesDoubleSize() {
        val widths = HQ.associateWith { art.icon(it)?.width }

        assertEquals(HQ.associateWith { HQ_PX }, widths)
    }

    @Test
    fun onlyTheDoubleSizeIconsAreSmoothed() {
        val filters = (HQ + PIXEL_ART).associateWith {
            requireNotNull(art.icon(it)) { "no icon $it" }.iconFilter
        }

        val expected = HQ.associateWith { FilterQuality.Medium } +
            PIXEL_ART.associateWith { FilterQuality.None }
        assertEquals(expected, filters)
    }

    private companion object {
        const val HQ_PX = 80

        val HQ: Set<String> =
            (1..5).map { "card_r${it}_icon" }.toSet() + BoosterType.entries.map { it.iconId }

        /** The widest of what is left, `card_frame` at 44, and a few of the narrower ones. */
        val PIXEL_ART = setOf("card_frame", "item_borders", "achievement_border", "PGS", "000713")
    }
}
