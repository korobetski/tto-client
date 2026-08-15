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

/**
 * How one screen becomes another.
 *
 * ### What was wrong with a crossfade
 *
 * Nothing, except that it says the same thing in both directions. Going *into* the shop and coming
 * *back out* of it looked identical, so the animation carried no information — and a transition
 * that carries none is a delay. Material calls the fix a shared axis: forward slides one way, back
 * slides the other, and the player can see which happened without reading the screen.
 *
 * ### Where the direction comes from
 *
 * [Screen.depth], derived from the `up` relation the app already maintains for its back button.
 * There is no second table to keep in step: a new screen names its parent once, and its motion
 * follows. Screens at the same depth — the sidesteps, like moving between two things hanging off
 * the dashboard — get a plain fade, because neither is "further in" than the other and sliding
 * would claim a relationship that does not exist.
 *
 * ### Reduced motion is not a slower animation
 *
 * It is **no** animation. Halving the duration of a slide still slides, and the setting exists for
 * people who are made unwell by movement rather than people who find it slow. So the reduced path
 * is a fade with no travel at all — the screen still changes visibly, it just does not move.
 *
 * @param screen the destination. Changing it runs the transition.
 */
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

/**
 * The transform for one move, as a pure function of the two screens.
 *
 * Separate from the composable so it can be reasoned about — and tested — without a composition.
 * `ScreenMotionTest` walks every pair.
 */
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

/** No travel at all: a sidestep, or a player who has asked for less movement. */
private const val STILL = 0

/** Going deeper: the new screen arrives from the right, as the reading order suggests. */
private const val FORWARD = 1

/** Coming back: it arrives from the left, undoing the gesture that took the player in. */
private const val BACKWARD = -1
