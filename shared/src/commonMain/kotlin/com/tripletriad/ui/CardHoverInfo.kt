package com.tripletriad.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
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
    val gap = with(LocalDensity.current) { TooltipGap.roundToPx() }

    TooltipBox(
        positionProvider = remember(gap) { BesideAnchor(gap) },
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

/**
 * Beside the thumbnail, on its end side, and never above its top edge.
 *
 * Material's `Above` put the tooltip of a first-row card over whatever sits over the grid — the
 * Type menu, in the collection — so a player reaching for a filter found it covered by the name of
 * the card the mouse had just crossed. Beside, it covers only neighbouring cells, which the mouse
 * leaving closes it over anyway. It flips to the start side where the end side has no room, and is
 * pushed up only as far as the window's bottom edge demands.
 */
internal class BesideAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val right = anchorBounds.right + gap
        val left = anchorBounds.left - gap - popupContentSize.width
        val fitsRight = right + popupContentSize.width <= windowSize.width
        val fitsLeft = left >= 0
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> if (fitsRight || !fitsLeft) right else left
            LayoutDirection.Rtl -> if (fitsLeft || !fitsRight) left else right
        }
        val lowest = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = anchorBounds.top.coerceIn(0, lowest),
        )
    }
}

private val TooltipShadow = SpaceXs

private val TooltipGap = SpaceXs
