package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.data.CardValue
import com.tripletriad.data.Inventory
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.data.StarterPack
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.protocol.ItemEffect
import com.tripletriad.protocol.effect
import com.tripletriad.time.Clock
import kotlin.random.Random

/**
 * Where the character in play comes from, and where its changes go.
 *
 * ### Why this exists
 *
 * Because there are now two answers and the thirteen screens behind the dashboard must not have to
 * know which one is in force. Without a server the profile is a local `.sav` file, created and
 * written by [ProfileSession]. With one it is the account, fetched and stored by [AccountSession],
 * and only the server may write the parts a match decides. The screens want the same three things
 * from either: the profile, somewhere to put a changed one, and the key its unjudged matches queue
 * under.
 *
 * ### Why a value and not an interface
 *
 * The two implementations have nothing else in common — one lists and deletes local files, the
 * other registers accounts and holds a bearer token — so an interface would have exactly these
 * three members and two classes would implement it by delegating to themselves. A value built at
 * the one place that knows which source is live says the same thing with less indirection, and it
 * makes the *absence* of a character (nobody signed in, nothing selected) a null [profile] rather
 * than a third implementation.
 *
 * @property profile the character in play, or null when there is none.
 * @property queueKey where this character's unsubmitted matches wait, or null with no character.
 *   Derived differently by each source — see [AccountSession.queueKey] — which is precisely the
 *   sort of thing the screens should not be choosing between.
 * @property persist takes a changed profile. Local, this writes a file and stamps it; server-held,
 *   it updates what is on screen and sends it on. Neither throws: a match is already over by the
 *   time this runs.
 * @property useItem consumes something from the bag and reports what it did, or null when the
 *   attempt could not be made at all. **The two sources differ in who rolls the dice**, which is
 *   the whole reason this is not just another [persist]: locally the roll happens here, on an
 *   account it happens on the server. See [rememberAccountGate].
 * @property perform carries out an [Intent] — everything else that moves something of value. Same
 *   split as [useItem]: locally it is arithmetic, on an account it is a request.
 * @property nextSeed the randomness for one match, or null when there is none to be had. **The
 *   third thing the two sources genuinely disagree about**: a local profile makes one up, because
 *   there is nobody to keep honest; an account spends one the server issued, because a client that
 *   chooses its own seed chooses its own deal. Null means an account has run its offline stock
 *   down and must reconnect — see `SeedTickets`.
 */
class ProfileGate(
    val profile: GameSave?,
    val queueKey: String?,
    val persist: suspend (GameSave) -> Unit,
    val useItem: suspend (Item) -> ItemEffect?,
    val perform: suspend (Intent) -> Unit,
    val nextSeed: () -> Int?,
)

/**
 * Something the player asked for that moves money or cards.
 *
 * ### Why these are values and not five more members on [ProfileGate]
 *
 * Because the gate would then be a bag of lambdas that grows with every screen, and each new one
 * would be a member two implementations have to remember to fill in. A sealed type inverts that:
 * adding a case is a **compile error** in both interpreters until both handle it, which is exactly
 * the guarantee wanted when one of them is the thing standing between a player and free MGP.
 *
 * It also puts the two readings of each intent side by side — `ProfileGate.kt` says what each one
 * means locally, `AccountRoutes.kt` says what it means on an account — where a lambda per screen
 * would scatter them across the files that happen to call them.
 *
 * [Intent.UseItem] is deliberately *not* here: it is the one with an answer worth reporting, so it
 * keeps its own member and its own return type rather than being flattened into a case that
 * secretly returns something.
 */
sealed interface Intent {
    /**
     * Buy [offer] from the shop of [formatId].
     *
     * The offer is carried whole because the screen listing the shelf already resolved it. Its
     * **price is not sent** — the account path puts only the item and the format on the wire, and
     * the server prices it from its own table. The price here is for the local path, which has
     * nobody to keep honest.
     */
    data class Buy(val offer: ShopOffer, val formatId: String) : Intent

    /** Sell a bag item for what the card table says. */
    data class SellItem(val item: Item) : Intent

    /**
     * Sell **every one** of a bag item, at the same price each.
     *
     * Distinct from [SellItem] rather than a count on it, because the count is not the client's to
     * name: on an account the server reads it off the stored bag. See `AccountClient.sellAllItems`.
     */
    data class SellAllItems(val item: Item) : Intent

    /** Throw a bag item away. Nothing is paid. */
    data class DiscardItem(val item: Item) : Intent

    /** Sell a card out of the collection. */
    data class SellCard(val cardId: Int) : Intent

    /** Pay a ladder's entry fee, which is taken on the way in and never given back. */
    data class EnterCampaign(val campaignKey: String, val fee: Int) : Intent

    /**
     * Claim the box a profile that can no longer field a deck is owed.
     *
     * Carries the catalogue for the local path, which has to grant it here. The account path sends
     * nothing at all: which box, and whether one is owed, are both the server's to decide.
     */
    data class ClaimStarter(val catalog: StarterCatalog) : Intent
}

/**
 * The gate for a build with no server: the locally selected `.sav` profile.
 *
 * **The roll stays here, and that is not an oversight.** A local save is a file the player owns and
 * can edit with a text editor; moving its dice somewhere else would protect nothing from anybody.
 * What matters is that nothing anyone else can see is decided this way — see [rememberAccountGate],
 * where it is.
 *
 * @param random the pack draw. Injected for the same reason `Inventory.use` takes one: a drop that
 *   cannot be pinned cannot be asserted on.
 */
@Composable
internal fun rememberLocalGate(
    session: ProfileSession,
    cards: Map<Int, Card>,
    clock: Clock,
    random: Random = Random.Default,
): ProfileGate {
    val active = session.active
    return remember(active, cards, random) {
        ProfileGate(
            profile = active,
            queueKey = active?.let(SaveRepository::keyFor),
            persist = session::persist,
            useItem = { item ->
                val current = session.active ?: return@ProfileGate null
                val outcome = Inventory.use(current, item, random)
                session.persist(outcome.save)
                outcome.effect()
            },
            // What each intent means to a local file. The server's reading of the same list is in
            // `AccountRoutes.intentRoutes`, and the two run the **same `:core` functions** — which
            // is what stops a price from being one thing offline and another on an account.
            perform = { intent ->
                val current = session.active
                if (current != null) session.persist(current.applying(intent, cards))
            },
            // Invented, and that is correct here. A local save is the player's own file; a seed
            // they could not choose would protect nothing from anybody, and it would take offline
            // play away from the one mode that never needed a server for anything.
            nextSeed = { clock.nowMillis().toInt() },
        )
    }
}

/**
 * This profile with [intent] carried out — the local reading of the list.
 *
 * Every branch is a `:core` function and nothing here is a rule of its own. A local save is the
 * player's own file and editable with a text editor, so this is not a defence; it is the same game,
 * played without a server. See [rememberLocalGate].
 */
private fun GameSave.applying(intent: Intent, cards: Map<Int, Card>): GameSave = when (intent) {
    is Intent.Buy -> ShopCatalog.buy(this, intent.offer)
    is Intent.SellItem -> Inventory.sell(this, intent.item, cards)

    // The same `:core` function the server calls, given the same count it would compute — the
    // local path reads the bag it owns, and there is nobody here to keep honest either way.
    is Intent.SellAllItems -> Inventory.sell(
        this,
        intent.item,
        cards,
        count = Inventory.count(this, intent.item).coerceAtLeast(1),
    )
    is Intent.DiscardItem -> Inventory.remove(this, intent.item)

    is Intent.SellCard ->
        if (!ownsCard(intent.cardId)) {
            this
        } else {
            withoutCard(intent.cardId).withMgp(CardValue.resaleOf(intent.cardId, cards))
        }

    // The same floor-at-zero guard the server makes, and for the same reason: `withMgp` bounds at
    // zero, so without it being broke is the cheapest way into a ladder.
    is Intent.EnterCampaign -> if (mgp < intent.fee) this else withMgp(-intent.fee)

    is Intent.ClaimStarter -> StarterPack.grantedTo(this, intent.catalog)
}

/**
 * The gate for a build with a server: the account's character.
 *
 * **The roll is the server's here**, which is the one place these two implementations do something
 * genuinely different rather than writing to a different place. Opening a booster on an account is
 * worth something to somebody, so the dice must not belong to the party it is worth something to —
 * see `BagItemRequest` in `:core`. Locally there is nobody to keep honest and the roll stays put.
 */
@Composable
internal fun rememberAccountGate(session: AccountSession): ProfileGate {
    val player = session.player
    return remember(player) {
        ProfileGate(
            profile = player?.save,
            queueKey = session.queueKey,
            persist = session::persist,
            useItem = session::useItem,
            perform = session::perform,
            nextSeed = session::nextSeed,
        )
    }
}
