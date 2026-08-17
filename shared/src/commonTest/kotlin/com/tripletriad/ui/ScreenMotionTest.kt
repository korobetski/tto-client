package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScreenMotionTest {

    @Test
    fun everyScreenReachesTheMenu() {
        for (screen in Screen.entries) {
            var here = screen
            var steps = 0
            while (here.up != here && steps <= Screen.entries.size) {
                here = here.up
                steps++
            }
            assertTrue(here.up == here, "$screen's parent chain does not terminate")
            assertEquals(steps, screen.depth, "$screen's depth disagrees with its own chain")
        }
    }

    @Test
    fun aParentIsAlwaysShallowerThanItsChild() {
        for (screen in Screen.entries) {
            if (screen.up == screen) continue
            assertTrue(
                screen.up.depth < screen.depth,
                "${screen.up} is not shallower than $screen",
            )
        }
    }

    @Test
    fun forwardAndBackAreDifferent() {
        for (screen in Screen.entries) {
            if (screen.up == screen) continue
            val into = transitionFor(screen.up, screen, reduced = false)
            val outOf = transitionFor(screen, screen.up, reduced = false)

            assertNotEquals(
                into.targetContentEnter,
                outOf.targetContentEnter,
                "entering and leaving $screen animate identically",
            )
        }
    }

    @Test
    fun aSidestepLooksTheSameBothWays() {
        val siblings = Screen.entries
            .groupBy { it.depth }
            .values
            .first { it.size >= 2 }

        val (first, second) = siblings
        val there = transitionFor(first, second, reduced = false)
        val back = transitionFor(second, first, reduced = false)

        assertEquals(there.targetContentEnter, back.targetContentEnter)
    }

    @Test
    fun reducedMotionRemovesTheMovement() {
        val siblings = Screen.entries.groupBy { it.depth }.values.first { it.size >= 2 }
        val plainFade = transitionFor(siblings[0], siblings[1], reduced = false)

        for (screen in Screen.entries) {
            if (screen.up == screen) continue
            val reduced = transitionFor(screen.up, screen, reduced = true)

            assertEquals(
                plainFade.targetContentEnter,
                reduced.targetContentEnter,
                "entering $screen still moves with reduced motion on",
            )
        }
    }
}
