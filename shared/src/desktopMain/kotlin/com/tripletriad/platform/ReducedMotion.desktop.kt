package com.tripletriad.platform

import androidx.compose.runtime.Composable

/**
 * The desktop, which has nothing to ask.
 *
 * Neither the JVM nor the desktop environments Compose runs on publish a reduce-motion preference
 * it can read. Answering false is "this platform does not say", not "the player wants motion" — the
 * difference matters if this ever grows an in-app toggle, which is what would replace this line.
 */
@Composable
actual fun rememberReducedMotion(): Boolean = false
