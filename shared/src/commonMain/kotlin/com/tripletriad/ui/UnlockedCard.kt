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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import kotlinx.coroutines.delay

const val UNLOCKED_CARD_TEST_TAG: String = "unlocked-card"

@Composable
internal fun UnlockedCard(card: Card, onFinished: () -> Unit) {
    val entry = remember(card.id) { Animatable(0f) }
    val exit = remember(card.id) { Animatable(0f) }

    LaunchedEffect(card.id) {
        entry.animateTo(1f, tween(ENTER_MILLIS, easing = EaseOut))
        delay(HOLD_MILLIS.toLong())
        exit.animateTo(1f, tween(EXIT_MILLIS, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(UNLOCKED_CARD_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        CardFace(
            card = card.copy(owner = CardColor.BLUE),
            modifier = Modifier
                // The card's name rather than a generic label: this *is* the announcement, and
                // a player who cannot see it is otherwise told nothing at all.
                .semantics { contentDescription = card.name }
                .graphicsLayer {
                    val arrived = entry.value
                    val leaving = exit.value
                    translationX = size.width *
                        (ENTER_X * (1f - arrived) + EXIT_X * leaving)
                    translationY = size.height *
                        (ENTER_Y * (1f - arrived) + EXIT_Y * leaving)
                    rotationZ = ENTER_DEGREES * (1f - arrived)
                    // 1.2 on the way in, 1.5 at rest, back to 1 on the way out. Three values and
                    // two legs, so the resting scale is the middle rather than either end.
                    val scale = lerp(ENTER_SCALE, REST_SCALE, arrived) +
                        (1f - REST_SCALE) * leaving
                    scaleX = scale
                    scaleY = scale
                    alpha = arrived * (1f - leaving)
                },
        )
    }
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private const val ENTER_MILLIS = 300

private const val HOLD_MILLIS = 1_400

private const val EXIT_MILLIS = 200

private const val ENTER_X = 3f
private const val ENTER_Y = -3f

private const val EXIT_X = -3f
private const val EXIT_Y = 3f

private const val ENTER_DEGREES = 55f

private const val ENTER_SCALE = 1.2f

private const val REST_SCALE = 1.5f
