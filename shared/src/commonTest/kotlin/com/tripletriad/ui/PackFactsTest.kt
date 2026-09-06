package com.tripletriad.ui

import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a booster tile claims about a pack, against a pool small enough to count by hand.
 *
 * The shelf's new argument is [PackFacts.missing] — how many of a pack's cards the collection
 * does not hold — and it is arithmetic over data the client already has rather than a request.
 * Tested here rather than through the screen because the screen can only show one answer at a
 * time and the interesting cases are the ends: a collection that holds none of a pool, one that
 * holds all of it, and a pool the format only partly admits.
 */
class PackFactsTest {
    private val pool = BoosterType.BRONZE.pool

    @Test
    fun aCollectionHoldingNoneOfThePoolIsMissingAllOfIt() {
        val facts = packFacts(BoosterType.BRONZE, table(rarity = 2), owned = emptyMap())

        assertEquals(pool.size, facts.missing)
    }

    @Test
    fun aCopyOfACardIsNotMissingAndAnEmptyRowIsNotACopy() {
        val held = mapOf(pool[0] to 1, pool[1] to 3, pool[2] to 0)

        val facts = packFacts(BoosterType.BRONZE, table(rarity = 2), owned = held)

        // The third row is a count of zero, which is the shape a collection takes after a card is
        // sold: present as a key, owned as nothing.
        assertEquals(pool.size - 2, facts.missing, "a row of zero copies is not a card")
    }

    @Test
    fun theStarsSpanTheRaritiesTheTableKnows() {
        val rarities = pool.mapIndexed { index, id -> id to (index % 3 + 1) }.toMap()
        val cards = rarities.mapValues { (id, rarity) -> card(id, rarity) }

        val facts = packFacts(BoosterType.BRONZE, cards, owned = emptyMap())

        assertEquals(1..3, facts.stars)
        assertEquals("★–★★★", starRange(facts.stars!!))
    }

    @Test
    fun aPoolOfOneRarityIsWrittenWithoutARange() {
        val facts = packFacts(BoosterType.BRONZE, table(rarity = 4), owned = emptyMap())

        assertEquals(4..4, facts.stars)
        assertEquals("★★★★", starRange(facts.stars!!))
    }

    /**
     * A pool the format admits none of has no rarities, and a range over nothing is not a range.
     *
     * The pack is still sold and still priced — `ShopCatalog` values it off the whole catalogue,
     * not off the format's share — so the line has to go quiet rather than the tile disappear.
     */
    @Test
    fun aPoolTheTableDoesNotKnowHasNoStars() {
        val facts = packFacts(BoosterType.BRONZE, cards = emptyMap(), owned = emptyMap())

        assertNull(facts.stars)
        assertEquals(pool.size, facts.missing, "what the table admits is not what the pack draws")
    }

    @Test
    fun theDrawsAreThePacksOwn() {
        val facts = packFacts(BoosterType.BRONZE, table(rarity = 1), owned = emptyMap())

        assertEquals(BoosterType.BRONZE.cardCount, facts.draws)
    }

    private fun table(rarity: Int): Map<Int, Card> = pool.associateWith { card(it, rarity) }

    private fun card(id: Int, rarity: Int) = Card(
        id = id,
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = 1,
        right = 1,
        bottom = 1,
        left = 1,
        rarity = rarity,
    )
}
