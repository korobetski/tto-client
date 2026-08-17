package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorialLessonTest {

    @Test
    fun everyLineIsSpokenExactlyOnce() {
        val spoken = (0 until PLACEMENTS_PER_MATCH).flatMap { tutorialLines(it, CAPTURED) }

        assertEquals(EXPECTED_LINES, spoken.size, "the lesson should say all nine of its lines")
        assertEquals(spoken.size, spoken.toSet().size, "a line was scheduled twice")
    }

    @Test
    fun thePlayersOwnTurnsAreSpokenTo() {
        val onPlayerTurns = (0 until PLACEMENTS_PER_MATCH)
            .filter { it % 2 == 1 }
            .flatMap { tutorialLines(it, CAPTURED) }

        assertEquals(
            listOf(StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5, StringKeys.TUTORIAL_8),
            onPlayerTurns,
            "the three instructional lines belong on the player's first two turns",
        )
    }

    @Test
    fun theCaptureLineWaitsForACapture() {
        val without = tutorialLines(CAPTURE_PLACEMENT, blueScore = OPENING)
        val with = tutorialLines(CAPTURE_PLACEMENT, blueScore = OPENING + 1)

        assertTrue(
            StringKeys.TUTORIAL_6 !in without,
            "nothing was captured, so nothing changed colour",
        )
        assertEquals(listOf(StringKeys.TUTORIAL_6, StringKeys.TUTORIAL_7), with)
    }

    @Test
    fun theMiddleOfTheMatchIsQuiet() {
        for (placement in QUIET_FROM..QUIET_TO) {
            assertEquals(
                emptyList(),
                tutorialLines(placement, CAPTURED),
                "placement $placement should have no line",
            )
        }
    }

    private companion object {
        const val EXPECTED_LINES = 9

        const val OPENING = 5
        const val CAPTURED = OPENING + 1

        const val CAPTURE_PLACEMENT = 2

        const val QUIET_FROM = 4
        const val QUIET_TO = 7
    }
}
