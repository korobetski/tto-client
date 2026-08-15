package com.tripletriad.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
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
            // **The one deliberate `clickable` left in the app.** Everything else goes through
            // `ttoClickable`, which draws a focus ring — and a focus ring on a tap target the size
            // of the whole screen is a blue border around the game. The button below it is the
            // reachable, announced control; this is the shortcut for a thumb anywhere on the glass.
            .clickable(onClick = advance)
            .padding(SpaceXl),
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

        PackStack(cardIds = cardIds, cards = cards, revealed = revealed)

        RevealButton(
            label = strings[if (isSpent) StringKeys.PACK_TO_COLLECTION else StringKeys.PACK_REVEAL],
            isSpent = isSpent,
            onClick = advance,
        )
    }
}

/**
 * The pack as a **pile**, not a grid.
 *
 * ### What was wrong with five slots in a row
 *
 * Two things. The cards were drawn at **70%** so that five of them plus their
 * gaps would fit a phone's width, which meant the most expensive purchase in the game was also the
 * only place the artwork was shown *smaller* than everywhere else. And five evenly spaced slots is
 * a grid: it says these five cards are a set to be compared, when what the screen is actually about
 * is turning them over one at a time.
 *
 * A pile says the second thing. It also stops the width dictating the size, because the cards
 * overlap — which is what buys the resolution back: they are drawn at **1:1** now, and the art is
 * authored at exactly [CardSpriteWidth] x [CardSpriteHeight], so this is the native pixel size
 * rather than any resampling of it.
 *
 * ### The fan, and the order they are drawn in
 *
 * Each card is offset and rotated by its distance from the middle of the pile, so all five peek out
 * and the stacking is visible rather than implied.
 *
 * Z-order is what makes it readable while it is being dealt: the **unrevealed** cards are drawn
 * first and back-to-front, so the next one to turn is the topmost of the face-down pile; then the
 * revealed ones, in the order they were turned, so the card that was just flipped is on top of
 * everything. The guaranteed slot is last in both — see the screen's own note — and therefore ends
 * up on top of the finished pile, which is where the one card worth looking at belongs.
 *
 * ### Why the loop is keyed, and what happened without it
 *
 * [key] is load-bearing here, not tidiness. This list is **reordered every tap** — a card leaves
 * the face-down group for the revealed one — and an unkeyed loop gives Compose no identity but
 * position, so the slot's remembered flip state stays with the *position* while the card moves out
 * from under it. From the third card on, the two ends of the pile trade states, and the visible
 * result is precisely the wrong animation: the card the player just turned appears already face-up
 * with no flip at all, while an older card underneath it turns over a second time.
 *
 * Keying on the slot index — which is the card's identity here, since [cardIds] is fixed for the
 * life of the screen — pins each `PackSlot`'s animation to its own card, so the one that animates
 * is the one on top.
 */
@Composable
private fun PackStack(cardIds: List<Int>, cards: Map<Int, Card>, revealed: Int) {
    val middle = (cardIds.size - 1) / 2f

    // Unrevealed first, furthest-out to nearest, then revealed in the order they were turned.
    val order = cardIds.indices.sortedWith(
        compareBy<Int> { if (it < revealed) 1 else 0 }.thenBy { if (it < revealed) it else -it },
    )

    Box(
        modifier = Modifier.padding(vertical = SpaceXl).size(PackStackWidth, PackStackHeight),
        contentAlignment = Alignment.Center,
    ) {
        for (index in order) {
            key(index) {
                val step = index - middle
                PackSlot(
                    index = index,
                    card = cards[cardIds[index]],
                    isOpen = index < revealed,
                    modifier = Modifier
                        .offset(x = PackFanX * step, y = PackFanY * step)
                        .rotate(PACK_FAN_DEGREES * step),
                )
            }
        }
    }
}

/**
 * One card in the pile: face down, or turned over.
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
private fun PackSlot(index: Int, card: Card?, isOpen: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalTtoColors.current
    val turn by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = PACK_FLIP_MILLIS),
        label = "pack-slot-$index",
    )
    val isPrize = isOpen && card != null && card.rarity >= PRIZE_RARITY

    Box(
        modifier = modifier
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
                    .width(CardSpriteWidth)
                    .height(CardSpriteHeight)
                    .background(colors.backdrop),
            )
        } else {
            CardFace(card = card, showBack = !isOpen)
        }
    }
}

/**
 * The one control: `Reveal` while there is a card left, then the way out.
 *
 * ### Two Material buttons, where there was a `Box` pretending to be one
 *
 * It was a clipped, filled, clickable box with hand-picked padding — which is a button with its
 * touch target, its ripple, its focus and its disabled state all left off. The two states it swings
 * between are exactly Material's two weights, and saying so gets all four back:
 *
 * - **While cards remain** the button is quiet, because the *cards* are the screen. An
 *   [OutlinedButton] is present without competing with what it is asking you to look at.
 * - **Once the pack is spent** it is the only thing left to do, so it fills — and in `tertiary`
 *   rather than `primary`, keeping the affirmative reading this app gives that role everywhere
 *   else. Amber here would make leaving look like the point of opening a pack.
 */
@Composable
private fun RevealButton(label: String, isSpent: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val text: @Composable RowScope.() -> Unit = {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
    val shell = Modifier.testTag(PACK_REVEAL_ACTION_TEST_TAG)

    if (isSpent) {
        Button(
            onClick = onClick,
            modifier = shell,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            content = text,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = shell,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = text,
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

/*
 * The pile's geometry. Small on purpose: this is a stack that has been knocked slightly out of
 * true, not a hand of cards fanned out to be read.
 */

/** How far each card sits from the one before it. */
private val PackFanX = 15.dp
private val PackFanY = 9.dp

/** And how far it is turned. Five cards span twice this either side of the middle. */
private const val PACK_FAN_DEGREES = 5f

/**
 * The box the pile is centred in.
 *
 * The fan itself is 104 + 4x15 across and 128 + 4x9 down; the rest is what the rotation throws
 * outside that, which has to be reserved or the outermost cards clip against the edge.
 */
private val PackStackWidth = 240.dp
private val PackStackHeight = 216.dp
private val PackLabelTracking = 2.4.sp

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
