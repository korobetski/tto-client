package com.tripletriad.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TalkAnim`, which has no caller in this port yet.
 *
 * It is built ahead of one deliberately: `TutorialScreen` and `TutorialRematchPanel` are the two
 * Phase 4 screens listed as blocked on it, and the tutorial *is* a sequence of these over a
 * scripted match. So the component is the dependency, and its contract is fully specified by the
 * AS3 — a line, a speaker, five seconds, gone.
 *
 * Which makes these tests the whole of its verification: there is no screen to see it on, so the
 * behaviour a caller will depend on has to be stated here.
 */
@OptIn(ExperimentalTestApi::class)
class TalkBubbleTest {

    /** A line is spoken, and then it is over. */
    @Test
    fun aLineIsShownAndThenClears() = runComposeUiTest {
        var finished = false
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) { finished = true } }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
        assertTrue(isVisible(SPEAKER), "the line should say who is speaking")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { finished }
        assertFalse(isVisible(LINE), "the line outlived its bubble")
    }

    /**
     * The text arrives **after** the bubble, not with it.
     *
     * `predispose()` — the entry tween's `onComplete` — is what builds both `TextField`s, so a
     * line is never readable while its frame is still flying in at 1.5×. Easy to lose by drawing
     * the text unconditionally, and the loss is invisible in a screenshot.
     *
     * Driven off a **stopped clock**, because the window being asserted is the 0.4s entry and
     * `waitUntil` cannot be relied on to look inside it — it advances the frame clock in order to
     * settle, so by the time an ordinary assertion runs the entry may already be over. A test
     * that passes because it happened to look early is worse than none.
     */
    @Test
    fun theTextWaitsForTheBubbleToArrive() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) {} }

        mainClock.advanceTimeByFrame()
        assertTrue(exists(TALK_BUBBLE_TEST_TAG), "the bubble should be up from the first frame")
        assertFalse(isVisible(LINE), "the line was readable before its bubble had landed")

        mainClock.advanceTimeBy(SETTLED_MS)
        assertTrue(isVisible(LINE), "the line should be up once the bubble has landed")
    }

    /**
     * A second line replaces the first rather than being swallowed.
     *
     * The tutorial plays these back to back — `setTimeout(talk, 6100, 6)` against a 5.8s line —
     * so a bubble keyed on first composition alone would speak once and then go quiet for the
     * rest of the lesson.
     */
    @Test
    fun aSecondLineSpeaksInTurn() = runComposeUiTest {
        var line by mutableStateOf(LINE)
        setContent { TalkBubble(message = line, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }

        line = SECOND

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(SECOND) }
        assertFalse(isVisible(LINE), "both lines were up at once")
    }

    /**
     * Without the artwork the line is still readable.
     *
     * The frame is decoration around text that carries the meaning, so a missing texture must
     * cost the frame and not the sentence — which is the opposite of the rule captions, where the
     * picture *is* the sentence and a missing one is skipped entirely.
     */
    @Test
    fun theLineSurvivesAMissingFrame() = runComposeUiTest {
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
    }

    /**
     * The longest line the tutorial can speak, in the bubble, with nothing hanging out of it.
     *
     * The bubble was drawn at the AS3's authored 544x144 whatever it held, and the AS3 could
     * afford that: its stage is 1024 wide and desktop-only. Here the nine tutorial lines run to
     * 189 characters in French and wrap to seven lines in a frame that holds three, so the
     * sentence spilled out of the picture and onto the board behind it.
     *
     * Read out of the shipped bundles rather than pinned as a literal, so a line added or a
     * translation revised is measured too — which is the only version of this test that keeps
     * working.
     */
    @Test
    fun theLongestLineAnyLocaleCanSpeakFitsInsideTheFrame() = runComposeUiTest {
        val longest = longestTutorialLine()
        setContent { TalkBubble(message = longest, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(longest) }

        // Both read unmerged. The bubble is `clickable` — it announces itself as something a
        // screen reader can dismiss — and a clickable merges its descendants, so the frame and the
        // line are both absorbed into one node carrying the whole bubble's bounds. Measured
        // merged, this compares that node against itself and can never fail.
        val frame = onNodeWithTag(TALK_FRAME_TEST_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val line = onNode(hasText(longest, substring = true), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            line.top >= frame.top && line.bottom <= frame.bottom,
            "the line runs from ${line.top} to ${line.bottom}, " +
                "the frame from ${frame.top} to ${frame.bottom}",
        )
    }

    /** The widest of the nine lines across all four bundles, by rendered length. */
    private fun longestTutorialLine(): String = runBlocking {
        AppLocale.entries
            .map { loadStrings(it) }
            .flatMap { strings -> TUTORIAL_KEYS.map { strings[it] } }
            .maxBy { it.length }
    }

    private companion object {
        const val LINE = "Place a card on any free cell."
        const val SECOND = "Now capture one of mine."
        const val SPEAKER = "Triple Triad Master"

        /** Past the 0.4s entry and comfortably inside the 5s hold. */
        const val SETTLED_MS = 1_000L

        val TUTORIAL_KEYS = listOf(
            StringKeys.TUTORIAL_1, StringKeys.TUTORIAL_2, StringKeys.TUTORIAL_3,
            StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5, StringKeys.TUTORIAL_6,
            StringKeys.TUTORIAL_7, StringKeys.TUTORIAL_8, StringKeys.TUTORIAL_9,
        )
    }
}
