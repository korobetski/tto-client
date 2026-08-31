package com.tripletriad.i18n

import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.CardOrigin
import com.tripletriad.model.NpcLevel
import com.tripletriad.model.PotionType
import com.tripletriad.model.PouchItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DerivedKeysTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun everyNpcLevelHasALabel() {
        for (level in NpcLevel.entries) {
            assertTrue(strings.has(level.labelKey), "no label for ${level.labelKey}")
        }
    }

    @Test
    fun everySellableItemDescribesItself() {
        for (booster in BoosterType.entries) {
            assertTrue(strings.has(booster.descriptionKey), "no text for ${booster.descriptionKey}")
        }
        for (potion in PotionType.entries) {
            assertTrue(strings.has(potion.descriptionKey), "no text for ${potion.descriptionKey}")
        }
        assertTrue(strings.has(CardItem(1).descriptionKey), "no text for a card item")

        // Every origin, not just the plain one: a card handed back from a lot nobody bid enough
        // for carries a different sentence, and `CardOrigin` is where the second one is declared.
        // Iterating the enum is what makes a third origin fail here rather than in the inventory.
        for (origin in CardOrigin.entries) {
            assertTrue(strings.has(origin.descriptionKey), "no text for ${origin.descriptionKey}")
        }
        assertTrue(
            strings.has(PouchItem(mgp = 1, cardId = 1, lotId = "lot").descriptionKey),
            "no text for a pouch",
        )
    }

    @Test
    fun everyFormatNamesItself() {
        val formats = runBlocking { loadFormatCatalog() }

        assertTrue(formats.formats.isNotEmpty(), "no formats are authored, so this proves nothing")
        for (format in formats.formats) {
            assertTrue(strings.has(format.nameKey), "no name for ${format.nameKey}")
        }
    }

    @Test
    fun everySellableItemNamesItself() {
        for (booster in BoosterType.entries) {
            assertTrue(strings.has(booster.nameKey), "no name for ${booster.nameKey}")
        }
        for (potion in PotionType.entries) {
            assertTrue(strings.has(potion.nameKey), "no name for ${potion.nameKey}")
        }
    }
}
