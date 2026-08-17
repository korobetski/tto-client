package com.tripletriad.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.tripletriad.platform.rememberReducedMotion
import com.tripletriad.ui.theme.DURATION_MEDIUM
import com.tripletriad.ui.theme.DURATION_SHORT
import com.tripletriad.ui.theme.EmphasizedAccelerate
import com.tripletriad.ui.theme.EmphasizedDecelerate
import com.tripletriad.ui.theme.Standard

@Composable
internal fun ScreenTransition(screen: Screen, content: @Composable (Screen) -> Unit) {
    val reduced = rememberReducedMotion()

    AnimatedContent(
        targetState = screen,
        label = "screen",
        transitionSpec = { transitionFor(initialState, targetState, reduced) },
    ) { destination ->
        content(destination)
    }
}

internal fun transitionFor(from: Screen, to: Screen, reduced: Boolean): ContentTransform {
    // Zero covers both cases that do not move: reduced motion, and a sidestep between two screens
    // at the same depth. They are one branch because they want the same thing — a change the player
    // can see, with no travel to it.
    val travel = when {
        reduced -> STILL
        to.depth > from.depth -> FORWARD
        to.depth < from.depth -> BACKWARD
        else -> STILL
    }

    if (travel == STILL) {
        val fade = tween<Float>(DURATION_SHORT, easing = Standard)
        return fadeIn(fade) togetherWith fadeOut(fade)
    }

    // Asymmetric on purpose: the arriving screen decelerates into place and the leaving one
    // accelerates away, which is what makes the pair read as one movement rather than as two
    // things sliding past each other. See `theme/Motion.kt` for why these two curves exist.
    val entering = tween<IntOffset>(DURATION_MEDIUM, easing = EmphasizedDecelerate)
    val leaving = tween<IntOffset>(DURATION_MEDIUM, easing = EmphasizedAccelerate)

    return slideInHorizontally(entering) { width -> travel * width } +
        fadeIn(tween(DURATION_MEDIUM, easing = Standard)) togetherWith
        slideOutHorizontally(leaving) { width -> -travel * width } +
        fadeOut(tween(DURATION_MEDIUM, easing = Standard))
}

private const val STILL = 0

private const val FORWARD = 1

private const val BACKWARD = -1
