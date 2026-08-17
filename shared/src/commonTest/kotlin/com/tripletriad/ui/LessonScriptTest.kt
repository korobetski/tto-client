package com.tripletriad.ui

import com.tripletriad.model.MatchResult
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LessonScriptTest {

    @Test
    fun everyLessonSaysSomethingWhenItEnds() {
        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = assertNotNull(scriptFor(step, TUTOR, LESSON_CATALOG), "lesson $step")

            for (result in MatchResult.entries) {
                assertTrue(
                    script.outcomeLines[result]?.isNotEmpty() == true,
                    "lesson $step ends in silence on $result",
                )
            }
        }
    }

    @Test
    fun everyLessonSpeaksBeforeAMoveIsAskedFor() {
        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = assertNotNull(scriptFor(step, TUTOR, LESSON_CATALOG), "lesson $step")
            val spoken = (0 until PLACEMENTS_PER_MATCH).flatMap {
                script.lesson.linesBefore(placement = it, blueScore = OPENING_SCORE)
            }

            assertTrue(spoken.isNotEmpty(), "lesson $step never says anything")
        }
    }

    @Test
    fun everyLessonIsSpokenByTheTutor() {
        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = assertNotNull(scriptFor(step, TUTOR, LESSON_CATALOG), "lesson $step")

            assertEquals(TUTOR, script.speakerKey, "lesson $step is spoken by somebody else")
        }
    }

    private companion object {
        const val TUTOR = "STR_NPC_TT_Master"

        const val OPENING_SCORE = 5
    }
}
