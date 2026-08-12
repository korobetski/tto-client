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

/**
 * Covers the **real** `npcs.json` in the Compose resource bundle, as [NpcCatalogTest] does not.
 *
 * The counts and invariants here are the ones `tools/extract_npcs.py` asserts on the way out.
 * Stated again on the way in, because the extractor is run by hand: a stale `npcs.json` in the
 * bundle is exactly the failure this catches, and the extractor cannot.
 */
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

    /**
     * Every opponent names at least one format, and every format it names is one that ships.
     *
     * An opponent naming nothing is unreachable — no list would ever include them — and one naming
     * a format that does not exist is the same thing with a typo instead of an omission.
     */
    @Test
    fun everyOpponentPlaysAFormatThatExists() {
        val known = runBlocking { loadFormatCatalog() }.formats.mapTo(mutableSetOf()) { it.id }

        for (npc in catalog.all) {
            assertTrue(npc.formats.isNotEmpty(), "${npc.iconId} plays nothing")
            val unknown = npc.formats.filterNot { it in known }
            assertTrue(unknown.isEmpty(), "${npc.iconId} names formats nothing ships: $unknown")
        }
    }

    /**
     * `NPC_W` is keyed by icon, so a collision inside one table would merge two opponents' records.
     */
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
            assertTrue(npc.nameKey.startsWith("STR_"), "${npc.iconId} name is not an i18n key")
            assertTrue(npc.iconId.isNotBlank())
            assertTrue(npc.matchFee >= 0, "${npc.iconId} has a negative fee")
            assertTrue(
                npc.fetishCards.isNotEmpty() || npc.cards.isNotEmpty(),
                "${npc.iconId} has no cards at all",
            )
        }
    }

    /**
     * Every opponent must be able to field five cards.
     *
     * This is the invariant that makes the AS3 `getRandomCards()` infinite loop unreachable with
     * the shipped data: fifteen entries have an empty pool, and all of them have five fetish cards.
     */
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

    /**
     * Every rule key in the data must be one `gameRules()` maps; an unmapped one is silently
     * dropped.
     */
    /**
     * Every card an opponent can field exists in that opponent's own collection.
     *
     * `PveMatches.assemble` refuses a hand it cannot resolve rather than quietly playing four
     * cards, so this is what keeps that refusal unreachable by playing. It also catches the cross-
     * collection mistake the data invites: card ids are per-table indices, so an `ff8` opponent
     * listing an `ff14` id would resolve to the wrong card rather than to none.
     */
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

    /** And a full hand resolves to five real cards, which is what a match actually needs. */
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

    /** A rate of 0 would be a drop that can never happen — a transcription slip, not a design. */
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

    /** Whatever the hour, the opponent list is non-empty — otherwise PvE would be unreachable. */
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

    /**
     * The entry whose pool is `cards.getCardsByRarities(...)` must have been resolved to ids.
     *
     * There were two. The FFVIII table declared a second Queen of Cards, the only `iconID` shared
     * by both tables — see document 19, which flagged it as the one collision the icon-keyed win
     * record could not tell apart. She is **deleted**: with `MODE` gone the roster is one roster,
     * `NpcCatalog.byIcon` resolves an icon by taking the first match, and two opponents answering
     * to one name is an ambiguity no lookup can settle. The FFXIV Queen stays.
     */
    @Test
    fun theQueenOfCardsPoolWasResolvedAtExtractionTime() {
        val queens = catalog.npcs.filter { it.iconId == "queen-of-cards" }
        val queen = queens.single()

        assertEquals(FF14_CARDS, queen.cards.size, "every ff14 card, rarities 1-5")
        assertTrue(queen.cards.all { it > 0 }, "index 0 is the Back sentinel and is not a card")
    }

    private companion object {
        const val FF14_NPCS = 60
        const val FF8_NPCS = 24
        const val FF14_CARDS = 153
        const val FF8_CARDS = 110
        const val HOURS = 24
    }
}

/**
 * A level high enough that [com.tripletriad.data.NpcCatalog.available]'s gate cannot bite.
 *
 * The tests that pass it are about the **hour** window or about a named opponent, and would
 * otherwise be asserting the level rule by accident. `OpponentUiTest` tests the gate itself.
 */
private const val ANY_LEVEL: Int = 99
