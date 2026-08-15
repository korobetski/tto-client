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

/** The line saying what using something did. Absent until something has been used. */
const val INVENTORY_NOTE_TEST_TAG: String = "inventory-note"

/** `inventory-row-<slug>` — see [itemSlug] for why the slug and not the bag position. */
fun inventoryRowTestTag(item: Item): String = "inventory-row-${itemSlug(item)}"

/**
 * The bag — the original's `InventoryScreen`.
 *
 * Tap an entry to select it, then Use, Sell or Discard it. That is the original's arrangement: a
 * list with a three-button footer whose buttons enable themselves from the selected item's own
 * flags (`listHandler`, `:104-117`). The flags are [Item.useable], [Item.sellable] and
 * [Item.dropable], and they are per-kind constants — see [Item].
 *
 * ### Four departures, each with a reason
 *
 * - **There is no Sort button.** `sortBtnHandler` re-ran `sortBag()`, which existed because the bag
 *   could get out of order and hold two rows of the same item — the AS3 pushed unconditionally from
 *   three different places. [Inventory.add] stacks and sorts on every insert, so the state the
 *   button repaired is unreachable and a control that can only ever do nothing is worse than none.
 * - **Discard asks twice.** `dropBtnHandler` opens on `// TODO : afficher une Alert` and then
 *   discards immediately; the bag is the only place in the game where a tap destroys something with
 *   no way back. The second tap is the alert the original meant to write, in the shape the
 *   character list already uses for deletion.
 * - **A card item draws its card, not an icon.** `CardItem.iconFor` names `card_r{n}_icon`, a plate
 *   whose whole content is the rarity — and the card itself says that and everything else besides.
 *   The other kinds keep their icons; see [itemIconId] for the one whose name does not match
 *   what is shipped.
 * - **Every write goes through [ProfileSession].** The original saved from `sellBtnHandler` and
 *   `dropBtnHandler` and **not** from `useBtnHandler` — opening a pack or drinking a potion was
 *   persisted only by the `sortBag()` call at the end of the handler, which saves as a side effect
 *   (`:200`). One path here, so a use cannot be the one operation that is lost.
 *
 * @param onUnlocked a card that has just entered the collection, to be shown off. Reported upwards
 *   rather than drawn here because [UnlockedCard] covers the **whole screen**, and this is one tab
 *   of one — a full-screen overlay placed inside a column is a very tall column entry.
 * @param onUse consumes the item and reports what it did. **Not `Inventory.use` called here**,
 *   which is what this used to be: on an account the roll belongs to the server, and this screen
 *   must not be the thing that decides whether it does. See [ProfileGate.useItem].
 */
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

/**
 * What a sale did, in one line — the same three answers a use gets, in the same three words.
 *
 * ### Why a sale needs one at all
 *
 * Because it can do nothing, and used to do nothing *invisibly*. `ProfileGate.perform` answered
 * `Unit`, so the bag could not tell a sale from a refusal and said nothing either way; on an
 * account a refusal also replaces the client's bag with the server's, so the row disappears and no
 * MGP arrives. That is the same shape the reported Use bug had, one button along.
 *
 * [IntentOutcome.APPLIED] is deliberately silent. The purse in the app bar and the row that just
 * lost a copy have already said it, and a snackbar repeating what two other things on screen show
 * is noise on the one action a player performs in runs of five.
 */
private fun sellNote(strings: Strings, outcome: IntentOutcome): String? = when (outcome) {
    IntentOutcome.APPLIED -> null
    IntentOutcome.REFUSED -> strings[StringKeys.ITEM_REFUSED]
    IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
}

/**
 * One bag entry: what it is, how many, and what it is worth.
 *
 * A card item draws its card beside the name — see [InventoryScreen] for why there are no icons.
 *
 * @param note what else the row has to say — today, that a card item is not the first copy. See
 *   [ownedNote], which is where the AS3's "already owned, Use disabled" rule used to live.
 */
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
            ItemIcon(iconId = itemIconId(item), description = itemName(strings, item, cards))
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

/**
 * Use, Sell and Sell all, each enabled by the selected item's own flags.
 *
 * ### Discard is gone, and nothing is stranded by its going
 *
 * The third button used to be Discard: destroy the item, be paid nothing. It was the only control
 * in the game where a tap destroyed something for no return, which is why it grew the two-tap arm.
 * Selling the stack is what a player actually wants from that corner of the screen — and the arm
 * goes with it, because being paid is not something to be protected from.
 *
 * Checked before removing it rather than assumed: every item type is **sellable or useable**, and
 * the two that cannot be sold — a pack and a potion — are exactly the two that are consumed by
 * using them. So no item can end up with no way out of the bag. `Item.dropable` is now read by
 * nothing on this screen; it stays on the model because the server's `/me/bag/discard` still
 * honours it for a client that asks.
 *
 * @param stack how many of the item the bag holds, which is what the third button says it will
 *   sell. Passed in rather than counted here so the label and the intent cannot disagree about it.
 * @param enabled false while an operation is out. All three at once, because all three act on the
 *   same item and each of them is a round trip on an account — see the `busy` flag in
 *   [InventoryBody] for what a second tap used to buy.
 */
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

/**
 * What a use did, in one line.
 *
 * [ItemUse.PackOpened] no longer appears here: a pack is several cards and it gets
 * [PackRevealScreen] instead, which is a better answer to the same problem this note was solving —
 * a pack yields *bag entries*, so without something saying so the pack simply vanishes and cards
 * appear further up a scrolled list.
 *
 * ### [ItemEffect.NotUseable] is the one that had to stop being null
 *
 * It means the item was **not spent**: either it does nothing, or — the case that actually happens
 * — the bag the server holds does not contain it. On an account that is reachable through an
 * ordinary door: a match against a program is credited by the client, the server discards the bag
 * from that write, and until the transcript has been submitted and replayed the drop exists only on
 * screen. See [MatchSettlement], which is what closes that window, and this line, which is what the
 * player gets if it is ever open again.
 *
 * Returning null here meant the answer arrived, the row disappeared as the server's own bag
 * replaced the client's, and nothing on screen accounted for either.
 */
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

/**
 * `Sells for 52  ·  already owned x2`, with whichever halves apply.
 *
 * The price comes from [Inventory.priceOf] and therefore from the card table, because a card's
 * worth is its rarity — `Item.value` used to answer `cardId * 4` and cannot any more. See
 * `CardValue`.
 */
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
