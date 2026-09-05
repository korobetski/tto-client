package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic behind the two things the board asks of the music.
 *
 * The whole point of the class is that it computes a level rather than setting one — see
 * [MatchMix], and `App`, which is the only caller of `AudioPlayer.volumes`. So the only thing worth
 * pinning is that the player's own number is always the input, and that the two requests compose in
 * the one order that makes sense.
 */
class MatchMixTest {
    @Test
    fun anUntouchedBoardPlaysAtWhateverTheSliderSays() {
        val mix = MatchMix()

        for (setting in listOf(0f, 0.25f, 0.6f, 1f)) {
            assertEquals(setting, mix.background(setting), "the slider was at $setting")
        }
    }

    @Test
    fun aCaptionDucksTheMusicWithoutSilencingIt() {
        val mix = MatchMix()
        mix.ducked = true

        val ducked = mix.background(1f)
        assertTrue(
            ducked > 0f,
            "an intro is four captions; a track that vanished four times is worse",
        )
        assertTrue(ducked < 1f, "it did not get out of the way at all")
    }

    @Test
    fun theLastFlipStopsIt() {
        val mix = MatchMix()
        mix.silenced = true

        assertEquals(0f, mix.background(1f))
    }

    @Test
    fun silenceWinsOverDucking() {
        // Both are set on the last placement of a match that ends on a caption — Same, Combo, then
        // the win itself. The result has to land in silence, not at a third of the volume.
        val mix = MatchMix()
        mix.ducked = true
        mix.silenced = true

        assertEquals(0f, mix.background(1f))
    }

    @Test
    fun aMutedPlayerStaysMutedThroughBoth() {
        // The failure this class exists to prevent: something ducks, then "restores" to a level it
        // invented. Every answer here is a function of the setting, so nothing can restore to 1.
        val mix = MatchMix()

        assertEquals(0f, mix.background(0f))
        mix.ducked = true
        assertEquals(0f, mix.background(0f))
        mix.silenced = true
        assertEquals(0f, mix.background(0f))
    }

    @Test
    fun droppingBothRequestsGivesTheSliderBack() {
        val mix = MatchMix()
        mix.ducked = true
        mix.silenced = true

        mix.ducked = false
        mix.silenced = false

        assertEquals(0.4f, mix.background(0.4f))
    }
}
