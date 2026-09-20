package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings

/** The quantity stepper on a picked offer. Absent while only one of it can be paid for. */
const val SHOP_FEWER_TEST_TAG: String = "shop-fewer"
const val SHOP_MORE_TEST_TAG: String = "shop-more"
const val SHOP_MOST_TEST_TAG: String = "shop-most"
const val SHOP_COUNT_TEST_TAG: String = "shop-count"

/**
 * How many of a picked offer to buy: fewer, more, or as many as the purse allows.
 *
 * The same stepper the collection sells duplicates with (`CardListBody`), for the same reason —
 * ten packs were ten taps on a button that closed its own sheet on a phone between each one. The
 * "most" shortcut is what makes it worth having at all: stepping to twenty is still twenty taps.
 *
 * The ceiling is the purse's, not a preference — see `ShopOffer.affordableCount`, which is also
 * what `:core` charges against, so the button can never offer a count the purchase would refuse.
 */
@Composable
internal fun BuyQuantity(count: Int, most: Int, onCount: (Int) -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = strings[StringKeys.QUANTITY],
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StepButton(
            glyph = "−",
            tag = SHOP_FEWER_TEST_TAG,
            description = strings[StringKeys.BUY_FEWER],
            enabled = count > 1,
        ) { onCount(count - 1) }
        Text(
            text = "$count",
            modifier = Modifier.testTag(SHOP_COUNT_TEST_TAG),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
        StepButton(
            glyph = "+",
            tag = SHOP_MORE_TEST_TAG,
            description = strings[StringKeys.BUY_MORE],
            enabled = count < most,
        ) { onCount(count + 1) }
        StepButton(
            glyph = "$most",
            tag = SHOP_MOST_TEST_TAG,
            description = strings.format(StringKeys.BUY_MOST, "$most"),
            enabled = count < most,
        ) { onCount(most) }
    }
}

/** "Buy ×10 · 12 000", or the plain "Buy · 1 200" for a single one. */
internal fun buyLabel(strings: Strings, offer: ShopOffer, count: Int): String {
    val price = grouped(offer.priceFor(count).toInt())
    val many = if (count > 1) " ×$count" else ""
    return "${strings[StringKeys.BUY]}$many$DOT_SEPARATOR$price"
}
