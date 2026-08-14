package com.tripletriad.platform

import androidx.compose.runtime.Composable

/**
 * Whether the player has asked their device for less movement.
 *
 * ### Why a game with full-screen banners has to ask
 *
 * This app announces a match with captions that fill the screen, flips three cards for a coin toss,
 * and slides between fourteen destinations. For most people that is the game. For somebody with a
 * vestibular disorder it is a reason to stop playing — motion sickness from an interface is not a
 * preference, and the operating systems that offer this setting offer it for that reason.
 *
 * Honouring it costs one branch per animation. Not honouring it means the setting the player
 * already turned on, once, for every app on their device, is ignored by this one.
 *
 * ### Why it mirrors [rememberUrlOpener] exactly
 *
 * Same shape and for the same reason: Android needs a `Context` to read the setting and the other
 * two do not. A plain `expect fun` would force a global holding the application context, set by the
 * host and read from anywhere — which works right up until something reads it before `onCreate`.
 *
 * ### What each platform can actually answer
 *
 * Android and iOS both expose it. The desktop does not: neither the JVM nor the common desktop
 * environments publish a reduce-motion flag that Compose can read, so it answers false — an honest
 * "this platform does not say" rather than a guess. If the desktop build ever grows an in-app
 * settings toggle for it, this is the function that reads it.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
