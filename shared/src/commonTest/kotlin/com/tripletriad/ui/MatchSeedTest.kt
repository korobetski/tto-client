package com.tripletriad.ui

import com.tripletriad.time.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **A lesson does not spend a seed.**
 *
 * Seeds are the stock a refereed match is drawn from, and they are finite — `MatchSeed`'s own empty
 * state is a screen telling the player there are none left. A scripted match has nothing to draw
 * for: the deal, the order and the opponent are all fixed by the script, so taking a ticket for one
 * would charge the player for a match the server never had to referee.
 *
 * So [seedFor] branches on the script and not on the stock, and the clock is what an ordinary
 * lesson gets instead — a value nothing replays from.
 */
class MatchSeedTest {

    @Test
    fun aScriptedMatchTakesTheClockAndLeavesTheStockAlone() {
        var asked = 0

        val seed = seedFor(script = lesson, clock = FixedClock(millis = AT)) {
            asked++
            7
        }

        assertEquals(AT.toInt(), seed, "a lesson is seeded from the clock")
        assertEquals(0, asked, "and must not spend a ticket to be played")
    }

    @Test
    fun anOrdinaryMatchTakesTheNextSeedFromTheStock() {
        val seed = seedFor(script = null, clock = FixedClock(millis = AT)) { 7 }

        assertEquals(7, seed, "an unscripted match is dealt from the stock")
    }

    /** An empty stock is an answer, and it is the one `NoSeedNotice` is drawn for. */
    @Test
    fun anEmptyStockAnswersWithNoSeedRatherThanFallingBackOnTheClock() {
        val seed = seedFor(script = null, clock = FixedClock(millis = AT)) { null }

        assertNull(seed, "there is no seed, and the clock is not a substitute for one")
    }

    private val lesson = MatchScript(speakerKey = "STR_NPC_TT_Master", counted = false)

    private companion object {
        const val AT = 1_700_000_000_000L
    }
}
