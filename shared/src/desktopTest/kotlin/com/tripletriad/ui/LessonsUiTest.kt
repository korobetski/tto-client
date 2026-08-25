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

@OptIn(ExperimentalTestApi::class)
class LessonsUiTest {

    @Test
    fun theDashboardOpensTheCourse() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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

    @Test
    fun aNewCharacterHasFinishedNothing() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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

    @Test
    fun aLessonCanBeStartedOutOfOrder() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()

        onNodeWithTag(lessonRowTestTag(PLUS_LESSON)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.BLUE) == 1 }
    }

    @Test
    fun finishingALessonIsRemembered() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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

    @Test
    fun aFinishedCourseSaysSoInsteadOfExplainingItself() = runComposeUiTest {
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US, lessonsDone = TUTORIAL_COURSE.size),
            )
        }
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

    @Test
    fun anUnfinishedCourseKeepsTheBlurb() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US, lessonsDone = PART_WAY)) }
        newCharacter()

        openLessons()

        assertTrue(exists(LESSONS_BLURB_TEST_TAG), "an unfinished course still explains itself")
        assertFalse(exists(LESSONS_ALL_DONE_TEST_TAG), "and has nothing to congratulate yet")
    }

    private companion object {
        const val PLUS_LESSON = 2

        const val PART_WAY = 3
    }
}
