package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val COIN_FLIP_TEST_TAG: String = "coin-flip"

fun coinFlipTestTag(roll: Int): String = "coin-flip-$roll"

internal const val COIN_FLIP_TOTAL_MILLIS: Int = 1_000

@Composable
internal fun CoinFlipCards(flip: CoinFlip, onFinished: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(COIN_FLIP_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        flip.rolls.take(FAN.size).forEachIndexed { roll, color ->
            TossedCard(roll = roll, color = color)
        }
    }

    // One timer for the whole flip rather than a completion callback on the last card, so
    // the queue's timing does not depend on which of three parallel animations happens to
    // settle last.
    LaunchedEffect(flip) {
        delay(COIN_FLIP_TOTAL_MILLIS.toLong())
        onFinished()
    }
}

@Composable
private fun TossedCard(roll: Int, color: CardColor) {
    val fan = FAN[roll]
    val progress = remember(roll, color) { Animatable(0f) }
    val exit = remember(roll, color) { Animatable(0f) }
    val alpha = remember(roll, color) { Animatable(0f) }

    LaunchedEffect(roll, color) {
        delay(fan.delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(ENTER_MILLIS, easing = LinearEasing)) }
        progress.animateTo(1f, tween(ENTER_MILLIS, easing = EaseOut))

        // Waits out the cards behind it as well as its own hold, so the three leave
        // together however they were staggered coming in.
        delay((LAST_DELAY_MILLIS - fan.delayMillis + HOLD_MILLIS).toLong())

        val leaving = tween<Float>(EXIT_MILLIS, easing = LinearEasing)
        launch { alpha.animateTo(0f, leaving) }
        exit.animateTo(1f, leaving)
    }

    CardBack(
        color = color,
        scale = CARD_SCALE,
        modifier = Modifier
            .testTag(coinFlipTestTag(roll))
            // A card back says nothing to a screen reader, and this one is the only place
            // the player is told who won the toss.
            .semantics { contentDescription = "${COIN_FLIP_TEST_TAG}-${color.name}" }
            .graphicsLayer {
                val from = fan.from * size.minDimension
                val to = fan.to * size.minDimension
                val rest = fan.rest * size.minDimension
                translationX = lerp(from.x, rest.x, progress.value) +
                    (to.x - rest.x) * exit.value
                translationY = lerp(from.y, rest.y, progress.value) +
                    (to.y - rest.y) * exit.value
                rotationZ = fan.degrees * progress.value
                // 1.2 in the original, settling to 1. The exit does not scale at all.
                val entering = lerp(ENTER_SCALE, 1f, progress.value)
                scaleX = entering
                scaleY = entering
                this.alpha = alpha.value
            },
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction

private operator fun Offset.times(scalar: Float): Offset = Offset(x * scalar, y * scalar)

private data class FanCard(
    val from: Offset,
    val rest: Offset,
    val to: Offset,
    val degrees: Float,
    val delayMillis: Int,
)

private val FAN = listOf(
    FanCard(
        from = Offset(OFF_SCREEN, -OFF_SCREEN),
        rest = Offset(-0.9f, -0.7f),
        to = Offset(-OFF_SCREEN, OFF_SCREEN),
        degrees = 55f,
        delayMillis = 0,
    ),
    FanCard(
        from = Offset(-OFF_SCREEN, OFF_SCREEN),
        rest = Offset(0f, 0f),
        to = Offset(OFF_SCREEN, -OFF_SCREEN),
        degrees = 45f,
        delayMillis = 100,
    ),
    FanCard(
        from = Offset(OFF_SCREEN, 0f),
        rest = Offset(0.9f, 0.7f),
        to = Offset(0f, OFF_SCREEN),
        degrees = 35f,
        delayMillis = 200,
    ),
)

private const val ENTER_MILLIS = 300

private const val EXIT_MILLIS = 200

private const val LAST_DELAY_MILLIS = 200

private const val HOLD_MILLIS =
    COIN_FLIP_TOTAL_MILLIS - LAST_DELAY_MILLIS - ENTER_MILLIS - EXIT_MILLIS

private const val ENTER_SCALE = 1.2f

private const val OFF_SCREEN = 4f

private const val CARD_SCALE = 0.8f
