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
            assertFalse(exists(lessonDoneTestTag(lesson)), "lesson $lesson cannot be done yet")
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
     * The course is eight lessons long, which is more than anyone finishes in one sitting — so
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

        playOut()
        // Back, not Next: a lesson played to the end counts however the player leaves it, which is
        // the whole reason progress is reported from the result rather than from a control.
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(LESSONS_LIST_TEST_TAG) }
        assertTrue(exists(lessonDoneTestTag(0)), "the first lesson was played to the end")
        assertFalse(exists(lessonDoneTestTag(1)), "and the second was never opened")
    }

    private companion object {
        /** The third row: the opening match, then Same, then Plus. */
        const val PLUS_LESSON = 2
    }
}
