package com.tripletriad.ui

import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [bulkSaleOf] at the two limits the collection's UI test cannot reach together: a card no deck
 * lists, and a card a deck lists twice.
 *
 * The second is a save from before the one-copy rule. With one copy per deck the deck's reservation
 * and the "keep one" rule always agree, so only a doubled entry tells them apart.
 */
class BulkSaleTest {
    private val save = GameSave.new(createdAt = 1)

    @Test
    fun aLooseCardKeepsOneCopy() {
        val held = save.copy(cards = mapOf(CARD to 3), decks = emptyList())
        assertEquals(2, held.bulkSaleOf(CARD))
        assertEquals(0, held.copy(cards = mapOf(CARD to 1)).bulkSaleOf(CARD))
    }

    @Test
    fun aDeckThatListsACardTwiceKeepsBothCopies() {
        val held = save.copy(
            cards = mapOf(CARD to 3),
            decks = listOf(Deck(name = "old", cards = listOf(CARD, CARD))),
        )
        assertEquals(1, held.bulkSaleOf(CARD))
    }

    private companion object {
        const val CARD = 7
    }
}
