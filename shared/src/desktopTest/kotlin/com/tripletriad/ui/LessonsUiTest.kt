package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The course, as a list — [LessonsScreen] and the dashboard card that opens it.
 *
 * What is worth asserting here is the three things the list is *for*, none of which the single row
 * it replaced could do: every lesson is reachable, one can be started out of order, and finishing
 * one is remembered.
 *
 * The tick is read from the **unmerged** tree throughout: a lesson row is clickable, so it merges
 * its descendants' semantics and the tick inside it has no node of its own in the merged tree.
 * Read merged, every one of these assertions says "nothing is ticked" whatever the course knows.
 * See [existsUnmerged].
 */
@OptIn(ExperimentalTestApi::class)
class LessonsUiTest {

    /** The dashboard has a way in, and it lists the whole course. */
    @Test
    fun theDashboardOpensTheCourse() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openLessons()

        for (lesson in TUTORIAL_COURSE.indices) {
            // Scrolled to rather than asserted where it sits: the course is twelve rows in a
            // `LazyColumn` and the last of them is below the fold on the test window, so an
            // unscrolled `exists` was asserting the viewport's height and not the list's contents.
            scrollToLesson(lesson)
            assertTrue(exists(lessonRowTestTag(lesson)), "lesson $lesson should be listed")
        }
    }

    /** Nothing is finished on a new character, so nothing is ticked. */
    @Test
    fun aNewCharacterHasFinishedNothing() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openLessons()

        for (lesson in TUTORIAL_COURSE.indices) {
            scrollToLesson(lesson)
            assertFalse(
                existsUnmerged(lessonDoneTestTag(lesson)),
                "lesson $lesson cannot be done yet",
            )
        }
    }

    /**
     * **A lesson can be started out of order**, which is the other half of what a list is for.
     *
     * Nothing is locked — see [LessonsScreen] — so a player who wants only the Plus lesson opens
     * it. Asserted through the board rather than the row: what matters is that the position is
     * the one that lesson teaches, and the hand is what says so. A puzzle deals one card; the
     * opening match deals five.
     */
    @Test
    fun aLessonCanBeStartedOutOfOrder() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()

        onNodeWithTag(lessonRowTestTag(PLUS_LESSON)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.BLUE) == 1 }
    }

    /**
     * Finishing a lesson is remembered, and remembered *through the list*.
     *
     * The course is twelve lessons long, which is more than anyone finishes in one sitting — so
     * the thing that makes it a course rather than eight screens is that it knows where you
     * stopped. Asserted by leaving the lesson the way a player does, through the panel's own
     * control, and coming back to the list.
     */
    @Test
    fun finishingALessonIsRemembered() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }

        // The first lesson is the nine-line opening match, so a turn waits on the speech as well
        // as on the tutor — the same reason `TutorialUiTest` plays on the longer clock.
        playOut(TUTORIAL_TIMEOUT_MS)
        // Back, not Next: a lesson played to the end counts however the player leaves it, which is
        // the whole reason progress is reported from the result rather than from a control.
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(LESSONS_LIST_TEST_TAG) }
        assertTrue(existsUnmerged(lessonDoneTestTag(0)), "the first lesson was played to the end")
        assertFalse(existsUnmerged(lessonDoneTestTag(1)), "and the second was never opened")
    }

    /**
     * A course with nothing left in it says so, in place of the blurb telling you how to start.
     *
     * Seeded through the settings file rather than played twelve times: the state under test is
     * "every lesson finished", and reaching it honestly would be a fifteen-minute test asserting
     * one paragraph. What it *does* go through honestly is the field the app persists —
     * `UserSettings.lessonsDone` — so a rename of that field fails here rather than leaving a
     * screen that congratulates nobody.
     */
    @Test
    fun aFinishedCourseSaysSoInsteadOfExplainingItself() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US, lessonsDone = TUTORIAL_COURSE.size)) }
        newCharacter()

        openLessons()

        assertTrue(exists(LESSONS_ALL_DONE_TEST_TAG), "a finished course should say so")
        assertFalse(
            exists(LESSONS_BLURB_TEST_TAG),
            "and should not still be explaining how to begin",
        )
        scrollToLesson(TUTORIAL_COURSE.size - 1)
        assertTrue(
            existsUnmerged(lessonDoneTestTag(TUTORIAL_COURSE.size - 1)),
            "the last row should be ticked, or the two halves disagree about the same number",
        )
    }

    /** And a course part-way through still explains itself. */
    @Test
    fun anUnfinishedCourseKeepsTheBlurb() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US, lessonsDone = PART_WAY)) }
        newCharacter()

        openLessons()

        assertTrue(exists(LESSONS_BLURB_TEST_TAG), "an unfinished course still explains itself")
        assertFalse(exists(LESSONS_ALL_DONE_TEST_TAG), "and has nothing to congratulate yet")
    }

    private companion object {
        /** The third row: the opening match, then Same, then Plus. */
        const val PLUS_LESSON = 2

        /** Enough lessons to have started and not enough to have finished. */
        const val PART_WAY = 3
    }
}
