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
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
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
        if (showType) card.type?.let { CardTypeBadge(it, card.id) }
    }
}

@Composable
private fun CardTypeBadge(type: CardType, cardId: Int) {
    val icon = LocalCardArt.current?.typeIcon(type) ?: return

    Image(
        bitmap = icon,
        contentDescription = type.name,
        modifier = Modifier.testTag(cardTypeTestTag(cardId)).size(TypeBadgeSize),
        filterQuality = FilterQuality.None,
    )
}

private const val POWER_SEPARATOR = " "

private val TypeBadgeSize = 11.dp
