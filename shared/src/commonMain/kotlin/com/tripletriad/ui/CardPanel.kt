package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.powerLabel

/**
 * A card read rather than picked out: the sprite at the size it was drawn, its name, its four
 * powers and its rarity, whatever prose the game has about it, and whatever the room lets you do
 * with it.
 *
 * ### Why this is one composable and not one per screen
 *
 * Two screens stop to let a player *decide* about a single card — the collection, before selling a
 * duplicate, and the auction's desk, before bidding four figures on one — and they had grown two
 * arrangements of it. The desk's was the poorer of the two by exactly the facts it left out: a
 * card there was a picture and a name, with no powers and no rarity in writing anywhere, on the
 * one screen in the game where a player is putting a number on what a card is worth.
 *
 * The sprite is at scale 1 in both. Every other surface in the app can afford to shrink its
 * picture — see [CardLine], which is what a *row* of cards uses — but here the numbers around it
 * are meaningless without it: a five at every edge and a five in the corner are the same row in a
 * list and different cards on a desk.
 *
 * ### `fillMaxHeight` on the column is what makes the rest of this work
 *
 * Without it the column is as tall as its own contents, so `weight(1f)` on the description has no
 * remainder to take, the column overflows the panel, and what falls off the bottom is [actions] —
 * the one control the panel exists to offer. The description was cut mid-sentence with nothing to
 * say it could be scrolled. The caller therefore owes this a **bounded height**: a pane that fills
 * its side of the screen, or [CardPanelHeight].
 *
 * @param tag what this panel is called in the tree it is drawn in. Named by the caller rather than
 *   fixed here, because the collection's detail pane and the auction's lectern are two different
 *   landmarks in two different screens' tests.
 * @param actions the room's own verbs, under the prose: Sell in the collection, nothing at all at
 *   the desk, where the money is committed further down the lectern.
 */
@Composable
internal fun CardPanel(
    card: Card,
    tag: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.testTag(tag).fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        CardFace(card = card, scale = PANEL_SCALE)

        Column(
            modifier = Modifier.fillMaxHeight().weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = strings[card.nameKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cardFacts(strings, card),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The `ff8_` bundle has names for all 110 cards and descriptions for none, so this
            // really is absent rather than merely untranslated — leaving the key on screen would
            // read as a defect in the port.
            val description = "${card.nameKey}_DESC"
            if (strings.has(description)) {
                Text(
                    // Quoted speech, with emphasis and the odd line break — the prose most likely
                    // to carry markup. See [markup].
                    text = markup(strings[description]),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    // `bodySmall` and not `labelSmall`: this is the only prose in the game and it
                    // was being drawn at the size the app uses for a stack count.
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            actions()
        }
    }
}

/**
 * What a card is, in one line of words: its four powers, then its rarity.
 *
 * Written out with their labels rather than drawn as [CardStatsLine] does it, because this line
 * sits beside the sprite that already shows both — the point of saying it twice is that the second
 * saying is *readable*, and that it names what it is showing.
 */
internal fun cardFacts(strings: Strings, card: Card): String = listOf(
    "${strings[StringKeys.SIDES]} " + listOf(card.top, card.right, card.bottom, card.left)
        .joinToString(" ", transform = ::powerLabel),
    "${strings[StringKeys.RARITY]} ${starsOf(card.rarity)}",
).joinToString(DOT_SEPARATOR)

/**
 * A panel's height where nothing else decides it.
 *
 * The collection's wide layout gives it a whole pane and this number is not used there; the sheet
 * over a phone and the auction's lectern both need one, and they use the same, because a card is
 * the same object in both.
 */
internal val CardPanelHeight = 196.dp

private const val PANEL_SCALE = 1f
