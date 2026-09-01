package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.model.Card

/**
 * A card named on one line: the tile it is recognised by, what it is called, and the four powers
 * with its element.
 *
 * ### Why the picture here is the tile and not a shrunk `CardFace`
 *
 * A row on the auction board used to draw the full card sprite at 0.42 — a 104x128 face with
 * digits scaled to four pixels, which is a picture of numbers rather than numbers. The tile is
 * art authored for this size, three decoded sheets for the whole catalogue rather than one image
 * per card (see `UiArt`), and the powers it is too small to carry are printed beside it by
 * [CardStatsLine] at a size that can be read. Illustration, digits and element, each drawn by the
 * one composable the rest of the app already draws it with.
 *
 * The full face is still what the auction's desk shows, where the player is deciding what a card
 * is worth and the sprite is the subject rather than the label — see `AuctionDesk`.
 *
 * @param card null when a lot names a card this client's catalogue does not have. The row is
 *   still worth drawing: its price and its countdown are readable, and a hole where the picture
 *   goes says more than a row that vanishes.
 * @param name what to call it. The caller's, not `card.nameKey` looked up here, because the one
 *   caller that can be handed a null card is the one that knows what to say instead — an id.
 * @param count the tile's badge, or null for none. What it counts is the caller's business, as
 *   it is everywhere else [CardTile] is drawn.
 * @param detail whatever this row is really about, under the name: a price and a countdown on the
 *   board, a rarity at the consignment desk.
 */
@Composable
internal fun CardLine(
    card: Card?,
    name: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    countTag: String? = null,
    detail: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        if (card == null) {
            EmptyCardSlot()
        } else {
            CardTile(
                card = card,
                count = count,
                countTag = countTag,
                modifier = Modifier.size(FramedThumbSide),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The element is already on the tile, in the corner every other screen puts it in, so
            // the line beside it is the powers alone rather than the same icon twice.
            card?.let { CardStatsLine(card = it, showType = false) }
            detail()
        }
    }
}
