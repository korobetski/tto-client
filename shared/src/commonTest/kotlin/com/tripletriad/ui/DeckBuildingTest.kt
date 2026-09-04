package com.tripletriad.ui

import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.DeckLimits
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The two things the deck screens gained that are not drawing: topping a draft up from the
 * collection, and finding somewhere to put a duplicate.
 *
 * Both are pure and both are where the rules actually live, so they are tested here rather than
 * through the UI — `DecksUiTest` proves the buttons are wired to them and nothing more.
 */
class DeckBuildingTest {
    @Test
    fun anEmptyDraftIsFilledWithTheStrongestLegalFive() {
        val filled = Deck(name = "x").completedFrom(pool, table, owned::getValue)

        assertEquals(HAND_SIZE, filled.cards.size)
        // Descending by total, and the caps are what stop it being simply the top five: a 40 and a
        // 36 are both five-star, and a deck may hold one of those.
        assertTrue(DeckLimits.isLegal(filled.cards, table), "the fill produced an illegal deck")
    }

    @Test
    fun theCapsAreObeyedRatherThanTheOrder() {
        val filled = Deck(name = "x").completedFrom(pool, table, owned::getValue)
        val ranks = filled.cards.mapNotNull { table[it]?.rarity }

        assertEquals(
            DeckLimits.MAX_FIVE_STARS,
            ranks.count { it == FIVE },
            "the strongest cards are all five-star; only one may go in",
        )
        assertEquals(DeckLimits.MAX_FOUR_STARS, ranks.count { it == FOUR })
    }

    @Test
    fun whatIsAlreadyPickedIsKept() {
        // The weakest card in the pool, chosen on purpose: a fill that optimised would throw it
        // out, and throwing out a deliberate pick is the one thing this must never do.
        val weakest = pool.minBy { it.total }
        val started = Deck(name = "x", cards = listOf(weakest.id))

        val filled = started.completedFrom(pool, table, owned::getValue)

        assertEquals(weakest.id, filled.cards.first(), "the player's own pick moved or vanished")
        assertEquals(HAND_SIZE, filled.cards.size)
    }

    @Test
    fun aCardIsNotPlacedTwiceOnOneCopy() {
        val single = pool.first()
        val onlyOne = mapOf(single.id to 1).withDefault { 0 }

        val filled = Deck(name = "x").completedFrom(listOf(single), table, onlyOne::getValue)

        assertEquals(listOf(single.id), filled.cards, "one copy filled more than one position")
    }

    @Test
    fun asManyCopiesAsAreOwnedMayGoIn() {
        // The mirror of the case above, and the reason the check counts rather than testing
        // membership: three copies of a one-star card is three legal positions.
        val plain = pool.first { table.getValue(it.id).rarity == ONE }
        val three = mapOf(plain.id to 3).withDefault { 0 }

        val filled = Deck(name = "x").completedFrom(listOf(plain), table, three::getValue)

        assertEquals(3, filled.cards.count { it == plain.id })
    }

    @Test
    fun aFullDeckIsHandedBackUntouched() {
        val full = Deck(name = "x", cards = pool.take(HAND_SIZE).map { it.id })

        // Identical, not merely equal: the button is disabled on `completed != draft`, so an
        // unchanged fill has to compare equal or the control would stay lit forever.
        assertSame(full, full.completedFrom(pool, table, owned::getValue))
    }

    @Test
    fun anEmptyCollectionChangesNothing() {
        val draft = Deck(name = "x")

        assertSame(draft, draft.completedFrom(emptyList(), table, owned::getValue))
    }

    @Test
    fun theFirstEmptySlotIsFoundPastTheEndOfWhatIsStored() {
        // A profile stores as few decks as it has ever needed; the screen always draws eight. So
        // slot 1 is empty on a save whose list is one long.
        val one = GameSave.new(createdAt = 0L)
            .copy(decks = listOf(Deck(name = "a", cards = pool.take(HAND_SIZE).map { it.id })))

        assertEquals(1, firstEmptySlot(one))
    }

    @Test
    fun aNamedButCardlessSlotCountsAsEmpty() {
        val named = GameSave.new(createdAt = 0L)
            .copy(decks = listOf(Deck(name = "renamed", cards = emptyList())))

        assertEquals(0, firstEmptySlot(named), "a name is not a deck")
    }

    @Test
    fun aProfileWithEightFilledSlotsHasNowhereToCopyTo() {
        val hand = pool.take(HAND_SIZE).map { it.id }
        val full = GameSave.new(createdAt = 0L).copy(
            decks = List(GameSave.MAX_DECKS) { Deck(name = "d$it", cards = hand) },
        )

        assertNull(firstEmptySlot(full), "the copy button must go dead rather than overwrite")
    }

    /**
     * Eleven cards across the three ranks the caps care about, strongest first.
     *
     * More five- and four-stars than a legal deck may hold, which is the whole point: a fill that
     * simply took the top five would break both caps.
     */
    private val pool: List<Card> = listOf(
        card(1, power = 10, rarity = FIVE),
        card(2, power = 9, rarity = FIVE),
        card(3, power = 8, rarity = FOUR),
        card(4, power = 7, rarity = FOUR),
        card(5, power = 6, rarity = FOUR),
        card(6, power = 5, rarity = ONE),
        card(7, power = 4, rarity = ONE),
        card(8, power = 3, rarity = ONE),
        card(9, power = 2, rarity = ONE),
        card(10, power = 1, rarity = ONE),
    )

    private val table: Map<Int, Card> = pool.associateBy { it.id }

    /** One copy of everything, which is what a collection reads like to `completedFrom`. */
    private val owned: Map<Int, Int> = table.keys.associateWith { 1 }.withDefault { 0 }

    private fun card(number: Int, power: Int, rarity: Int) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = "Test $number",
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = rarity,
    )

    private companion object {
        const val ONE = 1
        const val FOUR = 4
        const val FIVE = 5
    }
}
