package com.tripletriad.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The tonal ramps every Material role in [TtoColorScheme] is drawn from.
 *
 * ### What a ramp is, and why the app needs them rather than eight literals
 *
 * Material 3 does not ask for "a blue". It asks for a *family* of blues at fixed lightnesses — the
 * **tones** — and then names roles in terms of them: on a dark scheme `primary` is tone 80,
 * `onPrimary` is tone 20, `primaryContainer` is tone 30, `onPrimaryContainer` is tone 90. The
 * contrast between any role and its `on-` partner is therefore a property of the *system*, not
 * something each screen has to get right on its own.
 *
 * The scheme this replaced filled eighteen of the thirty-odd roles with single hand-picked values
 * and left the rest at `darkColorScheme()`'s baseline lavender. The visible consequence was a
 * `Snackbar` — which draws on `inverseSurface` — appearing as a **light grey box** on a dark game,
 * and the `FilterChip`s of three screens rendering their selected state in Material's purple. A
 * complete set of ramps is what makes "fill every role" a mechanical exercise instead of thirty
 * design decisions.
 *
 * ### How these numbers were derived
 *
 * Each family takes a **seed** colour, converts it to CIELAB, keeps the seed's hue angle, forces a
 * chosen chroma, and varies `L*` to the requested tone — tone *is* `L*`. Chroma is backed off in
 * half-unit steps where the result would fall outside sRGB. Reproducing a value needs only the hue,
 * the chroma and the tone, all three of which are written above each block.
 *
 * ### The seeds are the game's own colours
 *
 * Every family is seeded from a colour that was already in this app, so the refresh re-derives the
 * palette rather than replacing it. Two of the results are worth pointing out because they are
 * accidents that argue the seeds were right: [Neutral22] comes out at `#383430`, which is
 * `BaseTTOTheme.as`'s own `LIST_BACKGROUND_COLOR` to the byte, and [Neutral17] at `#2D2926` lands
 * one step off its `GROUPED_LIST_HEADER_BACKGROUND_COLOR` (`#2E2A26`). The AS3's warm greys were
 * already sitting on a tonal ramp seeded from themselves.
 *
 * ### Which family plays which part — and the one reversal from the old scheme
 *
 * - **Amber is `primary`.** The old scheme made the card blue primary and argued that the AS3's
 *   orange could not be, because `primary` drives every filled button and "painting them orange
 *   would read as everything is selected". That argument was about the raw `#FF9900` at full
 *   chroma. Inside a tonal system the accent arrives as tone 80 — a soft gold — and the role it
 *   used to conflict with is now filled by a different family entirely. What the reversal buys is
 *   worth more than what it cost: the game is **blue against red**, so a blue primary put every
 *   button in the same colour as one of the two players. Amber belongs to neither side.
 * - **Blue is `secondary`**, which on Material is the *state* family — chip selection, the
 *   navigation indicator, a row that is the current choice. So the app now says **amber for
 *   actions, blue for state**, and no colour means both.
 * - **Cyan is `tertiary`**, keeping the job `largeBlueElementFormat` already had here: the
 *   affirmative reading — a filled progress bar, an affordable price, a complete deck.
 * - **Red is `error`**, **Green is the app's own affirmative pair** — see [TtoColors.positive],
 *   which rehouses three greens that were private literals in as many screens.
 *
 * `docs/development/design-system.md` records this and the rest of the deviations from the AS3
 * transcription, which is what `CLAUDE.md`'s third convention asks for.
 */

/* Amber — hue 69°, chroma 55, seeded from `SELECTED_TEXT_COLOR` #FF9900. */

internal val Amber20 = Color(0xFF492900)
internal val Amber30 = Color(0xFF693C00)
internal val Amber40 = Color(0xFF8A5100)
internal val Amber80 = Color(0xFFFFB870)
internal val Amber90 = Color(0xFFFFDCBE)

/* Blue — hue 266°, chroma 40, seeded from `Card.BLUE_COLOR` #2D4660. */

internal val Blue20 = Color(0xFF023352)
internal val Blue30 = Color(0xFF014A76)
internal val Blue80 = Color(0xFF9DCAFF)
internal val Blue90 = Color(0xFFD1E4FF)

/* Cyan — hue 234°, chroma 38, seeded from `largeBlueElementFormat` #43A7C8. */

internal val Cyan20 = Color(0xFF023543)
internal val Cyan30 = Color(0xFF014D61)
internal val Cyan80 = Color(0xFF56D5FF)
internal val Cyan90 = Color(0xFFB8EAFF)

/* Red — hue 29°, chroma 60, seeded from the danger outline #E05252. */

internal val Red20 = Color(0xFF680012)
internal val Red30 = Color(0xFF91041E)
internal val Red80 = Color(0xFFFEB4AD)
internal val Red90 = Color(0xFFFEDAD7)

/* Green — hue 141°, chroma 40, seeded from `ServersScreen`'s healthy marker #5FA85F. */

internal val Green20 = Color(0xFF01390B)
internal val Green30 = Color(0xFF17521D)
internal val Green80 = Color(0xFF9AD596)
internal val Green90 = Color(0xFFB5F2B1)

/*
 * Neutral — hue 74°, chroma 3, seeded from `GROUPED_LIST_HEADER_BACKGROUND_COLOR` #2E2A26.
 *
 * A warm neutral rather than a grey one, which is the single decision that keeps this looking like
 * the same game. The five `surfaceContainer*` tones are what a card, a list row and a dialog sit on
 * in Material 3; `surfaceVariant` is **not** that role and using it as one is what made this app's
 * rows a shade the scheme could not explain.
 */

internal val Neutral0 = Color(0xFF000000)
internal val Neutral4 = Color(0xFF120D08)
internal val Neutral6 = Color(0xFF17130E)
internal val Neutral10 = Color(0xFF1E1B18)
internal val Neutral12 = Color(0xFF221F1C)
internal val Neutral17 = Color(0xFF2D2926)
internal val Neutral20 = Color(0xFF33302C)
internal val Neutral22 = Color(0xFF383430)
internal val Neutral24 = Color(0xFF3C3835)
internal val Neutral90 = Color(0xFFE7E2DD)

/* Neutral variant — the same hue at chroma 7, for outlines and the surfaces that carry them. */

internal val NeutralVar30 = Color(0xFF4E453C)
internal val NeutralVar60 = Color(0xFF998F85)
internal val NeutralVar80 = Color(0xFFD0C5BA)
