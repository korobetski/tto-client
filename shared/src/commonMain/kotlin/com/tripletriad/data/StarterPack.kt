package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE

/**
 * Granting a character the box it opens with, and repairing one that never got a usable box.
 *
 * ### What the box is
 *
 * [StarterCatalog] — `starters.json`, ten cards, document 19's replacement for the AS3's hard-coded
 * five. The composition rule and the reasoning behind it are documented there; this type is the two
 * places the grant happens.
 *
 * ### The hole this closes
 *
 * `GameSave.new` seeds a profile for the collection it is created with, and that is correct. What
 * global card ids broke is everything that changes the collection *afterwards*: an id used to be
 * an index into whichever table `MODE` named, so re-pointing a profile at the other set was a
 * one-field edit and the same five numbers meant that set's first five cards. An id names a set
 * now — `(block shl 8) or number` — so `copy(mode = FF8)` leaves a profile holding FFXIV cards
 * that its own opponents, shop and deck builder all refuse to show. `PveMatch.playableDecks`
 * filters decks to the ids of `catalog.collection(profile.mode)`, so the result is not a degraded
 * profile but an unplayable one: no deck, no collection, no match.
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
 *
 * ### What is not document 19 yet
 *
 * **The grant is the client's.** Document 19 § The server grants it, not the client puts it on the
 * server: the client would send `starterId` and never the cards, and the server would resolve it
 * against its own copy of `starters.json`. That needs the `NewCharacter` endpoint of document 18,
 * which is *proposed* and does not exist — today the server creates the character at registration.
 * Until then the client grants, and this comment is the marker for where that moves.
 *
 * **The choice is still a collection.** Document 19 has a player choose a *starter*, which does not
 * restrict them to its set. `MODE` still gates the shop, the opponents and the campaign here, so
 * the creation screen still chooses a collection and the starter follows from its block —
 * [forCollection]. It becomes a real choice when `MODE` goes.
 */
object StarterPack {
    /** How many cards a character needs before it can field a deck at all. */
    const val SIZE: Int = HAND_SIZE

    /**
     * The starter that opens [collection]'s set, or null if none is authored for it.
     *
     * The bridge from the enum that document 19 removes: a collection is a block, and a block has
     * a starter. When the creation screen offers starters directly this goes with `MODE`.
     */
    fun forCollection(catalog: StarterCatalog, collection: CardCollection): Starter? =
        catalog.forBlock(collection.block)

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
     * [save] holding exactly [starter]'s cards, with its deck in the first slot.
     *
     * The one place a starter is *written* onto a profile, and therefore the one place the shape of
     * that write is decided: one copy of each card, and the authored five as the opening deck.
     * Both creation paths and the collection step go through it, which is what stops a character
     * created locally and a character created by registering from being dealt different boxes —
     * they were, until this existed: `GameSave.new` still seeds the AS3's five, and only the
     * account path had been moved onto the catalogue.
     */
    fun opened(save: GameSave, starter: Starter): GameSave = save.copy(
        cards = starter.cards.associateWith { 1 },
        decks = listOf(Deck(GameSave.DEFAULT_DECK_NAME, starter.deck)),
    )

    /**
     * [save] with the starter pack in it, or unchanged when it was not owed one.
     *
     * Additive: a character that owns three fieldable cards keeps them and is topped up, because
     * the two it is missing are the whole of what is wrong with it. Copies already held are not
     * doubled — this is a repair, not a reward.
     *
     * The authored deck is prepended when none of the saved decks can be fielded, which is always
     * the case here: a profile owed this pack owns fewer than five playable cards, so no complete
     * deck can be affordable. The list is trimmed to [GameSave.MAX_DECKS], so a character already
     * holding five named decks loses the last of them — decks it demonstrably cannot play, on the
     * one path that exists to make it playable again.
     *
     * Unchanged when the set has no authored starter, which [StarterCatalog.violations] refuses at
     * authoring time. A released set reaching a player without one is a content bug, and giving
     * nothing is the honest outcome rather than inventing five ids.
     */
    // ReturnCount: two guards and the result. Both guards say "there is nothing to do", and
    // folding them into one `if` would nest the whole body inside it for no gain.
    @Suppress("ReturnCount")
    fun grantedTo(save: GameSave, catalog: StarterCatalog): GameSave {
        if (!isOwedBy(save)) return save
        val starter = forCollection(catalog, save.mode) ?: return save
        val granted = starter.cards.fold(save) { profile, id ->
            if (profile.ownsCard(id)) profile else profile.withCard(id)
        }
        return granted.copy(
            decks = (listOf(Deck(GameSave.DEFAULT_DECK_NAME, starter.deck)) + granted.decks)
                .take(GameSave.MAX_DECKS),
        )
    }

    /**
     * [save] re-pointed at [collection], with that set's starter and its deck.
     *
     * A **replacement** and not a top-up, unlike [grantedTo], and only sound because of where it is
     * called from: the collection step is offered once, immediately after registration, before a
     * single match — so the cards being dropped are the ones the server dealt a minute ago and
     * nothing else. Adding instead would leave the character holding both sets' starters, which is
     * twenty cards towards `ac-td2`'s thirty for having changed its mind about a menu.
     *
     * Returns [save] untouched when the collection is the one it already plays, so confirming the
     * default cannot reset anything — and when the target set has no starter, for the reason
     * [grantedTo] gives.
     */
    // ReturnCount: as [grantedTo], and for the same two reasons.
    @Suppress("ReturnCount")
    fun startingIn(
        save: GameSave,
        collection: CardCollection,
        catalog: StarterCatalog,
    ): GameSave {
        if (save.mode == collection) return save
        val starter = forCollection(catalog, collection) ?: return save
        return opened(save.copy(mode = collection), starter)
    }
}
