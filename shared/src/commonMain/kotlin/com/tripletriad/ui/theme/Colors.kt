package com.tripletriad.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The game's Material scheme — **every** role, none left at Material's baseline.
 *
 * ### Why completeness is the point
 *
 * A `ColorScheme` is a contract with the component library: hand it thirty roles and every Material
 * control in the tree draws itself correctly, including the ones this app has not written yet. Hand
 * it eighteen and the remaining twelve are a trap that springs the day somebody uses a component
 * that reads one. That is not hypothetical here — it had already sprung twice before this rewrite:
 *
 * - **`Snackbar`** draws on `inverseSurface` / `inverseOnSurface`. Neither was set, so every
 *   confirmation in the app — bought a pack, saved a deck, deleted a profile — appeared as a
 *   **light lavender-grey box** on a dark screen.
 * - **`FilterChip`** draws its selected state on `secondaryContainer`. `OptionsScreen` overrode the
 *   colours at its call site and the chips in `CardListBody`, `PvpScreen` and `PvpTableScreen` did
 *   not, so the same control looked like two different controls depending on the screen.
 *
 * Both are fixed by filling the roles rather than by patching the call sites, which is the whole
 * argument for having a scheme.
 *
 * ### The tone each role takes
 *
 * Material's own dark mapping, applied without deviation: the accent families give
 * 80 / 20 / 30 / 90 to `x` / `onX` / `xContainer` / `onXContainer`, and the neutral ramp gives the
 * surfaces. Reading a role here should never require knowing what this app does — `Palette.kt` has
 * the one place a decision was made, which is *which family plays which part*.
 *
 * ### `surfaceVariant` is no longer the row surface
 *
 * It used to be, and that was the mistake underneath a lot of the drift. In Material 3
 * `surfaceVariant` is a **de-emphasis** role, several tones lighter than the surface; the thing a
 * card or a list row sits on is `surfaceContainer` and its high/low siblings. The row surface moves
 * to `surfaceContainerHigh`, which comes out at `#2D2926` — within one step of the `#2E2A26` the
 * rows were already drawn in. The screens keep their appearance and gain a role that explains it.
 */
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
 * `display/Card.as:29-31`. These are **card artwork** colours rather than theme colours — the AS3
 * keeps them in the display class and not in the theme — and they survive the refresh untouched,
 * because they are what the two players *are*. Re-deriving them from a ramp would have changed the
 * board to make the buttons tidier.
 */

/** `Card.GREY_COLOR`. */
internal val CardGrey = Color(0xFF5A595A)

/** `Card.BLUE_COLOR`. */
internal val CardBlue = Color(0xFF2D4660)

/** `Card.RED_COLOR`. */
internal val CardRed = Color(0xFF602D2D)

/**
 * `largeBlueElementFormat` / `largeRedElementFormat` — `BaseTTOTheme.as:1537-1544`.
 *
 * The *text* colours of the two sides, which is why they are so much brighter than the two card
 * quads above. The port draws them as the card's edge, which the AS3 does not have: its frame is
 * part of the per-card artwork.
 */
internal val CardBlueEdge = Color(0xFF43A7C8)
internal val CardRedEdge = Color(0xFFBB594F)

/*
 * The board's own three colours. They have no AS3 source — Feathers drew an empty tile with
 * `emptyTileSkin` out of the UI atlas, which this port does not import — so these are the port's
 * own. They are here rather than in `MatchBoard.kt` because a colour a screen keeps to itself is a
 * screen that will never follow a theme.
 */

/** An empty cell: cooler than the surfaces, so the grid reads as a board and not as a list. */
internal val BoardTile = Color(0xFF1E2230)

/** Its edge — [BoardTile] lightened until the 3x3 grid is legible on the background. */
internal val BoardTileOutline = Color(0xFF3A4152)

/**
 * The ring around the held card and around the cell being aimed at.
 *
 * Deliberately **not** equal to `primary`, even now that primary is the amber family: the ring
 * lands next to five orange element glyphs on an elemental board, and a selection marker that
 * matches the thing beside it marks nothing.
 */
internal val SelectionRing = Color(0xFFF2C14E)

/**
 * Game colours Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * A `ColorScheme` is thirty-odd named roles — primary, surface, error — and "the blue player's
 * card" is not one of them. Forcing them into `tertiary` and `tertiaryContainer` would make every
 * call site read as a lie about what it is drawing, so they travel beside the scheme instead.
 */
@Immutable
data class TtoColors(
    val cardBlue: Color = CardBlue,
    val cardRed: Color = CardRed,
    val cardGrey: Color = CardGrey,
    val cardBlueEdge: Color = CardBlueEdge,
    val cardRedEdge: Color = CardRedEdge,
    /**
     * A row that is the current choice: the fill and the outline.
     *
     * The **blue** family, because Material's secondary is the state family and this app now says
     * amber for actions and blue for state. Before the refresh both were the card blue, so a
     * primary button and a selected row were the same colour and neither read as either.
     */
    val selectedFill: Color = Blue20,
    val selectedOutline: Color = Blue80,
    /** The two boon markers and an opponent row's rules — a temporary effect. */
    val transient: Color = Amber80,
    /** An empty board cell, its edge, and the ring on whatever is currently being aimed. */
    val boardTile: Color = BoardTile,
    val boardTileOutline: Color = BoardTileOutline,
    val selectionRing: Color = SelectionRing,
    /** Behind everything, and darker than `surface` so a screen's content reads as a layer. */
    val backdrop: Color = Neutral4,
    /**
     * "This went **well**" — the counterpart of `error`, which Material has no role for.
     *
     * Material names a role for failure and none for success, so three screens had grown a private
     * green literal each: `ServersScreen`'s reachable-server marker, `MatchChrome`'s payout line,
     * and the two of `TalkBubble`. The first two are this pair; `TalkBubble`'s stay where they are,
     * being dark text on light artwork rather than a theme colour at all.
     */
    val positive: Color = Green80,
    val onPositive: Color = Green20,
    val positiveContainer: Color = Green30,
)

/**
 * [TtoColors] for the tree below [TripleTriadTheme].
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value never changes for the life
 * of the app — there is one theme and no light variant — so the reader-tracking a dynamic local
 * pays for would buy nothing.
 */
val LocalTtoColors = staticCompositionLocalOf { TtoColors() }
