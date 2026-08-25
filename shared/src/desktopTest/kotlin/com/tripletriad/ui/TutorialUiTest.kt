package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TutorialUiTest {
    @Test
    fun theLessonOpensStraightOntoABoard() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()

        onNodeWithTag(lessonRowTestTag(0)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        assertFalse(
            exists(DECK_SELECT_CHOOSE_TEST_TAG),
            "the lesson deals a fixed hand its lines are written around; nothing is chosen",
        )
    }

    @Test
    fun theTutorMovesFirst() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        awaitPlayer(TUTORIAL_TIMEOUT_MS)
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player has not played yet")
        assertEquals(
            HAND_SIZE - 1,
            handSize(CardColor.RED),
            "the opponent should have opened the match",
        )
    }

    @Test
    fun theTutorSpeaksBeforePlaying() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
        assertTrue(
            isVisible(FIRST_LINE),
            "the opening line should be the one about the three-by-three grid",
        )
        assertEquals(HAND_SIZE, handSize(CardColor.RED), "the opponent spoke before it played")
    }

    @Test
    fun theLessonIsWinnableByPlayingBadly() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut(TUTORIAL_TIMEOUT_MS)

        val (blue, red) = score()
        assertTrue(
            blue > red,
            "the tutor plays its worst move; the lesson should be won, was $blue-$red",
        )
    }

    @Test
    fun theLessonAnnouncesNoPayout() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut(TUTORIAL_TIMEOUT_MS)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isFinished() }
        assertFalse(exists(MATCH_PAYOUT_TEST_TAG), "a lesson pays nothing and should say nothing")
        assertFalse(exists(MATCH_REWARDS_TEST_TAG), "and drops nothing")
    }

    @Test
    fun theLessonLeavesTheProfileUntouched() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLesson()
        val before = storedSave(documents)

        playOut(TUTORIAL_TIMEOUT_MS)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isFinished() }

        val after = storedSave(documents)
        assertEquals(before.startedMatches, after.startedMatches, "no match was started")
        assertEquals(before.endedMatches, after.endedMatches, "and none was ended")
        assertEquals(0, after.forfeits, "so no forfeit is left behind")
        assertEquals(0, after.stats.played, "no win, defeat or draw goes on the record")
        assertEquals(before.mgp, after.mgp, "and the lesson pays nothing")
    }

    @Test
    fun theEndOfTheFirstLessonLeadsToTheNext() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut(TUTORIAL_TIMEOUT_MS)

        assertTrue(isVisible("Next lesson"), "the first action should lead on, not rematch")
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()

        // The panel going away is what says the next lesson has opened — the board tag is up
        // throughout, so waiting on it would wait for something that never stopped being true.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }

        // The Same lesson: a board already eight cards deep, with one card left to place.
        assertEquals(1, handSize(CardColor.BLUE), "one card, and one cell for it")
        assertEquals(
            0,
            handSize(CardColor.RED),
            "the opponent has nothing left to play; the lesson ends on the player's move",
        )
    }

    @Test
    fun aLessonEndsByNamingItselfRatherThanClaimingAVictory() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut(TUTORIAL_TIMEOUT_MS)

        assertTrue(isVisible("Lesson complete"), "the panel should name what was finished")
        assertFalse(isVisible("You win !"), "a lesson that cannot be lost should not claim a win")
    }

    @Test
    fun aLessonClosesOnASentenceAndNotOnItsKey() = runComposeUiTest {
        val strings = runBlocking { loadStrings(AppLocale.EN_US) }
        val closings = listOf(
            StringKeys.LESSON_BASICS_WIN,
            StringKeys.LESSON_BASICS_LOSE,
            StringKeys.LESSON_BASICS_DRAW,
        ).map { strings[it] }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()
        playOut(TUTORIAL_TIMEOUT_MS)

        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { closings.any { isVisible(it) } }
        assertFalse(isVisible("APP_LESSON"), "the tutor read out a string key")
    }

    /**
     * The one lesson test that runs at the pace the game ships at, and it has to.
     *
     * `TalkBubble` is `clickable` — that is how a line is advanced early — so wherever it overlaps
     * the board it takes the tap the cell underneath wanted. The bubble's own note says this test
     * is what caught that, and it still is: run at [TEST_PACING] it fails with "no cell accepted a
     * card", because a hurried line is still up when the player is given the turn. That is the
     * overlap, reproduced. Speeding this one up would mean deleting the only check on it.
     *
     * The other lesson tests are not about the bubble and take the fast pace.
     */
    @Test
    fun theCourseEndsAtTheRuleBook() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut(TUTORIAL_TIMEOUT_MS)
        repeat(LAST_LESSON) {
            onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }
            playOut(TUTORIAL_TIMEOUT_MS)
        }

        assertTrue(isVisible("To the rule book"), "the course should end at the rule book")
        // **The exam keeps the real result.** It is the one lesson that can be lost, against an
        // opponent playing to win, and being told plainly which of the two happened is what the
        // course ends on. `MatchAiOptions.TUTOR` is off here, so which line it is cannot be
        // predicted — that it is one of the three, and not the lesson wording, is the claim.
        assertFalse(isVisible("Lesson complete"), "the exam is a test, and reports its result")
        assertTrue(
            isVisible("You win") || isVisible("You lose") || isVisible("Draw"),
            "the exam should say how it actually went",
        )
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(HELP_LIST_TEST_TAG) }
    }

    private fun ComposeUiTest.openLesson(lesson: Int = 0) {
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(lesson)).performClick()
        waitUntil(timeoutMillis = TUTORIAL_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
    }

    private companion object {
        const val FIRST_LINE = "Triple Triad is played by placing cards"
    }
}
