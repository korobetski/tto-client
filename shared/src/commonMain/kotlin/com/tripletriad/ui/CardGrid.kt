package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Card

/**
 * The grid every screen that shows a lot of cards at once lays them out in.
 *
 * ### The columns are the size of the art, and the leftovers go to the margins
 *
 * `FixedSize`, not `Adaptive`: adaptive divides the width among as many columns as fit and hands
 * each of them **all** of its share, so a cell was as wide as the leftover space allowed and a
 * 40 dp picture sat in the middle of it. A frame drawn on that traces a box the picture does not
 * fill. Fixed columns are exactly the art's size and the spare width goes to the margins, split
 * between the two edges rather than left in one strip down the right-hand side.
 *
 * No spacing between cells either: `card_frame.png` carries its own margin, so abutting frames
 * already leave 4 dp between two pictures — the gap the original shows. Adding more would double
 * it.
 *
 * ### Why the cell is the caller's
 *
 * What a cell counts is not the same question in two rooms — copies owned in the collection,
 * copies *spare* at the consignment desk — and neither is what tapping one does. The arrangement
 * above is what they share, and it is the part that was written twice.
 */
@Composable
internal fun CardGrid(
    cards: List<Card>,
    tag: String,
    modifier: Modifier = Modifier,
    cell: @Composable (Card) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(FramedThumbSide),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        modifier = modifier.testTag(tag),
    ) {
        items(cards, key = { it.id }) { card -> cell(card) }
    }
}

/**
 * One card in that grid: the app's own tile, sized to the frame, and tappable as a choice.
 *
 * ### The cell is the frame, and the frame is bigger than the picture
 *
 * [FramedThumbSide] and nothing more — no padding, no surface, no room for a border to sit
 * *outside* the art. Both sizes are authored, and the 2 dp between them is the margin
 * `card_frame.png` leaves around the picture, not a layout choice. **Neither image is ever
 * resized**; the cell was made to fit the art rather than the art made to fit the cell.
 *
 * ### Every card is tappable, owned or not
 *
 * The original made unowned thumbs untouchable, which meant the description of a card you were
 * hunting for was the one thing you could not read. Not owning one is said by dimming it.
 *
 * @param copies how many the player has of it *for this screen's purposes*: the collection counts
 *   what is owned, the consignment desk what is spare. Zero dims the tile; the badge appears past
 *   one, because `×1` on two hundred cells is noise.
 */
@Composable
internal fun CardCell(
    card: Card,
    copies: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    copiesTag: String? = null,
    onClick: () -> Unit,
) {
    CardTile(
        card = card,
        selected = selected,
        dim = copies < 1,
        // A badge and not a second cell. The grid answers "what is there, and what do I have",
        // and two identical thumbnails answer it worse — the second one reads as a different card
        // until you look twice.
        count = copies.takeIf { it > 1 },
        countTag = copiesTag,
        modifier = modifier
            .size(FramedThumbSide)
            // Tapping a cell opens the card beside the grid, and tapping it again closes it — so
            // it is a toggle, and the selected state is what the detail pane is showing.
            .ttoClickable(selected = selected, onClick = onClick),
    )
}
