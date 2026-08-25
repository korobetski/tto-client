package com.tripletriad.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration

/**
 * A factor on every authored pause, so a test does not have to sit through the game's pacing.
 *
 * The pacing itself stays where it is written — `HOLD_MILLIS` beside the bubble that holds,
 * `OPPONENT_PAUSE_MS` beside the opponent that pauses. Those numbers are the design. This
 * multiplies them on the way into `delay` and `tween`, and nothing else.
 *
 * It exists because there was no other honest way to make the suite faster. A Compose UI test that
 * plays a lesson waits out every pause in real time: `TutorialUiTest` was 247s of a 526s run
 * (2026-08-25), and one `awaitPlayer` in it measured 16.6s of tutor speech against
 * `TalkBubble`'s five seconds a line. Build-level tuning cannot reach that — parallel forks only
 * moved the suite 13%, because nothing finishes before the longest class does.
 *
 * [Default] is 1.0 and every multiplication at that factor is the identity on these magnitudes, so
 * the shipped app runs on exactly the numbers the constants say. `App` takes it as a parameter
 * defaulting to [Default], in the same way it takes a `Clock` or an `AudioPlayer`; nothing outside
 * a test ever passes anything else.
 */
@Immutable
data class Pacing(val scale: Double) {
    operator fun times(millis: Int): Int = (millis * scale).toInt()

    operator fun times(millis: Long): Long = (millis * scale).toLong()

    operator fun times(duration: Duration): Duration = duration * scale

    companion object {
        /** The pace the game ships at. */
        val Default: Pacing = Pacing(1.0)
    }
}

/**
 * Read wherever a pause is about to be taken.
 *
 * `static` because it changes once per composition at most — it is set by `App` and never again,
 * so there is nothing to gain from tracking reads of it.
 */
val LocalPacing = staticCompositionLocalOf { Pacing.Default }
