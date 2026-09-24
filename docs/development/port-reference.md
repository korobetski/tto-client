# Port reference

Reference material from the port of the original game, moved out of the repository's `README.md`
on 2026-09-24 so the README could stay an introduction. It is kept as it was written; the
measurements carry the date they were taken, and anything without one has **not been re-verified**
since the move. The code, the version catalog and [design-system.md](./design-system.md) are the
authority where they disagree with this page.

---

## Card rendering

| Element | Dimensions | Position |
|---------|------------|----------|
| Card sprite | 104 × 128 dp | — |
| Color quad | 88 × 118 dp | (8, 5) |
| Digit cluster | 44 × 30 dp | (28, 88) |
| `cdbg` plate | 28 × 28 dp | (8, 1), α=0.5 |
| Digits | 18 × 18 dp | Various positions |
| Rarity stars | 29 × 28 dp | (9, 6) relative to face |
| Type icon | 20 × 20 dp | (80, 3) relative to face |
| Artwork | 104 × 128 dp | (0, 0) over color quad |

| Element | Value |
|---------|-------|
| Blue card color | `0xFF2D4660` |
| Red card color | `0xFF602D2D` |
| Grey card color | `0xFF5A595A` |
| Power order | top/right/bottom/left |
| Power 10 renders as A | Uses `cdA` texture — no `cd10` in digits.xml |
| Card flip animation | 400ms, 4-leg squash |

Each card has four power values (top, right, bottom, left) from 1 to 10, shown as A for 10. The
**effective power range is 0–10**: the Elemental penalty and Descension can reduce a 1 to 0.

---

## Audio

The original **22 sound files** were analyzed:

| Property | Values |
|----------|---------|
| Codec | MPEG-1/2 Layer III (MP3) |
| Sample rates | 22,050 / 44,100 / 48,000 Hz |
| Channels | 21 mono, 1 stereo (music) |
| Bitrate | 17 VBR, 5 CBR |
| Total | 22 files, 1.40 MB, 84.5s |

**10 sounds are used** in the port (out of 22 original):

| Moment | Sound File | Source |
|--------|------------|--------|
| Match music (looping from 16.374s) | `shuffle_or_boogie` | BaseMatchScreen.as:114 |
| Hands dealt | `se_ttriad.scd_2` | openPhase() |
| Placed (no capture) | `se_ttriad.scd_1` | TTOCore.as:87 |
| Card captured/flipped | `se_ttriad.scd_157` | Card.as:229 |
| Combo propagation | `se_ttriad.scd_15` | TTOCore.as:125 |
| Turn change | `se_ttriad.scd_4` | BaseMatchScreen.as:374 |
| Blue wins | `se_ttriad.scd_7` | PVEMatchScreen.as:95 |
| Red wins | `se_ttriad.scd_8` | PVEMatchScreen.as:139 |
| Control tapped | `se_ui.scd_72` | TouchLabel.as:31 |
| Next match | `se_gs.scd_162` | RematchPanel.as:36 |

- **Dual-channel mixing**: background music and sound effects on separate channels
- **SoundPool** for short effects (overlapping allowed), **MediaPlayer** for music (streaming with
  seek support)
- **No Media3 dependency** — platform APIs directly

---

## Rules engine design decisions

The engine lives in [tto-core](https://github.com/korobetski/tto-core):

```kotlin
GameRules      // the rule slots of a match
Board          // Immutable 3×3 grid, positions 0..8 row-major
AscensionTally // Board-wide per-type counter
RulesEngine    // Capture resolution, precedence, combo propagation
TurnOrder      // 9 placements: 5 for first player, 4 for second
```

1. **Same and Plus use effective powers**, not printed values
2. **Same Wall fires with a single neighbour**
3. **Mutually exclusive rules** — Ascension/Elemental cannot be enabled together

These are configurable via `RulesEngineOptions`: `RulesEngineOptions.FAITHFUL` reproduces the
legacy behavior (including the printed-value defect); the default uses the corrected behavior.

---

## User settings

Stored in `UserSettings.json`:

```json
{
  "language": "fr_FR",
  "background_volume": 1.0,
  "noise_volume": 1.0
}
```

| Host | Path |
|------|------|
| Android | `Context.filesDir/UserSettings.json` |
| Desktop | `~/My Games/Triple Triad Online/UserSettings.json` |

- **Immediate apply**: changes take effect without a Save button
- **Corruption repair**: invalid files are repaired, not fatal
- **Language persistence**: first run seeds from device language

---

## Measurements

| Metric | Value |
|--------|-------|
| Cold start (Android) | ~650-750ms |
| Memory usage (idle) | ~72.4 MB PSS |

| Platform | Command | Result |
|----------|---------|--------|
| Shared Tests | `:shared:desktopTest` | **856 tests, 0 failures** (measured 2026-08-17) |
| Android host tests | `:shared:testAndroidHostTest` | **396 tests, 0 failures** (measured 2026-08-17) |
| Coverage | `:shared:coverageReport` | **95.0% line, 75.8% branch**, desktop target only (measured 2026-08-17) |

### Device testing

Verified on **Pixel 6a, Android 17 (API 37), arm64-v8a**: installation and launch, both
orientations, card artwork layering, capture mechanics and flip animations, touch input and
gestures, complete matches, audio playback, and localization (French and Japanese tested).

---

## Known limitations

1. **No frame-timing measurement** — `dumpsys gfxinfo` not yet integrated
2. **No card-internal layout assertions** — visual regression tests needed
3. **Easing curves differ** — Starling vs Compose easing functions
4. **Portrait is custom** — the original was landscape-only; the port adds portrait support
5. **Card scaling approach** — multiplies geometry rather than scaling the render layer

Three deprecation warnings come from plugin internals, all scheduled for Gradle 10:
`ReportingExtension.file(String)` (detekt), the `archives` configuration and multi-string dependency
notation (both Kotlin Multiplatform).

---

## Useful ADB commands

```bash
adb logcat -s AndroidRuntime:E System.err:W            # view logs
adb shell input tap 1200 450                           # simulate a tap
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
MSYS_NO_PATHCONV=1 adb shell screencap -p /sdcard/s.png  # the same, from Git Bash on Windows
adb uninstall com.tripletriad.android
```
