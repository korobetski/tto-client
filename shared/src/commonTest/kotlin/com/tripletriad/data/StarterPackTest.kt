package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Granting the authored starter, and repairing a character that cannot field a hand.
 *
 * The catalogue here is a fixture, not the shipped one: these are rules about *a* starter, and
 * pinning them to `starters.json` would make every one of them fail the day a card is swapped for
 * flavour. That the shipped file obeys the composition rule is `StarterBundleTest`'s job.
 */
class StarterPackTest {
    private fun character(collection: CardCollection = CardCollection.FF14) =
        GameSave.new(username = "Tester", mode = collection, createdAt = 1L)

    @Test
    fun aFreshCharacterIsOwedNothing() {
        for (collection in CardCollection.entries) {
            val save = character(collection)
            assertFalse(StarterPack.isOwedBy(save), collection.name)
            assertEquals(save, StarterPack.grantedTo(save, catalog), "nothing to grant")
        }
    }

    /**
     * The defect this whole object exists for.
     *
     * `copy(mode = …)` is what the post-registration collection step used to do, and with global
     * card ids it leaves a character holding five cards of the set it *left*. Stated as a test so
     * the reasoning cannot quietly go stale again: if a future card id scheme makes this harmless
     * once more, this is the assertion that says so.
     */
    @Test
    fun changingOnlyTheModeStrandsEveryCard() {
        val stranded = character(CardCollection.FF14).copy(mode = CardCollection.FF8)

        assertEquals(0, StarterPack.playableCards(stranded), "no FF8 card is owned")
        assertTrue(StarterPack.isOwedBy(stranded))
    }

    @Test
    fun startingInTheOtherCollectionGrantsThatSetsAuthoredStarter() {
        val moved = StarterPack.startingIn(
            character(CardCollection.FF14),
            CardCollection.FF8,
            catalog,
        )

        assertEquals(CardCollection.FF8, moved.mode)
        assertEquals(ff8.cards.associateWith { 1 }, moved.cards)
        assertEquals(listOf(ff8.deck), moved.decks.map { it.cards })
        assertFalse(StarterPack.isOwedBy(moved), "the moved character can play")
        // Not both sets' starters: the FFXIV cards are replaced, not kept alongside. Adding
        // would hand a player twenty cards towards `ac-td2`'s thirty for changing their mind.
        assertEquals(ff8.cards.size, moved.cards.size)
    }

    @Test
    fun confirmingTheCollectionAlreadyInPlayChangesNothing() {
        val save = character(CardCollection.FF14)

        assertEquals(save, StarterPack.startingIn(save, CardCollection.FF14, catalog))
    }

    /**
     * A set with no authored starter grants nothing rather than inventing ids.
     *
     * `StarterCatalog.violations` refuses this at authoring time, so it is unreachable through the
     * shipped bundle — and handled anyway, because the alternative to "nothing happened" is a
     * character holding cards nobody chose.
     */
    @Test
    fun aSetWithNoStarterIsLeftAlone() {
        val empty = StarterCatalog(emptyList())
        val stranded = character(CardCollection.FF14).copy(mode = CardCollection.FF8)

        assertNull(StarterPack.forCollection(empty, CardCollection.FF8))
        assertEquals(stranded, StarterPack.grantedTo(stranded, empty))
        assertEquals(stranded, StarterPack.startingIn(stranded, CardCollection.FF14, empty))
    }

    @Test
    fun grantingRepairsAStrandedCharacterWithoutTouchingWhatItKept() {
        val kept = Card.idFor(block = CardCollection.FF8.block, number = 99)
        val stranded = character(CardCollection.FF14)
            .copy(mode = CardCollection.FF8, cards = mapOf(kept to 3))

        val repaired = StarterPack.grantedTo(stranded, catalog)

        assertFalse(StarterPack.isOwedBy(repaired))
        assertEquals(3, repaired.copiesOf(kept), "copies already held are not disturbed")
        for (id in ff8.cards) {
            assertTrue(repaired.ownsCard(id), "starter card $id was not granted")
        }
    }

    /** A card already in the starter is topped up to one copy, never to two. */
    @Test
    fun grantingDoesNotDoubleWhatIsAlreadyOwned() {
        val partial = character(CardCollection.FF14).copy(cards = mapOf(ff14.cards.first() to 1))

        val repaired = StarterPack.grantedTo(partial, catalog)

        assertEquals(ff14.cards.associateWith { 1 }, repaired.cards)
    }

    @Test
    fun grantingLeavesAPlayableDeckAtTheTop() {
        val stranded = character(CardCollection.FF14).copy(
            mode = CardCollection.FF8,
            decks = listOf(Deck("Stranded", ff14.deck)),
        )

        val repaired = StarterPack.grantedTo(stranded, catalog)
        val deck = repaired.decks.first()

        assertTrue(deck.isComplete, "the granted deck is a full hand")
        assertTrue(deck.isAffordable(repaired.cards), "and every card in it is owned")
        assertEquals(ff8.deck, deck.cards)
        assertTrue(repaired.decks.size <= GameSave.MAX_DECKS)
    }

    /** Copies are not cards: five of one is not a hand, and must not read as one. */
    @Test
    fun fiveCopiesOfOneCardIsStillOwedThePack() {
        val hoarder = character(CardCollection.FF14).copy(cards = mapOf(ff14.cards.first() to 5))

        assertEquals(1, StarterPack.playableCards(hoarder))
        assertTrue(StarterPack.isOwedBy(hoarder))
    }

    /** Only the starters of released sets may be opened with. */
    @Test
    fun anUnreleasedSetIsNotOnOffer() {
        val sets = listOf(
            CardSet(block = 1, slug = "ff14", nameKey = "A", sortOrder = 1, released = true),
            CardSet(block = 2, slug = "ff8", nameKey = "B", sortOrder = 2, released = false),
        )

        assertEquals(listOf(ff14.id), catalog.released(sets).map { it.id })
    }

    private companion object {
        /** Ten ids in block 1, the last of them the rare, and a deck of five holding it. */
        val ff14 = Starter(
            id = "test-ff14",
            block = 1,
            nameKey = "APP_TEST_FF14",
            cards = (1..10).map { Card.idFor(block = 1, number = it) },
            deck = listOf(1, 2, 3, 4, 10).map { Card.idFor(block = 1, number = it) },
        )

        val ff8 = Starter(
            id = "test-ff8",
            block = 2,
            nameKey = "APP_TEST_FF8",
            cards = (1..10).map { Card.idFor(block = 2, number = it) },
            deck = listOf(1, 2, 3, 4, 10).map { Card.idFor(block = 2, number = it) },
        )

        val catalog = StarterCatalog(listOf(ff14, ff8))
    }
}
