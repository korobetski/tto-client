package com.tripletriad.data

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The **shipped** `starters.json`, checked against document 19's own refusals.
 *
 * § What an importer should refuse lists five content bugs, each of which reaches a player as
 * something worse than an error message: a starter of the wrong size is a character that begins
 * stronger or weaker than every other, and a released set with no starter is a set nobody can begin
 * with. There is no importer — the file is authored by hand — so this is where the refusals live,
 * and CI is what runs them.
 *
 * The rule itself is [StarterCatalog.violations]; this file is the bundle it is pointed at. Stating
 * it once means the day the grant moves to the server, the server checks the same sentences.
 */
class StarterBundleTest {
    private val starters = runBlocking { loadStarterCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    private companion object {
        /** Dodo, Tonberry, Sabotender — three real rarity-1 cards of block 1. */
        val FF14_COMMONS = listOf(257, 258, 259)

        /** Geezard, which is block 2 and therefore foreign to a block-1 starter. */
        val FF8_COMMON = listOf(513)

        /** An id no set will ever hold: block 3906 does not exist. */
        const val GHOST = 999_998
    }

    /**
     * Every refusal at once, which is the check that earns the file.
     *
     * One assertion rather than five, because [StarterCatalog.violations] returns sentences: a
     * failure here names what is wrong with which starter, and splitting it into five tests would
     * report the same sentence five times with four of them silent.
     */
    @Test
    fun theShippedStartersBreakNoneOfTheAuthoringRules() {
        val problems = starters.violations(cards, cards.sets)

        assertTrue(problems.isEmpty(), "starters.json: ${problems.joinToString("; ")}")
    }

    /** And there really is something in it, so the check above cannot pass on an empty file. */
    @Test
    fun everyReleasedSetHasAStarterAndNothingElseDoes() {
        val released = cards.releasedSets.map { it.block }.toSet()

        assertEquals(released.size, starters.starters.size)
        assertEquals(released, starters.starters.mapTo(mutableSetOf()) { it.block })
        assertEquals(
            starters.starters.map { it.id },
            starters.released(cards.sets).map { it.id },
            "every authored starter belongs to a released set",
        )
    }

    /**
     * The composition, restated as numbers.
     *
     * [StarterCatalog.violations] already refuses anything else; this is the assertion that says
     * *what the rule is* in a form a reader can check against the document without reading the
     * implementation. Ten cards, nine commons, one rare, and the rare in a five-card deck.
     */
    @Test
    fun everyStarterIsTenCardsWithItsRareInTheDeck() {
        for (starter in starters.starters) {
            val held = starter.cards.mapNotNull { cards[it] }
            val rare = held.single { it.rarity == StarterCatalog.RARE_RARITY }

            assertEquals(StarterCatalog.SIZE, held.size, starter.id)
            assertEquals(
                StarterCatalog.COMMONS,
                held.count { it.rarity == StarterCatalog.COMMON_RARITY },
                starter.id,
            )
            assertEquals(HAND_SIZE, starter.deck.size, starter.id)
            assertTrue(rare.id in starter.deck, "${starter.id} leaves ${rare.name} out of its deck")
            assertTrue(starter.cards.containsAll(starter.deck), starter.id)
        }
    }

    /** Ids are unique, so `StarterCatalog.get` cannot silently prefer one of two. */
    @Test
    fun theIdsAreUnique() {
        val ids = starters.starters.map { it.id }

        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    /**
     * Each starter's name resolves, in the locale everything else falls back to.
     *
     * `nameKey` is data-driven, so `StringsBundleTest` cannot see it — the same blind spot
     * `DerivedKeysTest` exists for. Nothing displays these yet: the creation screen still chooses a
     * collection, and the starter follows from its block. Asserted now so the day it does, the
     * strings are already there rather than rendering as `APP_STARTER_FF14_BEASTS`.
     */
    @Test
    fun everyStarterNameResolves() {
        val english = runBlocking { loadStrings(AppLocale.EN_US) }
        val unresolved = starters.starters.map { it.nameKey }.filter { english[it] == it }

        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /**
     * A hand-built catalogue that breaks every rule is caught by all of them.
     *
     * Without this the suite above only proves the shipped file is fine today — it would pass just
     * as happily against a `violations` that returned an empty list unconditionally.
     */
    @Test
    fun aBrokenCatalogueIsRefusedOnEveryCount() {
        val broken = StarterCatalog(
            listOf(
                Starter(
                    id = "broken",
                    block = 1,
                    nameKey = "APP_NOWHERE",
                    // Four cards, one of them from the other block, and no rarity-2 at all.
                    cards = FF14_COMMONS + FF8_COMMON,
                    // Two long, and naming a card the starter does not hold.
                    deck = listOf(FF14_COMMONS.first(), GHOST),
                ),
            ),
        )

        val problems = broken.violations(cards, cards.sets)

        assertTrue(problems.any { "another block" in it }, problems.toString())
        assertTrue(problems.any { "rarity" in it }, problems.toString())
        assertTrue(problems.any { "subset" in it }, problems.toString())
        assertTrue(problems.any { "deck of" in it }, problems.toString())
        // Block 2 is released and this catalogue opens only block 1.
        assertTrue(problems.any { "released and has no starter" in it }, problems.toString())
    }

    /** A starter naming a card that does not exist is refused before anything else is judged. */
    @Test
    fun aStarterNamingAnUnknownCardSaysSoAndStops() {
        val ghost = StarterCatalog(
            listOf(
                Starter(
                    id = "ghost",
                    block = 1,
                    nameKey = "APP_NOWHERE",
                    cards = listOf(GHOST, GHOST + 1),
                    deck = listOf(GHOST),
                ),
            ),
        )

        val problems = ghost.violations(cards, cards.sets)

        assertTrue(problems.any { "cards that do not exist" in it }, problems.toString())
        // And nothing about its composition, which cannot be judged without the cards.
        assertTrue(problems.none { "rarity" in it }, problems.toString())
    }
}
