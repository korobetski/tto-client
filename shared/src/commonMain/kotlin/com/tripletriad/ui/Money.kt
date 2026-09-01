package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.ui.theme.LocalTtoColors

/**
 * A sum of MGP: the game's own coin, then the number.
 *
 * The coin is the one the purse in the top bar shows, so every price in the app reads as the
 * number that purse is about to change — a shelf, a bid, a resale. It was drawn by hand on each
 * of those screens, at three coin sizes and with only one of them grouping its digits.
 *
 * The colours are the caller's, because what a price *means* is not the same everywhere: the shop
 * says out-of-reach on the price itself, the auction board says nothing of the sort, and the
 * collection's Sell button is money coming the other way.
 */
@Composable
internal fun PriceTag(
    price: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    coin: Color = LocalTtoColors.current.currency,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    coinSize: Dp = PriceCoinSize,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = TtoIcons.Chip,
            contentDescription = strings[StringKeys.MGP],
            tint = coin,
            modifier = Modifier.size(coinSize),
        )
        Text(
            text = grouped(price),
            color = color,
            style = style,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * `1000000` as `1 000 000`.
 *
 * A narrow no-break space, not a comma or a dot: those two swap meanings between the app's two
 * languages, and the space is the one grouping both read the same way. Not locale-aware — Kotlin
 * common has no number formatter, and a hand-rolled per-locale one would be a second place for
 * the shop's prices to disagree with the purse's.
 */
internal fun grouped(value: Int): String {
    val digits = value.toString()
    return buildString {
        for ((index, digit) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % GROUP == 0) append(THIN_SPACE)
            append(digit)
        }
    }
}

private val PriceCoinSize = 13.dp

private const val THIN_SPACE = ' '

private const val GROUP = 3
