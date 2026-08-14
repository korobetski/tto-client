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
import com.tripletriad.platform.rememberReducedMotion

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
        return fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS))
    }

    return slideInHorizontally(tween(SLIDE_MILLIS)) { width -> travel * width } +
        fadeIn(tween(SLIDE_MILLIS)) togetherWith
        slideOutHorizontally(tween(SLIDE_MILLIS)) { width -> -travel * width } +
        fadeOut(tween(SLIDE_MILLIS))
}

/** No travel at all: a sidestep, or a player who has asked for less movement. */
private const val STILL = 0

/** Going deeper: the new screen arrives from the right, as the reading order suggests. */
private const val FORWARD = 1

/** Coming back: it arrives from the left, undoing the gesture that took the player in. */
private const val BACKWARD = -1

/**
 * Short enough not to be a wait, long enough to be seen.
 *
 * The crossfade this replaced used 220 ms and the reasoning holds; a slide needs a little longer to
 * read as travel rather than as a jump.
 */
private const val SLIDE_MILLIS = 260

/** A fade has no distance to cover, so it does not need the extra time. */
private const val FADE_MILLIS = 220
