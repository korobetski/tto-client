# Triple Triad — Kotlin Multiplatform client

Proof of concept for the migration described in
[docs/migration/00-INDEX.md](docs/migration/00-INDEX.md).

It does seven things:

1. **Loads all 263 cards** from a JSON resource extracted out of the AS3 source
   (`tto/datas/cards.as`), through the Compose Multiplatform resource bundle.
2. **Draws a card** at the real geometry from the real artwork, layer for layer as
   `tto.display.Card` stacks them, with the four edge powers positioned exactly as
   `tto.display.CardDigits` positions them.
3. **Plays a match** — a 3×3 board and two hands, turn by turn, laid out either side of the
   board in landscape and above and below it in portrait; a captured card flips with
   `Card.flip()`'s own four-leg squash.
4. **Implements the rules engine** — capture, Reverse, Fallen Ace, Same, Same Wall, Plus,
   combo, the three type rules, turn order and scoring — as pure functions with no UI, tested
   against the [specification's 35-case matrix](docs/analysis/game-rules.md#16-test-matrix-for-the-port).
   See [§ Rules engine](#rules-engine).
5. **Sequences the match as a state machine**, `MatchState -> MatchState`, replacing the
   original's cascade of `setTimeout` callbacks.
6. **Starts like an app**: a splash that names each startup phase, a main menu (play / options /
   quit) and an options screen that changes the language on the spot and persists it. See
   [§ Screens and navigation](#screens-and-navigation).
7. **Plays the original's audio** — the looping match theme and nine effects, on two channels with
   the volumes from the settings file. See [§ Audio](#audio).

Everything else (drag-and-drop, AI, network, save games, the collection and deck-builder
screens) is deliberately out of scope. See
[§ What is proven, and what is left](#what-is-proven-and-what-is-left).

This replaces the earlier `poc/` directory, which was reported as validating the
technology stack but had never been compiled and contained 12 build-blocking defects.
Everything below has been executed; the results are in
[§ Verified build results](#verified-build-results).

### Where the AS3 original is

This repository holds the Kotlin client only. The Adobe AIR / ActionScript 3 original stays in
[AS3-Triple-Triad](https://github.com/korobetski/AS3-Triple-Triad) under `sources/`, and is not
copied here — the same split as the rules engine, which lives in
[tto-core](https://github.com/korobetski/tto-core) and arrives as `com.tripletriad:core`.

Every `sources/…` path in this README, in `docs/`, and in the Kotlin comments is therefore a
**citation of that repository**, not a directory in this checkout. Only the `tools/` scripts
actually need it present, and they are the only thing that breaks without it — see
[§ Card data](#card-data) and [§ Card artwork](#card-artwork). They find it in this order:

1. `TTO_AS3_SOURCES`, an absolute path to an `AS3-Triple-Triad` checkout's `sources/`;
2. otherwise `../AS3-Triple-Triad/sources`, i.e. a clone beside this one.

Their *output* — the card tables, the artwork, the locale bundles, the sounds, the launcher
icons — is committed here, so a plain `./gradlew build` never touches the AS3 tree.

---

## Layout

```
.
├── CONTRIBUTING.md              the front door: the loop, and what this project holds you to
├── settings.gradle.kts          3 modules, repositories declared once — including tto-core's
├── build.gradle.kts             plugin versions; applies ktlint + detekt to all modules
├── .editorconfig                formatting rules — read by both ktlint and the IDE
├── detekt/detekt.yml            static-analysis overrides, each with its reason
├── gradle/libs.versions.toml    single source of truth for versions
├── gradle/wrapper/              Gradle 9.6.1
├── tools/as3_tree.py            where the AS3 original is — every script below reads it
├── tools/extract_cards.py       regenerates cards.json from the AS3 source
├── tools/extract_npcs.py        regenerates npcs.json — the 85 PvE opponents
├── tools/extract_campaigns.py   the two tournament ladders, as campaigns.json
├── tools/import_card_art.py     copies the card artwork into composeResources
├── tools/import_ui_art.py       avatars, opponent portraits, bag icons, thumbnail atlases
├── tools/import_rule_banners.py the twenty match captions, in four locales
├── tools/import_locales.py      normalises the four AS3 string bundles
├── tools/make_launcher_icons.py regenerates the Android launcher icon from the AIR art
├── tools/import_sounds.py       copies the ten sounds this port plays into res/raw
├── tools/import_fonts.py        copies Raleway — the AS3 theme's own face — and its licence
├── docker/postgres/             the local account server's database, for end-to-end runs
├── shared/                      KMP module: data + network + Compose UI
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/tripletriad/
│       │   │   ├── data/            CatalogLoaders, SaveRepository, MatchHistoryRepository
│       │   │   ├── storage/         DocumentStore + SaveCodec — the obfuscated .sav format
│       │   │   ├── net/             AccountClient, ServerDirectory, SessionStore,
│       │   │   │                    TranscriptQueue, MatchReporter, ServerStatus
│       │   │   ├── i18n/            AppLocale, lookup + fallback, every key the UI names
│       │   │   ├── time/Clock.kt    the wall clock, injected so tests can pin the hour
│       │   │   ├── log/Log.kt       levels, lazy messages, pluggable sink
│       │   │   ├── audio/AudioPlayer.kt  Sound, the loop point, silent + recording players
│       │   │   ├── settings/        SettingsStore + UserSettings.json
│       │   │   ├── platform/OpenUrl.kt   the one thing only a host can do
│       │   │   └── ui/
│       │   │       ├── theme/           the AS3 palette, Raleway, and the type scale
│       │   │       ├── App.kt           root composable, the routing table, back handling
│       │   │       ├── Screen.kt        the 21 destinations, and where back goes from each
│       │   │       ├── Navigation.kt    the four-tab bar and rail, and the width that picks
│       │   │       ├── Controls.kt      WideButton, the scaffolds, the shared row palette
│       │   │       ├── Startup.kt       the phases, and SplashScreen.kt which shows them
│       │   │       ├── MainMenuScreen.kt / OptionsScreen.kt
│       │   │       ├── ProfileGate.kt   where the character comes from: a .sav, or an account
│       │   │       ├── ProfileSession.kt / ProfileScreen.kt   local characters, and the
│       │   │       │                    collection chosen at creation
│       │   │       ├── AccountSession.kt / AccountScreen.kt / ServersScreen.kt
│       │   │       ├── Connectivity.kt  the five-state indicator and the update notice
│       │   │       ├── DashboardScreen.kt  the character's own menu, and the hub
│       │   │       ├── StatsScreen.kt   the record, all 22 achievements, and the level bar
│       │   │       ├── AvatarScreen.kt  the 27 portraits, and choosing one
│       │   │       ├── CollectionScreen.kt + CardListBody / DecksBody   cards and decks
│       │   │       ├── StoreScreen.kt + ShopBody / InventoryBody        shop and bag
│       │   │       ├── OpponentScreen.kt   who can be challenged, by collection and hour
│       │   │       ├── CampaignScreen.kt / TutorialScreen.kt / MatchScript.kt   scripted
│       │   │       ├── DeckSelectorScreen.kt   which deck to play, inside the match
│       │   │       ├── MatchScreen.kt / MatchBoard.kt / MatchChrome.kt   the match itself
│       │   │       ├── MatchBanner.kt / MatchBannerOverlay.kt / MatchAnimations.kt
│       │   │       ├── CardArt.kt / UiArt.kt / Portraits.kt   every image the app draws
│       │   │       └── CardView.kt / CardColors.kt   the card face, lifted from the AS3
│       │   └── composeResources/files/
│       │       ├── cards.json / npcs.json / campaigns.json   generated from the AS3 source
│       │       ├── art/          card faces, avatars, portraits, icons and thumbnail atlases
│       │       └── locales/      tto-<tag>.json imported ×4, app-<tag>.json authored ×4
│       ├── commonTest/…         263 tests, run on desktop and on androidHostTest
│       ├── desktopTest/…        260 more — the real Compose tree on the JVM, plus the
│       │                        JVM-only bundle reads
│       └── iosMain/…/MainViewController.kt
├── androidApp/                  Android host: document store, logcat sink, audio player
│   └── src/main/res/            generated launcher icon + the ten sounds, in raw/
├── desktopApp/                  JVM host — run the UI without an emulator
└── iosApp/*.swift               SwiftUI host sources (see the iOS caveat below)
```

CI is [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Toolchain

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 9.6.1 | via the committed wrapper; daemon JVM pinned by `gradle/gradle-daemon-jvm.properties` |
| JDK | 17 | `jvmToolchain(17)` in every module |
| Kotlin | 2.2.20 | `org.jetbrains.kotlin.plugin.compose` is versioned with Kotlin, not with Compose |
| Compose Multiplatform | 1.9.3 | Material 3, plus `compose.components.resources` |
| kotlinx.serialization | 1.9.0 | plugin version tracks Kotlin |
| kotlinx-coroutines-test | 1.8.1 | `commonTest` only; pinned to the version Compose already brings in |
| Android Gradle Plugin | 9.3.1 | `:shared` uses `com.android.kotlin.multiplatform.library`; `:androidApp` needs no Kotlin plugin of its own |
| ktlint (via `org.jlleitschuh.gradle.ktlint`) | plugin 12.1.2 | configured entirely from `.editorconfig` |
| JaCoCo | Gradle built-in | coverage; **not Kover**, which cannot be applied here at all — see [§ Coverage](#coverage) |
| detekt | 1.23.8 | `buildUponDefaultConfig`, `maxIssues = 0` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 | |

There is no `composeOptions.kotlinCompilerExtensionVersion` anywhere: since Kotlin 2.0 the
Compose compiler ships inside Kotlin and is applied as a plugin.

## Prerequisites

Summarised here; [docs/development/project-setup.md](docs/development/project-setup.md) is the
full version, including which host can build what and the first-run failures worth recognising.

- JDK 17 on `PATH` or `JAVA_HOME`.
- **A GitHub token with `read:packages`.** The rules engine is `com.tripletriad:core`, published
  from [tto-core](https://github.com/korobetski/tto-core) — it used to be the `:core` module here,
  and it left so that the server could link the same engine without a copy of this repository. It
  is resolved from GitHub Packages, which answers an anonymous request with a 401 even for a public
  package, so this is required rather than optional. It goes in `~/.gradle/gradle.properties`,
  outside every repository:

  ```properties
  gpr.user=<your-github-username>
  gpr.key=<a token carrying read:packages and nothing else>
  ```

  Without it the build fails on an unresolved `com.tripletriad:core` — a resolution error, not an
  authentication one, which is why it is worth recognising here.
- For the Android module only: an Android SDK with platform 37 (what `compileSdk` names) and a `local.properties`
  pointing at it. Copy `local.properties.sample` and fill in `sdk.dir`. On Windows
  **escape both the drive colon and the backslashes**, and end the file with a single
  `LF` — an unescaped colon or a `CRLF` makes `lintDebug` fail with `PropertyEscape`:

  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```

- Nothing extra for the desktop module.
- Python 3 only if you need to regenerate `cards.json`.

## Commands

The ones used daily. What each task actually runs, where its output lands and how to reproduce a
CI job locally: [docs/development/build-guide.md](docs/development/build-guide.md). Running,
filtering and writing tests: [docs/development/testing-guide.md](docs/development/testing-guide.md).

Run the UI without an emulator or Xcode:

```bash
./gradlew :desktopApp:run
```

Build the Android debug APK (output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`):

```bash
./gradlew :androidApp:assembleDebug
```

Everything — compile all JVM/Android targets, unit tests, UI tests, Android lint, ktlint
and detekt:

```bash
./gradlew build
```

Fast test loop (all 165 tests, 22 s forced from scratch, instant when nothing changed):

```bash
./gradlew :shared:desktopTest
```

Static analysis on its own, and the formatter:

```bash
./gradlew ktlintCheck detekt
```

```bash
./gradlew ktlintFormat
```

Regenerate the card catalog after any change to `tto/datas/cards.as`, from the
**repository root**:

```bash
python tools/extract_cards.py shared/src/commonMain/composeResources/files/cards.json
```

Re-import the artwork after any change to the catalog — it fails if a card has no picture:

```bash
python tools/import_card_art.py
```

Regenerate the PvE opponents after any change to `tto/datas/NPCs.as` — or to `cards.as`, since
two opponents draw their pool from it via `getCardsByRarities`:

```bash
python tools/extract_npcs.py shared/src/commonMain/composeResources/files/npcs.json
```

## Card data

`shared/src/commonMain/composeResources/files/cards.json` is **generated**, not
hand-written. [`tools/extract_cards.py`](tools/extract_cards.py) parses the two array
literals in `sources/src/tto/datas/cards.as` and resolves the i18n keys against
`sources/bin/datas/locales/en_US.json`.

| | Count |
|---|--:|
| `ff14` collection | 153 cards |
| `ff8` collection | 110 cards |
| **total** | **263** |

Each table in the AS3 starts with a `{name:"Back", power:[], rarity:0}` sentinel at index
0, which is not a playable card and is dropped; `id` is otherwise the array index, which
is what `Card.draw()`, `CardItem` and the save file use as a card's identity.

Two details the extractor has to get right:

- **Powers are hex.** `Card.as:316-330` reads each one as `uint("0x" + power[i])`, which
  is how the literal `'A'` in `power:[1,8,'A',8]` means 10.
- **`type` means two different things.** In the `ff14_` collection it is one of four FFXIV
  tribes (`beast`, `garlean`, `primals`, `scions`) driving `RULE_TYPE`; in `ff8_` it is one
  of eight FF8 elements driving `RULE_ELEMENTAL`. One field, compared against
  `tile.element` by the same two lines of `TTOCore.as:48-49`, so it is one enum here.

The script asserts both counts and spot-checks specific cards against the source, so it
fails loudly rather than emitting a plausible-looking wrong catalog.

## Card artwork

`shared/src/commonMain/composeResources/files/art/` is **imported**, not authored:
[`tools/import_card_art.py`](tools/import_card_art.py) copies it byte-for-byte out of
`sources/assets/` and renames each file to the AS3 texture id, so a card's picture is
addressable as `files/art/{collection}{id}.png` — literally `Card.as:166`.

| | Count | Size |
|---|--:|--:|
| card faces (`ff14_1` … `ff8_110`) | 263 | 7.00 MB |
| card back, digit atlas, 5 rarity rows, 12 type icons | 19 | 85 KB |
| `logo.png` — the wordmark, for the splash and the menu | 1 | 15 KB |

### Individual files, not the sprite sheets

`sources/assets/cards/` ships two ShoeBox atlases, and the question of whether to use them is
a measurement rather than a preference. Both encodings are 8-bit RGBA, so this compares like
with like:

| | Individual files | Sprite sheets |
|---|--:|--:|
| Download size, all 263 cards | 7.00 MB | ~4.9 MB (three 1024×2048 sheets) |
| Resident bitmap, a match (≤ 19 cards) | ~1.0 MB | 24 MB |
| Resident bitmap, all 263 decoded | 14 MB | 24 MB |
| Cards actually covered by the shipped sheets | 263 / 263 | **190 / 263** |

A sheet decodes whole or not at all: 1024 × 2048 × 4 bytes = 8 MB each, resident whether one
card is on screen or all of them. Trading 2 MB of download for 24 MB of permanently resident
memory is the wrong way round on a phone, and `ff14_cards.xml` stops at id 80 — a complete set
would need repacking with a tool this repository does not contain. So: individual files, loaded
on demand and cached (`CardArt`).

`digits.png` is the one atlas kept, and not for size — it is 10 KB either way. It is the only
source of the 28×28 `cdbg` plate, and its entries are the untrimmed 18×18 rectangles the
geometry in `CardColors.kt` is built on; the loose `digits/1.png` files are trimmed to 15×12
and would need their offsets re-derived. It is sliced into `BitmapPainter`s with source
rectangles, so the sheet is decoded once and no glyph is ever copied out of it.

### Layer order

The card is the full **104×128 sprite**, not the 88×118 colour quad, because the artwork
includes the frame. `CardFace` stacks `Card.as`'s display list in `addChild` order — colour
quad, artwork, rarity row, type icon, digit cluster, and the back on top while flipping. The
artwork has a **translucent centre**, and what shows through it is the owner's colour, which is
how the original serves both sides from 263 images instead of 526. Verified on the device: the
same picture reads blue in one hand and red in the other.

Two exceptions, both marked in the code. The `cardSelected` glow (layer 0) is drawn at
(−16, −4) — outside even the sprite bounds — so this port rings the card instead rather than
grow every slot by 16 dp. The `_modifier` badge (layer 5) is the Ascension/Descension `±N`
text, which no rule in this UI switches on yet.

Not every card has a translucent centre: the FF8 five-star character cards ship a silver frame
with an opaque illustration, so they read grey in both hands. That is the source artwork, not a
layering fault — `ff8_102.png` (Laguna) shows it directly.

## Launcher icon

The manifest used to say `android:icon="@mipmap/"` — an empty reference, so the launcher fell
back to the stock Android robot. `androidApp/src/main/res/` now holds a full icon set, and every
file in it is generated by
[`tools/make_launcher_icons.py`](tools/make_launcher_icons.py) from
`sources/assets/appIcons/icon_128.png`, the icon the AIR build shipped and the only size of it
that exists. Do not hand-edit `res/`; re-run the script.

The two halves of the source are **separated rather than resized together**, because 128 px is
not enough for an xxxhdpi icon (432 px of adaptive canvas):

| Layer | How | Sharpness |
|---|---|---|
| teal plate | vector gradient, `drawable/ic_launcher_background.xml` | exact at every density |
| white wing | PNG per density, `mipmap-*/ic_launcher_foreground.png` | resampled from 128 px |
| legacy (API 24–25) | plate + wing composited, `mipmap-*/ic_launcher.png` | plate exact |
| themed (API 33+) | the same wing as `<monochrome>` | resampled |

The whole set is 85 KB across 12 files, of which 58 KB is the legacy bitmaps.

Both halves were **measured, not eyeballed**. Fitting a plane per channel across the plate's
9 338 opaque background pixels gives equal x and y slopes to three decimals with a median
residual of 4/255 — so the plate is a linear gradient at 45°, `#0CD4FE` to `#034D60`, and it is
kept as a vector. The wing is pure white, so the plate's red channel (0–12) and the wing's (255)
never overlap and the red channel *is* the coverage mask; a blend pixel at the wing edge,
(190, 233, 242), is white at α = 0.74 over the fitted plate to within 4/255. Only the wing is
ever resampled, and its mask is re-sharpened by the scale factor afterwards, so the one lossy
step is confined to a smooth silhouette.

The source's drop shadow under the wing is **dropped on purpose**: an adaptive icon must not
carry a baked shadow, since the launcher casts its own from the layer geometry.

### The foreground is smaller than the usual 72dp

The wing reaches a radius of 66.7 px in the 128 px source — past the plate's own rounded
corners. Mapping the plate to the standard 72dp would put the wing at radius 37.5dp, which a
**circular mask (r = 36) cuts**. So the plate maps to 63.4dp instead, which keeps every wing
pixel inside the 66dp safe circle under any mask shape. The icon therefore reads a little
smaller than a stock Android Studio import, and is never clipped — verified against circle,
squircle and square masks before building, then on a device.

**`android:roundIcon` is deliberately not declared.** It exists so an API 25 launcher can ask
for a circular treatment of a square icon, and the AS3 plate is already a circle — its corner
radius is 59/128 of the side, recovered from the 2 994 transparent corner pixels, leaving a
10 px straight segment per side. The round variant was generated first, compared, found
indistinguishable, and dropped: it was 59 KB of near-duplicate bitmaps.

**Not verified:** the `<monochrome>` themed-icon layer. Seeing it requires turning on themed
icons in the launcher's own settings, which is a change to the device rather than to this build.

## Coverage

`./gradlew :shared:coverageReport` writes HTML and XML to
`shared/build/reports/jacoco/coverageReport/`. `./gradlew build` runs `coverageVerify`, which is
wired into `check`, so a coverage collapse fails the build rather than waiting to be noticed.

| Counter | Covered | Gate |
|---|--:|--:|
| line | **96.8%** (5662/5852) | 90% |
| branch | **78.9%** (1722/2183) | 75% |
| instruction | 95.7% (51 535/53 832) | — |
| method | 93.1% (1273/1367) | — |

The gates are **floors, not targets**: they exist to catch a test file being deleted or a whole
area going untested, not to turn every refactor into a coverage negotiation. The branch figure now
sits close to its floor — 78.9% against 75% — which is worth watching rather than acting on.

### JaCoCo, not Kover

Task 1.11 asked for Kover. **Kover cannot be applied to this module at all.** Version 0.9.3 —
the newest that exists; there is no 0.10.0 — aborts during plugin application with:

```
Kover error: Kover requires extension with name 'android' for project ':shared'
since it is recognized as Kotlin/Android project
```

Under `com.android.kotlin.multiplatform.library` there is no project-level `android` extension to
find, because the Android configuration moved inside `kotlin { android { } }`. Kover offers no way
to opt out of that detection, so the choice was JaCoCo or no coverage. 0.9.1, 0.9.2 and 0.9.3 were
each tried and each fail identically.

### Measured on desktop only

Not a shortcut. `commonMain` is the code under test; `desktopTest` runs all 110 common tests plus
the 55 that need a UI; the Android host-test run executes *the same common sources* a second time.
Instrumenting both would double-count identical lines rather than reach new ones. What is
genuinely not measured is the two host modules — `AndroidSettingsStore`, `DesktopSettingsStore`,
`MainActivity`, the logcat sink — which have no tests and are excluded rather than counted as 0%.
The generated Compose resource accessors are excluded too.

## Logging

Nothing logged at all until the settings layer needed to explain itself.
[`log/Log.kt`](shared/src/commonMain/kotlin/com/tripletriad/log/Log.kt) is about eighty lines:
four levels, a `fun interface` sink, `println` by default.

```kotlin
Log.w(TAG, failure) { "settings file is not readable JSON; replacing it" }
```

**The message is a lambda**, so a suppressed line is never formatted. A logger that builds its
string and then discards it is one nobody dares call from the per-frame and per-card paths, which
is exactly where it would earn its keep. A test pins this by counting how many times the lambda
runs while the level is above it.

**No Napier**, which the plan named. Napier's job is to forward to `android.util.Log` on Android
and `println` elsewhere; that is the file above plus four lines in `MainActivity`, which installs
the logcat sink and holds release builds at `INFO`. `:shared` keeps no Android import. A
dependency earns its place by doing something hard.

### It has callers, which is the point

`UserSettingsRepository` had three `runCatching { }.getOrNull()` that discarded the failure with
it: an unreadable file, unparseable JSON, a failed write. All three now report. Repairing a
settings file silently is how a file that is rewritten on *every* launch goes unnoticed for
months.

Verified end to end rather than only in tests: a corrupt `UserSettings.json` was written onto a
Pixel with `adb shell run-as`, and the relaunch produced

```
W Settings: settings file is not readable JSON; replacing it
W Settings: kotlinx.serialization.json.internal.JsonDecodingException: Unexpected JSON token
            at offset 2: Expected quotation mark '"', but had 'n' instead at path: $
```

followed by a repaired file holding the device's own language.

## Screens and navigation

Twenty-one destinations, in a `remember`ed enum. The shape is a tree of depth three, and it forks
once at the top: without a server the character is a local `.sav`, with one it is an account.

```
SPLASH ─(startup finishes)─▶ MENU ─▶ PROFILES ──▶ PROFILE_NEW        (no server)
                              │      ACCOUNT ───▶ COLLECTION_CHOICE  (with one)
                              │        │  └────▶ SERVERS
                              │        ▼
                              │    DASHBOARD ─▶ OPPONENTS ─▶ MATCH
                              │        │            │         └ deck selector
                              │        │            ├───────▶ TUTORIAL
                              │        │            └───────▶ CAMPAIGN ─▶ CAMPAIGN_MATCH
                              │        ├───────▶ STATS ─▶ AVATAR
                              │        ├───────▶ CARDS / DECKS
                              │        ├───────▶ SHOP / INVENTORY
                              │        └───────▶ HELP
                              ├────▶ OPTIONS
                              └────▶ onQuit  (the host's business)
```

`CARDS`/`DECKS` and `SHOP`/`INVENTORY` are two tabs each of one screen, and four of these are the
navigation bar's own entries — see [`Navigation`](shared/src/commonMain/kotlin/com/tripletriad/ui/Navigation.kt).
[`ProfileGate`](shared/src/commonMain/kotlin/com/tripletriad/ui/ProfileGate.kt) is what lets the
thirteen screens behind the dashboard ignore which of the two sources the character came from.

The deck selector is a **step inside the match**, not a destination — which is where the original
put it, and for a reason: under `RULE_RANDOM` the hand is dealt from the whole collection and the
panel never opens, so whether the player is asked at all is not known until the roulette has been
drawn. `MatchScreen` resolves the rules first and asks only if they permit it.

`COLLECTION_CHOICE` is the other one-time step: `POST /accounts` carries a name and a password and
nothing else, so an account's character always starts on `ff14_`, and this is the only moment the
collection can be changed harmlessly — before a card, a deck or a match depends on it.

Every arrow reverses with the ‹ chevron or the system back gesture. **Play** on the menu goes to
the dashboard when a character is loaded and to the chooser when none is — the original's Continue
and Load Game behind one button, chosen by what is loaded rather than by asking.

**The dashboard is the hub, and that is the original's shape**, not an invention:
`dashboardScreen.as:49-59` builds this exact stack and every screen behind it returns to it.

**No Compose Navigation.** `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.3 specifies a `NavHost`
with named routes. There are no deep links, no arguments beyond what the session already holds, and
nothing to restore across process death that is not already on disk; what a navigation library would
replace is `Screen.up` and two `when`s. The point to reconsider it is "a screen reachable from two
places with a different back destination from each", which the dashboard is what prevents.

**The Android system back gesture is handled**: `BackHandler` from
`androidx.compose.ui.backhandler` — multiplatform, so no Android-only source set — returns to the
menu from a match or the options. Without it, back mid-match finished the activity and the app
appeared to quit from the middle of a game. It is deliberately *disabled* on the menu, where
leaving the app is the right answer.

### Splash

The AS3 build had no splash — Flash's own preloader covered the wait and `MenuScreen` was the first
thing drawn. This is not a port of anything: an installed APK has no preloader, and the update
check that is coming needs somewhere visible to live.

So startup is an **ordered enum, not a spinner**:

| `StartupPhase` | Waits for | Line |
|---|---|---|
| `SETTINGS` | `UserSettings.json` — it decides the language everything after is shown in | `reading settings…` |
| `CARDS` | `cards.json`, 263 records | `loading cards…` |
| `ART` | the nineteen shared textures | `loading artwork…` |
| `READY` | nothing; terminal | `ready` |

Adding an update check is one entry, one branch in `rememberStartup`, and one string. A bare
spinner would have had nowhere to put something that can be slow *and can fail*.

The sequence is now **sequential**, where the two loads used to run concurrently with the match
gated on neither. That was right without a splash — the board arrived a fraction sooner — and wrong
with one: the artwork pop-in happened in front of the user instead of behind a progress line. What
did *not* change is that `CardArt` is still nullable everywhere downstream, so a failed art load
still costs appearance and not playability.

### Main menu

`MenuScreen.as` in shape — the `logo_white_512` wordmark centred over a vertical stack, 8 px gap —
with four actions: **Play, Characters, Options, Quit**, and a line under the logo naming the loaded
character. The original's Continue / New Game / Load Game are the first two of those, folded
together: which one Play means is decided by whether a character is loaded, not by asking.

### Dashboard, and what hangs off it

The character's own menu: Play, the record, the collection, the decks, the bag, the shop, the rules,
and Logout. Everything the data layer exists for is reachable from here, and everything these
screens change is written through [`ProfileGate`](shared/src/commonMain/kotlin/com/tripletriad/ui/ProfileGate.kt)
— a local `.sav` through `ProfileSession`, an account through `AccountSession`, and no screen below
it knows which.

Logout means what it says on a build with a server: the token is dropped and the session ended, not
merely the screen changed. The original navigated away and left `Game.PROFILE_DATAS` loaded.

Three screens fix something the original got wrong rather than merely porting it: **a purchase is
now saved** (`shopScreen.as:149` ends on a commented-out `Save.save`), **Reset on a deck actually
empties it** (`resetDeckHandler` calls `slice` where `splice` was meant, so the deck came back on the
next load), and **discarding a bag item asks twice** (the original's handler opens on
`// TODO : afficher une Alert` and then destroys it on the first tap). The full list, with the
AS3 line numbers, is in `docs/migration/08-PHASE-4-UI-LAYER.md`.

`onQuit` is a parameter, not something `:shared` does: `finish()` on Android, `exitApplication` on
desktop, and on iOS nothing at all, since Apple's guidelines have no "quit". A test asserts the
callback fires and that the menu stays put — `:shared` must not try to leave an app.

### Options

The three fields `UserSettings.json` actually holds, under the AS3's own headings
(`STR_GENERAL_SETTINGS`, `STR_AUDIO_SETTINGS` — `SettingsScreen.as` splits them the same way).

**Changes apply and persist immediately.** There is no Save button and `STR_SETTINGS_SAVED` — which
exists in all four bundles — is not used: on a phone, a pane you can leave with a back gesture must
not be able to lose what you just did, and picking a language redraws the screen in it, which *is*
the confirmation.

**The volume sliders work**, one per channel, and reach the running music as they are dragged —
see [§ Audio](#audio). They were shipped before the audio was, with a caveat under them saying
`saved, but nothing plays yet`; that string is now unused and stays in the bundles for the next
thing that is half-built.

Nearly every label came for free: `STR_PLAY`, `STR_SETTINGS`, `STR_QUIT`, `STR_LANGUAGE`, both
volume labels and both headings are all in the imported bundles, translated four ways. Only five
new `APP_*` keys were needed — `APP_BACK`, `APP_AUDIO_PENDING` and three `APP_STARTUP_*` — which is
why the app-owned count went from 5 to 10 and not to 17.

## Playing a card

Two ways, and the original has both: **tap a card then tap a cell**, or **drag the card onto the
cell**. `Card.onTouch` dispatches `TRIGGERED` on a tap and starts a drag on a move; the migration
plan's own note says not to ship drag-only, and dragging into a 3×3 grid on a phone is fiddly.
Compose keeps the two apart on its own — `clickable` gives up once the pointer passes touch slop,
which is exactly where the drag begins.

The drag is hit-tested by hand, in **root coordinates**: Compose's `Modifier.dragAndDropTarget` is
for drags *between applications*, and there is no in-process equivalent. A card converts its pointer
with `localToRoot`, each free cell registers its `boundsInRoot`, and
[`BoardDragState`](shared/src/commonMain/kotlin/com/tripletriad/ui/BoardDragState.kt) matches them.
An occupied cell registers nothing, so it never lights up — the refusal is visible while the card is
still in the air, where the original checked only after the finger lifted.

Only the player's own **playable** cards can be lifted, which is `Card._draggable` plus whatever
`RULE_ORDER` or `RULE_CHAOS` allows this turn. Lifting a card the rules forbid and having the drop
do nothing is worse than not being able to lift it.

**And there is a clock.** Thirty seconds a turn, shown as a bar under the status line, and when it
runs out a card is played for you — a *random* one on a *random* free cell. That is
`playerPanel._timer = 30` plus `BaseMatchScreen.timeUp_play` → `autoPlay()`, and the randomness is
the penalty: reusing the opponent's AI would reward inattention with a good move. The original arms
both players' clocks but listens to only the player's, so only the player's is drawn.

## Theme

One dark theme, built from `theme/BaseTTOTheme.as` rather than from Material's defaults:
`PRIMARY_BACKGROUND_COLOR`, `LIST_BACKGROUND_COLOR`, `LIGHT_TEXT_COLOR` and the rest, transcribed in
[`ui/theme/Colors.kt`](shared/src/commonMain/kotlin/com/tripletriad/ui/theme/Colors.kt) and pinned
by `ThemeTest`. The card colours travel beside the scheme in `TtoColors`, because "the blue player's
card" is not one of Material's thirty-odd colour roles and forcing it into `tertiaryContainer` would
make every call site read as a lie about what it is drawing.

**The font is Raleway**, which is what `BaseTTOTheme.as:118` declares and what `:115-116` embed —
Regular as `normal`, Medium as `bold`, so the game's "bold" is a medium weight. The migration plan
named Eurostile and warned that redistributing it might need a licence; Eurostile appears once in
the whole AS3 source, drawing the `±N` modifier on a card, and Raleway ships under the SIL Open Font
License with its own `OFL.txt`. `tools/import_fonts.py` copies both faces and the licence.

Raleway has no CJK coverage, so `ja_JA` falls back per glyph to the platform face — Latin stays
Raleway in the same line. The AS3 needed two `Noto-ja` bitmap fonts for this; Skia and Android do it
themselves.

The **type scale is re-anchored, not transcribed**. The AS3's four sizes (18/24/28/36) are pixels at
326 DPI, which convert to about 9/12/14/18 dp — too small on anything that is not a 2013 Retina
display. The ladder's shape is kept and its anchor is not, the same judgement the card geometry gets.

Before this existed the app ran on `darkColorScheme()` and every Material control was hand-coloured
at its call site to hide the default purple. Fourteen screens now read the theme, and no
`Color.White` or `fontSize` literal is left in any of them.

## Audio

`SoundManager.as` is ported as
[`audio/AudioPlayer.kt`](shared/src/commonMain/kotlin/com/tripletriad/audio/AudioPlayer.kt) — an
interface plus a `Sound` enum — with
[`AndroidAudioPlayer`](androidApp/src/main/kotlin/com/tripletriad/android/AndroidAudioPlayer.kt)
behind it. Same seam as `SettingsStore`, and for a stronger reason: there is no multiplatform audio
API at all.

### The files are not converted, and this is the measurement that says so

Every MPEG frame header in `sources/bin/sounds/` was parsed rather than assumed:

| | |
|---|---|
| codec | MPEG-1 and MPEG-2 Layer III |
| sample rates | 22 050 / 44 100 / 48 000 Hz |
| channels | **21 of 22 mono**; only the music is stereo |
| bitrate | 17 of 22 are VBR — all 22 carry a Xing header |
| total | 22 files, 1.40 MB, 84.5 s |

**No conversion is needed for Android**, which is the only target that plays anything today. Every
one of those combinations is in Android's *mandatory* decoder set at every API level from 24 up, so
`SoundPool` and `MediaPlayer` take the bytes as they are. The Xing headers matter for exactly one
file — the music, which seeks back to its loop point — because a VBR seek without one is a bitrate
guess.

Converting would cost something and buy nothing here:

| Target format | Verdict |
|---|---|
| **Ogg Vorbis / Opus** | Android supports both, so no gain. Would *lose* a future iOS: neither is decoded natively by AVFoundation, where MP3 is |
| **WAV / PCM** | The only format the desktop JVM decodes out of the box — at 6–10× the size, for a host that exists so the UI can be run without an emulator |
| **AAC / M4A** | Supported on Android and iOS, so no compatibility gain over MP3, and a re-encode of already-lossy audio |

**One caveat, and it needs ears rather than a build.** MP3 cannot loop sample-exactly: every encoder
adds decoder delay at the start and pads the end to a whole frame, so the seek back to 16.374 s
lands a few milliseconds off and may click. Ogg Vorbis would fix that on Android. Whether it is
audible over the loop point of this particular track is a judgement no test here can make — and the
AS3 original was *worse*, since it polled the position every frame and could overshoot by a whole
frame before restarting. If it turns out to click, the fix is to convert that **one** file.

### What is played, and what is left out

`Sound` names **ten** of the original's twenty-two files. The rest are accounted for rather than
forgotten, by [`tools/import_sounds.py`](tools/import_sounds.py), which fails if a file is neither
played nor explained:

* **ten are referenced by no call site at all** in the AS3 source — including `flip`, whose only two
  calls are commented out in favour of `se_ttriad.scd_157`, and `win`, which the win sounds are not;
* **two belong to the coin flip** (`anims/PileOuFace.as`), whose own sounds the AS3 never wired up.

That is 0.31 MB of sounds nothing could play, left out of the APK.

| Moment | AS3 id | From |
|---|---|---|
| match music, looping from 16.374 s | `shuffle_or_boogie` | `BaseMatchScreen.as:114` |
| hands dealt | `se_ttriad.scd_2` | `:157`, `openPhase()` |
| placed, captured nothing | `se_ttriad.scd_1` | `TTOCore.as:87` |
| a card changes hands | `se_ttriad.scd_157` | `Card.as:229`, in `flipTo` |
| a combo propagates | `se_ttriad.scd_15` | `TTOCore.as:125` |
| turn changes | `se_ttriad.scd_4` | `BaseMatchScreen.as:374` |
| blue / red wins | `se_ttriad.scd_7` / `_8` | `PVEMatchScreen.as:95` / `:139` |
| any control tapped | `se_ui.scd_72` | `TouchLabel.as:31` |
| next match | `se_gs.scd_162` | `RematchPanel.as:36` |

A draw is silent, which is the original's behaviour: its draw branch plays nothing.

### No Media3, and two engines rather than one

Task 1.5 names Media3. It is not needed: `SoundPool` and `MediaPlayer` are platform classes older
than `minSdk 24`, and between them they do everything `SoundManager` did. **No dependency was
added.**

The two are for different jobs, and the split is `SoundManager`'s own (`NOISE_CHANNEL` against
`BACKGROUND_CHANNEL`): `SoundPool` keeps short sounds decoded and overlaps them, so three cards
flipping in a combo do not cut each other off, but it would hold a 64-second track as ~11 MB of PCM
and cannot loop to a point. `MediaPlayer` streams and seeks, and would add latency if constructed
per tap.

### Three deviations from the original, each deliberate

1. **The cross-fade is not reproduced.** `fadeSoundChannel` was called with a 150 ms delay and
   stepped the volume by 0.01, so fading out from 1.0 took a hundred steps — **fifteen seconds**,
   not 150 ms — and it compared a float to `0` exactly. There is one track, so nothing cross-fades.
2. **The capture sound plays once per placement, not once per card.** `Card.as` fires it inside
   `flipTo`, so four simultaneous flips fired it four times; on `SoundPool` that is the same sample
   four times in the same millisecond — a volume spike, not a richer sound.
3. **The music pauses when the app is backgrounded** and resumes where it left off. AIR on a desktop
   had no notion of being backgrounded. Pause rather than stop, because backgrounding does not change
   the composition: the effect that starts the music would not fire again, and the match would come
   back silent.

### Verified on a device, because nothing else can verify it

No test can hear anything. What the tests pin is the **mapping** — which moment asks for which
sound — through the real UI with a recording player.

On the phone, `dumpsys audio` during a match:

```
type:android.media.MediaPlayer  state:started  usage=USAGE_GAME content=CONTENT_TYPE_MUSIC
    sampleRate=44100  channelMask=0x3        <- shuffle_or_boogie, stereo, decoding
type:android.media.SoundPool    state:idle    usage=USAGE_GAME content=CONTENT_TYPE_SONIFICATION
```

The music is genuinely decoding from the unconverted MP3, and no `SoundPool` load failed — a failure
logs a warning, and there were none.

**What remains unverified**: whether the loop point clicks, and whether the mix sounds right. Both
need someone to listen.

## Rules engine

The rules are implemented as **pure functions over immutable state** — no UI, no coroutines,
no display objects. `RulesEngine.resolve(board, position, card, player, tally)` returns a `Resolution`: the resulting
board plus every capture, each tagged with its kind and its combo wave.

The engine and the model it works over live in **`tto-core`**, published as
`com.tripletriad:core` and consumed here as an ordinary dependency — see `settings.gradle.kts`
for the two repositories that can serve it. What follows describes that library; it is here
because it is what the app plays by.

That shape is the point. The AS3 original keeps domain state *inside* Starling display
objects — `Card.modifier` has no backing field, it is stored in a `TextField` and parsed back
out — which is why its rules engine cannot be unit-tested and why its AI dry run corrupts the
board it is evaluating. See
[data-flow.md § 1.1](docs/analysis/data-flow.md) and
[§ 4.3](docs/analysis/data-flow.md).

| Type | What it holds |
|---|---|
| `GameRules` | the 12 rule slots — **3 enums and 9 booleans**, not 20 flags, so Ascension and Elemental are mutually exclusive by construction |
| `Board` | immutable 3×3, positions 0..8 row-major; `place()` and `capture()` return new boards |
| `effectivePower(…)` | printed power → Fallen Ace → type modifier → clamp, in that order |
| `AscensionTally` | the board-wide per-type counter behind Ascension and Descension |
| `RulesEngine` | capture resolution, precedence, and combo propagation |
| `TurnOrder`, `score()` | 9 placements, 5 for the first player and 4 for the second; score counts unplayed cards |

### Two power ranges, not one

**Card powers are 1..10. Effective powers are 0..10.** The floor is zero because Fallen Ace
produces 0 directly and Descension can drive a 1 down to 0. `Card` enforces `1..10` in its
`init` block, which is right for card data and would be wrong for a tile — hence
`MIN_EFFECTIVE_POWER` and `clampPower`, the AS3 `tools.madmax`.

### Two deliberate departures from the source

Both are recorded in [game-rules.md § 15](docs/analysis/game-rules.md#15-defects-and-ambiguities)
and both change the outcome of real games, so neither is silent. They live in
`RulesEngineOptions`, and `RulesEngineOptions.FAITHFUL` reproduces the original including its
defects — used by tests that pin both behaviours.

1. **Same and Plus use effective powers** (§ 15.4). The AS3 computes them from *printed*
   values while basic capture uses modified ones, so under Elemental or Ascension the two
   disagree. FF14 uses the modified values, and the author's own comment at `TTOCore.as:215`
   reads as uncertainty rather than a decision.
2. **Same Wall fires with one neighbour** (§ 15.2). The AS3 gates it behind
   `same.length > 1`, which makes the rule inoperative in exactly the board states it exists
   for — a wall is meant to *be* the second match.

The defaults are the corrected behaviour rather than the original because the AIR client is
abandoned and unrunnable, so bit-for-bit fidelity is unverifiable anyway, while FF14 remains a
reference anyone can check by playing it. Flip either option to reverse the choice.

### What is not implemented

Nothing, on the solo side: the roulette, the pre-match chain (Random hand, Swap, Open, the coin
flip), Order and Chaos, Sudden Death, the AI and the match state machine are all in place and
driven from the screens. What is left is **local PvP** — the peer half of `MatchView`, whose
transport is still undecided.

## Running on a real Android device

### From Android Studio

Open the **repository root** — the Gradle build is the root, so Studio finds the three
modules on sync. (Before the project was promoted out of a `kotlin/` subdirectory, the root
had no `settings.gradle.kts` and Studio found nothing.) After the sync, pick the
`androidApp` configuration, select the device, Run.

If the sync is refused with something like *"AGP version not supported"*, the IDE is older
than AGP 9.3.1 requires. Either update Studio,
or lower `agp` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — in which case
also lower `androidCompileSdk` to 35 and let AGP download that platform. The command line
below does not depend on the IDE version at all.

### From the command line

Build, install and launch:

```bash
./gradlew :androidApp:installDebug
```

```bash
adb shell am start -n com.tripletriad.android/.MainActivity
```

Useful while testing:

```bash
adb logcat -s AndroidRuntime:E System.err:W
```

```bash
adb shell input tap 1200 450
```

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

```bash
adb uninstall com.tripletriad.android
```

Two Windows traps, both hit during this work:

- Do **not** pipe `adb exec-out screencap -p` into a file from PowerShell — the
  redirection re-encodes the stream and corrupts the PNG. Write it on the device and
  `adb pull` it, as above.
- Under **Git Bash / MSYS**, `adb shell screencap -p /sdcard/s.png` fails with a usage
  message: MSYS rewrites `/sdcard/...` into a Windows path before `adb` sees it. Run adb
  from PowerShell, or prefix the command with `MSYS_NO_PATHCONV=1`.

If the device is paired over Wi-Fi (`adb devices` shows an
`adb-…._adb-tls-connect._tcp` entry rather than a serial), expect to re-pair when the
connection drops; USB is steadier for tight iteration.

## Verified build results

Run on Windows 11, JDK 17 (Temurin), Android SDK platform 36.1 / build-tools 36.0.0.

| Command | Result |
|---------|--------|
| `./gradlew clean` then `./gradlew build assembleRelease` | **BUILD SUCCESSFUL**, 264 tasks |
| `./gradlew :androidApp:assembleDebug` | **BUILD SUCCESSFUL** — `androidApp-debug.apk`, 19 070 KB |
| `./gradlew :androidApp:assembleRelease` | **BUILD SUCCESSFUL** — `androidApp-release-unsigned.apk`, 16 141 KB |
| `./gradlew :desktopApp:build` | **BUILD SUCCESSFUL** — `desktopApp.jar` |
| `./gradlew :shared:desktopTest` | **523 tests, 0 failures** |
| `./gradlew :shared:build` (all targets) | **786 test executions, 0 failures** |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** — 0 findings, `maxIssues = 0` |
| `./gradlew :shared:lint` | **0 errors**, warnings only ("a newer version is available") |
| `./gradlew :desktopApp:run` | window opens, titled "Triple Triad", nothing on stderr |
| `./gradlew :androidApp:installDebug` + launch | **runs on a physical device** — see below |

Release APK note: `isMinifyEnabled = false`, so 16 141 KB is an **un-shrunk upper bound**,
not what a shipped build would weigh.

### On a physical device

Installed and launched on a **Pixel 6a, Android 17 (API 37), arm64-v8a**, 1080×2400 at
420 dpi, paired over adb-over-Wi-Fi:

- `installDebug` → "Installed on 1 device", then `am start` →
  `topResumedActivity=com.tripletriad.android/.MainActivity`.
- Nothing from `AndroidRuntime` or `FATAL` in logcat; the only app line is
  `ProfileInstaller: Installing profile for com.tripletriad.android`.
- **Both orientations verified by screenshot.** Landscape (2400×1080): red hand left in a
  2×3 block, board centred, blue hand right, every card at the authored 104×128 with no
  overlap and nothing clipped. Portrait (1080×2400): red hand a strip across the top, board
  centred, blue hand across the bottom. Rotating a running match keeps it (`configChanges`).
- **The status bar, the navigation buttons and the clock/battery/signal row are hidden** —
  `MainActivity.goFullScreen`. Recoverable with an edge swipe.
- **The artwork renders and the layers stack correctly.** The colour quad shows through the
  translucent centre — the same picture reads blue in one hand and red in the other; the rarity
  row sits top-left, the type icon top-right, the digit badge over the artwork and not under it.
- Placement, capture and the flip all work under real touch and under `adb shell input tap`.
  A match played out to nine placements ended `blue 5 — 5 red` / `draw`, with four cards
  showing their captured colour and red's unplayed card still counting for red. A scripted
  capture — a card with `left = A` played beside one with `right = 3` — moved the score from
  `5 — 5` to `6 — 4` and left the flipped card's artwork, stars and digits upright.
- A power of 10 renders as `A` (visible on `Laguna 9/5/3/A`).

Measured on that device, **debug build** (no R8, no baseline profile — pessimistic):

| Metric | Value |
|---|--:|
| Cold start (`am start-activity -W`, 2 runs) | 658 ms / 752 ms |
| TOTAL PSS, idle | 72.4 MB |
| Java heap / Native heap / Code / Graphics | 12.7 / 6.5 / 29.2 / 3.0 MB |
| Frame timing, jank % | **not measured** — see [Known issues](#known-issues) |

API 37 is above the declared `targetSdk 36`, which is fine — Android is backward
compatible in that direction — but it does mean this run did not exercise any behaviour
gated on targeting 37.

### Test breakdown

**523 distinct tests; 786 executions** — the 263 in `commonTest` run once per target
(`desktopTest` and `testAndroidHostTest`) and the 260 in `desktopTest` once. 0 failures.

`commonTest` is everything that needs no resources and no Compose tree: the save format and its
repositories, the string lookup and its fallback chain, the settings, the log, the sounds, the
network client and the offline queue. `desktopTest` is the rest — the real `App()` driven through
the real screens, plus the reads of the shipped bundles, which are JVM-only because they go through
the packaged resources.

Line coverage is **96.8%**, branch **78.9%**, measured on the desktop target and gated in
`check` — see [§ Coverage](#coverage).

`RulesEngineTest` lives in `tto-core` now, with the engine: it is the
[§ 16 test matrix](docs/analysis/game-rules.md#16-test-matrix-for-the-port) from the rules
specification, case for case.

**The suite is not vacuous.** Three checks that are load-bearing rather than incidental:

- `CardBundleTest` reads the shipped `cards.json` out of the actual resource bundle, so it fails if
  the resource is dropped from packaging, if the generated `Res` accessor moves, or if the JSON
  schema drifts from the model.
- `MatchUiTest` drives the real `App()` — pick a card, pick a cell, nine times over — and asserts
  invariants rather than a particular board: the turn passes, an illegal placement is swallowed
  rather than thrown, the score always totals 10, a finished match announces a result. Every one of
  its tests also covers resource packaging, because `App()` shows nothing until the bundle parses.
- `MatchLayoutTest` covers `matchLayout`, a pure function of a measured width and height precisely
  so it *can* be covered. `theArrangementAlwaysFitsInTheSpaceItWasGiven` checks, across nine
  viewports, that two hand areas plus the board do not exceed the bounds — an over-subscribed
  column is not a visible error, since `Modifier.size` silently coerces into its constraints, so
  children collapse to zero height while continuing to draw at full size.

## Localisation

Four languages, from the original's own bundles: **English, French, German, Japanese**
(`utils/conf.as:11`, `application.xml`). The device language is narrowed to the nearest of the
four and everything falls back to `en_US`.

```
shared/src/commonMain/composeResources/files/locales/
  tto-<tag>.json    687-688 keys, imported by tools/import_locales.py — do not edit
  app-<tag>.json    5 keys this port wrote — edit these
```

The split is provenance. `tto-*` is Square Enix wording that must stay exactly what the original
displayed; `app-*` exists because the AS3 showed whose turn it was *graphically* and never wrote
the sentence. `Strings` merges them with `app-*` on top, then falls back to English.

`app-de_DE.json` and `app-ja_JA.json` are `{}` on purpose. Those five sentences are not
translated into German or Japanese — they resolve to English while the other 647 / 680 keys stay
in the device's language. An empty file states that; a missing one would read as an oversight.

### What the source data turned out to be

The plan (Task 1.10) said the bundles were under `sources/bin/assets/{de_DE,…}/`. They are not:
those four directories hold `rules.png` + `rulesAtlas.xml`, a *texture* atlas of rule-name
images. The strings are already JSON, in **`sources/bin/datas/locales/`**.

Four defects in that data, all reported by the importer on every run:

| Defect | Detail |
|---|---|
| Duplicate key, different values | `STR_REGISTER_MATCH` twice in `en_US` ("Create Match" then "Defy") and `fr_FR`. Resolved **last-wins**, which is what AS3's `JSON.parse` did, so the port shows what the original showed. Pinned by a test, because the alternative is an implementation detail silently choosing a product string. |
| Keys absent from the fallback | `RULE_OPEN` (only `de_DE`, a pre-rename leftover), `STR_GSGROUP` (only `fr_FR`), and two malformed `ja_JA` keys — `STR_SAVES_LISTは` and a `STR_NPC_MA_DINCHT` with two trailing zero-width spaces. Unreachable typos; kept rather than quietly deleted. |
| Uneven coverage | `de_DE` is 44 keys short of the union, `ja_JA` 11. `STR_NEXT_MATCH` is one of them, which is why the reset control reads "Next Match" on a German device — a real fallback, exercised by the shipped data rather than by a contrived fixture. |
| Markup in one locale only | `fr_FR` prefixes 18 `RULE_*_HELP` values with `<i>FF14 uniquement</i>`; Feathers rendered HTML in a text field and Compose's `Text` does not. Nothing displays rule help yet, so the markup is left in the data — stripping it now would hide that the rules screen needs an `AnnotatedString` converter. |

Mistranslations are also present and **not** fixed. `STR_DRAW` is "Zeichnen" (de) and "描く" (ja),
both meaning *to draw a picture* rather than *a tie*. The main menu adds two more, now that
`STR_PLAY` is on screen: it is **untranslated in German** ("Play") and in Japanese reads **"再生"**,
which is *playback* — the button on a media player, not an invitation to play a game. All of them
are the original's strings, they are visible on the app's most prominent button, and overriding them
is a product decision rather than a porting one. `app-<tag>.json` is the mechanism when it is taken:
an entry there wins over the imported bundle. Inventing replacements inside a bundle that is
evidently machine-translated would not make it trustworthy, so they are reported instead.

### Verified on the device

French renders end to end. Japanese renders too — **あなたは勝つ！** for a win — with no bundled
font: the port uses the platform font family, so Android's CJK fallback covers it. The plan's
"Japanese needs a CJK font" warning is about `Eurostile`, which `Card.as:81` bundles for card
text and this port does not use; the concern returns if that font is ever adopted.

One real bug came out of switching language, and only the screenshot could see it. The status bar
was three items in a centred row, which fitted because every string was English — French pushed
"Match suivant" onto a second line. The turn line now takes the leftover width and elides; the
controls keep theirs. **No test in the suite can catch that**, because wrapping does not change
the semantics text a Compose test reads.

## User settings

`UserSettings.json` — three fields, exactly the ones `utils/conf.as` wrote:

```json
{ "language": "fr_FR", "background_volume": 1.0, "noise_volume": 1.0 }
```

The snake_case keys are deliberate and pinned by a test: a file written by the AS3 build has to keep
parsing, and one written here has to stay readable by it. Two volumes rather than one because
`SoundManager` has two channels — with a single stream every card flip would duck the music.

First run writes the file, seeded from the device language, which is `conf.as:22-40`. After that
**the file decides**: changing the device language does not follow, as in the original, and the
settings screen is where it is meant to be changed. Verified on a French device — a file saying
`en_US` produces an English UI.

### Where it lives, and why not where the AS3 put it

| Host | Path |
|---|---|
| Android | `Context.filesDir/UserSettings.json` |
| Desktop | `~/My Games/Triple Triad Online/UserSettings.json` |

`conf.as:14` wrote `My Games/Triple Triad Online/UserSettings.json` under AIR's
`documentsDirectory` — shared external storage on Android. Scoped storage (API 29+) closed that
off, `minSdk` is 24 so a legacy path would need a permission prompt plus a runtime branch, and a
settings file has no business being visible to a file manager. App-private storage is the right
target; the file name is kept so the contents stay recognisable.

### No `expect`/`actual`, on purpose

Task 1.6 asks for `expect class FileManager`. That is the wrong shape here:

- **`expect` obliges every declared target to supply an `actual`,** and `:shared` declares the three
  iOS targets. Kotlin/Native cannot build Apple targets from a Windows host, so an iOS `actual`
  could not be compiled — let alone run — before being pushed; the macOS CI job would be the first
  thing to see it. Writing unverifiable code to satisfy a target that was explicitly descoped
  ("Android only for now") buys nothing.
- **The Android implementation needs a `Context`,** which means threading one into a composable or
  parking it in a global.

So `:shared` declares a `SettingsStore` interface and each host supplies the implementation it can
actually build. `:shared` keeps no platform file access at all, and when iOS returns it implements
the same interface with `NSFileManager` — still no `expect`. The Android store writes to a temp file
and renames, so a kill mid-write leaves the old settings rather than a truncated file.

A corrupt file is **repaired, not fatal**: `conf.as` would have thrown out of `JSON.parse` and taken
the launch with it. Nothing in this file is worth failing to start over, and an unrepaired file
would throw on every launch thereafter.

## Known issues

**Three deprecation warnings remain, none of them ours.** `./gradlew build --warning-mode all`
prints exactly these, all from plugin internals and all scheduled for removal in Gradle 10:

| Warning | Raised by |
|---|---|
| `ReportingExtension.file(String) has been deprecated` | detekt 1.23.8, `DetektPlugin.kt:28` |
| `The archives configuration has been deprecated for artifact declaration` | Kotlin Multiplatform, when `jvm("desktop")` registers its jar |
| `Declaring dependencies using multi-string notation has been deprecated` | Kotlin Multiplatform, resolving `kotlin-native-prebuilt` |

**No frame-timing measurement.** `dumpsys gfxinfo` recorded zero frames because the device's screen
locked partway through the session, so the plan's "60+ FPS" criterion is unverified — the
animations look smooth, which is not a measurement. The commands to fill this in are in
[docs/analysis/performance-baseline.md](docs/analysis/performance-baseline.md) §2.

**No card-internal layout assertions.** `MatchLayoutTest` covers the *arrangement* — which hand
goes where, at what scale, and that it fits. Nothing asserts where a layer sits *inside* a card, so
a regression that moved the digit badge would pass CI. Since the whole point of `CardColors.kt` is
reproducing exact AS3 coordinates, `assertLeftPositionInRootIsEqualTo` and friends would close it.

**Starling's easing curves are not Compose's.** `Transitions.EASE_IN` / `EASE_OUT` are mapped to
`FastOutLinearInEasing` / `LinearOutSlowInEasing`, which are the closest equivalents and not the
same functions. A visual diff pass against the original is still owed — see
[docs/analysis/api-mapping.md](docs/analysis/api-mapping.md).

**Portrait support is a deliberate departure.** `application.xml` declares
`<aspectRatio>landscape</aspectRatio>` and the AS3 build is desktop-only, so the original has
exactly one arrangement. A phone does not, so `matchLayout` picks between two: hands either side
of the board in landscape (the FFXIV arrangement — opponent left, player right), above and below
it in portrait. The `screenOrientation` lock is therefore gone from the manifest. Board tiles get
their own scale, always ≥ the hand scale, because a portrait hand is five cards across where the
board is three and would otherwise leave a third of the screen empty; FFXIV draws the board
larger than the hands too.

**A card is scaled by multiplying its geometry, not by scaling its render layer.** Measuring at the
authored size and shrinking with `graphicsLayer` reports a small size while drawing a large one, so
anything that promotes the composable to an offscreen layer — a dimmed hand does, because
`alpha < 1` forces one — clips it. Multiplying every dp and sp by the scale keeps drawn bounds and
reported bounds equal. Noted on `CardFace`.

**CI runs five jobs** from [`.github/workflows/build.yml`](.github/workflows/build.yml), including
`ios-framework` on `macos-latest` — `linkDebugFrameworkIosSimulatorArm64` plus
`iosSimulatorArm64Test`, which is the only Apple compilation this project gets, since it cannot be
run from the Windows host. It proves the framework links and its common tests pass; there is still
no `.xcodeproj`, so no iOS *app* has been built.

`gradle/actions/setup-gradle` is pinned to **v5, deliberately not v6**: v6 moves caching into a
proprietary `gradle-actions-caching` component whose use implies accepting Gradle's Terms of Use.
That is a licensing call for the project owner, not a maintenance bump.

### iOS caveat

The iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) are declared and each produces
a static `shared.framework` — `linkDebugFrameworkIosSimulatorArm64` and friends exist in
the task graph. **They have never been built on this host**, because Kotlin/Native cannot
compile Apple targets on Windows; those compilations are skipped there, which is why
`./gradlew build` still succeeds locally. They *have* now been built on the
`macos-latest` CI runner — see the `ios-framework` note below.

`iosApp/*.swift` contains the SwiftUI host, but **there is no `.xcodeproj` or
`.xcworkspace`** — an Xcode project cannot be authored meaningfully off a Mac. To finish
the iOS side, on macOS: create an iOS App target, add `iOSApp.swift`/`ContentView.swift`
to it, and add a "Run Script" build phase calling
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Until someone has done that and
run it, **the iOS app remains unvalidated** — do not report it otherwise.

The `ios-framework` CI job was the project's first real Apple compile, and it passed: the
shared framework links for `iosSimulatorArm64` and its common tests run there. That closes
the "does the shared code compile for Apple at all" question and leaves only the app shell
— no simulator run, no UI, no `.xcodeproj`.

## What is proven, and what is left

Proven by execution — the game is playable end to end, on Android and on the JVM desktop, against
a real account server:

- Kotlin 2.2.20 + Compose Multiplatform 1.9.3 + kotlinx.serialization 1.9.0 + AGP 9.3.1
  + Gradle 9.6.1 + Ktor are a working combination.
- One `commonMain` Compose UI runs on Android and on the JVM from a single source: 28 of the
  original's 32 screens, the whole rules engine, the tutorial and both tournament ladders.
- **The artwork is the original's.** 263 card faces as individual files, three thumbnail atlases
  sliced with `BitmapPainter`, 27 avatars, 84 opponent portraits, and the twenty match captions in
  four locales — which was the highest unvalidated risk when this was a proof of concept.
- Drag-and-drop alongside tap, on a 3×3 board, in common code — Feathers' `DragDropManager` has no
  Compose equivalent and needed none.
- Accounts, sessions and an offline queue over Ktor, verified against the local Docker/Postgres
  container. The legacy socket protocol was abandoned rather than ported; matches are replayed and
  verified server-side from a signed transcript, which only works because the engine is pure.
- ktlint and detekt run clean at `maxIssues = 0`, and coverage is gated in `check`.

Left:

- **Local PvP** — `MatchView` and the peer protocol; the transport is undecided.
- **iOS.** The SwiftUI host sources exist; nothing has ever compiled them, and doing so needs a
  Mac. See the caveat below.
- **Store release**, which is out of scope by decision.

## Fidelity to the AS3 source

Every value below was read out of the original rather than invented. The geometry is
placed by absolute offset in the AS3 sprite's coordinate space so it can be checked
line-for-line.

| Here | AS3 source |
|------|-----------|
| card sprite 104 × 128 dp, face centred | `this.width = 104; this.height = 128` — `Card.as:60-61` |
| coloured face 88 × 118 dp at (8, 5) | `new Quad(88, 118, 0x5a595a)` at `x=8, y=5` — `Card.as:73-75` |
| `BlueCard = 0xFF2D4660`, `RedCard = 0xFF602D2D`, `GreyCard = 0xFF5A595A` | `Card.BLUE_COLOR` / `RED_COLOR` / `GREY_COLOR` — `Card.as:29-31` |
| digit cluster at (28, 88), bounds 44 × 30 | `_digits.x = 28; _digits.y = 88` — `Card.as:89-90` |
| `cdbg` plate 28 × 28 at (8, 1), alpha 0.5 | `CardDigits.as:26-29`, size from `assets/digits/digits.xml` |
| digits 18 × 18 at (14,0) (26,6) (14,12) (2,6) | `CardDigits.positions` — `CardDigits.as:13`; sizes from `digits.xml` |
| power order top / right / bottom / left | `// power [top, right, bottom, left];` — `CardDigits.as:22` |
| power 10 renders as `A` | `CardDigits` picks `cdA`; there is no `cd10` in `digits.xml` |
| rarity row at (1, 1) relative to the face | `{rarity}stars` texture at (9, 6) in sprite space — `Card.as:176-178` |
| type marker at x = 72 relative to the face | `type-{type}` texture at (80, 3) in sprite space — `Card.as:181-183` |
| no action bar, no system bars | `application.xml`: `fullScreen true` |
| 400 ms flip, four 0.1 s legs | `Card.as:249-291` — `flip`/`yoyo`/`unflip`/`yoyo2` |
| `scaleY` 1→0→1.2→0→1, `scaleX` 1→1.2→1 | same, `horizon = false` |
| rarity row at (9, 6), 29×28 | `Card.as:177-178`; size from `card_rarities` |
| type icon at (80, 3), 20×20 | `Card.as:182-183`; size from `card_types` |
| artwork 104×128 at (0, 0), over the colour quad | `Card.as:169-170` |
| card back on top, shown while flipping | `Card.as:93-94` |

## Licensing note

⚠️ **This repository ships Square Enix material, and there is no reading of it that does not.**

[`cards.json`](shared/src/commonMain/composeResources/files/cards.json) carries the **names and
stats of all 263 cards** — "Dodo", "Geezard", "Odin", the FFXIV tribes, the `STR_FF14_CARD_*` i18n
keys — and `composeResources/files/art/` carries the **artwork itself**: card faces, thumbnail
atlases, avatars, opponent portraits, icons and the match captions. The four locale bundles are
imported Square Enix wording, and ten of the original's sounds are in `androidApp/src/main/res/raw/`.

So nothing here is a demonstration that the IP problem can be side-stepped.

BR-003 in
[docs/migration/16-RISK-ASSESSMENT.md](docs/migration/16-RISK-ASSESSMENT.md) —
unlicensed Square Enix IP across art, audio and naming — is unresolved and blocking. If it
is resolved by reskinning, `cards.json` needs new names too; the *stats* (powers, rarity,
type) would carry over, since `tools/extract_cards.py` separates them from the naming.
