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

fun packSlotTestTag(index: Int): String = "pack-slot-$index"

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

private fun labelFor(revealed: Int, size: Int): String = when {
    revealed == 0 -> StringKeys.PACK_SEALED
    revealed >= size -> StringKeys.PACK_SPENT
    else -> StringKeys.PACK_BREAK_SEAL
}

/*
 * The pile's geometry. Small on purpose: this is a stack that has been knocked slightly out of
 * true, not a hand of cards fanned out to be read.
 */

private val PackFanX = 15.dp
private val PackFanY = 9.dp

private const val PACK_FAN_DEGREES = 5f

private val PackStackWidth = 240.dp
private val PackStackHeight = 216.dp
private val PackLabelTracking = 2.4.sp

private const val PACK_FLIP_MILLIS = 400
private const val FLIP_DEGREES = 90f
private const val FLIP_SCALE_FROM = 0.9f
private const val FLIP_CAMERA_DISTANCE = 12f

private const val PRIZE_RARITY = 5
private const val PRIZE_GLOW_ALPHA = 0.55f

private val PackGlowCentre = Color(0xFF241D12)
private val PackGlowEdge = Color(0xFF0C0A08)
