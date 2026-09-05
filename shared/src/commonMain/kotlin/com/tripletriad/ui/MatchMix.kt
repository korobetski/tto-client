package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the board asks of the music, without ever setting a volume itself.
 *
 * ### Why a signal and not a call
 *
 * `AudioPlayer.volumes` is absolute: it takes the two levels and applies them. The player owns
 * those levels — they are in the settings file — so anything that wanted to duck the music had to
 * know what to restore afterwards, and two places calling `volumes` is one place quietly undoing
 * the other's slider. The classic failure is not subtle: duck under a caption, restore to 1.0, and
 * the player who muted the music is listening to it.
 *
 * So the board raises a flag and `App` remains the only caller. It already recomputes the volumes
 * whenever the settings change; it now also recomputes them when either of these moves, and the
 * player's own numbers are the only input.
 *
 * ### BR-003, and why this is a mix rather than a soundtrack
 *
 * There is one music file and there may not be a second — the shipped audio is Square Enix's and
 * nothing more may be added. A boss theme is therefore out of reach. What is not out of reach is
 * *when the one track plays*: getting out of the way while the board is announcing itself, and
 * stopping altogether for the last flip so the result lands in silence. Neither needs a byte of new
 * audio, and the second is the louder of the two effects.
 */
@Stable
internal class MatchMix {
    /**
     * The music steps back while a caption is on screen.
     *
     * Set by `MatchBannerOverlay`, which is the one thing that knows a banner is playing, and
     * cleared by it. Under it rather than silent: an intro is four captions with gaps between them,
     * and a track that stopped and restarted four times would be worse than one that never ducked.
     */
    var ducked: Boolean by mutableStateOf(false)

    /**
     * The music stops for the end of the match.
     *
     * The ninth card is the only one nothing follows, and the board has been playing a loop under
     * every other. Cutting it is the whole of the "last move" effect that BR-003 leaves available,
     * and it costs nothing to restore: leaving a board stops the music anyway — see `App` — and
     * arriving at the next one starts it.
     *
     * A volume of zero rather than `stopMusic`, so the loop keeps its position and a sudden-death
     * rematch does not restart the track from the top on a board that never left.
     */
    var silenced: Boolean by mutableStateOf(false)

    /** The player's own level, after whatever the board is asking for. */
    fun background(setting: Float): Float = when {
        silenced -> 0f
        ducked -> setting * DUCK
        else -> setting
    }

    private companion object {
        /**
         * How far under a caption the music goes.
         *
         * Not off. A caption is 1.4 seconds and four of them open a match; a track that vanished
         * and came back four times would draw more attention than the captions do, which is the
         * opposite of getting out of their way.
         */
        const val DUCK = 0.35f
    }
}

/**
 * The mix for the board on screen, or null where nothing is asking — a test rendering one
 * animation, a screen that is not a match.
 *
 * Nullable rather than defaulting to an instance, because a `staticCompositionLocalOf` default is
 * built once and shared: a mutable holder there would be a static mutable singleton, which is the
 * thing `CLAUDE.md` § 8 forbids and which would let one test's board duck another's.
 */
internal val LocalMatchMix = staticCompositionLocalOf<MatchMix?> { null }

@Composable
internal fun rememberMatchMix(): MatchMix = remember { MatchMix() }

/**
 * Stops the music for a board that has just been filled, and starts it again for the next one.
 *
 * Called by the two play areas rather than by the three screens, because "the board is full" is a
 * fact about the board. The `onDispose` is what makes a rematch work: a sudden-death board is
 * replaced in place, and a flag left set would hand the next match a silent loop.
 */
@Composable
internal fun SilenceForTheLastFlip(finished: Boolean) {
    val mix = LocalMatchMix.current
    DisposableEffect(mix, finished) {
        mix?.silenced = finished
        onDispose { mix?.silenced = false }
    }
}
