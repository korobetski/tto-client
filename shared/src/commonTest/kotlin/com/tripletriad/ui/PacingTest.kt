package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The claim [Pacing] is only worth having if it holds: at the shipped factor, every pause is the
 * number its constant says.
 *
 * Written against the real durations rather than round ones, because the risk is arithmetic — a
 * `Double` multiplication that lands a millisecond short would change the game by an amount nobody
 * would ever notice in a screenshot and every one of these numbers would drift.
 */
class PacingTest {
    @Test
    fun theShippedFactorChangesNothing() {
        val pacing = Pacing.Default

        // `TalkBubble.HOLD_MILLIS`, `MatchScreen.OPPONENT_PAUSE_MS` / `OUTCOME_PAUSE_MS`,
        // `MatchBoard.LAND_MS` / `COMBO_WAVE_MS` / `FLIP_LEG_MS`, `CoinFlipCards`' total, and
        // `UnlockedCard`'s hold — every authored pause in the match screens, by value.
        for (millis in listOf(100, 200, 240, 280, 300, 400, 450, 550, 700, 1_000, 1_400, 5_000)) {
            assertEquals(millis, pacing * millis, "$millis ms as an Int")
            assertEquals(millis.toLong(), pacing * millis.toLong(), "$millis ms as a Long")
            assertEquals(
                millis.milliseconds,
                pacing * millis.milliseconds,
                "$millis ms as a Duration",
            )
        }
    }

    @Test
    fun aSlowerFactorIsAStrictSlowdownAndAFasterOneAStrictSpeedup() {
        assertEquals(2_800L, Pacing(2.0) * 1_400L, "twice the pace is twice the wait")
        assertEquals(100, Pacing(0.02) * 5_000, "a fiftieth of five seconds is a tenth of one")
    }

    /**
     * The property the test pace depends on: `TalkBubble` races a tap against its hold, and a hold
     * that scaled to zero would decide that race before the tap could be made. Every authored pause
     * has to stay positive at the factor the suite uses.
     */
    @Test
    fun theTestFactorNeverFloorsAPauseToNothing() {
        val fast = Pacing(0.02)

        for (millis in listOf(100, 240, 280, 300, 400, 450, 550, 700, 1_000, 1_400, 5_000)) {
            assertTrue(fast * millis > 0, "$millis ms scaled to ${fast * millis}")
        }
    }
}
