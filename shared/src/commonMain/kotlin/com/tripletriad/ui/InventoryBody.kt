package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PouchItem
import com.tripletriad.protocol.ItemEffect
import kotlinx.coroutines.launch

const val INVENTORY_LIST_TEST_TAG: String = "inventory-list"
const val INVENTORY_EMPTY_TEST_TAG: String = "inventory-empty"

/** The way out of an empty bag: the shelf that fills it. */
const val INVENTORY_SHOP_TEST_TAG: String = "inventory-shop"

/**
 * The answer to the last action, on the row it was asked of.
 *
 * One tag rather than one per row, and that is not an oversight: there is one operation at a time
 * — see the lock in [InventoryBody] — so there is one note at a time, and it belongs to whichever
 * row asked for it.
 */
const val INVENTORY_NOTE_TEST_TAG: String = "inventory-note"

/** The header above each group of the bag. */
fun inventoryGroupTestTag(group: String): String = "inventory-group-$group"

/**
 * The bag, in three shelves.
 *
 * ### Why grouped
 *
 * A bag holds three kinds of thing that are used for three unrelated reasons — packs to be opened,
 * boons to be drunk, cards to be kept or sold — and a flat list interleaves them in the order they
 * happened to arrive. The groups are the same three the shop sells under, so what was bought under
 * `Boosters` is found again under `Boosters`.
 *
 * A header is drawn for every stocked group, including when it is the only one. A header that
 * appears and disappears with the shape of the bag would make the same screen look like two
 * different ones between two visits.
 */
@Composable
@Suppress("LongParameterList")
internal fun ColumnScope.InventoryBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    onUse: suspend (Item) -> ItemEffect?,
    onIntent: suspend (Intent) -> IntentOutcome,
    onUnlocked: (Card) -> Unit,
    onShop: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val cards = remember(catalog, format) {
        catalog.admittedBy(format).associateBy { it.id }
    }
    val owned = profile.cards

    /*
     * Which item an operation is out for, which is what stops a second tap landing behind the
     * first.
     *
     * On an account every one of these actions is a round trip, and until the buttons disabled
     * themselves two taps on Use meant **two requests with two operation ids** — precisely what
     * `Idempotent` cannot help with, since they are two different intents as far as the server is
     * concerned, so a double-tapped pack really was opened twice. Two taps on Sell all sold a
     * stack that was no longer there and paid nothing for it.
     *
     * One lock for the whole bag rather than one per row: they act on the same profile, and there
     * is no pair of them that makes sense to have in flight at once. What changed with the actions
     * moving onto the rows is where it is *seen* — the row that asked wears it (`acting`), and
     * every other row's controls go quiet (`locked`).
     *
     * Held as an [itemKey] rather than the item, so the row keeps its lock while its stack shrinks.
     */
    var busyKey by remember(format) { mutableStateOf<Item?>(null) }

    // The answer to that operation, and the item it belongs to. Paired rather than a bare string
    // because it is drawn on a row now: a line left over a different row reads as being about
    // that row.
    var note by remember(format) { mutableStateOf<Pair<Item, String>?>(null) }

    // The cards a pack just dealt, while the player is turning them over. Held here rather than
    // navigated to because the reveal is a *moment inside using an item*, not a destination: the
    // profile has already been written by the time it appears, so backing out of it cannot lose
    // anything and there is nothing for a back stack to restore.
    var opened by remember(format) { mutableStateOf<List<Int>?>(null) }

    opened?.let { drawn ->
        PackRevealScreen(cardIds = drawn, cards = cards, onDone = { opened = null })
        return
    }

    // An answer whose row is gone — the last of a stack sold, a pack opened — is drawn above the
    // list instead. The note belongs to the action, not to the row, and the actions that empty a
    // row are exactly the ones worth reporting.
    note?.takeIf { pair -> profile.bag.none { itemKey(it) == pair.first } }?.let { (_, line) ->
        EmptyNote(line, INVENTORY_NOTE_TEST_TAG)
    }

    if (profile.bag.isEmpty()) {
        EmptyBag(onShop)
        return
    }

    // One operation at a time, and the note is cleared as it starts: a line left standing while
    // the next request is out is an answer to the previous tap being read as an answer to this one.
    val start: (Item, suspend () -> String?) -> Unit = { item, work ->
        val key = itemKey(item)
        note = null
        busyKey = key
        scope.launch {
            try {
                note = work()?.let { key to it }
            } finally {
                busyKey = null
            }
        }
    }

    val use: (Item) -> Unit = { item ->
        // Suspending, and it has to be: on an account the answer is a round trip, and there is
        // nothing to show optimistically because the client no longer knows what came out. The
        // profile is written by whoever answered — see [ProfileGate.useItem].
        start(item) {
            // **Null is an answer**: the attempt was not made at all — nobody signed in, or the
            // request did not come back — as opposed to [ItemEffect.NotUseable], which means it
            // was made and refused. Two different sentences, and both used to be silence.
            val effect = onUse(item) ?: return@start strings[StringKeys.ACTION_FAILED]
            // Only a card *entering the collection* is revealed, which is the single branch
            // `useBtnHandler` plays it in (`:236-245`). Opening a pack yields another bag item
            // rather than a card, and showing it here would announce a card that is not owned yet.
            (effect as? ItemEffect.CardDrawn)?.let { cards[it.cardId] }?.let(onUnlocked)
            // A pack is turned over rather than announced — see [PackRevealScreen].
            opened = (effect as? ItemEffect.PackOpened)?.cardIds
            useNote(strings, effect, cards)
        }
    }

    val groups = remember(profile.bag) { profile.bag.groupBy(::bagGroupOf) }

    LazyColumn(
        modifier = Modifier
            .testTag(INVENTORY_LIST_TEST_TAG)
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (group in BagGroup.entries) {
            val held = groups[group] ?: continue

            item(key = "group-${group.slug}") {
                SectionHeader(
                    text = strings[group.labelKey],
                    modifier = Modifier.testTag(inventoryGroupTestTag(group.slug)),
                )
            }

            items(held, key = { itemSlug(it) }) { item ->
                BagItemRow(
                    item = item,
                    cards = cards,
                    owned = owned,
                    note = note?.takeIf { it.first == itemKey(item) }?.second,
                    locked = busyKey != null,
                    acting = busyKey == itemKey(item),
                    actions = BagActions(
                        onUse = { use(item) },
                        onSell = {
                            start(item) { sellNote(strings, onIntent(Intent.SellItem(item))) }
                        },
                        onSellAll = {
                            start(item) { sellNote(strings, onIntent(Intent.SellAllItems(item))) }
                        },
                    ),
                )
            }
        }
    }
}

/**
 * The three shelves of the bag, in the order the shop stocks them.
 *
 * A pouch and an unrecognised item sit under [BOONS] rather than in a fourth group of their own:
 * both are things that are used once for what they hold, which is what the group means, and a
 * group with one row in it every few weeks is a header that mostly says nothing.
 */
internal enum class BagGroup(val slug: String, val labelKey: String) {
    BOOSTERS("boosters", StringKeys.BOOSTERS),
    BOONS("boons", StringKeys.BOONS),
    CARDS("cards", StringKeys.CARDS),
}

internal fun bagGroupOf(item: Item): BagGroup = when (item) {
    is BoosterItem -> BagGroup.BOOSTERS
    is PotionItem, is PouchItem, is MiscItem -> BagGroup.BOONS
    is CardItem -> BagGroup.CARDS
}

/**
 * An empty bag, and the one thing that fills it.
 *
 * The line alone was a dead end on a screen whose other tab is the shop — the way out was a tab
 * the player had to think of themselves.
 */
@Composable
private fun EmptyBag(onShop: () -> Unit) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyNote(strings[StringKeys.EMPTY_BAG], INVENTORY_EMPTY_TEST_TAG)
        WideButton(
            label = strings[StringKeys.CARD_SHOP],
            tag = INVENTORY_SHOP_TEST_TAG,
            filled = false,
            onClick = onShop,
        )
    }
}

private fun sellNote(strings: Strings, outcome: IntentOutcome): String? = when (outcome) {
    IntentOutcome.APPLIED -> null
    IntentOutcome.REFUSED -> strings[StringKeys.ITEM_REFUSED]
    IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
}

private fun useNote(strings: Strings, effect: ItemEffect, cards: Map<Int, Card>): String? =
    when (effect) {
        is ItemEffect.PackOpened -> null

        is ItemEffect.CardDrawn -> strings.format(
            StringKeys.OBTAINED,
            cards[effect.cardId]?.let { strings[it.nameKey] } ?: "#${effect.cardId}",
        )

        // The boon is shown by the character bar's `MGP ×n`, which is the fact itself rather than
        // a sentence about it — and it is on screen the moment the potion is drunk.
        is ItemEffect.BoonRaised -> null

        is ItemEffect.NotUseable -> strings[StringKeys.ITEM_REFUSED]

        // The one effect here whose *number* is the point. Everything else the bag does is
        // visible on the row it changed; a payout is visible only as a purse that is larger
        // than it was, and nobody remembers what it was.
        is ItemEffect.PouchOpened -> strings.format(
            StringKeys.POUCH_OPENED,
            effect.mgp.toString(),
            cardName(strings, effect.cardId, cards),
        )
    }
