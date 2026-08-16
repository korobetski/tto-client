# Triple Triad — Kotlin Multiplatform Client

[![Build Status](https://github.com/korobetski/tto-client/actions/workflows/build.yml/badge.svg)](https://github.com/korobetski/tto-client/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-%237F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.9.3-4285F4.svg)](https://github.com/JetBrains/compose-multiplatform)

**A modern, cross-platform implementation of the classic Triple Triad card game from Final Fantasy series.**

This client faithfully recreates the original Adobe AIR/ActionScript 3 game while leveraging modern Kotlin Multiplatform and Compose technologies. The project serves as both a playable game and a proof of concept for migrating legacy Flash applications to modern platforms.

---

## Screenshots

Screenshots will be added as the project matures. To generate screenshots:

### From Android Device

```bash
# Capture and pull screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png docs/screenshots/

# On Windows (Git Bash), use:
MSYS_NO_PATHCONV=1 adb shell screencap -p /sdcard/screenshot.png
```

### From Desktop

Run the app and take screenshots manually, or use automated tools.

### Recommended Screenshots

Place screenshots in `docs/screenshots/` directory:

- `menu.png` — Main menu with logo and options
- `match_landscape.png` — Match in landscape orientation
- `match_portrait.png` — Match in portrait orientation
- `dashboard.png` — Player dashboard with stats
- `collection.png` — Card collection view
- `deck_builder.png` — Deck editing screen
- `card_detail.png` — Close-up of a single card
- `tutorial.png` — Tutorial lesson in progress

> **Status:** Screenshots directory created. Contributions of actual device screenshots are welcome!

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots](#screenshots)
- [Project Structure](#project-structure)
- [Toolchain](#toolchain)
- [Prerequisites](#prerequisites)
- [Commands](#commands)
- [Game Features](#game-features)
- [Technical Highlights](#technical-highlights)
- [Running the App](#running-the-app)
- [Build Results](#build-results)
- [Localization](#localization)
- [Known Issues](#known-issues)
- [Licensing](#licensing)

---

## Overview

This repository contains a **Kotlin Multiplatform** client for Triple Triad, a strategic card game originally from Final Fantasy VIII and later expanded in Final Fantasy XIV. The project demonstrates a complete migration path from legacy ActionScript 3 code to modern Kotlin, maintaining full fidelity to the original game mechanics while providing a native experience on multiple platforms.

### Supported Platforms

- **Android** — Full support with native UI
- **Desktop (JVM)** — Complete implementation for development and testing
- **iOS** — Framework compiles (SwiftUI host sources included, project setup pending)

---

## Features

### Implemented

The client currently implements **7 core systems**:

1. **Card Catalog** — All 263 cards loaded from JSON resources
2. **Card Rendering** — Faithful recreation of original card artwork with exact layer positioning
3. **Match System** — Complete 3x3 board gameplay with turn-based mechanics
4. **Rules Engine** — All game rules (Capture, Reverse, Fallen Ace, Same, Same Wall, Plus, Combo, Elemental, etc.)
5. **State Machine** — Match sequencing as pure state transitions
6. **App Framework** — Splash screen, main menu, options, and navigation
7. **Audio System** — Original game music and sound effects with dual-channel mixing

### Game Content

- **263 unique cards** across FF14 and FF8 collections
- **85 PvE opponents** with varying difficulty levels
- **2 tournament ladders** (Card Club and Gold Saucer Tournament)
- **Complete tutorial system** with 12 interactive lessons
- **4 languages** (English, French, German, Japanese)

### Technical Achievements

- **Single codebase** for all platforms using Kotlin Multiplatform
- **Compose Multiplatform UI** with shared components
- **96.8% line coverage** with gated CI checks
- **Zero detekt/ktlint warnings** (maxIssues = 0)
- **Pure functional rules engine** enabling server-side verification

---

## Project Structure

```
.
├── README.md                    # This file
├── CONTRIBUTING.md              # Development guidelines
├── CLAUDE.md                    # AI assistant context
├── settings.gradle.kts          # Module configuration
├── build.gradle.kts             # Build configuration
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── shared/                      # KMP module (core logic + UI)
│   └── src/
│       ├── commonMain/kotlin/com/tripletriad/
│       │   ├── data/             # Data loaders, repositories
│       │   ├── storage/          # Save format handling
│       │   ├── net/              # Network client, session management
│       │   ├── i18n/             # Localization system
│       │   ├── audio/            # Audio player interface
│       │   ├── settings/         # User preferences
│       │   ├── log/              # Logging framework
│       │   ├── time/             # Clock abstraction
│       │   └── ui/               # Compose UI components
│       │       ├── theme/        # Design system (palette, typography)
│       │       ├── App.kt        # Root composable
│       │       ├── Screen.kt     # Navigation destinations
│       │       ├── Navigation.kt # Navigation components
│       │       ├── Controls.kt   # Shared UI controls
│       │       ├── Startup.kt    # Startup sequence
│       │       ├── MainMenuScreen.kt
│       │       ├── OptionsScreen.kt
│       │       ├── ProfileGate.kt / ProfileScreen.kt
│       │       ├── AccountSession.kt / AccountScreen.kt
│       │       ├── DashboardScreen.kt
│       │       ├── MatchScreen.kt / MatchBoard.kt
│       │       ├── CollectionScreen.kt
│       │       ├── DeckSelectorScreen.kt
│       │       ├── CardView.kt   # Card rendering
│       │       └── ...
│       └── commonMain/composeResources/files/
│           ├── cards.json        # Card catalog (generated)
│           ├── npcs.json          # PvE opponents (generated)
│           ├── campaigns.json    # Tournament ladders (generated)
│           ├── art/               # Card faces, avatars, portraits, icons
│           └── locales/           # Localization bundles
├── androidApp/                  # Android host
│   └── src/main/
│       └── kotlin/com/tripletriad/android/
│           ├── AndroidAudioPlayer.kt
│           ├── AndroidSettingsStore.kt
│           ├── MainActivity.kt
│           └── DocumentStore.kt
├── desktopApp/                  # JVM host
│   └── src/main/kotlin/
│       └── Main.kt
├── iosApp/                      # iOS host (SwiftUI)
│   └── *.swift
└── tools/                       # Extraction/import scripts
    ├── extract_cards.py       # Generates cards.json
    ├── import_card_art.py      # Imports card artwork
    ├── import_locales.py       # Normalizes locale bundles
    ├── import_sounds.py        # Imports sound files
    └── ...
```

---

## Toolchain

| Component | Version | Notes |
|-----------|---------|-------|
| **Gradle** | 9.6.1 | Via committed wrapper |
| **JDK** | 17 | `jvmToolchain(17)` in all modules |
| **Kotlin** | 2.2.20 | With Compose Multiplatform plugin |
| **Compose Multiplatform** | 1.9.3 | Material 3 components |
| **kotlinx.serialization** | 1.9.0 | JSON parsing |
| **Android Gradle Plugin** | 9.3.1 | For Android target |
| **ktlint** | 12.1.2 | Code formatting (configured via .editorconfig) |
| **detekt** | 1.23.8 | Static analysis (maxIssues = 0) |
| **JaCoCo** | Built-in | Code coverage (96.8% line, 78.9% branch) |

---

## Prerequisites

### Required

- **JDK 17** on `PATH` or `JAVA_HOME`
- **GitHub token** with `read:packages` scope (for `com.tripletriad:core` dependency)
  - Add to `~/.gradle/gradle.properties`:
    ```properties
    gpr.user=<your-github-username>
    gpr.key=<token with read:packages>
    ```

### Android Specific

- **Android SDK** with platform 37 and build-tools
- `local.properties` file with `sdk.dir` path
  - On Windows, escape backslashes: `sdk.dir=C\:\Users\\<you>\\AppData\\Local\\Android\\Sdk`

### Optional

- **Python 3** — Only needed for regenerating data files from AS3 source

---

## Commands

### Run the App

```bash
# Desktop (JVM) - Fastest iteration
./gradlew :desktopApp:run

# Android - Build debug APK
./gradlew :androidApp:assembleDebug

# Android - Install and launch
./gradlew :androidApp:installDebug
adb shell am start -n com.tripletriad.android/.MainActivity
```

### Build Everything

```bash
# Full build: compile, test, lint, format check
./gradlew build

# Fast test loop (523 tests, ~22s from scratch)
./gradlew :shared:desktopTest

# Static analysis
./gradlew ktlintCheck detekt

# Code formatting
./gradlew ktlintFormat
```

### Data Regeneration

```bash
# Regenerate card catalog from AS3 source
python tools/extract_cards.py shared/src/commonMain/composeResources/files/cards.json

# Re-import card artwork
python tools/import_card_art.py

# Regenerate PvE opponents
python tools/extract_npcs.py shared/src/commonMain/composeResources/files/npcs.json

# Regenerate tournament ladders
python tools/extract_campaigns.py shared/src/commonMain/composeResources/files/campaigns.json

# Import localization bundles
python tools/import_locales.py

# Import sound files
python tools/import_sounds.py

# Regenerate launcher icons
python tools/make_launcher_icons.py
```

---

## Game Features

### Card System

Each card has:
- **4 power values** (top, right, bottom, left) ranging from 1 to 10 (or A for 10)
- **Elemental type** (for Elemental rule): Beast, Garlean, Primals, Scions (FF14) or 8 elements (FF8)
- **Rarity** (1-5 stars)
- **Collection** (FF14 or FF8)

The **effective power range is 0-10** (Fallen Ace produces 0, Descension can reduce to 0).

### Rules Engine

All original rules are implemented and tested against a 35-case specification matrix:

| Rule | Description |
|------|-------------|
| **Basic Capture** | Higher adjacent power captures opponent's card |
| **Reverse** | Lower adjacent power captures (inverts comparison) |
| **Fallen Ace** | Ace (A) counts as 0 instead of 10 |
| **Same** | Equal adjacent powers capture |
| **Same Wall** | Card can capture using board edge as "wall" |
| **Plus** | Sum of adjacent powers captures when equal |
| **Combo** | Captured cards trigger chain captures |
| **Elemental** | Card powers modified by board element |
| **Order** | Must play cards in dealt order |
| **Chaos** | Random card selected from hand each turn |
| **Sudden Death** | First to capture 5 cards wins immediately |

### Match Flow

1. **Roulette** — Random rule selection
2. **Pre-match** — Random hand, Swap, Open, Coin flip options
3. **Turn-based play** — Alternating placements on 3x3 grid
4. **Scoring** — Count controlled cards at end of match

---

## Technical Highlights

### Architecture

- **Pure Functional Core**: Rules engine uses immutable data structures and pure functions
- **Separation of Concerns**: UI, audio, networking, and game logic are cleanly separated
- **Dependency Injection**: Clock and platform services are injected for testability

### UI/UX

- **Responsive Design**: Adapts to landscape and portrait orientations
- **Scale-Invariant**: Card and board scaling maintains exact geometry
- **Animation Fidelity**: Card flip animation matches original 400ms duration with 4-leg squash
- **Drag and Drop**: Full support alongside tap-to-play

### Performance

| Metric | Value |
|--------|-------|
| Cold start (Android) | ~650-750ms |
| Memory usage (idle) | ~72.4 MB PSS |
| Line coverage | **96.8%** |
| Branch coverage | **78.9%** |

### Testing

- **523 distinct tests** across common and platform-specific modules
- **786 total test executions** (common tests run on multiple targets)
- **Zero failures** in CI pipeline
- **Coverage gated** in build (fails if below thresholds)

---

## Running on Real Devices

### Android

```bash
# From command line
./gradlew :androidApp:installDebug
adb shell am start -n com.tripletriad.android/.MainActivity

# From Android Studio
1. Open repository root
2. Select androidApp configuration
3. Run on device/emulator
```

### Desktop

```bash
./gradlew :desktopApp:run
```

### Useful ADB Commands

```bash
# View logs
adb logcat -s AndroidRuntime:E System.err:W

# Simulate tap (for testing)
adb shell input tap 1200 450

# Capture screenshot
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png

# On Windows (Git Bash):
MSYS_NO_PATHCONV=1 adb shell screencap -p /sdcard/s.png

# Uninstall
adb uninstall com.tripletriad.android
```

---

## Verified Build Results

| Platform | Command | Result |
|----------|---------|--------|
| All | `./gradlew clean build` | **BUILD SUCCESSFUL** (264 tasks) |
| Android Debug | `:androidApp:assembleDebug` | **BUILD SUCCESSFUL** (19,070 KB) |
| Android Release | `:androidApp:assembleRelease` | **BUILD SUCCESSFUL** (16,141 KB) |
| Desktop | `:desktopApp:build` | **BUILD SUCCESSFUL** |
| Shared Tests | `:shared:desktopTest` | **523 tests, 0 failures** |
| All Tests | `:shared:build` | **786 executions, 0 failures** |
| Lint | `ktlintCheck detekt` | **BUILD SUCCESSFUL** (0 issues) |
| Coverage | `:shared:coverageReport` | **96.8% line, 78.9% branch** |

### Device Testing

Verified on **Pixel 6a, Android 17 (API 37), arm64-v8a**:

- ✅ Installation and launch
- ✅ Both landscape and portrait orientations
- ✅ Card artwork rendering with correct layering
- ✅ Capture mechanics and flip animations
- ✅ Touch input and gesture handling
- ✅ Complete match gameplay
- ✅ Audio playback (music and effects)
- ✅ Localization (French and Japanese tested)

---

## Localization

Four languages are supported, imported from the original AS3 bundles:

| Language | Code | Status |
|----------|------|--------|
| English | `en_US` | ✅ Complete |
| French | `fr_FR` | ✅ Complete |
| German | `de_DE` | ✅ Complete (some mistranslations noted) |
| Japanese | `ja_JA` | ✅ Complete |

### Localization Structure

```
shared/src/commonMain/composeResources/files/locales/
├── tto-en_US.json    # Original Square Enix strings (688 keys) - DO NOT EDIT
├── tto-fr_FR.json    # French translation
├── tto-de_DE.json    # German translation
├── tto-ja_JA.json    # Japanese translation
├── app-en_US.json    # App-specific strings (172 keys) - EDIT THESE
├── app-fr_FR.json    # French app strings
├── app-de_DE.json    # German app strings (empty - falls back to English)
└── app-ja_JA.json    # Japanese app strings (empty - falls back to English)
```

The split between `tto-*` and `app-*` bundles:
- **`tto-*`**: Original Square Enix content (must remain unchanged)
- **`app-*`**: New strings added by this port (safe to modify)

---

## Design System

The app uses a **custom Material 3-based design system** defined in:

- [`ui/theme/Palette.kt`](shared/src/commonMain/kotlin/com/tripletriad/ui/theme/Palette.kt) — Six tonal ramps
- [`ui/theme/Colors.kt`](shared/src/commonMain/kotlin/com/tripletriad/ui/theme/Colors.kt) — Color roles
- **Raleway font** — Imported from original (Regular as normal, Medium as bold)

### Card Geometry

All measurements are **exact reproductions** of the original AS3 source:

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

---

## Audio System

The original **22 sound files** have been analyzed:

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

### Implementation

- **Dual-channel mixing**: Background music and sound effects on separate channels
- **SoundPool** for short effects (overlapping allowed)
- **MediaPlayer** for music (streaming with seek support)
- **No Media3 dependency** — Uses platform APIs directly

---

## Rules Engine Details

The rules engine (`tto-core`) is implemented as **pure Kotlin** with:

```kotlin
// Core types
GameRules      // 12 rule slots (3 enums + 9 booleans)
Board          // Immutable 3×3 grid, positions 0..8 row-major
AscensionTally // Board-wide per-type counter
RulesEngine    // Capture resolution, precedence, combo propagation
TurnOrder      // 9 placements: 5 for first player, 4 for second
```

### Key Design Decisions

1. **Same and Plus use effective powers** — Fixed from original AS3 which used printed values
2. **Same Wall fires with one neighbour** — Fixed from original which required >1
3. **Mutually exclusive rules** — Ascension/Elemental cannot be enabled together

These corrections are **configurable** via `RulesEngineOptions`:
- `RulesEngineOptions.FAITHFUL` — Reproduces original behavior (including defects)
- Default — Uses corrected behavior

---

## Screens and Navigation

The app has **21 destinations** arranged in a tree structure:

```
SPLASH → MENU → PROFILES → PROFILE_NEW (no server)
                    ACCOUNT → COLLECTION_CHOICE (with server)
                       │
                       ▼
                    DASHBOARD → OPPONENTS → MATCH
                               │          └─ deck selector
                               │
                               ├── STATS → AVATAR
                               ├── CARDS / DECKS (tabs)
                               ├── SHOP / INVENTORY (tabs)
                               ├── CAMPAIGN → CAMPAIGN_MATCH
                               ├── TUTORIAL
                               └── HELP

MENU → OPTIONS
MENU → onQuit
```

### Navigation Features

- **Tree-based**: Maximum depth of 3, forks at dashboard
- **Back handling**: Android system gesture supported via `BackHandler`
- **No Compose Navigation**: Uses custom `Screen.up` and conditional routing
- **Platform-agnostic**: Works on Android, Desktop, and will work on iOS

---

## User Settings

Stored in `UserSettings.json` with **3 fields**:

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

### Features

- **Immediate apply**: Changes take effect without Save button
- **Corruption repair**: Invalid files are repaired, not fatal
- **Language persistence**: First run seeds from device language
- **Dual volume control**: Separate channels for music and effects

---

## Known Issues

### Current Limitations

1. **No frame-timing measurement** — `dumpsys gfxinfo` not yet integrated
2. **No card-internal layout assertions** — Visual regression tests needed
3. **Easing curves differ** — Starling vs Compose easing functions
4. **Portrait is custom** — Original was landscape-only, port adds portrait support
5. **Card scaling approach** — Multiplies geometry vs scaling render layer

### iOS Status

- ✅ Framework compiles on macOS CI
- ✅ Common tests pass on iOS simulator
- ⏳ No `.xcodeproj` yet (requires macOS to create)
- ⏳ No iOS app has been built or tested

To complete iOS support:
1. Create Xcode project on macOS
2. Add `iosApp/*.swift` to target
3. Add build phase: `./gradlew :shared:embedAndSignAppleFrameworkForXcode`

### Deprecation Warnings

Three warnings from plugin internals (all scheduled for Gradle 10):
- `ReportingExtension.file(String)` — from detekt
- `archives configuration` — from Kotlin Multiplatform
- `multi-string dependency notation` — from Kotlin Multiplatform

---

## What's Proven and What's Left

### ✅ Proven by Execution

- Kotlin 2.2.20 + Compose Multiplatform 1.9.3 + Ktor stack works
- Single `commonMain` UI runs on Android and JVM
- All 28 of original's 32 screens implemented
- Complete rules engine with server-side verification
- Drag-and-drop + tap input in common code
- Accounts, sessions, offline queue over Ktor
- Local Docker/Postgres container integration
- Original artwork (263 cards, 27 avatars, 84 portraits, 20 captions)
- 4-language localization
- 96.8% line coverage, 0 detekt issues

### 📋 Remaining

1. **Local PvP** — `MatchView` and peer protocol (transport undecided)
2. **iOS app** — Framework compiles, needs Xcode project setup
3. **Store release** — Out of scope by decision

---

## Fidelity to Original

Every value is **measured from AS3 source**, not guessed:

| This Implementation | AS3 Source |
|---------------------|-------------|
| Card sprite 104×128 | `this.width = 104; this.height = 128` — Card.as:60-61 |
| Colored face 88×118 at (8,5) | `new Quad(88, 118, 0x5a595a)` at x=8, y=5 — Card.as:73-75 |
| Blue card color | `0xFF2D4660` — Card.as:29 |
| Red card color | `0xFF602D2D` — Card.as:30 |
| Grey card color | `0xFF5A595A` — Card.as:31 |
| Digit cluster position | (28, 88), bounds 44×30 — Card.as:89-90 |
| `cdbg` plate | 28×28 at (8,1), α=0.5 — CardDigits.as:26-29 |
| Power order | top/right/bottom/left — CardDigits.as:22 |
| Power 10 renders as A | Uses `cdA` texture — no `cd10` in digits.xml |
| Card flip animation | 400ms, 4-leg squash — Card.as:249-291 |

---

## Licensing Note

⚠️ **This repository contains Square Enix material**

The following files contain Square Enix IP:
- `cards.json` — Names and stats of all 263 cards
- `art/*` — Card faces, thumbnails, avatars, portraits, icons, captions
- `locales/tto-*.json` — Original localization strings
- `androidApp/src/main/res/raw/*.mp3` — Original sound files

**BR-003** (unlicensed Square Enix IP) from the risk assessment is **unresolved and blocking**. If resolved by reskinning, only the names in `cards.json` would need replacement — card stats (powers, rarity, type) are separate from naming.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines, code of conduct, and contribution process.

---

## Related Repositories

- **[AS3-Triple-Triad](https://github.com/korobetski/AS3-Triple-Triad)** — Original Adobe AIR/ActionScript 3 source
- **[tto-core](https://github.com/korobetski/tto-core)** — Rules engine library (consumed as `com.tripletriad:core`)

---

## Documentation

- **[Migration Documentation](docs/migration/)** — Complete migration plan and progress
- **[Analysis Documentation](docs/analysis/)** — Rules, performance, data flow analysis
- **[Development Guides](docs/development/)** — Setup, build, testing guides

---

*Built with ❤️ using Kotlin, Compose, and a lot of reverse engineering.*

*Original game: Triple Triad from Final Fantasy series © Square Enix*
