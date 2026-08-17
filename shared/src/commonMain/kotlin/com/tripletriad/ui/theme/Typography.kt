package com.tripletriad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tripletriad.shared.generated.resources.Res
import tripletriad.shared.generated.resources.raleway_medium
import tripletriad.shared.generated.resources.raleway_regular

@Composable
internal fun rememberGameFontFamily(): FontFamily = FontFamily(
    Font(Res.font.raleway_regular, FontWeight.Normal),
    Font(Res.font.raleway_medium, FontWeight.Medium),
    Font(Res.font.raleway_medium, FontWeight.Bold),
)

@Composable
internal fun appTypography(): Typography {
    val game = rememberGameFontFamily()

    fun style(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight) = TextStyle(
        fontFamily = game,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        fontWeight = weight,
    )

    val regular = FontWeight.Normal
    val medium = FontWeight.Medium

    return Typography(
        displayLarge = style(57, 64, -0.25, regular),
        displayMedium = style(45, 52, 0.0, regular),
        displaySmall = style(36, 44, 0.0, regular),

        headlineLarge = style(32, 40, 0.0, regular),
        headlineMedium = style(28, 36, 0.0, regular),
        // A screen title, and the largest thing the app draws outside the splash.
        headlineSmall = style(24, 32, 0.0, medium),

        // The app bar's own title.
        titleLarge = style(22, 28, 0.0, regular),
        // The label on a `WideButton`, and a card's name in a detail pane.
        titleMedium = style(16, 24, 0.15, medium),
        // A list row's name.
        titleSmall = style(14, 20, 0.1, medium),

        // Body text, and the default every `Text` inherits.
        bodyLarge = style(16, 24, 0.5, regular),
        bodyMedium = style(14, 20, 0.25, regular),
        // A row's secondary line.
        bodySmall = style(12, 16, 0.4, regular),

        labelLarge = style(14, 20, 0.1, medium),
        // The `·`-joined metadata line.
        labelMedium = style(12, 16, 0.5, medium),
        // The smallest thing on screen: a rules strip, a stack count, a progress figure.
        labelSmall = style(11, 16, 0.5, medium),
    )
}
