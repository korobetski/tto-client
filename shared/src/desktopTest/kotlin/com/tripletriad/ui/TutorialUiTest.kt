package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.storage.InMemoryDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lesson — `TutorialScreen`, through the real app.
 * It is [MatchScreen] with a [MatchScript] on it, so what is worth asserting is exactly the five
 * things the script changes: the deal, who starts, how the opponent plays, what is said, and where
 * the end panel goes. The match underneath is already covered by [MatchUiTest] and is not re-tested
 * here.
 */
@OptIn(ExperimentalTestApi::class)
class TutorialUiTest {

    /** The course opens from the dashboard, onto a board with no deck to choose. */
    @Test
    fun theLessonOpensStraightOntoABoard() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openLessons()

        onNodeWithTag(lessonRowTestTag(0)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        assertFalse(
            exists(DECK_SELECT_CHOOSE_TEST_TAG),
            "the lesson deals a fixed hand its lines are written around; nothing is chosen",
        )
    }

    /**
     * The opponent moves first, and the player still holds five cards when it is their turn.
     *
     * `pof.rolls = [0,1,0]` (`TutorialScreen.as:64`) rigs the flip to red — 0 is red in
     * `PileOuFace` — so that the first thing the lesson does is demonstrate a placement rather than
     * ask for one. Read off the hands rather than off a turn tag, because "red has played" is the
     * claim and the turn has already passed back by the time it can be asserted.
     */
    @Test
    fun theTutorMovesFirst() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        awaitPlayer()
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player has not played yet")
        assertEquals(
            HAND_SIZE - 1,
            handSize(CardColor.RED),
            "the opponent should have opened the match",
        )
    }

    /**
     * The first line is spoken before the opponent moves.
     * The whole of the pacing in one assertion: the lines play after the pre-match captions, and
     * the opponent waits behind them — on [LessonSpeech.isSpeaking] now rather than on the line
     * count times 6.1 seconds, since a line can be tapped away. If the AI were not held back, the
     * lesson would be explaining a board that had already changed twice.
     */
    @Test
    fun theTutorSpeaksBeforePlaying() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
        assertTrue(
            isVisible(FIRST_LINE),
            "the opening line should be the one about the three-by-three grid",
        )
        assertEquals(HAND_SIZE, handSize(CardColor.RED), "the opponent spoke before it played")
    }

    /**
     * The opponent loses on purpose — `MatchAiOptions.TUTOR`.
     * Asserted as an outcome rather than as a move: what the lesson promises is a match the player
     * can win while being told what a card is, and the way to check that is to play it out badly
     * (first card, first free cell, every turn) and still come out ahead. A test that pinned the
     * exact cell would break on any tie-break change and would not be saying anything about the
     * lesson.
     */
    @Test
    fun theLessonIsWinnableByPlayingBadly() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()

        val (blue, red) = score()
        assertTrue(
            blue > red,
            "the tutor plays its worst move; the lesson should be won, was $blue-$red",
        )
    }

    /**
     * **A lesson pays nothing, and does not claim to.**
     *
     * The panel's payout line used to be unconditional — "every result pays here" was true while
     * every match counted — so an uncounted lesson ended on `+0 MGP` in the affirmative colour: a
     * reward announced, in the place rewards are announced, for a match deliberately paying none.
     * Asserted on the tag rather than on the text, so it does not depend on the wording or the
     * language.
     */
    @Test
    fun theLessonAnnouncesNoPayout() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isFinished() }
        assertFalse(exists(MATCH_PAYOUT_TEST_TAG), "a lesson pays nothing and should say nothing")
        assertFalse(exists(MATCH_REWARDS_TEST_TAG), "and drops nothing")
    }

    /**
     * **Finishing a lesson leaves the profile exactly as it found it.**
     *
     * The end-to-end half of what `LessonRecordTest` asserts on the extensions: no result, no
     * counters, no money. `startedMatches` is the one worth reading twice — [MatchScreen] persists
     * it when the screen *opens*, so a lesson that counted would already have written it before a
     * card was played, and `forfeits` would carry the difference for the rest of the character's
     * life.
     */
    @Test
    fun theLessonLeavesTheProfileUntouched() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLesson()
        val before = storedSave(documents)

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isFinished() }

        val after = storedSave(documents)
        assertEquals(before.startedMatches, after.startedMatches, "no match was started")
        assertEquals(before.endedMatches, after.endedMatches, "and none was ended")
        assertEquals(0, after.forfeits, "so no forfeit is left behind")
        assertEquals(0, after.stats.played, "no win, defeat or draw goes on the record")
        assertEquals(before.mgp, after.mgp, "and the lesson pays nothing")
    }

    /**
     * The end panel leads on to the next lesson, where a match offers a rematch.
     *
     * `TutorialRematchPanel.rematchFooter` overrides the footer with Help and Quit (`:19-33`), and
     * `nextLesson` dispatches `NEXT_SCREEN`, which `TutorialScreen.endGame` sets to `HELP_SCREEN`
     * on all three results — so in the original this control ended the course, because the course
     * was one match. It now advances through [TUTORIAL_PUZZLES] and only the last of them leads to
     * the rule book; see [theCourseEndsAtTheRuleBook]. Replacing the control rather than adding one
     * is unchanged, and is still what keeps a lesson from being a repeatable source of MGP.
     */
    @Test
    fun theEndOfTheFirstLessonLeadsToTheNext() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()

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

    /**
     * The last lesson is the one that leads to the rule book — the original's own ending, moved.
     *
     * Played by walking the course rather than by jumping to the end: what is being asserted is
     * that the sequence *terminates*, and a test that opened the last lesson directly could not
     * tell the difference between a course of four and a course that loops.
     */
    @Test
    fun theCourseEndsAtTheRuleBook() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()
        repeat(LAST_LESSON) {
            onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }
            playOut()
        }

        assertTrue(isVisible("Help"), "the course should end at the rule book")
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(HELP_LIST_TEST_TAG) }
    }

    /** Creates a character and opens one lesson of the course, waiting for its board. */
    private fun ComposeUiTest.openLesson(lesson: Int = 0) {
        newCharacter()
        openLessons()
        onNodeWithTag(lessonRowTestTag(lesson)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
    }

    private companion object {
        /** `APP_TUTORIAL_1`, first clause — enough to identify, short enough to survive a wrap. */
        const val FIRST_LINE = "Triple Triad is played by placing cards"
    }
}
