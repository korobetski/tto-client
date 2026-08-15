package com.tripletriad.ui

import androidx.compose.ui.unit.dp

/**
 * The spacing grid, the sizes and the alphas every screen draws with.
 *
 * ### Why a grid, given the screens already had spacing
 *
 * They had *values*, not a grid. Counting the `padding`, `spacedBy`, `height` and `size` literals
 * across `ui/` before this file existed gave: 8 dp sixty-six times, 6 dp twenty-six, 4 dp
 * twenty-five, 12 dp twenty-two, 10 dp nineteen, 2 dp fourteen, 24 dp and 16 dp eight each, 3 dp
 * seven, 14 dp four, 1 dp four, then a 5 dp, a 22 dp and a 32 dp on their own. Half of those sit on
 * no grid at all, and the ones that do not are not off it *for a reason* — they are what one
 * screen's author reached for on one afternoon.
 *
 * The cost is not tidiness. It is that **6 and 8 dp look the same, and 10 and 12 dp look the
 * same**, so a difference meant to say something says nothing, and no screen can be adjusted
 * without guessing which of its numbers were deliberate. Material's answer is a 4 dp baseline, and
 * so is this one.
 *
 * Where a number does *not* fit — the card geometry in `MatchBoard`, the 88x118 the artwork was
 * authored at — the screen keeps its own value and says why. That is a measurement, not a spacing
 * decision.
 *
 * ### Why here and not in `ui/theme/`
 *
 * Because `ui/theme/` holds what is handed to `MaterialTheme` — the scheme, the type scale, the
 * shapes — and a screen never names those directly; it reads them back off `MaterialTheme`. These
 * are named at the call site on every screen, and living in the same package as the screens is what
 * keeps them free of an import line in forty files. [ContentMaxWidth] and the four alphas were
 * already here, in `Controls.kt`, for exactly that reason; this file is where they were always
 * going.
 */

/** Between a glyph and the word beside it; inside a chip. */
internal val SpaceXs = 4.dp

/** The default gap between two things in the same group. */
internal val SpaceSm = 8.dp

/** Inside a row or a card, between its edge and its content. */
internal val SpaceMd = 12.dp

/** Between two groups; a screen's horizontal margin. */
internal val SpaceLg = 16.dp

/** Around a thing that stands alone — an empty-state note, a section break. */
internal val SpaceXl = 24.dp

/** The top of a screen whose content should not start against the bar above it. */
internal val SpaceXxl = 32.dp

/**
 * Material 3's minimum touch target, and Android's own accessibility guidance.
 *
 * Material's own components reserve this whether or not they *look* it. `Modifier.clickable` does
 * not, and this app has some twenty-eight rows built that way — see `TouchTargetTest`, which turned
 * the arithmetic into a measurement after a `TextButton` was found sitting at 40 dp.
 */
internal val MinTouchTarget = 48.dp

/** A glyph inside a line of text, at about the size the text is. */
internal val IconSm = 18.dp

/** A glyph that labels a row or a card. */
internal val IconMd = 24.dp

/** A glyph that *is* the content — an empty state, a large affordance. */
internal val IconLg = 32.dp

/** A one-dp hairline: the border of an outlined surface. */
internal val HairlineWidth = 1.dp

/** Keeps every list screen the same width on a desktop window that is far wider than a phone. */
internal val ContentMaxWidth = 520.dp

/**
 * What a screen gets instead when it lays out two panes and the window is [LocalWideLayout].
 *
 * Only the screens that opt in take it. A list does not become more readable at 900 dp — it becomes
 * a row of text with a hand's width of nothing in the middle — so widening every screen because the
 * window allows it would be spending the space rather than using it.
 */
internal val WideContentMaxWidth = 920.dp

/** The `·`-joined metadata line used by the profile and opponent rows. */
internal const val DOT_SEPARATOR = "  ·  "

/*
 * The alphas this app dims text by. Four steps, named once: a screen with six shades of white is a
 * screen where each was picked separately.
 */

/** A secondary line under a row's name. */
internal const val SUBDUED = 0.75f

/** An explanatory line, and an empty-state note. */
internal const val MUTED = 0.7f

/** Metadata that should recede: a count, a rarity, a description. */
internal const val FAINT = 0.6f

/** A disabled control's own label. */
internal const val DISABLED = 0.4f
