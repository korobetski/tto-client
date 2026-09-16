package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardValue
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave

/** The chip that turns taps on the grid from "open this card" into "tick this card". */
const val CARD_SELECT_TEST_TAG: String = "card-select"

const val CARD_BULK_BAR_TEST_TAG: String = "card-bulk-bar"
const val CARD_BULK_COUNT_TEST_TAG: String = "card-bulk-count"
const val CARD_BULK_SELL_TEST_TAG: String = "card-bulk-sell"
const val CARD_BULK_CANCEL_TEST_TAG: String = "card-bulk-cancel"

/** The tick in a cell's corner, drawn only on a cell a bulk sale can take something from. */
fun cardPickTestTag(cardId: Int): String = "card-pick-$cardId"

/**
 * How many copies of [cardId] a bulk sale takes: the spares, less one when every copy is spare.
 *
 * Tighter than [GameSave.spareCopiesOf], which the one-card Sell button uses. A card no deck lists
 * has every copy spare, and there selling the last one is a decision made while reading that card;
 * ticked among twenty others it would be a card gone from the collection by accident. So the bar
 * promises what the mockup promised — one of each is kept — and a card held once is not tickable.
 */
internal fun GameSave.bulkSaleOf(cardId: Int): Int =
    minOf(spareCopiesOf(cardId), copiesOf(cardId) - 1).coerceAtLeast(0)

@Composable
internal fun SelectToggle(picking: Boolean, onToggle: () -> Unit) {
    TtoFilterChip(
        label = LocalStrings.current[StringKeys.SELECT_CARDS],
        tag = CARD_SELECT_TEST_TAG,
        selected = picking,
        onClick = onToggle,
    )
}

/** A cell's tick: a ring when it could be picked, filled when it is. */
@Composable
internal fun PickMark(picked: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .padding(SpaceXs)
            .size(PickMarkSize)
            .semantics { selected = picked }
            .background(
                if (picked) ring else MaterialTheme.colorScheme.surface.copy(alpha = MUTED),
                CircleShape,
            )
            .border(width = PickRingWidth, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                imageVector = TtoIcons.Done,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(PickMarkSize - SpaceXs),
            )
        }
    }
}

/**
 * What the ticks add up to, and the two ways out of it.
 *
 * The count on the button is copies and the count on the left is cards, because they differ as soon
 * as one ticked card has three spares, and each is the number its own side is about: the left says
 * what was chosen, the button says what leaves the collection.
 *
 * @param sale each ticked card with what [bulkSaleOf] takes of it, cards that no longer have
 *   anything to give already dropped.
 */
@Composable
internal fun BulkSellBar(
    sale: List<Pair<Card, Int>>,
    busy: Boolean,
    onCancel: () -> Unit,
    onSell: () -> Unit,
) {
    val strings = LocalStrings.current
    val copies = sale.sumOf { it.second }
    val total = sale.sumOf { (card, count) ->
        CardValue.resaleOf(card.id, mapOf(card.id to card)) * count
    }

    val bar = Modifier
        .testTag(CARD_BULK_BAR_TEST_TAG)
        .fillMaxWidth()
        .padding(top = SpaceSm)
        .rowSurface()
        .padding(horizontal = SpaceMd, vertical = SpaceSm)
    val counts: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier) {
            Text(
                text = strings.format(StringKeys.CARDS_SELECTED, "${sale.size}"),
                modifier = Modifier.testTag(CARD_BULK_COUNT_TEST_TAG),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[StringKeys.SELL_KEEPS_ONE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val actions: @Composable () -> Unit = {
        TextButton(onClick = onCancel, modifier = Modifier.testTag(CARD_BULK_CANCEL_TEST_TAG)) {
            Text(text = strings[StringKeys.CANCEL], maxLines = 1, softWrap = false)
        }
        FilledTonalButton(
            onClick = onSell,
            enabled = copies > 0 && !busy,
            modifier = Modifier.testTag(CARD_BULK_SELL_TEST_TAG),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceSm),
        ) {
            Text(
                text = "${strings[StringKeys.SELL]} $copies",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(modifier = Modifier.width(SpaceXs))
            PriceTag(
                price = total,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.labelLarge,
                coinSize = IconSm,
            )
        }
    }

    // One line where there is room for one. A phone's line left "1 carte(s) séle…" beside the two
    // buttons, and the sentence is the half of the bar that says what the button will do.
    if (LocalWideLayout.current) {
        Row(
            modifier = bar,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            counts(Modifier.weight(1f))
            actions()
        }
    } else {
        Column(modifier = bar, verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
            counts(Modifier)
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                actions()
            }
        }
    }
}

private val PickMarkSize = 18.dp

private val PickRingWidth = 1.5.dp
