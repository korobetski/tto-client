package com.tripletriad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The theme every screen renders inside.
 *
 * Four pieces, each in its own file because each is a different kind of decision:
 * [TtoColorScheme] (which family plays which Material role — `Colors.kt`, drawn from the tonal
 * ramps in `Palette.kt`), [appTypography] (the type scale), [TtoShapes] (the corner radii), and
 * [TtoColors] (the game's own colours, which Material has no role for). `Motion.kt` sits beside
 * them and is read directly by the two files that animate.
 *
 * ### One theme and no light variant
 *
 * A decision rather than an omission: the original is a dark game with artwork drawn for a dark
 * ground, and a light scheme would need a second set of card frames before it was anything but
 * inverted text on white. What the refresh changed is that the dark scheme is now a *complete* one
 * — see `Colors.kt` for the two components that were visibly wrong while it was not.
 *
 * `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1's snippet had this as
 * `val AppTheme = MaterialTheme(...)`, which does not compile — `MaterialTheme` is a `@Composable`
 * function and cannot be assigned to a top-level `val`. The document records that correction
 * already; this is the shape it settles on.
 */
@Composable
fun TripleTriadTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTtoColors provides TtoColors()) {
        MaterialTheme(
            colorScheme = TtoColorScheme,
            typography = appTypography(),
            shapes = TtoShapes,
            content = content,
        )
    }
}
