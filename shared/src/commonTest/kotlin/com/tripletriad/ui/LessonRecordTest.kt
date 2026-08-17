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

class LessonRecordTest {

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

    @Test
    fun aLessonStartsNoMatch() {
        val lesson = MatchScript(speakerKey = TUTOR, counted = false)

        assertEquals(
            PLAYER,
            lesson.startingMatch(PLAYER),
            "a lesson must not count as a match started, or finishing it is the only way out",
        )
    }

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

    @Test
    fun anOrdinaryMatchIsStillCredited() {
        val ordinary = MatchScript(speakerKey = TUTOR)
        val earned = MatchCredit(save = PLAYER.withMgp(PAYOUT), reward = paid(PAYOUT))

        assertEquals(earned, ordinary.creditFor(MatchResult.WIN, PLAYER) { earned })
    }

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

        const val PAYOUT = 1_000

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
