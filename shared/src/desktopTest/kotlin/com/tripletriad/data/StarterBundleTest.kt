package com.tripletriad.data

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterBundleTest {
    private val starters = runBlocking { loadStarterCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    private companion object {
        val FF14_COMMONS = listOf(257, 258, 259)

        val FF8_COMMON = listOf(513)

        const val GHOST = 999_998
    }

    @Test
    fun theShippedStartersBreakNoneOfTheAuthoringRules() {
        val problems = starters.violations(cards, cards.sets)

        assertTrue(problems.isEmpty(), "starters.json: ${problems.joinToString("; ")}")
    }

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

    @Test
    fun theIdsAreUnique() {
        val ids = starters.starters.map { it.id }

        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    @Test
    fun everyStarterNameResolves() {
        val english = runBlocking { loadStrings(AppLocale.EN_US) }
        val unresolved = starters.starters.map { it.nameKey }.filter { english[it] == it }

        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

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
