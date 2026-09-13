package com.tripletriad.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The browser's own answer to the question the Android host reads from its animator scale.
 *
 * Read once per composition, like Android's: a player who changes the setting mid-match gets it on
 * the next screen rather than halfway through a capture animation.
 */
@Composable
actual fun rememberReducedMotion(): Boolean = remember { prefersReducedMotion() }

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")
