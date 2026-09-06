package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.Item
import com.tripletriad.ui.theme.LocalTtoColors

fun inventoryRowTestTag(item: Item): String = "inventory-row-${itemSlug(item)}"

fun inventoryUseTestTag(item: Item): String = "inventory-use-${itemSlug(item)}"

fun inventoryMenuTestTag(item: Item): String = "inventory-menu-${itemSlug(item)}"

fun inventorySellTestTag(item: Item): String = "inventory-sell-${itemSlug(item)}"

fun inventorySellAllTestTag(item: Item): String = "inventory-sell-all-${itemSlug(item)}"

/** What holding the item is worth, on the row. See [itemEffect]. */
fun inventoryEffectTestTag(item: Item): String = "inventory-effect-${itemSlug(item)}"

/** The three things a bag row does, so the row itself is not eight parameters of lambda. */
internal data class BagActions(
    val onUse: () -> Unit,
    val onSell: () -> Unit,
    val onSellAll: () -> Unit,
)

/**
 * One item, with its own actions.
 *
 * ### Why the actions are on the row
 *
 * They used to be a bar under the list, acting on whatever radio row was selected — so using a
 * potion was select, look down, press, and the two rows that could not be sold were discovered by
 * pressing a button that had already greyed for a different reason. Here every row says what it
 * offers, and a row that offers nothing but Use says only that.
 *
 * ### Why "sell all" is not a second button
 *
 * `Sell 12` and `Sell all 36` side by side were twins: the same verb, two numbers, and the
 * expensive one no harder to hit than the cheap one. Sell moves to the menu and Sell all moves
 * behind a second tap that names the stack and the payout — see [BagMenu].
 *
 * [locked] is the one-operation-at-a-time lock, which is now a state of the rows rather than of a
 * detached bar: every row's controls go quiet while a request is out, and [acting] is the one that
 * asked for it.
 */
@Composable
internal fun BagItemRow(
    item: Item,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    note: String?,
    locked: Boolean,
    acting: Boolean,
    actions: BagActions,
) {
    val strings = LocalStrings.current
    val price = Inventory.priceOf(item, cards)

    Row(
        modifier = Modifier
            .testTag(inventoryRowTestTag(item))
            .fillMaxWidth()
            // The row it is waiting on, marked the way a selected row used to be: the lock has to
            // be visible somewhere, and the row that asked is where the player is looking.
            .rowSurface(selected = acting)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        // A card item shows the card; everything else shows the icon `Item.iconId` names — the
        // booster's own tribe pack, the rarity plate, the two boosts.
        val card = itemCard(item, cards)
        if (card != null) {
            CardThumb(card = card)
        } else {
            ItemGlyph(item = item, description = itemName(strings, item, cards))
        }

        BagItemFacts(
            item = item,
            cards = cards,
            owned = owned,
            price = price,
            note = note,
            modifier = Modifier.weight(1f),
        )

        // `×1` is drawn too. A stack column that appears only above one is a column that shifts
        // the name every time a purchase lands.
        Text(
            text = "×${item.stack}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )

        RowButton(
            label = strings[StringKeys.USE],
            tag = inventoryUseTestTag(item),
            enabled = item.useable && !locked,
            color = MaterialTheme.colorScheme.primary,
            onClick = actions.onUse,
        )

        BagMenu(
            item = item,
            price = price,
            enabled = !locked,
            onSell = actions.onSell,
            onSellAll = actions.onSellAll,
        )
    }
}

@Composable
private fun BagItemFacts(
    item: Item,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    price: Int,
    note: String?,
    modifier: Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier = modifier) {
        Text(
            text = itemName(strings, item, cards),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        itemEffect(strings, item, cards, owned)?.let { effect ->
            Text(
                text = effect,
                modifier = Modifier.testTag(inventoryEffectTestTag(item)),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The resale value and the "already owned ×2" line, which is everything about the item
        // that is about *this* copy rather than about the kind.
        itemFacts(strings, price, ownedNote(strings, item, owned))?.let { facts ->
            Text(
                text = facts,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        note?.let {
            Text(
                text = it,
                modifier = Modifier.testTag(INVENTORY_NOTE_TEST_TAG),
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Sell, and sell all under a second tap.
 *
 * The confirmation is the menu item's own label rather than a dialog, which is the shape
 * `PvpScreen`'s heavy join already uses here: it names the stack and the payout in the place the
 * finger is already pointing, and dismissing the menu is enough to change your mind.
 *
 * A refused entry is left in the menu rather than dropped from it, for the reason `DeckMenu`
 * gives: the menu is the same two lines on every row, and the greyed one says why nothing happens.
 */
@Composable
private fun BagMenu(
    item: Item,
    price: Int,
    enabled: Boolean,
    onSell: () -> Unit,
    onSellAll: () -> Unit,
) {
    val strings = LocalStrings.current
    var open by remember(item) { mutableStateOf(false) }
    var armed by remember(item) { mutableStateOf(false) }
    val close = {
        open = false
        armed = false
    }

    Box {
        StripButton(
            icon = TtoIcons.More,
            description = strings[StringKeys.ITEM_ACTIONS],
            tag = inventoryMenuTestTag(item),
            enabled = enabled,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = close) {
            DropdownMenuItem(
                text = { Text("${strings[StringKeys.SELL]} $price") },
                enabled = price > 0,
                onClick = {
                    close()
                    onSell()
                },
                modifier = Modifier.testTag(inventorySellTestTag(item)),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (armed) {
                            strings.format(
                                StringKeys.SELL_ALL_CONFIRM,
                                "${item.stack}",
                                "${price * item.stack}",
                            )
                        } else {
                            "${strings[StringKeys.SELL_ALL]} ${price * item.stack}"
                        },
                    )
                },
                // At a stack of one it would be the line above it, and two entries that do the
                // same thing invite the player to wonder which one they got wrong.
                enabled = price > 0 && item.stack > 1,
                onClick = {
                    if (armed) {
                        close()
                        onSellAll()
                    } else {
                        armed = true
                    }
                },
                modifier = Modifier.testTag(inventorySellAllTestTag(item)),
            )
        }
    }
}

/**
 * The line about *this copy*: what the shop pays for it, and how many the collection already has.
 *
 * Null rather than an empty string when neither applies, so a pouch does not carry a blank line
 * where a card carries two facts.
 */
private fun itemFacts(strings: Strings, price: Int, owned: String?): String? = buildList {
    if (price > 0) add("${strings[StringKeys.SELL]} $price")
    owned?.let(::add)
}.joinToString(DOT_SEPARATOR).ifEmpty { null }
