package com.tripletriad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.model.Card

/**
 * Whether a thumbnail names its card under the mouse, which `App` provides from the settings file.
 *
 * `static` for the reason [LocalUnownedCards] is. On by default, so a preview or a test that mounts
 * a grid without `App` hovers the way the player would.
 */
val LocalCardTooltips = staticCompositionLocalOf { true }

fun cardTooltipTestTag(cardId: Int): String = "card-tooltip-$cardId"

/**
 * A thumbnail's name, sides and rarity, over it while the mouse is on it.
 *
 * A grid thumbnail draws the powers at a size nobody reads and the name not at all, so finding a
 * card by eye meant clicking it open. On a phone the grid keeps its taps: Material's box answers a
 * **long** press there, and a short one still reaches the cell underneath.
 *
 * Persistent, unlike Material's plain default that fades after a second and a half: the mouse
 * leaving is what closes it, and a player reading four digits and a name is still reading.
 *
 * Never wrapped round the "?" — the tile exists to keep all of this back. The caller decides, as
 * `CardCell` does, because only it knows whether the card is one.
 *
 * @param copies the room's own line about its count, or null where it has none worth saying: the
 *   collection's is what is owned, the deck editor's badge is already the only count that matters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardHoverInfo(card: Card, copies: String?, content: @Composable () -> Unit) {
    if (!LocalCardTooltips.current) {
        content()
        return
    }
    val strings = LocalStrings.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            // The surface the detail panel sits on rather than Material's inverted chip: this is
            // a small copy of that panel, and a black label over a warm grid read as a system hint.
            PlainTooltip(
                modifier = Modifier.testTag(cardTooltipTestTag(card.id)),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = TooltipShadow,
            ) {
                Column {
                    Text(text = strings[card.nameKey], style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = cardFacts(strings, card),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    copies?.let { Text(text = it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        state = rememberTooltipState(isPersistent = true),
    ) {
        content()
    }
}

private val TooltipShadow = SpaceXs
