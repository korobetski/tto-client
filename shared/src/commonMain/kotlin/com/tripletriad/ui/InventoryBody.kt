package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.protocol.ItemEffect
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch

const val INVENTORY_LIST_TEST_TAG: String = "inventory-list"
const val INVENTORY_EMPTY_TEST_TAG: String = "inventory-empty"
const val INVENTORY_USE_TEST_TAG: String = "inventory-use"
const val INVENTORY_SELL_TEST_TAG: String = "inventory-sell"
const val INVENTORY_SELL_ALL_TEST_TAG: String = "inventory-sell-all"

const val INVENTORY_NOTE_TEST_TAG: String = "inventory-note"

fun inventoryRowTestTag(item: Item): String = "inventory-row-${itemSlug(item)}"

@Composable
internal fun ColumnScope.InventoryBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    onUse: suspend (Item) -> ItemEffect?,
    onIntent: suspend (Intent) -> IntentOutcome,
    onUnlocked: (Card) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val cards = remember(catalog, format) {
        catalog.admittedBy(format).associateBy { it.id }
    }
    val owned = profile.cards

    // The selection is held as an [itemKey] and looked up in the *current* bag on every
    // composition, so selling one of three leaves the same row selected and emptying the stack
    // deselects it. Holding the `Item` itself would keep a row whose stack no longer exists.
    var selectedKey by remember(format) { mutableStateOf<Item?>(null) }
    var note by remember(format) { mutableStateOf<String?>(null) }

    /*
     * Whether an operation is out, which is what stops a second tap landing behind the first.
     *
     * On an account every one of these three buttons is a round trip, and until now nothing
     * disabled them while it was in flight. Two taps on Use meant **two requests with two operation
     * ids**, which is precisely what `Idempotent` cannot help with: they are two different intents
     * as far as the server is concerned, so a double-tapped pack really was opened twice — and the
     * second reveal replaced the first, so the cards from one of them appeared out of nowhere in
     * the bag. Two taps on Sell all sold a stack that was no longer there and paid nothing for it.
     *
     * One flag for all three rather than one each: they act on the same selected item, and there
     * is no pair of them that makes sense to have in flight at once.
     */
    var busy by remember(format) { mutableStateOf(false) }

    // The cards a pack just dealt, while the player is turning them over. Held here rather than
    // navigated to because the reveal is a *moment inside using an item*, not a destination: the
    // profile has already been written by the time it appears, so backing out of it cannot lose
    // anything and there is nothing for a back stack to restore.
    var opened by remember(format) { mutableStateOf<List<Int>?>(null) }

    val selected = profile.bag.firstOrNull { itemKey(it) == selectedKey }

    opened?.let { drawn ->
        PackRevealScreen(cardIds = drawn, cards = cards, onDone = { opened = null })
        return
    }

    note?.let { EmptyNote(it, INVENTORY_NOTE_TEST_TAG) }

    if (profile.bag.isEmpty()) {
        EmptyNote(strings[StringKeys.EMPTY_BAG], INVENTORY_EMPTY_TEST_TAG)
    } else {
        LazyColumn(
            modifier = Modifier
                .testTag(INVENTORY_LIST_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(profile.bag, key = { itemSlug(it) }) { item ->
                ItemRow(
                    item = item,
                    cards = cards,
                    note = ownedNote(strings, item, owned),
                    isSelected = itemKey(item) == selectedKey,
                    onClick = {
                        // The note is about the last thing that was done, so it does not survive
                        // picking something else to do — a refusal left standing over a different
                        // row reads as a refusal of *that* row.
                        note = null
                        selectedKey = itemKey(item).takeIf { it != selectedKey }
                    },
                )
            }
        }
    }

    selected?.let { item ->
        // One operation at a time, and the note is cleared as it starts: a line left standing while
        // the next request is out is an answer to the previous tap being read as an answer to this
        // one. See [busy].
        val start: (suspend () -> String?) -> Unit = { work ->
            note = null
            busy = true
            scope.launch {
                try {
                    note = work()
                } finally {
                    busy = false
                }
            }
        }

        BagActions(
            item = item,
            cards = cards,
            stack = item.stack,
            canUse = item.useable && !busy,
            enabled = !busy,
            onUse = {
                // Suspending, and it has to be: on an account the answer is a round trip, and
                // there is nothing to show optimistically because the client no longer knows
                // what came out. The profile is written by whoever answered — see
                // [ProfileGate.useItem] — so nothing is persisted from here.
                start {
                    // **Null is an answer and it used to be an early return**, which made a tap
                    // that could not reach the server indistinguishable from a tap that never
                    // registered. It means the attempt was not made at all — nobody signed in, or
                    // the request did not come back — as opposed to [ItemEffect.NotUseable], which
                    // means it was made and refused. Two different sentences, and both were
                    // silence.
                    val effect = onUse(item) ?: return@start strings[StringKeys.ACTION_FAILED]
                    // Only a card *entering the collection* is revealed, which is the single
                    // branch `useBtnHandler` plays it in (`:236-245`). Opening a pack yields
                    // another bag item rather than a card, and showing it here would announce a
                    // card the player does not own yet.
                    (effect as? ItemEffect.CardDrawn)?.let { cards[it.cardId] }?.let(onUnlocked)
                    // A pack is turned over rather than announced — see [PackRevealScreen].
                    opened = (effect as? ItemEffect.PackOpened)?.cardIds
                    useNote(strings, effect, cards)
                }
            },
            onSell = { start { sellNote(strings, onIntent(Intent.SellItem(item))) } },
            onSellAll = { start { sellNote(strings, onIntent(Intent.SellAllItems(item))) } },
        )
    }
}

private fun sellNote(strings: Strings, outcome: IntentOutcome): String? = when (outcome) {
    IntentOutcome.APPLIED -> null
    IntentOutcome.REFUSED -> strings[StringKeys.ITEM_REFUSED]
    IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
}

@Composable
private fun ItemRow(
    item: Item,
    cards: Map<Int, Card>,
    note: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(inventoryRowTestTag(item))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            // One at a time: the buttons below the list act on whatever is selected.
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        // A card item shows the card; everything else shows the icon `Item.iconId` has named
        // since Phase 2 and that nothing has drawn until now — the booster's own tribe pack, the
        // rarity plate, the two boosts.
        val card = itemCard(item, cards)
        if (card != null) {
            CardThumb(card = card)
        } else {
            ItemGlyph(item = item, description = itemName(strings, item, cards))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, item, cards),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemFacts(strings, item, cards, note),
                color = if (note == null) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
                } else {
                    LocalTtoColors.current.transient
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // `×1` is drawn too. A stack column that appears only above one is a column that shifts the
        // name every time a purchase lands.
        Text(
            text = "×${item.stack}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun BagActions(
    item: Item,
    cards: Map<Int, Card>,
    stack: Int,
    canUse: Boolean,
    enabled: Boolean,
    onUse: () -> Unit,
    onSell: () -> Unit,
    onSellAll: () -> Unit,
) {
    val strings = LocalStrings.current
    val price = Inventory.priceOf(item, cards)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                label = strings[StringKeys.USE],
                tag = INVENTORY_USE_TEST_TAG,
                enabled = canUse,
                onClick = onUse,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                // Only a card item is sellable, and what it fetches is its **rarity** — see
                // `CardValue`, and `CardItem.value`, which used to answer `id × 4` and no longer
                // answers at all. Zero means the shop will not buy it.
                label = "${strings[StringKeys.SELL]} $price",
                tag = INVENTORY_SELL_TEST_TAG,
                enabled = enabled && price > 0,
                onClick = onSell,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                // What it will pay, not how many it will sell: the player can see the stack on the
                // row, and the number that decides the tap is the total. `Sell 12` beside
                // `Sell all 36` reads as one price and one price times three, which is what it is.
                label = "${strings[StringKeys.SELL_ALL]} ${price * stack}",
                tag = INVENTORY_SELL_ALL_TEST_TAG,
                // Disabled at a stack of one, where it would be the button beside it: two controls
                // that do the same thing invite the player to wonder which one they got wrong.
                enabled = enabled && price > 0 && stack > 1,
                onClick = onSellAll,
            )
        }
    }
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
    }

private fun itemFacts(
    strings: Strings,
    item: Item,
    cards: Map<Int, Card>,
    note: String?,
): String = buildList {
    val price = Inventory.priceOf(item, cards)
    if (price > 0) add("${strings[StringKeys.SELL]} $price")
    note?.let(::add)
}.joinToString(DOT_SEPARATOR)
