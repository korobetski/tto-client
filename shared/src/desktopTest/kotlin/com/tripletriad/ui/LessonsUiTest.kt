package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardColor
import kotlinx.coroutines.runBlocking
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

    /**
     * The card at the top: what a tinted row was standing in for.
     *
     * It names the lesson the course is *at*, not the first one — a player three lessons in who
     * is offered lesson one has been offered the thing they already did.
     */
    @Test
    fun theCourseOpensOnWhereItWasPutDown() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US, lessonsDone = PART_WAY)) }
        newCharacter()

        openLessons()

        assertTrue(exists(LESSONS_RESUME_TEST_TAG), "a course put down halfway offers to resume")
        assertVisible(
            english[TUTORIAL_COURSE[PART_WAY].titleKey],
            "the card should name the lesson the course is at",
        )
    }

    /** And the button on it is the lesson, not a way back to the list. */
    @Test
    fun theResumeButtonPlaysThatLesson() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US, lessonsDone = PLUS_LESSON)) }
        newCharacter()
        openLessons()

        onNodeWithTag(LESSONS_RESUME_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        // The Plus position deals one card, which is what says *which* lesson opened.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.BLUE) == 1 }
    }

    @Test
    fun aFinishedCourseHasNothingLeftToResume() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US, lessonsDone = TUTORIAL_COURSE.size))
        }
        newCharacter()

        openLessons()

        assertFalse(
            exists(LESSONS_RESUME_TEST_TAG),
            "there is no next lesson, so there is nothing to offer",
        )
    }

    /** Three chapters, each counting its own lessons rather than repeating the course's total. */
    @Test
    fun eachChapterCountsItsOwnLessons() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US, lessonsDone = PART_WAY)) }
        newCharacter()

        openLessons()

        for (chapter in LessonChapter.entries) {
            onNodeWithTag(LESSONS_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(lessonChapterTestTag(chapter.slug)))
            onNodeWithTag(lessonChapterCountTestTag(chapter.slug))
                .assertTextEquals("${chapter.doneIn(PART_WAY)} / ${chapter.lessons.count()}")
        }
    }

    /**
     * The link that was missing between the two screens.
     *
     * The lesson names a rule; the rule's entry is one screen away and used to be reachable only
     * by remembering which of four families it was filed under.
     */
    @Test
    fun aRulePillOpensTheBookAtThatRule() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()
        scrollToLesson(PLUS_LESSON)

        onNodeWithTag(ruleLinkTestTag(PLUS_RULE), useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(HELP_LIST_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(PLUS_RULE)) }
    }

    private companion object {
        val english = runBlocking { loadStrings(AppLocale.EN_US) }

        const val PLUS_LESSON = 2

        const val PLUS_RULE = "RULE_PLUS"

        const val PART_WAY = 3
    }
}
