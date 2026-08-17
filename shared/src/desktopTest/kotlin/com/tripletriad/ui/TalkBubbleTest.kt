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

@OptIn(ExperimentalTestApi::class)
class TalkBubbleTest {

    @Test
    fun aLineIsShownAndThenClears() = runComposeUiTest {
        var finished = false
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) { finished = true } }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
        assertTrue(isVisible(SPEAKER), "the line should say who is speaking")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { finished }
        assertFalse(isVisible(LINE), "the line outlived its bubble")
    }

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

    @Test
    fun aSecondLineSpeaksInTurn() = runComposeUiTest {
        var line by mutableStateOf(LINE)
        setContent { TalkBubble(message = line, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }

        line = SECOND

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(SECOND) }
        assertFalse(isVisible(LINE), "both lines were up at once")
    }

    @Test
    fun theBubbleNeedsNoArtwork() = runComposeUiTest {
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
    }

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

        const val SETTLED_MS = 1_000L

        val TUTORIAL_KEYS = listOf(
            StringKeys.TUTORIAL_1, StringKeys.TUTORIAL_2, StringKeys.TUTORIAL_3,
            StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5, StringKeys.TUTORIAL_6,
            StringKeys.TUTORIAL_7, StringKeys.TUTORIAL_8, StringKeys.TUTORIAL_9,
        )
    }
}
