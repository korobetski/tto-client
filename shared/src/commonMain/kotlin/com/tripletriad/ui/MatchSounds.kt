package com.tripletriad.ui

import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.model.Capture
import kotlinx.coroutines.delay

internal fun placementSound(audio: AudioPlayer, captures: List<Capture>, finished: Boolean) {
    audio.play(if (captures.isEmpty()) Sound.CARD_PLACED else Sound.CARD_CAPTURED)
    if (!finished) audio.play(Sound.TURN_CHANGE)
}

internal suspend fun cascadeSounds(
    audio: AudioPlayer,
    captures: List<Capture>,
    won: Boolean?,
    // Passed rather than read: this is not a composable, and the wave it counts out has to stay in
    // step with the flips [MatchBoard] is drawing to the same beat.
    pacing: Pacing = Pacing.Default,
) {
    repeat(captures.maxOfOrNull { it.wave } ?: 0) {
        delay(pacing * COMBO_WAVE_MS)
        audio.play(Sound.COMBO)
    }

    when (won) {
        true -> audio.play(Sound.BLUE_WINS)
        false -> audio.play(Sound.RED_WINS)
        null -> Unit
    }
}
