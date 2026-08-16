package com.tripletriad.ui

import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.model.Capture
import kotlinx.coroutines.delay

/**
 * What a placement sounds like — the mapping, in one place, for every board that has one.
 *
 * `MatchScreen` and `PvpMatchScreen` are deliberately separate screens (see the latter's header:
 * one runs the match, the other renders what a referee sends). The *sounds* are not separate. A
 * capture is the same event whoever resolved it, and two copies of this mapping would be two
 * chances for a rule to sound different against a person than against the AI — which is exactly the
 * kind of drift the player would notice and nobody would think to test.
 *
 * `MatchAudioTest` asserts the mapping through the real UI with a recording player.
 *
 * * **nothing captured** → [Sound.CARD_PLACED]. `TTOCore.as:87` plays `se_ttriad.scd_1` in exactly
 *   the branch that returns a power of 0, i.e. the placement that flips nothing.
 * * **something captured** → [Sound.CARD_CAPTURED], **once**. `Card.as:229` plays it per flipped
 *   card, inside `flipTo`; four cards flipping at once would fire it four times, which on
 *   `SoundPool` is the same sample four times in the same millisecond — a volume spike, not a
 *   richer sound. One is the faithful *result*.
 * * **the match continues** → [Sound.TURN_CHANGE], `BaseMatchScreen.as:374`, which plays it for
 *   either side. Immediate, because it is the cue that somebody may move and nothing on the board
 *   is waiting on it.
 *
 * The chain and the result are [cascadeSounds]', because both have to wait for the cards.
 *
 * @param finished whether that placement ended the match, in which case the turn passes to nobody.
 */
internal fun placementSound(audio: AudioPlayer, captures: List<Capture>, finished: Boolean) {
    audio.play(if (captures.isEmpty()) Sound.CARD_PLACED else Sound.CARD_CAPTURED)
    if (!finished) audio.play(Sound.TURN_CHANGE)
}

/**
 * Everything that has to wait for the cards to stop turning: the chain, and then the result.
 *
 * ### The chain
 *
 * One [Sound.COMBO] **per generation, on that generation's beat**. It used to be one COMBO fired
 * with the capture whatever the chain's length, which was right while every card turned on the same
 * frame and is wrong now that they do not — the sound arrived a beat before the flip it was
 * announcing, which is exactly the join the stagger exists to make. So it waits with the cards, on
 * the same [COMBO_WAVE_MS], and a chain of three is three events. The AS3's own `flipData.waveEffect`
 * (`TTOCore.as:125`) counts them the same way.
 *
 * ### And then the result
 *
 * Here rather than in [placementSound] because of the order it would otherwise come out in: a final
 * placement that chains would announce the result *before* the last card had turned, which on the
 * combo lesson means hearing "you win" and then hearing the thing that won it.
 *
 * With no chain the loop does not run and both sound exactly when they always did.
 *
 * @param won true if the local player won, false if they lost, **null for no result yet or a draw**
 *   — `PVEMatchScreen`'s draw branch plays nothing and neither does this. The sounds are named for
 *   colours because the original's are (`PVEMatchScreen.as:95`/`:139`, where blue is always the
 *   local player); against a person the local player may be red, and what the sample means is still
 *   "you won", so it is chosen by outcome rather than by colour.
 */
internal suspend fun cascadeSounds(audio: AudioPlayer, captures: List<Capture>, won: Boolean?) {
    repeat(captures.maxOfOrNull { it.wave } ?: 0) {
        delay(COMBO_WAVE_MS)
        audio.play(Sound.COMBO)
    }

    when (won) {
        true -> audio.play(Sound.BLUE_WINS)
        false -> audio.play(Sound.RED_WINS)
        null -> Unit
    }
}
