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

class ProfileGate(
    val profile: GameSave?,
    val queueKey: String?,
    val persist: suspend (GameSave) -> Unit,
    val useItem: suspend (Item) -> ItemEffect?,
    val perform: suspend (Intent) -> IntentOutcome,
    val nextSeed: () -> Int?,
)

enum class IntentOutcome {
    APPLIED,

    REFUSED,

    UNREACHABLE,
}

sealed interface Intent {
    data class Buy(val offer: ShopOffer, val formatId: String) : Intent

    data class SellItem(val item: Item) : Intent

    data class SellAllItems(val item: Item) : Intent

    data class DiscardItem(val item: Item) : Intent

    data class SellCard(val cardId: Int) : Intent

    data class EnterCampaign(val campaignKey: String, val fee: Int) : Intent

    data class ClaimStarter(val catalog: StarterCatalog) : Intent
}

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
                    ?: return@ProfileGate IntentOutcome.UNREACHABLE
                // Every branch of `applying` refuses by returning the profile **unchanged** — see
                // `Intent.SellCard`, which is the one that most visibly does — so comparing is how
                // the local path knows the same thing a status code tells the account path. It is
                // a data class, so this is the field-by-field comparison it looks like.
                val updated = current.applying(intent, cards)
                if (updated == current) {
                    IntentOutcome.REFUSED
                } else {
                    session.persist(updated)
                    IntentOutcome.APPLIED
                }
            },
            // Invented, and that is correct here. A local save is the player's own file; a seed
            // they could not choose would protect nothing from anybody, and it would take offline
            // play away from the one mode that never needed a server for anything.
            nextSeed = { clock.nowMillis().toInt() },
        )
    }
}

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

    // **Spare copies, not merely owned.** `SellButton` has always hidden itself for a card a saved
    // deck is built on — see `GameSave.spareCopiesOf` — and this, the code that actually applies
    // the sale, asked only whether the card was held at all. A rule enforced by the screen and not
    // by the thing behind it is a rule enforced by nothing, which is the argument
    // `Deck.isAffordable` makes about the deck editor. Selling the copy a deck names leaves a deck
    // that cannot be fielded and a card the player meant to keep, gone for a fraction of its worth.
    is Intent.SellCard ->
        if (spareCopiesOf(intent.cardId) < 1) {
            this
        } else {
            withoutCard(intent.cardId).withMgp(CardValue.resaleOf(intent.cardId, cards))
        }

    // The same floor-at-zero guard the server makes, and for the same reason: `withMgp` bounds at
    // zero, so without it being broke is the cheapest way into a ladder.
    is Intent.EnterCampaign -> if (mgp < intent.fee) this else withMgp(-intent.fee)

    is Intent.ClaimStarter -> StarterPack.grantedTo(this, intent.catalog)
}

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
