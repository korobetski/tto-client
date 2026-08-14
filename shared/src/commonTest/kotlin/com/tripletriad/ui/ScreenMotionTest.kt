package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That a transition says which way the player went.
 *
 * ### What a crossfade could not say
 *
 * Entering the shop and leaving it looked the same, so the animation carried no information — and
 * one that carries none is a delay with extra steps. These tests are about the *distinction*: they
 * do not describe what a slide looks like, which is a matter of taste, but that forward and back
 * are told apart, and that a sidestep is neither.
 *
 * ### Why the depth is derived and therefore worth testing
 *
 * [Screen.depth] walks the `up` relation the back button already uses, so a new screen names its
 * parent once and gets its motion for free. The risk in deriving it is a cycle — two screens each
 * other's parent — which would hang the walk rather than return a wrong answer.
 * [everyScreenReachesTheMenu] is the guard, and it is the reason `depth` can be a `while` loop.
 */
class ScreenMotionTest {

    /**
     * Every screen's `up` chain terminates, and at a root.
     *
     * `Screen.depth` is a `while (here.up != here)` loop, so a cycle anywhere in that relation is
     * not a wrong answer — it is a hang, in navigation, on a device. Walked here with a step limit
     * so the test fails instead.
     */
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

    /** A screen's parent is always shallower, which is what makes "deeper" mean anything. */
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

    /**
     * Going in and coming back out do not look the same.
     *
     * The whole point, asserted over **every** parent/child pair rather than one example — a rule
     * that holds for the shop and not for the lobby would be worse than no rule.
     */
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

    /**
     * A sidestep is a fade, and it is the *same* fade in both directions.
     *
     * Two screens at the same depth — the things hanging off the dashboard — have no in-and-out
     * relationship, and sliding between them would claim one. Asserting the symmetry is what says
     * "neither of these is further in".
     */
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

    /**
     * Reduced motion removes the movement, not the transition.
     *
     * The setting exists for people made unwell by movement, so a faster slide is not an answer —
     * it still slides. What is left is a fade, which is the same thing a sidestep gets, and this
     * asserts exactly that equivalence rather than describing a fade in the abstract.
     */
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
