package com.tripletriad.ui

import androidx.compose.ui.graphics.FilterQuality
import com.tripletriad.model.CardType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which type icons are the 40x40 tribe drawings and which are still the AS3's 20x20 elements, and
 * that the sampling rule tells them apart — see `typeIconFilter`.
 *
 * Only the rule is pinned, not that every `Image` drawing a type icon asks it: `CardTypeBadge` and
 * `TypeIcon` do; the card face's own layer and the board's element badge keep their defaults.
 */
class TypeIconQualityTest {
    private val art = runBlocking { loadCardArt() }

    @Test
    fun theTribesAreTheDoubleSizeIconsAndTheElementsAreNot() {
        val widths = CardType.entries.associateWith { art.typeIcon(it)?.width }

        val expected = CardType.entries.associateWith {
            if (it in TRIBES) HQ_PX else AUTHORED_TYPE_ICON_PX
        }
        assertEquals(expected, widths)
    }

    @Test
    fun onlyTheTribesAreSmoothed() {
        val filters = CardType.entries.associateWith {
            requireNotNull(art.typeIcon(it)) { "no icon for ${it.textureName}" }.typeIconFilter
        }

        val expected = CardType.entries.associateWith {
            if (it in TRIBES) FilterQuality.Medium else FilterQuality.None
        }
        assertEquals(expected, filters)
    }

    private companion object {
        const val HQ_PX = 40

        val TRIBES = setOf(CardType.BEAST, CardType.PRIMALS, CardType.GARLEAN, CardType.SCIONS)
    }
}
