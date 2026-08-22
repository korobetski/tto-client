package com.tripletriad.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val TtoColorScheme = darkColorScheme(
    primary = Amber80,
    onPrimary = Amber20,
    primaryContainer = Amber30,
    onPrimaryContainer = Amber90,
    inversePrimary = Amber40,

    secondary = Blue80,
    onSecondary = Blue20,
    secondaryContainer = Blue30,
    onSecondaryContainer = Blue90,

    tertiary = Cyan80,
    onTertiary = Cyan20,
    tertiaryContainer = Cyan30,
    onTertiaryContainer = Cyan90,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,

    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVar30,
    onSurfaceVariant = NeutralVar80,
    surfaceTint = Amber80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,

    surfaceDim = Neutral6,
    surfaceBright = Neutral24,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,

    outline = NeutralVar60,
    outlineVariant = NeutralVar30,
    scrim = Neutral0,
)

/*
 * These are **card artwork** colours rather than theme colours, and they survive the refresh
 * untouched, because they are what the two players *are*. Re-deriving them from a ramp would have
 * changed the
 * board to make the buttons tidier.
 */

internal val CardGrey = Color(0xFF5A595A)

internal val CardBlue = Color(0xFF2D4660)

internal val CardRed = Color(0xFF602D2D)

internal val CardBlueEdge = Color(0xFF43A7C8)
internal val CardRedEdge = Color(0xFFBB594F)

/*
 * The board's own three colours, chosen for this app. They are here rather than in
 * `MatchBoard.kt` because a colour a screen keeps to itself is a
 * screen that will never follow a theme.
 */

internal val BoardTile = Color(0xFF1E2230)

internal val BoardTileOutline = Color(0xFF3A4152)

internal val SelectionRing = Color(0xFFF2C14E)

@Immutable
data class TtoColors(
    val cardBlue: Color = CardBlue,
    val cardRed: Color = CardRed,
    val cardGrey: Color = CardGrey,
    val cardBlueEdge: Color = CardBlueEdge,
    val cardRedEdge: Color = CardRedEdge,
    val selectedFill: Color = Blue20,
    val selectedOutline: Color = Blue80,
    val transient: Color = Amber80,
    val boardTile: Color = BoardTile,
    val boardTileOutline: Color = BoardTileOutline,
    val selectionRing: Color = SelectionRing,
    val backdrop: Color = Neutral4,
    val positive: Color = Green80,
    val onPositive: Color = Green20,
    val positiveContainer: Color = Green30,
    val currency: Color = Amber80,
    val experience: Color = Blue80,
)

val LocalTtoColors = staticCompositionLocalOf { TtoColors() }
