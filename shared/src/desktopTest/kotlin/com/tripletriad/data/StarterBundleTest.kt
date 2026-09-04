package com.tripletriad.data

import com.tripletriad.FF8_BLOCK
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
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

        /** Chimera — the one rarity-2 the FFXIV box ships. */
        val FF14_RARE = listOf(281)

        /** One FFVIII card, whose whole job here is to be from a *different* block. */
        val FF8_COMMON = listOf(Card.idFor(block = FF8_BLOCK, number = 1))

        const val GHOST = 999_998
    }

    @Test
    fun theShippedStartersBreakNoneOfTheAuthoringRules() {
        val problems = starters.violations(cards, cards.sets)

        assertTrue(problems.isEmpty(), "starters.json: ${problems.joinToString("; ")}")
    }

    @Test
    fun everyReleasedSetHasAStarterAndNothingElseDoes() {
        // One starter per released *set*, not per block. A set spanning two blocks is still one
        // collection to the player and opens with one box, whose cards all sit in whichever of
        // its blocks holds the commons — see `CardSet`.
        val released = cards.releasedSets

        assertEquals(released.size, starters.starters.size)
        for (set in released) {
            assertTrue(
                starters.starters.count { it.block in set.blocks } == 1,
                "${set.slug} should be opened by exactly one starter",
            )
        }
        assertEquals(
            starters.starters.map { it.id },
            starters.released(cards.sets).map { it.id },
            "every authored starter belongs to a released set",
        )
    }

    /**
     * Five authored cards, at least one of them a rarity 2, and a block that can fill the draw.
     *
     * The size the *player* is dealt is nine — five plus `StarterPack.DRAWN` — and this is the half
     * of that which is authored. The other half is asserted on the pool rather than on a draw,
     * because a test that shuffled would be pinning a seed and not a rule.
     */
    @Test
    fun everyStarterIsFiveAuthoredCardsWithARareAmongThem() {
        for (starter in starters.starters) {
            val held = starter.deck.mapNotNull { cards[it] }

            assertEquals(HAND_SIZE, held.size, starter.id)
            assertTrue(
                held.any { it.rarity == StarterCatalog.RARE_RARITY },
                "${starter.id} has no rarity-${StarterCatalog.RARE_RARITY} card",
            )
            assertTrue(
                held.none { it.rarity > StarterCatalog.RARE_RARITY },
                "${starter.id} holds a card above rarity ${StarterCatalog.RARE_RARITY}",
            )
            assertTrue(
                held.all { it.block == starter.block },
                "${starter.id} holds a card from another block",
            )
        }
    }

    /** The four unauthored cards have somewhere to come from, in the shipped card table. */
    @Test
    fun everyStarterBlockCanFillTheDraw() {
        for (starter in starters.starters) {
            val pool = StarterPack.pool(starter, cards.byId)

            assertTrue(
                pool.size >= StarterPack.DRAWN,
                "${starter.id} draws ${StarterPack.DRAWN} from a pool of ${pool.size}",
            )
            assertEquals(
                StarterCatalog.SIZE,
                HAND_SIZE + StarterPack.DRAWN,
                "the box is the deck plus the draw",
            )
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
                    deck = FF14_COMMONS + FF8_COMMON,
                ),
            ),
        )

        val problems = broken.violations(cards, cards.sets)

        assertTrue(problems.any { "another block" in it }, problems.toString())
        assertTrue(problems.any { "no rarity-" in it }, problems.toString())
        assertTrue(problems.any { "deck of" in it }, problems.toString())
        // Block 8 is released and this catalogue opens only block 1.
        assertTrue(problems.any { "released and has no starter" in it }, problems.toString())
    }

    /** A starter whose deck names the same card twice is a four-card deck wearing five. */
    @Test
    fun aStarterNamingACardTwiceIsRefused() {
        val doubled = StarterCatalog(
            listOf(
                Starter(
                    id = "doubled",
                    block = 1,
                    nameKey = "APP_NOWHERE",
                    deck = FF14_COMMONS + FF14_COMMONS.first() + FF14_RARE,
                ),
            ),
        )

        val problems = doubled.violations(cards, cards.sets)

        assertTrue(problems.any { "names a card twice" in it }, problems.toString())
    }

    @Test
    fun aStarterNamingAnUnknownCardSaysSoAndStops() {
        val ghost = StarterCatalog(
            listOf(
                Starter(
                    id = "ghost",
                    block = 1,
                    nameKey = "APP_NOWHERE",
                    deck = listOf(GHOST, GHOST + 1),
                ),
            ),
        )

        val problems = ghost.violations(cards, cards.sets)

        assertTrue(problems.any { "cards that do not exist" in it }, problems.toString())
        // And nothing about its composition, which cannot be judged without the cards.
        assertTrue(problems.none { "rarity" in it }, problems.toString())
    }
}
