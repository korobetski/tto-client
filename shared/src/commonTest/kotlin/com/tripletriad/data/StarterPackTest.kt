package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarterPackTest {
    private fun character(collection: CardCollection = CardCollection.FF14) =
        GameSave.new(username = "Tester", mode = collection, createdAt = 1L)

    @Test
    fun aFreshCharacterIsOwedNothing() {
        for (collection in CardCollection.entries) {
            val save = character(collection)
            assertEquals(StarterPack.SIZE, StarterPack.playableCards(save), collection.name)
            assertFalse(StarterPack.isOwedBy(save), collection.name)
            assertEquals(save, StarterPack.grantedTo(save), "nothing to grant")
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
    fun startingInTheOtherCollectionRepointsCardsAndDeck() {
        val moved = StarterPack.startingIn(character(CardCollection.FF14), CardCollection.FF8)

        assertEquals(CardCollection.FF8, moved.mode)
        assertEquals(GameSave.defaultCollection(CardCollection.FF8), moved.cards)
        assertEquals(listOf(StarterPack.cardsFor(CardCollection.FF8)), moved.decks.map { it.cards })
        assertFalse(StarterPack.isOwedBy(moved), "the moved character can play")
        // Not ten cards: the FF14 starter is replaced, not kept alongside. Adding would hand a
        // player half of `ac-td1` for changing their mind about a menu.
        assertEquals(StarterPack.SIZE, moved.cards.size)
    }

    @Test
    fun confirmingTheCollectionAlreadyInPlayChangesNothing() {
        val save = character(CardCollection.FF14)

        assertEquals(save, StarterPack.startingIn(save, CardCollection.FF14))
    }

    @Test
    fun grantingRepairsAStrandedCharacterWithoutTouchingWhatItKept() {
        val kept = Card.idFor(block = CardCollection.FF8.block, number = 42)
        val stranded = character(CardCollection.FF14)
            .copy(mode = CardCollection.FF8, cards = mapOf(kept to 3))

        val repaired = StarterPack.grantedTo(stranded)

        assertFalse(StarterPack.isOwedBy(repaired))
        assertEquals(3, repaired.copiesOf(kept), "copies already held are not disturbed")
        for (id in StarterPack.cardsFor(CardCollection.FF8)) {
            assertTrue(repaired.ownsCard(id), "starter card $id was not granted")
        }
    }

    /** A card already in the starter is topped up to one copy, never to two. */
    @Test
    fun grantingDoesNotDoubleWhatIsAlreadyOwned() {
        val ids = StarterPack.cardsFor(CardCollection.FF14)
        val partial = character(CardCollection.FF14).copy(cards = mapOf(ids.first() to 1))

        val repaired = StarterPack.grantedTo(partial)

        assertEquals(ids.associateWith { 1 }, repaired.cards)
    }

    @Test
    fun grantingLeavesAPlayableDeckAtTheTop() {
        val stranded = character(CardCollection.FF14).copy(
            mode = CardCollection.FF8,
            decks = listOf(Deck("Stranded", StarterPack.cardsFor(CardCollection.FF14))),
        )

        val repaired = StarterPack.grantedTo(stranded)
        val deck = repaired.decks.first()

        assertTrue(deck.isComplete, "the granted deck is a full hand")
        assertTrue(deck.isAffordable(repaired.cards), "and every card in it is owned")
        assertEquals(StarterPack.cardsFor(CardCollection.FF8), deck.cards)
        assertTrue(repaired.decks.size <= GameSave.MAX_DECKS)
    }

    /** Copies are not cards: five of one is not a hand, and must not read as one. */
    @Test
    fun fiveCopiesOfOneCardIsStillOwedThePack() {
        val one = StarterPack.cardsFor(CardCollection.FF14).first()
        val hoarder = character(CardCollection.FF14).copy(cards = mapOf(one to 5))

        assertEquals(1, StarterPack.playableCards(hoarder))
        assertTrue(StarterPack.isOwedBy(hoarder))
    }
}
