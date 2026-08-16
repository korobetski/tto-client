package com.tripletriad.ui

import com.tripletriad.model.MatchResult
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every lesson in the course speaks — before the move, and after the match.
 *
 * ### The gap this exists for
 *
 * `TutorialPuzzleTest` checks what the positions *do*, `TutorialDrillTest` what the matches are
 * played under, and `LessonRecordTest` that none of it touches the record. Nothing checked that a
 * lesson says anything, and one did not: the **opening lesson** — nine placements and nine spoken
 * lines, the longest in the course — ended in silence, because `TutorialScreen.as` closes on its
 * rematch panel with no `TalkAnim` and the port inherited that. Every one-move position added after
 * it closes with a sentence, so the defect was the original lesson being left behind rather than a
 * new one being written wrong, which is exactly the kind that survives a review of the diff.
 *
 * ### Asserted over the whole course, not over a list of lessons
 *
 * Both tests walk [FIRST_LESSON]`..`[LAST_LESSON] and ask [scriptFor] rather than reading
 * [TUTORIAL_COURSE]'s rows. That is the difference between checking the data and checking what a
 * player is handed: the opening lesson has no row content at all — no puzzle, no drill — and is the
 * one that was wrong.
 */
class LessonScriptTest {

    /**
     * Every lesson closes with a line, for **every result it can end on**.
     *
     * All three, not just the likely one. A lesson is uncounted and most cannot be lost, but the
     * exam is a real match and the opening one is a whole game against an opponent that is merely
     * playing badly — so `WIN` alone would leave the two that can go the other way ending on
     * nothing at the moment a player most wants to be told something.
     */
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

    /**
     * And every lesson says something *before* it asks for a move.
     *
     * Asked across the whole match rather than at placement zero, because the lessons speak at
     * different moments and are right to: a position speaks on the one placement it is about, a
     * drill on the player's first and third, the opening match on five of the nine. What none of
     * them may be is silent throughout, which is a lesson that teaches by watching.
     */
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

    /** The tutor is the one speaking, in every lesson — the bubbles are signed with a name. */
    @Test
    fun everyLessonIsSpokenByTheTutor() {
        for (step in FIRST_LESSON..LAST_LESSON) {
            val script = assertNotNull(scriptFor(step, TUTOR, LESSON_CATALOG), "lesson $step")

            assertEquals(TUTOR, script.speakerKey, "lesson $step is spoken by somebody else")
        }
    }

    private companion object {
        const val TUTOR = "STR_NPC_TT_Master"

        /** Five all: what the score is before anything has been captured. */
        const val OPENING_SCORE = 5
    }
}
