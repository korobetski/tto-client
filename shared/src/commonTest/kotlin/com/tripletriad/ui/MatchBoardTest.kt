package com.tripletriad.ui

import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.PlayResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [settleMillis] is what stops the opponent playing over its own capture animation — see the
 * function's own doc comment for why [waveDelayMillis] alone was not enough.
 */
class MatchBoardTest {

    @Test
    fun aPlacementWithNoPlayStillGetsTheLandingTime() {
        assertEquals(LAND_MS.toLong(), settleMillis(null))
    }

    @Test
    fun aPlacementThatCapturesNothingSettlesInTheLandingTimeAlone() {
        assertEquals(LAND_MS.toLong(), settleMillis(play()))
    }

    @Test
    fun aFirstWaveCaptureSettlesInTheLandingTimeAlone() {
        val captured = play(Capture(position = 0, kind = CaptureKind.BASIC, wave = 0))

        assertEquals(LAND_MS.toLong(), settleMillis(captured))
    }

    @Test
    fun aChainedCaptureWaitsForItsWaveThenTheFlip() {
        val chained = play(
            Capture(position = 0, kind = CaptureKind.SAME, wave = 0),
            Capture(position = 1, kind = CaptureKind.COMBO, wave = 1),
        )

        assertEquals(COMBO_WAVE_MS + FLIP_MS, settleMillis(chained))
    }

    @Test
    fun eachFurtherWaveAddsAFullWaveDelay() {
        val threeWaves = play(
            Capture(position = 0, kind = CaptureKind.SAME, wave = 0),
            Capture(position = 1, kind = CaptureKind.COMBO, wave = 1),
            Capture(position = 2, kind = CaptureKind.COMBO, wave = 2),
        )

        assertEquals(2 * COMBO_WAVE_MS + FLIP_MS, settleMillis(threeWaves))
    }

    private fun play(vararg captures: Capture) = PlayResult(
        player = CardColor.BLUE,
        card = card,
        position = 4,
        captures = captures.toList(),
    )

    private val card = Card(
        id = Card.idFor(block = 1, number = 1),
        nameKey = "STR_FF14_CARD_1",
        name = "Test",
        top = 1,
        right = 2,
        bottom = 3,
        left = 4,
        rarity = 1,
    )
}
