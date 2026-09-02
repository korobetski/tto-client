package com.tripletriad.data

import com.tripletriad.FF8_BLOCK
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.ui.loadCardArt
import com.tripletriad.ui.textureId
import com.tripletriad.ui.textureName
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardBundleTest {
    private val catalog = runBlocking { loadCardCatalog() }

    @Test
    fun theBundledCatalogHoldsBothCollectionsInFull() {
        // FF14 spans two blocks since the set outgrew 255 cards: block 1 holds the first 255,
        // block 2 the remaining 199. See `CardSet`.
        assertEquals(FF14_CARDS_BLOCK_1, catalog.block(1).size, "the FF14 set's first block")
        assertEquals(FF14_CARDS_BLOCK_2, catalog.block(2).size, "the FF14 set's second block")
        assertEquals(FF8_CARDS, catalog.block(FF8_BLOCK).size, "the FF8 set")
        assertEquals(
            FF14_CARDS_BLOCK_1 + FF14_CARDS_BLOCK_2 + FF8_CARDS,
            catalog.all.size,
            "both sets together",
        )
        assertEquals(listOf("ff14", "ff8"), catalog.releasedSets.map { it.slug })
    }

    @Test
    fun everyBundledIdNamesADeclaredSetAndANumberInRange() {
        val blocks = catalog.sets.flatMap { it.blocks }.toSet()

        assertEquals(catalog.all.size, catalog.all.map { it.id }.toSet().size, "ids are unique")
        for (card in catalog.all) {
            assertTrue(card.id >= Card.FIRST_ID, "card ${card.id} is a legacy id")
            assertTrue(card.block in blocks, "card ${card.id} names no declared set")
            assertTrue(
                card.number in Card.NUMBER_RANGE,
                "card ${card.id} has number ${card.number}",
            )
            assertEquals(
                card.id,
                Card.idFor(card.block, card.number),
                "id disagrees with its parts",
            )
        }
        for (block in blocks) {
            assertTrue(
                catalog.block(block).size <= Card.NUMBER_MASK,
                "block $block holds more than ${Card.NUMBER_MASK} cards",
            )
        }
    }

    @Test
    fun everyBundledCardIsPlayable() {
        assertTrue(catalog.all.all { it.name.isNotBlank() }, "every card needs a name to draw")
        val powers = catalog.all.flatMap { listOf(it.top, it.right, it.bottom, it.left) }
        assertTrue(
            powers.all { it in PRINTED_POWERS },
            "printed powers are $PRINTED_POWERS — a 0 means a parse failure, not a weak card",
        )
    }

    @Test
    fun everyCardInTheCatalogHasArtworkInTheBundle() = runBlocking {
        val art = loadCardArt()
        val missing = catalog.all.filter { card ->
            runCatching { art.face(card) }.isFailure
        }
        assertTrue(
            missing.isEmpty(),
            "${missing.size} cards have no artwork: " +
                missing.take(MISSING_TO_REPORT).joinToString { "${it.textureId} (${it.name})" },
        )
    }

    @Test
    fun everySharedTextureLoads() = runBlocking {
        val art = loadCardArt()

        for (rarity in Card.RARITY_RANGE) {
            assertNotNull(art.starsFor(rarity), "no $rarity-star row")
        }
        for (type in CardType.entries) {
            assertNotNull(art.typeIcon(type), "no icon for ${type.textureName}")
        }
        assertNotNull(art.digitPlate, "no cdbg plate")
        for (power in Card.POWER_RANGE) {
            assertNotNull(art.digit(power), "no glyph for power $power")
        }
    }

    @Test
    fun theTribeSetsAreExactlyWhatTheTableTypes() {
        // `AchievementCatalog` hard-codes four lists of card ids because `:core` cannot read a
        // bundle, and its KDoc promises this test is what keeps them honest. Re-derived from
        // `cards.json` rather than compared against a second literal, so a card whose `type`
        // changes — three of them just did — fails here instead of quietly shrinking a badge
        // nobody can finish any more.
        val byType = catalog.all.filter { it.type != null }.groupBy { it.type }

        for ((type, expected) in TRIBE_SETS) {
            val actual = byType[type].orEmpty().map { it.id }.sorted()
            assertEquals(expected.sorted(), actual, "the $type set")
        }
    }

    /**
     * The `name` column and the English bundle agree, card by card.
     *
     * Two records of the same fact — `cards.json` carries a `name` for logs and test messages, and
     * `STR_*_CARD_n` is what the screen actually draws — so nothing stops them drifting apart, and
     * three of them had: the table said Sahuagin, Rhitahtyna sas Arvina and Good King Moggle Mog
     * XIII where arrtripletriad.com says Sahagin, Rhitahtyn sas Arvina and Good King Moggle Mog
     * XII. They were corrected in both places at once; this is what keeps them that way.
     *
     * English only. The other three bundles are Square Enix's translations of the same card and are
     * *supposed* to read differently; the `name` column is English, so English is the one locale it
     * can be held against.
     */
    @Test
    fun everyCardsNameColumnMatchesWhatTheEnglishBundleDraws() {
        val strings = runBlocking { loadStrings(AppLocale.EN_US) }

        val disagreeing = catalog.all
            .filter { strings[it.nameKey] != it.name }
            .map { "${it.id} ${it.nameKey}: table ${it.name}, bundle ${strings[it.nameKey]}" }

        assertEquals(emptyList(), disagreeing)
    }

    private companion object {
        /** Each tribe `AchievementCatalog` counts a ladder against, and the ids it counts. */
        val TRIBE_SETS = mapOf(
            CardType.BEAST to AchievementCatalog.BEAST_CARDS,
            CardType.PRIMALS to AchievementCatalog.PRIMAL_CARDS,
            CardType.GARLEAN to AchievementCatalog.GARLEAN_CARDS,
            CardType.SCIONS to AchievementCatalog.SCION_CARDS,
        )

        const val FF14_CARDS_BLOCK_1 = 255
        const val FF14_CARDS_BLOCK_2 = 199

        // 110 shipped, plus the one secret card the collection screen hides until it is owned —
        // see `SECRET_CARD_IDS` in `CardListBody.kt`. It is still in the catalog and in this count:
        // hidden is a fact about one screen's list, not about the card table.
        const val FF8_CARDS = 110 + 1
        const val MISSING_TO_REPORT = 10

        val PRINTED_POWERS = 1..10
    }
}
