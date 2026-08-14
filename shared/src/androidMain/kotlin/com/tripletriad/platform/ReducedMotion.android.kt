package com.tripletriad.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android: Settings → Accessibility → Remove animations, which zeroes the animator duration scale.
 *
 * ### Why the animator scale and not a dedicated flag
 *
 * Because that is what the toggle actually sets, and what the platform's own animations obey. A
 * scale of zero means "run animations instantly", which is exactly the question being asked. There
 * is no separate reduce-motion boolean on Android to prefer over it.
 *
 * Defaults to **animating** when the setting cannot be read: a missing value means an unusual
 * system image rather than a stated preference, and guessing "reduce" there would take the game's
 * motion away from everybody on that device without being asked.
 */
@Composable
actual fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
