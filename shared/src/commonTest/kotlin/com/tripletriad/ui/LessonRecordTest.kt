package com.tripletriad.ui

import com.tripletriad.data.MatchCredit
import com.tripletriad.data.MatchReward
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The tutorial leaves no mark on the player's record** — [MatchScript.counted].
 *
 * A course of four lessons that counted would open every character on four wins, and a win rate
 * that is partly a record of being taught the rules is not a win rate.
 *
 * The requirement covers wins, defeats, draws **and forfeits**, and the last of those is why this
 * is asserted at both ends rather than only at the credit: [GameSave.forfeits] is
 * `startedMatches - endedMatches`, so a lesson skipped at one end only would leave behind exactly
 * the mark it was avoiding — an abandoned match, one per lesson. That is a failure mode where
 * each half looks correct alone, which is why [theWholeCourseLeavesTheRecordAlone] walks the pair.
 *
 * Asserted against the extensions rather than through the UI because they are where the decision
 * is; `TutorialUiTest` covers the screen that calls them.
 */
class LessonRecordTest {

    /** Every lesson in the course, the opening match included, is uncounted. */
    @Test
    fun noLessonIsCounted() {
        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = scriptFor(step, speakerKey = TUTOR, catalog = LESSON_CATALOG)

            assertFalse(
                script?.counted ?: true,
                "lesson $step goes on the record, or does not build; a tutorial should do neither",
            )
        }
    }

    /** Nothing is counted as started, so there is nothing that has to be closed. */
    @Test
    fun aLessonStartsNoMatch() {
        val lesson = MatchScript(speakerKey = TUTOR, counted = false)

        assertEquals(
            PLAYER,
            lesson.startingMatch(PLAYER),
            "a lesson must not count as a match started, or finishing it is the only way out",
        )
    }

    /** An ordinary match still counts: the flag defaults on, and no script at all is one too. */
    @Test
    fun anOrdinaryMatchStillCounts() {
        val ordinary = MatchScript(speakerKey = TUTOR)
        val none: MatchScript? = null

        assertEquals(
            PLAYER.startedMatches + 1,
            ordinary.startingMatch(PLAYER).startedMatches,
            "a real match is counted",
        )
        assertEquals(
            PLAYER.startedMatches + 1,
            none.startingMatch(PLAYER).startedMatches,
            "no script at all is the ordinary match, and it counts",
        )
    }

    /**
     * Finishing a lesson writes nothing: not the result, not the counters, not the money.
     *
     * The save handed back has to be the *same value*, not merely one with the same win count — a
     * credit that recorded a rule tally, an achievement or a finished quest would pass a narrower
     * check. Crediting is not even computed, which is the stronger claim and the one that holds:
     * [com.tripletriad.data.MatchRewards.credit] writes the payout and the stats in one pass, so
     * not calling it is the whole of the implementation.
     */
    @Test
    fun aLessonCreditsNothing() {
        val lesson = MatchScript(speakerKey = TUTOR, counted = false)
        var earned = false

        val credit = lesson.creditFor(MatchResult.WIN, PLAYER) {
            earned = true
            MatchCredit(save = PLAYER.withMgp(PAYOUT), reward = paid(PAYOUT))
        }

        assertFalse(earned, "crediting a lesson should not even be computed")
        assertEquals(PLAYER, credit.save, "a lesson must leave the profile exactly as it found it")
        assertEquals(0, credit.reward.mgp, "a lesson pays no MGP")
        assertEquals(0, credit.reward.xp, "a lesson pays no XP")
        assertEquals(
            MatchResult.WIN,
            credit.reward.result,
            "the panel still has to say how it went — that is the one thing still reported",
        )
    }

    /** A counted match credits exactly as it always did. */
    @Test
    fun anOrdinaryMatchIsStillCredited() {
        val ordinary = MatchScript(speakerKey = TUTOR)
        val earned = MatchCredit(save = PLAYER.withMgp(PAYOUT), reward = paid(PAYOUT))

        assertEquals(earned, ordinary.creditFor(MatchResult.WIN, PLAYER) { earned })
    }

    /**
     * The record is untouched across the whole course — both ends, once per lesson.
     *
     * Walked rather than checked once because the two halves are what can disagree, and because a
     * course is what a player actually plays: the profile that comes out the far end of four
     * lessons has to be the one that went in.
     */
    @Test
    fun theWholeCourseLeavesTheRecordAlone() {
        var save = PLAYER

        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = scriptFor(step, speakerKey = TUTOR, catalog = LESSON_CATALOG)
            val playing = script.startingMatch(save)
            save = script.creditFor(MatchResult.WIN, playing) {
                error("lesson $step reached the crediting path")
            }.save
        }

        assertEquals(PLAYER, save, "four lessons should leave the profile as it started")
        assertEquals(0, save.forfeits, "and above all, no forfeits")
        assertTrue(
            save.stats.played == PLAYED_BEFORE,
            "no lesson may be counted as a match played, was ${save.stats}",
        )
    }

    private companion object {
        const val TUTOR = "STR_NPC_TT_Master"

        /** Enough MGP to be unmistakable in an assertion if it ever reached the profile. */
        const val PAYOUT = 1_000

        /** A profile part-way through a career, so "unchanged" is a claim worth making. */
        val PLAYER = GameSave(
            username = "kuplu",
            mgp = 4_200,
            startedMatches = 12,
            endedMatches = 12,
            stats = Stats(wins = 7, defeats = 4, draws = 1),
        )

        val PLAYED_BEFORE = PLAYER.stats.played

        fun paid(mgp: Int) = MatchReward(result = MatchResult.WIN, mgp = mgp, xp = mgp)
    }
}
