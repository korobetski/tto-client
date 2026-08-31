package com.tripletriad.ui

import com.tripletriad.model.GameSave
import com.tripletriad.protocol.PvpStakePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two sums the stake field draws itself from.
 *
 * Neither is the rule — `PvpStakePolicy` is, and the server checks its own copy — but both are
 * arithmetic the field commits to on screen, and getting either wrong offers a wager that will be
 * refused or withholds one that would have been taken. `PvpStakePolicyTest` in `:core` covers the
 * ceiling itself; this covers what the screen does with it.
 */
class PvpStakeFieldTest {

    // ---- The limit ---------------------------------------------------------

    /** Rich and low: the level is what binds, and the purse has nothing to say. */
    @Test
    fun aFortuneDoesNotRaiseTheCeiling() {
        val profile = save(level = 2, mgp = 500_000)

        assertEquals(PvpStakePolicy.DEFAULT_PER_LEVEL * 2, stakeLimit(profile, PvpStakePolicy()))
    }

    /** Levelled and broke: the purse binds instead, because a wager has to be covered. */
    @Test
    fun aLevelYouCannotPayForIsNotTheLimit() {
        assertEquals(30, stakeLimit(save(level = 20, mgp = 30), PvpStakePolicy()))
    }

    /**
     * A purse cannot go below zero, but neither can a field bound: `stakeRungs` would otherwise
     * offer a negative top rung, and a negative wager is one the winner pays — see
     * `PvpStakePolicyTest.aNegativeWagerIsNotAWager`.
     */
    @Test
    fun thereIsNeverLessThanNothingToWager() {
        assertEquals(0, stakeLimit(save(level = 5, mgp = -1), PvpStakePolicy()))
    }

    @Test
    fun theDeploymentsOwnNumberIsTheOneDrawn() {
        val profile = save(level = 10, mgp = 500_000)

        assertEquals(400, stakeLimit(profile, PvpStakePolicy(perLevel = 40)))
    }

    // ---- The rungs ---------------------------------------------------------

    /**
     * The last chip is the limit itself and not the largest round number under it.
     *
     * That is the whole reason the row exists next to a field: 2,200 at level 22 is the one figure
     * a player cannot work out from anything on screen, and a row ending at 2,000 would hide it.
     */
    @Test
    fun theTopRungIsExactlyWhatIsAllowed() {
        assertEquals(2_200, stakeRungs(2_200).last())
        assertEquals(37, stakeRungs(37).last())
    }

    @Test
    fun theRungsClimbAndStopAtTheLimit() {
        assertEquals(listOf(0, 50, 100, 250, 500), stakeRungs(500))

        assertTrue(stakeRungs(500).zipWithNext().all { (a, b) -> a < b }, "not strictly climbing")
    }

    /** A limit that is already a rung is not offered twice. */
    @Test
    fun aRoundLimitIsNotDuplicated() {
        assertEquals(listOf(0, 50, 100), stakeRungs(100))
    }

    /**
     * Nothing to wager is still a table — a free one — so the row keeps the chip that opens it
     * rather than disappearing and leaving the player with no way back to zero.
     */
    @Test
    fun anEmptyPurseStillOffersAFreeTable() {
        assertEquals(listOf(0), stakeRungs(0))
    }

    private fun save(level: Int, mgp: Int) =
        GameSave(username = "kuplu", level = level, mgp = mgp)
}
