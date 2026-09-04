package com.tripletriad.ui

import com.tripletriad.settings.MatchSpeed
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
        // `MatchBoard.LAND_MS` / `COMBO_WAVE_MS` / `FLIP_LEG_MS`, `CoinFlipCards`' total,
        // `MATCH_OPENING_MILLIS`, `SWAP_CARDS_TOTAL_MILLIS` and `UnlockedCard`'s hold — every
        // authored pause in the match screens, by value.
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
        assertEquals(500, Pacing(0.1) * 5_000, "a tenth of five seconds is half of one")
    }

    @Test
    fun theTwoDialsCompose() {
        // The test parameter and the player's setting, multiplied — see `App`, which is the only
        // caller. At the shipped setting the product must be the identity on the test's own factor,
        // or every fixture's timing would move the day a player-facing dial was added.
        assertEquals(Pacing(0.1), Pacing(0.1).scaledBy(MatchSpeed.NORMAL.scale))
        assertEquals(Pacing.Default, Pacing.Default.scaledBy(MatchSpeed.NORMAL.scale))

        assertEquals(0.5, Pacing.Default.scaledBy(MatchSpeed.FAST.scale).scale)
        assertEquals(0.05, Pacing(0.1).scaledBy(MatchSpeed.FAST.scale).scale, 1e-9)
    }

    @Test
    fun theInstantCranFloorsEveryPauseWhateverElseIsSet() {
        // The one cran that is allowed to reach zero, and the point of it: nothing waits. Asserted
        // against a *test* factor as well, because that is the combination a fixture would hit.
        for (base in listOf(Pacing.Default, Pacing(0.1), Pacing(2.0))) {
            val instant = base.scaledBy(MatchSpeed.INSTANT.scale)
            assertEquals(0, instant * MATCH_OPENING_MILLIS, "$base ms did not floor")
            assertEquals(0L, instant * 5_000L)
        }
    }

    /**
     * The property the test pace depends on: `TalkBubble` races a tap against its hold, and a hold
     * that scaled to zero would decide that race before the tap could be made. Every authored pause
     * has to stay positive at the factor the suite uses.
     *
     * The literal mirrors `TEST_PACING`, which lives in `desktopTest` and cannot be seen from here.
     * If one moves, move both — and read that KDoc first: the factor is measured against harness
     * latency, not picked.
     */
    @Test
    fun theTestFactorNeverFloorsAPauseToNothing() {
        val fast = Pacing(0.1)

        val authored = listOf(100, 240, 280, 300, 400, 450, 550, 700, 1_000, 1_400, 5_000) +
            // By constant rather than by value, so retiming either one moves this with it.
            listOf(MATCH_OPENING_MILLIS, SWAP_CARDS_TOTAL_MILLIS)
        for (millis in authored) {
            assertTrue(fast * millis > 0, "$millis ms scaled to ${fast * millis}")
        }
    }
}
