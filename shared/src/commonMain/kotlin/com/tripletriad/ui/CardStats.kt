package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Card
import com.tripletriad.model.powerLabel

fun cardStatsTestTag(cardId: Int): String = "card-stats-$cardId"

fun cardTypeTestTag(cardId: Int): String = "card-type-$cardId"

@Composable
internal fun CardStatsLine(
    card: Card,
    modifier: Modifier = Modifier,
    showType: Boolean = true,
) {
    Row(
        modifier = modifier.testTag(cardStatsTestTag(card.id)),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOf(card.top, card.right, card.bottom, card.left)
                .joinToString(POWER_SEPARATOR, transform = ::powerLabel),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
        if (showType) CardTypeBadge(card = card)
    }
}

/**
 * The element a card belongs to, when it has one — nothing at all when it does not.
 *
 * @param size how big to draw it. The stats line's own [TypeBadgeSize] is as small as the icon
 *   stays legible; the deck builder asks for more, because there the element is what the player
 *   is choosing on rather than a footnote to the powers.
 */
@Composable
internal fun CardTypeBadge(card: Card, size: Dp = TypeBadgeSize) {
    val type = card.type ?: return
    val icon = LocalCardArt.current?.typeIcon(type) ?: return

    Image(
        bitmap = icon,
        contentDescription = type.name,
        modifier = Modifier.testTag(cardTypeTestTag(card.id)).size(size),
        filterQuality = FilterQuality.None,
    )
}

/**
 * A rarity, as the stars a player counts off the card itself.
 *
 * The one place the character is written. Three screens had a `private const val STAR` of their
 * own, which is three chances to disagree about which glyph a rarity is spelled with.
 */
internal fun starsOf(rarity: Int): String = STAR.repeat(rarity)

internal const val STAR: String = "★"

private const val POWER_SEPARATOR = " "

private val TypeBadgeSize = 11.dp
