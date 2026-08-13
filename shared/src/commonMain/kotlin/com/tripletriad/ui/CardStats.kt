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

/** `card-stats-<id>` — the four edges and the type, wherever a card is being chosen. */
fun cardStatsTestTag(cardId: Int): String = "card-stats-$cardId"

/** `card-type-<id>` — the element badge, absent on a card that has no type. */
fun cardTypeTestTag(cardId: Int): String = "card-type-$cardId"

/**
 * A card's four edges and its type, in one line.
 *
 * ### Why it exists
 *
 * The deck editor drew **thumbnails and nothing else**. A thumbnail is 44dp of artwork with the
 * powers rendered into it at a size nobody can read, so building a deck meant recognising cards
 * from memory or tapping each one to find out what it was. The original had the same problem and
 * answered it on a screen 1024 wide; on a phone it is the difference between choosing a deck and
 * guessing at one.
 *
 * The type matters as much as the powers and is harder to recall. Under Elemental a card played on
 * a matching tile gains one on every side and loses one on every other, so a deck's spread of
 * elements is a real decision — and the type is drawn nowhere on the thumbnail at all.
 *
 * ### The order is the game's
 *
 * Top, right, bottom, left — `power[0..3]` as `CardDigits.display()` states it and as
 * `DecksScreen.as:296` prints it. Not clockwise-from-anywhere: it is the order the data is in, the
 * order the card face draws, and the order the rest of this port reads.
 *
 * `10` is written `A`, which is how every Triple Triad has ever written it — see [powerLabel].
 */
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

/**
 * The element, as its own icon.
 *
 * The same texture the card face carries, at the size a list can hold. An icon rather than the
 * type's name because there are twelve of them and eight are elements a player already recognises
 * by colour — and because a name would need translating into four bundles to say what a picture
 * says.
 *
 * Falls back to nothing while the atlas is loading, rather than to a placeholder box: a gap is
 * momentary and a wrong badge is not.
 */
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

/** A thin space either side of a digit, so `A 5 3 2` reads as four numbers and not as one. */
private const val POWER_SEPARATOR = " "

/** Cap height of the label beside it, so the row does not grow to fit the badge. */
private val TypeBadgeSize = 11.dp
