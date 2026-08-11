package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave

/**
 * The five cards a character cannot play without, and the two places they are handed out.
 *
 * ### The hole this closes
 *
 * `GameSave.new` seeds a profile with [GameSave.defaultCards] for the collection it is created
 * with, and that is correct. What global card ids broke is everything that changes the collection
 * *afterwards*: an id used to be an index into whichever table `MODE` named, so re-pointing a
 * profile at the other set was a one-field edit and the same five numbers meant the other set's
 * first five cards. An id names a set outright now — `(block shl 8) or number` — so
 * `copy(mode = FF8)` leaves a profile holding five FFXIV cards that its own opponents, shop and
 * deck builder all refuse to show. [PveMatch.playableDecks] filters decks to the ids of
 * `catalog.collection(profile.mode)`, so the result is not a degraded profile but an unplayable
 * one: no deck, no collection, no match.
 *
 * That is exactly what the post-registration collection step did — see `CollectionChoiceScreen`,
 * whose comment still argued the edit was harmless because "every card id, deck and opponent it
 * could invalidate is still the default one". True while ids were local; false since.
 *
 * ### Why the same object answers both questions
 *
 * [startingIn] fixes the profiles that have not been made yet. It cannot fix the ones already
 * stored on a server, which is what [isOwedBy] and [grantedTo] are for: the shop offers the pack
 * back to any character short of a playable hand, so a broken account repairs itself without a
 * migration and without support. One rule — *fewer than five cards this set can field* — asked in
 * both places, rather than a screen-local guess in each.
 */
object StarterPack {
    /** How many cards a character needs before it can field a deck at all. */
    val SIZE: Int = GameSave.DEFAULT_CARD_NUMBERS.size

    /** The starter five as ids in [collection]'s own set. */
    fun cardsFor(collection: CardCollection): List<Int> = GameSave.defaultCards(collection)

    /**
     * How many distinct cards [save] owns that its own set can actually field.
     *
     * Distinct, not copies: five copies of one card is not a deck, and [Deck.isAffordable] would
     * refuse the hand built from it.
     */
    fun playableCards(save: GameSave): Int = save.cards.count { (id, copies) ->
        copies > 0 && id shr Card.BLOCK_SHIFT == save.mode.block
    }

    /** Whether [save] is short of a playable hand and should be given the pack. */
    fun isOwedBy(save: GameSave): Boolean = playableCards(save) < SIZE

    /**
     * [save] with the starter pack in it, or unchanged when it was not owed one.
     *
     * Additive: a character that owns three fieldable cards keeps them and is topped up, because
     * the two it is missing are the whole of what is wrong with it. Copies already held are not
     * doubled — this is a repair, not a reward.
     *
     * A starter deck is prepended when none of the saved decks can be fielded, which is always the
     * case here: a profile owed this pack owns fewer than five playable cards, so no complete deck
     * can be affordable. The list is trimmed to [GameSave.MAX_DECKS], so a character already
     * holding five named decks loses the last of them — decks it demonstrably cannot play, on the
     * one path that exists to make it playable again.
     */
    fun grantedTo(save: GameSave): GameSave {
        if (!isOwedBy(save)) return save
        val ids = cardsFor(save.mode)
        val granted = ids.fold(save) { profile, id ->
            if (profile.ownsCard(id)) profile else profile.withCard(id)
        }
        return granted.copy(
            decks = (listOf(Deck(GameSave.DEFAULT_DECK_NAME, ids)) + granted.decks)
                .take(GameSave.MAX_DECKS),
        )
    }

    /**
     * [save] re-pointed at [collection], cards and starter deck included.
     *
     * A **replacement** and not a top-up, unlike [grantedTo], and only sound because of where it is
     * called from: the collection step is offered once, immediately after registration, before a
     * single match — so the cards being dropped are the ones the server dealt a minute ago and
     * nothing else. Adding instead would leave the character holding both sets' starters, which is
     * ten cards towards `ac-td1`'s ten for having changed its mind about a menu.
     *
     * Returns [save] untouched when the collection is the one it already plays, so confirming the
     * default cannot reset anything.
     */
    fun startingIn(save: GameSave, collection: CardCollection): GameSave =
        if (save.mode == collection) {
            save
        } else {
            save.copy(
                mode = collection,
                cards = GameSave.defaultCollection(collection),
                decks = listOf(Deck(GameSave.DEFAULT_DECK_NAME, cardsFor(collection))),
            )
        }
}
