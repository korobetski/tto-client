package com.tripletriad.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.ui.theme.LocalTtoColors

const val PACK_REVEAL_TEST_TAG: String = "pack-reveal"
const val PACK_REVEAL_LABEL_TEST_TAG: String = "pack-reveal-label"
const val PACK_REVEAL_ACTION_TEST_TAG: String = "pack-reveal-action"

/** `pack-slot-0` — one per card in the pack, in the order the pack dealt them. */
fun packSlotTestTag(index: Int): String = "pack-slot-$index"

/**
 * Opening a booster, one card at a time.
 *
 * ### Why this screen exists at all
 *
 * The AS3 pack was a single card and a line of text. You paid, `useBtnHandler` put one `CardItem`
 * in the bag, and a label said what it was — so the most expensive purchase in the game was the
 * least eventful thing in it. `BoosterItem.open` deals several cards now, and several cards
 * arriving at once as a note above a scrolled list is somehow worse than one.
 *
 * So the pack is opened here, face down, and turned over by the player. That is the whole design:
 * the cards are already decided — the draw happened in `:core` before this screen existed, and
 * nothing here can change what is under them — and the reveal is the player being allowed to find
 * out in their own time. Every game that sells packs does this, and it is not decoration: the
 * interesting part of a pack is the moment before you know.
 *
 * ### The last card is the one that matters
 *
 * `BoosterItem.open` returns its cards worst-prospect first and the **guaranteed** slot last, and
 * this screen flips them in exactly that order. So the run of ordinary cards comes first and the
 * question — did the guaranteed slot give a five-star? — is answered last. Sorting or shuffling
 * here would throw away the only structure the list has.
 *
 * A five-star turns over with a gold edge and a glow. It is the one visual distinction on the
 * screen, and it is spent on the one thing worth distinguishing.
 *
 * ### Tap anywhere
 *
 * The card grid and the button do the same thing, which is what the design asks for and what a
 * player does anyway: the button is where the label lives, and the grid is where their thumb
 * already is. When the last card is over, the same control becomes the way out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackRevealScreen(
    cardIds: List<Int>,
    cards: Map<Int, Card>,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    var revealed by remember(cardIds) { mutableStateOf(0) }
    val isSpent = revealed >= cardIds.size
    val advance = { if (isSpent) onDone() else revealed += 1 }

    Column(
        modifier = Modifier
            .testTag(PACK_REVEAL_TEST_TAG)
            .fillMaxSize()
            .background(
                // The design's `radial-gradient(70% 40% at 50% 45%, …)`: a pool of warm light
                // under the cards, so the grid reads as lit rather than as a list on a page.
                Brush.radialGradient(
                    colors = listOf(PackGlowCentre, PackGlowEdge),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            )
            .clickable(onClick = advance)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings[labelFor(revealed, cardIds.size)],
            modifier = Modifier.testTag(PACK_REVEAL_LABEL_TEST_TAG),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = PackLabelTracking,
            textAlign = TextAlign.Center,
        )

        FlowRow(
            modifier = Modifier.padding(vertical = 22.dp).widthIn(max = PackGridMaxWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((index, id) in cardIds.withIndex()) {
                PackSlot(
                    index = index,
                    card = cards[id],
                    isOpen = index < revealed,
                )
            }
        }

        RevealButton(
            label = strings[if (isSpent) StringKeys.PACK_TO_COLLECTION else StringKeys.PACK_REVEAL],
            isSpent = isSpent,
            onClick = advance,
        )
    }
}

/**
 * One slot: face down, or turned over.
 *
 * The turn is a Y-axis rotation from 90° to 0 — the design's `flipin .4s ease-out` — plus a scale
 * from .9, so the card arrives rather than appears. Driven by [animateFloatAsState] keyed on
 * [isOpen] and not by an animation that restarts: a recomposition for an unrelated reason must not
 * flip a card the player has already seen.
 *
 * A face-down slot draws [CardFace]'s own back, which is the same back the board uses. Inventing a
 * second one for this screen would put two card backs in a game that has one.
 */
@Composable
private fun PackSlot(index: Int, card: Card?, isOpen: Boolean) {
    val colors = LocalTtoColors.current
    val turn by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = PACK_FLIP_MILLIS),
        label = "pack-slot-$index",
    )
    val isPrize = isOpen && card != null && card.rarity >= PRIZE_RARITY

    Box(
        modifier = Modifier
            .testTag(packSlotTestTag(index))
            .graphicsLayer {
                rotationY = (1f - turn) * FLIP_DEGREES
                cameraDistance = FLIP_CAMERA_DISTANCE
            }
            .scale(FLIP_SCALE_FROM + (1f - FLIP_SCALE_FROM) * turn)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isPrize) colors.transient.copy(alpha = PRIZE_GLOW_ALPHA) else Color.Transparent,
            )
            .padding(if (isPrize) 2.dp else 0.dp),
    ) {
        if (card == null) {
            // A pack naming a card the catalogue does not hold. Unreachable through the shipped
            // pools — `ShopBundleTest` resolves every one of them — and drawn as a back rather than
            // as a gap, because a hole in the grid would read as a card that failed to turn.
            Box(
                modifier = Modifier
                    .width(PackSlotWidth)
                    .height(PackSlotHeight)
                    .background(colors.backdrop),
            )
        } else {
            CardFace(card = card, scale = PACK_SLOT_SCALE, showBack = !isOpen)
        }
    }
}

/** The one control: `Reveal` while there is a card left, then the way out. */
@Composable
private fun RevealButton(label: String, isSpent: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(PACK_REVEAL_ACTION_TEST_TAG)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSpent) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = if (isSpent) {
                MaterialTheme.colorScheme.onTertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * What the line above the grid says, which is the whole of the screen's narration.
 *
 * Three states and not a counter. "3 of 5" is a progress bar and tells the player something they
 * can see by looking at the grid; the design's wording tells them what to *do*, which is the only
 * thing they do not already know on their first pack.
 */
private fun labelFor(revealed: Int, size: Int): String = when {
    revealed == 0 -> StringKeys.PACK_SEALED
    revealed >= size -> StringKeys.PACK_SPENT
    else -> StringKeys.PACK_BREAK_SEAL
}

/** Five 84dp slots wrap to 3 + 2 on a phone, which is the design's grid. */
private val PackGridMaxWidth = 276.dp
private val PackSlotWidth = 84.dp
private val PackSlotHeight = 112.dp
private val PackLabelTracking = 2.4.sp

/** `CardFace` is drawn at its sprite size; this brings a card down to the grid's 84dp. */
private const val PACK_SLOT_SCALE = 0.7f

/** `flipin .4s ease-out`. */
private const val PACK_FLIP_MILLIS = 400
private const val FLIP_DEGREES = 90f
private const val FLIP_SCALE_FROM = 0.9f
private const val FLIP_CAMERA_DISTANCE = 12f

/** A five-star, and nothing else, is worth a gold edge. */
private const val PRIZE_RARITY = 5
private const val PRIZE_GLOW_ALPHA = 0.55f

private val PackGlowCentre = Color(0xFF241D12)
private val PackGlowEdge = Color(0xFF0C0A08)
