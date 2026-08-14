package com.tripletriad.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS: Settings → Accessibility → Motion → Reduce Motion.
 *
 * Read on each composition rather than observed. The setting changes when the player leaves the app
 * to change it, and comes back to a fresh composition — so a notification observer would buy the
 * ability to react to something that cannot happen while this is on screen.
 */
@Composable
actual fun rememberReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
