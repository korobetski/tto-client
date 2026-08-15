# The design system

What the app's Material 3 tokens are, and where they deliberately stop being a transcription of the
AS3 original.

`CLAUDE.md`'s third convention asks that a port cite its AS3 source and, where it differs, record
the deviation *and* the reason. The visual refresh is one large deviation, so it is recorded here
once rather than repeated in a note on every screen. Each section below names what the original did,
what this port does now, and why.

**Status:** the tokens (below) are in place. The screens are being moved onto them in batches; the
sections marked *in progress* describe the target, not what every screen does today.

---

## 1. Colour

### What the AS3 had

`theme/BaseTTOTheme.as:124-137` declares about a dozen `uint` constants — a background, three greys,
two text colours, an orange accent, an overlay — and `display/Card.as:29-31` declares three more for
the cards. There is no scale, no tonal relationship between any two of them, and no notion of a
"role": each is used wherever its author thought it looked right.

### What the port had until the refresh

Those constants, transcribed, poured into eighteen of Material's thirty-odd `ColorScheme` roles.
Twelve roles were never filled and kept `darkColorScheme()`'s baseline lavender. Two of the twelve
were visible in the shipped app:

- `Snackbar` draws on `inverseSurface` / `inverseOnSurface`, so **every confirmation in the game —
  bought a pack, saved a deck, deleted a profile — rendered as a light lavender-grey box** on a dark
  screen.
- `FilterChip` draws its selected state on `secondaryContainer`. `OptionsScreen` overrode the
  colours at its call site; the chips in `CardListBody`, `PvpScreen` and `PvpTableScreen` did not.
  The same control looked like two different controls depending on the screen.

### What it is now

Six **tonal ramps** in `ui/theme/Palette.kt`, and a `ColorScheme` in `ui/theme/Colors.kt` with every
role filled from them.

A ramp is derived by converting a seed colour to CIELAB, keeping its hue, forcing a chosen chroma,
and varying `L*` to the requested Material tone — tone *is* `L*`. Chroma backs off where the result
would leave sRGB. Hue, chroma and tone are written above each block, so any value can be
re-derived.

| Family | Seed | Plays |
|---|---|---|
| Amber | `SELECTED_TEXT_COLOR` `#FF9900` | `primary` — actions |
| Blue | `Card.BLUE_COLOR` `#2D4660` | `secondary` — state |
| Cyan | `largeBlueElementFormat` `#43A7C8` | `tertiary` — affirmative readings |
| Red | the danger outline `#E05252` | `error` |
| Green | `ServersScreen`'s healthy marker `#5FA85F` | `TtoColors.positive` |
| Neutral / NeutralVariant | `GROUPED_LIST_HEADER_BACKGROUND_COLOR` `#2E2A26` | surfaces and outlines |

Every seed is a colour that was already in this app, so the refresh **re-derives** the palette
rather than replacing it. Two results argue the seeds were right: `Neutral22` comes out at `#383430`
— `BaseTTOTheme.as`'s own `LIST_BACKGROUND_COLOR`, to the byte — and `Neutral17` at `#2D2926`, one
step off its `GROUPED_LIST_HEADER_BACKGROUND_COLOR`. The AS3's warm greys were already sitting on a
tonal ramp seeded from themselves.

### The one reversal: amber is `primary`, blue is `secondary`

The old scheme made the card blue `primary` and argued the orange could not be, because `primary`
drives every filled button and "painting them orange would read as everything is selected". That
argument was about the raw `#FF9900` at full chroma. Inside a tonal system the accent arrives as
tone 80 — a soft gold — and the role it conflicted with is filled by a different family entirely.

What the reversal buys is worth more than what it cost: the game is **blue against red**, so a blue
`primary` put every button in the same colour as one of the two players. Amber belongs to neither
side. And since Material's `secondary` is the *state* family — chip selection, the navigation
indicator, a row that is the current choice — the app now says **amber for actions, blue for
state**, with no colour meaning both.

### `surfaceVariant` is not the row surface

It used to be, and that was the mistake underneath a lot of the drift. In Material 3
`surfaceVariant` is a **de-emphasis** role several tones lighter than the surface; the thing a card
or a list row sits on is `surfaceContainer` and its high/low siblings.

This is not only a naming question. `ContrastTest` measures it: `FAINT` text on `surfaceVariant` is
**3.77:1**, under WCAG AA, and `MUTED` is 4.51 — clear by a hundredth. On `surfaceContainerHigh`,
which is where the row surface moved and which comes out at `#2D2926`, the same `FAINT` text is
5.04. The screens keep the appearance they had and gain a role that explains it.

*In progress:* the screens are still being moved off `surfaceVariant`.

### Colours that stayed

`Card.BLUE_COLOR`, `Card.RED_COLOR`, `Card.GREY_COLOR` and the two edge colours are **untouched**.
They are what the two players *are*, not what the app's chrome is, and re-deriving them from a ramp
would have changed the board to make the buttons tidier. Same for the board's three own colours,
which have no AS3 source at all. They live in `TtoColors`, beside the scheme, because Material has
no role that means "the blue player's card".

`TalkBubble` keeps its two dark literals: they are text on light artwork, not theme colours.

### What is checked, and where

- `ContrastTest` (`commonTest`) — arithmetic over the palette. Dimmed text on every ground it is
  drawn on, every `x`/`onX` pair, the accent as text, the affirmative pair, and the five-step
  container ramp. AA is 4.5:1; the tightest case asserted is `FAINT` on `surfaceContainerHighest`
  at 4.55.
- `ThemeTest` (`desktopTest`) — what a composition actually receives. That **no role is left at
  Material's baseline** (`scrim` exempt: black is the right answer and Material's default is also
  black), that the accent families play their declared parts, and the type scale.

---

## 2. Type

`BaseTTOTheme.as:669-672` declares four sizes — 18, 24, 28, 36 — multiplied by the device's DPI over
326. They are **pixels at 326 DPI**, so at 160 dp-per-inch they land near 9, 12, 14 and 18 dp.

The port re-anchored that ladder once, to 11 / 12 / 13 / 14 / 15 / 16 / 18 sp, preserving its shape.
The refresh re-anchors it again, to **Material 3's published scale**, because the first anchoring
never stopped being the AS3's: seven steps inside eight points, so a screen title was two points
larger than the body under it and the whole app read as one dense weight of grey. The gap between 11
and 18 sp is not a hierarchy a player can see.

Line height and letter spacing were **absent entirely** before this — every style set a size and
left the metrics unspecified, which is why the denser screens ran their lines together. Both are now
Material's own.

Colour is still not set in any `TextStyle`: the scheme decides what colour text is, and two things
claiming to decide it is how a palette stops being followed.

The face is unchanged — Raleway, in the two weights `BaseTTOTheme.as:115-116` embeds, with the
original's "bold" being a medium weight. **Not Eurostile**, which appears once in the AS3 source and
draws a field this port does not render.

`TextScalingTest` renders the densest screen at 200% and is the fence the larger scale had to come
past.

---

## 3. Shape

The AS3 rounds nothing itself: every rounded edge is a nine-slice texture out of a UI atlas this
port does not import. So there was nothing to transcribe, and the values that stood — 4 / 6 / 8 dp,
with `large` and `extraLarge` left at Material's defaults — were the port's own, chosen to match
what the screens already drew.

Three barely distinguishable radii is not a scale; it is one radius with rounding error, and it was
most of what made the app look like a 2013 tablet game. `ui/theme/Shapes.kt` now carries Material's
five steps: 4 / 8 / 12 / 16 / 28 dp. Filling `large` and `extraLarge` matters for the same reason
filling every colour role does — `ModalBottomSheet`, `AlertDialog` and the large button variants all
reach for them.

---

## 4. Spacing

Counting the `padding`, `spacedBy`, `height` and `size` literals across `ui/` before `ui/Dimens.kt`
existed gave 8 dp sixty-six times, 6 dp twenty-six, 4 dp twenty-five, 12 dp twenty-two, 10 dp
nineteen, 2 dp fourteen, then a long tail of 3, 14, 1, 5, 22 and 32.

Half of those sit on no grid, and not for a reason. The cost is not tidiness: **6 and 8 dp look the
same, and 10 and 12 dp look the same**, so a difference meant to say something says nothing, and no
screen can be adjusted without guessing which of its numbers were deliberate.

`ui/Dimens.kt` names a 4 dp baseline — 4 / 8 / 12 / 16 / 24 / 32 — plus the touch minimum, three
icon sizes, the two content widths and the four dimming alphas. It sits in `com.tripletriad.ui`
rather than in `ui/theme/` because screens name these at the call site, and living in the screens'
own package is what keeps them free of an import line in forty files. `ui/theme/` holds what is
handed to `MaterialTheme` and read back off it.

Where a number does not fit — the card geometry in `MatchBoard`, the 88x118 the artwork was authored
at — the screen keeps its own value and says why. That is a measurement, not a spacing decision.

---

## 5. Motion

`ScreenMotion` had a 260 ms and a 220 ms, `MatchAnimations` had its own set, and each was defended
by a comment saying it was "short enough not to be a wait, long enough to be seen" — the same
sentence arriving at different numbers, which is what a house style looks like before it is written
down.

`ui/theme/Motion.kt` carries Material 3's published easing curves and three durations (200 / 300 /
500 ms). The screen transition is now asymmetric: the arriving screen decelerates into place and the
leaving one accelerates away, which is what makes the pair read as one movement rather than as two
things sliding past each other.

Nothing has a "reduced" variant, deliberately. Reduced motion is **not a shorter animation** —
`ScreenMotion` drops the travel entirely rather than speeding it up, because the setting exists for
people made unwell by movement. A token set offering a fast duration would invite exactly the wrong
fix.

---

## 6. Components

The shared controls live in `ui/Controls.kt`. The rule: **a pattern drawn on more than two screens
becomes one of them.** What follows is what that rule caught.

### `ttoClickable` — the one that mattered most

There were twenty-two bare `Modifier.clickable` call sites in `ui/` and **not one `Role`,
`selected`, `toggleable` or `stateDescription` in the whole package**. A screen reader met every
list row in this app as an unlabelled node with no role and no state: it could not say a row was a
button, and on the screens where one row is the current choice — a deck, a server, a locale — it
could not say which. That is not a rough edge; it is the app being unusable without sight.

`Modifier.ttoClickable` carries the three things those rows always want together:

- **The touch target.** `minimumInteractiveComponentSize()` grows the tappable area to 48 dp without
  touching the layout. Material's own components do this; `clickable` does not, which is what
  `TouchTargetTest` measured when it found a `TextButton` sitting at 40 dp.
- **The semantics.** A role, and where the caller has one, a selected state. `selected` is
  *nullable*: "chosen", "not chosen" and "not the kind of thing that gets chosen" are three
  different announcements, and a boolean could only make two of them.
- **The focus ring.** The desktop build is driven by keyboard as well as by mouse and had no visible
  focus anywhere — tabbing through a screen moved an invisible cursor.

It also plays the click sound, for the reason `WideButton` already gave about its own: `TouchLabel.as:31`
played it on any tap on a control, so it belongs to the control rather than to each caller.
Opt out with `sound = null` where the control has a voice of its own — a board cell plays a card
being placed, and a UI click underneath it is one sound too many.

*In progress:* the rows are being moved onto it batch by batch.

### `TtoCard` — one container, and there were five

"Rounded surface, `surfaceVariant` fill, one-dp outline" was written out by hand in five places —
`rowSurface`, the settings group, the menu's resume card, the match's rule chip and its outcome
panel — and the five had already begun to disagree about their radius. Material ships the pattern as
`OutlinedCard`; what was being hand-rolled was a card with the parts that make it a card left off.

### `TtoFilterChip` — one chip, and there were three

`CardListBody` drew a `Text` on a `rowSurface` and called it a chip. `PvpScreen` and
`PvpTableScreen` used Material's `FilterChip` with its defaults. `OptionsScreen` used `FilterChip`
with eight lines of hand-written colours. Two of the three were **visibly wrong**: with
`secondaryContainer` unfilled in the old scheme, a selected chip on either PvP screen came out in
Material's baseline purple.

The element filter in `CardListBody` is the one chip still hand-built, and the reason is in its
KDoc: a Material chip is a label with an optional icon, and that one has no label at all. It takes
`ttoClickable` instead, which is where its touch target went from about 24 dp to 48.

### `WideButton` — two Material components, not one recoloured one

It was a `Button` whose container was swapped between `primary` and `surfaceVariant` by hand.
Material already ships the quiet half of a pair as `FilledTonalButton`, and imitating one means
imitating its disabled state, its elevation and its ripple too — three more places to drift. Now
`filled = false` simply *is* the tonal button. Height 56 dp and radius `large`: a 4 dp radius on a
56 dp bar reads as a rectangle, which is what made the menu look like a list of table rows.

### `SectionHeader`

Promoted out of `OptionsScreen`, which was the only screen that had one — the other dense screens
wrote a bare `Text` and each picked its own colour and case. What it gains is
`semantics { heading() }`, which is how a screen reader offers "jump to next heading"; without it
the only way through a long settings column is to read every row of it.

### `ScreenScaffold`

Unchanged in structure. Its `Snackbar` now names `inverseSurface` / `inverseOnSurface` /
`inversePrimary` explicitly — **because the default is right**, and for as long as the scheme left
those roles unfilled every confirmation in the game appeared as a light lavender box on a dark
screen. Saying them at the one call site they were ever wrong at is what keeps that visible.

`ScreenTabs` owns the gap under itself: a `PrimaryTabRow` puts its indicator flush against its own
bottom edge, so content starting immediately underneath had the underline running through its first
line once the type scale grew.
