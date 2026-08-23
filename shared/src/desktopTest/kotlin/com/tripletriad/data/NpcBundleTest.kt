package com.tripletriad.data

import com.tripletriad.FF14_FORMAT
import com.tripletriad.FF8_FORMAT
import com.tripletriad.SINGLE_SET_FORMATS
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.NpcLevel
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcBundleTest {
    private val catalog = runBlocking { loadNpcCatalog() }

    @Test
    fun theBundledCatalogHoldsEveryOpponent() {
        assertEquals(FF14_NPCS + FF8_NPCS, catalog.all.size, "the whole roster")
        // Counted by the format each declares, which is what replaced the two arrays the file used
        // to have. The totals are the same because the same opponents are there.
        assertEquals(FF14_NPCS, catalog.playing(FF14_FORMAT).size, "the FFXIV opponents")
        assertEquals(FF8_NPCS, catalog.playing(FF8_FORMAT).size, "the FFVIII opponents")
    }

    @Test
    fun everyOpponentPlaysAFormatThatExists() {
        val known = runBlocking { loadFormatCatalog() }.formats.mapTo(mutableSetOf()) { it.id }

        for (npc in catalog.all) {
            assertTrue(npc.formats.isNotEmpty(), "${npc.iconId} plays nothing")
            val unknown = npc.formats.filterNot { it in known }
            assertTrue(unknown.isEmpty(), "${npc.iconId} names formats nothing ships: $unknown")
        }
    }

    @Test
    fun iconIdsAreUniqueWithinEachTable() {
        for (formatId in SINGLE_SET_FORMATS) {
            val icons = catalog.playing(formatId).map { it.iconId }
            assertEquals(icons.size, icons.distinct().size, "$formatId has a duplicate iconID")
        }
    }

    @Test
    fun everyOpponentIsUsable() {
        for (npc in catalog.all) {
            assertTrue(npc.id > 0, "${npc.iconId} has no id")
            // `STR_` for everything lifted from the AS3 bundles, `APP_` for anything this port
            // had to name itself — which is Ishtar, whose AS3 name key is the FFXIV Queen's and
            // could not be shared once they became two opponents.
            assertTrue(
                npc.nameKey.startsWith("STR_") || npc.nameKey.startsWith("APP_"),
                "${npc.iconId} name is not an i18n key",
            )
            assertTrue(npc.iconId.isNotBlank())
            assertTrue(npc.matchFee >= 0, "${npc.iconId} has a negative fee")
            assertTrue(
                npc.fetishCards.isNotEmpty() || npc.cards.isNotEmpty(),
                "${npc.iconId} has no cards at all",
            )
        }
    }

    @Test
    fun everyOpponentCanFieldAFullHand() {
        for (npc in catalog.all) {
            assertTrue(
                npc.fetishCards.size + npc.cards.size >= HAND_SIZE,
                "${npc.iconId} can only field ${npc.fetishCards.size + npc.cards.size} cards",
            )
            assertEquals(HAND_SIZE, npc.randomHand(Random(1)).size, npc.iconId)
        }
    }

    @Test
    fun everyOpponentCardExistsInItsOwnCollection() {
        val cards = runBlocking { loadCardCatalog() }
        val formats = runBlocking { loadFormatCatalog() }
        for (formatId in SINGLE_SET_FORMATS) {
            val ids = cards.admittedBy(requireNotNull(formats[formatId])).map { it.id }.toSet()
            for (npc in catalog.playing(formatId)) {
                val missing = (npc.fetishCards + npc.cards).filterNot { it in ids }
                assertTrue(
                    missing.isEmpty(),
                    "${npc.iconId} names $missing, absent from $formatId",
                )
            }
        }
    }

    @Test
    fun everyOpponentResolvesToAFullHandOfRealCards() {
        val cards = runBlocking { loadCardCatalog() }
        val formats = runBlocking { loadFormatCatalog() }
        for (formatId in SINGLE_SET_FORMATS) {
            val ids = cards.admittedBy(requireNotNull(formats[formatId])).map { it.id }.toSet()
            for (npc in catalog.playing(formatId)) {
                val hand = npc.randomHand(Random(1)).filter { it in ids }
                assertEquals(
                    HAND_SIZE,
                    hand.size,
                    "${npc.iconId} cannot field a hand in $formatId",
                )
            }
        }
    }

    @Test
    fun everyRuleKeyInTheDataIsMapped() {
        for (npc in catalog.all) {
            val mapped = npc.gameRules().activeRuleKeys()
            for (key in npc.ruleKeys) {
                assertTrue(key in mapped, "${npc.iconId} declares $key, which gameRules() ignores")
            }
        }
    }

    @Test
    fun everyLevelIsOneTheXpFormulaKnows() {
        for (npc in catalog.all) {
            assertTrue(npc.level in NpcLevel.entries, npc.iconId)
        }
        // The shipped data uses all five real bands; NONE would mean an opponent that pays no XP.
        assertEquals(
            setOf(
                NpcLevel.NOVICE,
                NpcLevel.INITIATE,
                NpcLevel.AVERAGE,
                NpcLevel.ADVANCED,
                NpcLevel.EXPERT,
            ),
            catalog.all.map { it.level }.toSet(),
        )
    }

    @Test
    fun everyItemRewardResolvesAndHasAUsableRate() {
        for (npc in catalog.all) {
            for (reward in npc.itemRewards) {
                assertTrue(
                    reward.item() != null,
                    "${npc.iconId} has a reward this build cannot resolve: $reward",
                )
                assertTrue(
                    reward.rate > 0.0 && reward.rate <= 1.0,
                    "${npc.iconId}: rate ${reward.rate}",
                )
            }
        }
    }

    @Test
    fun availabilityWindowsAreValidHours() {
        val windowed = catalog.all.filterNot { it.availability.isAlwaysAvailable }

        assertTrue(windowed.isNotEmpty(), "some opponents declare a window")
        for (npc in windowed) {
            assertTrue(npc.availability.begins in 0..HOURS, "${npc.iconId}")
            assertTrue(npc.availability.ends in 0..HOURS, "${npc.iconId}")
        }
    }

    @Test
    fun someOpponentIsAvailableAtEveryHour() {
        for (hour in 0 until HOURS) {
            for (formatId in SINGLE_SET_FORMATS) {
                assertTrue(
                    catalog.available(formatId, hour, level = ANY_LEVEL).isNotEmpty(),
                    "$formatId has no opponent at $hour:00",
                )
            }
        }
    }

    @Test
    fun theQueenOfCardsPoolWasResolvedAtExtractionTime() {
        val queens = catalog.npcs.filter { it.iconId == "queen-of-cards" }
        val queen = queens.single()

        assertEquals(FF14_CARDS, queen.cards.size, "every ff14 card, rarities 1-5")
        assertTrue(queen.cards.all { it > 0 }, "index 0 is the Back sentinel and is not a card")
    }

    private companion object {
        // 60 shipped originally, plus 73 from arrtripletriad.com's own roster that this project's
        // hand-transcribed table did not have — see `add_ff14_npcs.js`. Matched by *name*, not
        // iconID: the shipped icons carry old transcription quirks (`tratchoum` for "Trachtoum",
        // `okalkaya` for "O'kalkaya") a slug diff would have missed and doubled.
        const val FF14_NPCS = 60 + 73

        // 25, the count `NPCs.as` declares, and 24 until the FFVIII Queen of Cards was recovered.
        // She was lost flattening the two tables into one keyed by icon: both Queens are authored
        // as `queen-of-cards`, so one of them overwrote the other. See `tools/extract_npcs.py`,
        // which now ships the FFVIII one as `ishtar`.
        const val FF8_NPCS = 25

        // The full FFXIV set, both of its blocks, now that it has grown past 153 — see `CardSet`.
        const val FF14_CARDS = 454
        const val FF8_CARDS = 110
        const val HOURS = 24
    }
}

private const val ANY_LEVEL: Int = 99
