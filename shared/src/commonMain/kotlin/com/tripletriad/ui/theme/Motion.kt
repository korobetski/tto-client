package com.tripletriad.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Material 3's motion tokens — the durations and the easing curves, in one place.
 *
 * ### What was wrong with `tween(260)`
 *
 * Nothing, once. The trouble is that `ScreenMotion` had a 260 and a 220, `MatchAnimations` had its
 * own set, and each was defended by a comment saying it was "short enough not to be a wait, long
 * enough to be seen" — the same sentence arriving at different numbers, which is what a house style
 * looks like before it is written down. Two animations that should feel like one system cannot be
 * checked against each other when neither knows what the other chose.
 *
 * ### The curves, and why `LinearOutSlowIn` is not among them
 *
 * Material 3 replaced Material 2's easing set. [Emphasized] is the one to reach for by default: it
 * accelerates harder and settles softer than anything in the old set, which is what makes a
 * transition read as a *movement* rather than as a slide at constant speed. [Standard] is for small
 * changes that should not draw the eye. The two one-way variants exist because an element that only
 * enters should not spend time easing out of a position it never had.
 *
 * The values are the published Material 3 curves, not this port's approximations.
 *
 * ### Reduced motion is handled elsewhere, and on purpose
 *
 * Nothing here has a "reduced" variant, because reduced motion is **not a shorter animation** — see
 * `ScreenMotion`, which drops the travel entirely rather than speeding it up. A token set that
 * offered a fast duration would invite exactly the wrong fix.
 */

/** The default: emphasized deceleration both ways, for anything the player should notice. */
internal val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Entering only — no time spent easing out of a position the element never occupied. */
internal val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Leaving only. */
internal val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** For a change that should happen without being watched: a colour, a small fade. */
internal val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * A state change with no travel — a colour, an alpha, a selection ring appearing.
 *
 * Material's `short4`. Long enough not to be a jump, short enough that a player tapping through a
 * list never waits on it.
 */
internal const val DURATION_SHORT = 200

/**
 * The default for something that moves across the screen.
 *
 * Material's `medium2`. `ScreenMotion`'s slide was 260 and is now this; the reasoning it recorded —
 * a slide needs a little longer than a fade to read as travel rather than as a jump — is why the
 * medium step and not the short one.
 */
internal const val DURATION_MEDIUM = 300

/**
 * A whole screen, or something that crosses most of one.
 *
 * Material's `long2`. The match's own set pieces — a card flipping a line of three, the coin toss —
 * are the things that take it.
 */
internal const val DURATION_LONG = 500
