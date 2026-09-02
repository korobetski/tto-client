# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The Kotlin Multiplatform + Compose Multiplatform client for a Triple Triad game. It is one of
**two repositories** that ship together:

| Repo | Holds | How it arrives here |
|---|---|---|
| `tto-client` (this one) | UI, data, network client, hosts | — |
| [`tto-core`](https://github.com/korobetski/tto-core) | rules engine, `model`, `data`, `protocol` | `com.tripletriad:core`, from GitHub Packages, `api`-exported by `:shared` |
| `tto-server` | account/match server | over HTTP; pins the same `core` version |

Consequences worth knowing before touching anything:

- `Card`, `MatchState`, `RulesEngine`, `MatchTranscript`, `CURRENT_VERSION` and everything under
  `com.tripletriad.model` / `.data` / `.protocol` live in **`tto-core`, not here**. Changing the
  engine means editing that repo, `publishToMavenLocal`, then bumping `core` in
  `gradle/libs.versions.toml` — `settings.gradle.kts` orders `mavenLocal` first on purpose so a
  local install shadows the published artifact. A stale local install shadows it too:
  `rm -rf ~/.m2/repository/com/tripletriad`.
- The `core` version here and in `tto-server` **must match** — a match is verified by replaying its
  transcript with the engine both sides linked.

## Prerequisites

- **JDK 17**, and a GitHub token with `read:packages` — GitHub Packages answers anonymous requests
  with 401 even for public packages, so without it every build fails on an unresolved
  `com.tripletriad:core`. Put it in `~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`), never in
  a file inside this repo. CI uses `GITHUB_ACTOR` / `GITHUB_TOKEN`.
- Android only: SDK platform 37 and a `local.properties` with `sdk.dir` (copy
  `local.properties.sample`).

## Commands

```bash
./gradlew :desktopApp:run              # the UI, no emulator, no SDK — the fastest loop
./gradlew :shared:desktopTest          # the fast test loop: commonTest + desktopTest on the JVM
./gradlew ktlintFormat                 # fix formatting before it fails the build
./gradlew ktlintCheck detekt           # static analysis alone
./gradlew build                        # what CI runs: compile, tests, lint, ktlint, detekt, coverage gate
```

A single test, on any test task:

```bash
./gradlew :shared:desktopTest --tests "com.tripletriad.ui.MatchAudioTest"
./gradlew :shared:desktopTest --tests "*RulesEngine*" --tests "*MatchState*"
./gradlew :shared:desktopTest --tests "*.MatchUiTest.pickingACardThenACellPlacesItAndPassesTheTurn"
```

Test tasks and what each proves:

| Task | Runs |
|---|---|
| `:shared:desktopTest` | `commonTest` + `desktopTest` — the only place Compose UI tests run |
| `:shared:testAndroidHostTest` | `commonTest` again on the Android host JVM (**not** `testDebugUnitTest` — the module uses `com.android.kotlin.multiplatform.library`) |
| `:shared:iosSimulatorArm64Test` | `commonTest` on Kotlin/Native — **macOS only, silently skipped elsewhere** |
| `:androidApp:testDebugUnitTest` | the host module's own tests; `assembleDebug` does not run them |

Other:

```bash
./gradlew :androidApp:assembleDebug          # androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug           # onto an attached device
./gradlew :androidApp:verifyReleaseApk       # release-only; not wired into `check` (costs ~2 min)
./gradlew :desktopApp:packageDeb             # jpackage builds for the host only
./gradlew :shared:coverageReport             # shared/build/reports/jacoco/coverageReport/html/
```

Reproducing a CI job locally: `quality` → `ktlintCheck detekt`; `shared` → `:shared:build
:shared:coverageReport`; `android` → `:androidApp:testDebugUnitTest :androidApp:assembleDebug
:androidApp:assembleRelease`; `desktop` → `:desktopApp:build`; `ios-framework` → macOS only.

## Architecture

### Layering

`ui → domain → data → model`, arrows never point back. `model`/`domain` are in `:core` and must not
see Compose or I/O; `ui/` does no I/O and holds no rules.

### `App()` is the whole application, and every platform capability is a parameter

`shared/src/commonMain/kotlin/com/tripletriad/ui/App.kt` takes `SettingsStore`, `DocumentStore`,
`Clock`, `AudioPlayer`, `onQuit` and `server: ServerConnection?` — each with an **inert default**
(in-memory store, stopped clock, silent audio, no server). That is why previews, screenshots and the
UI tests run with no filesystem, no network and no machine locale bleeding in. `:androidApp` and
`:desktopApp` are thin hosts that supply the real implementations; `iosApp/` has Swift sources but
**no `.xcodeproj` — no iOS app has ever run**.

`server == null` is a supported configuration, not a degraded one: the game plays off local `.sav`
profiles. Adding a feature means keeping both paths working.

### Navigation

A `remember`ed `Screen` enum (`ui/Screen.kt`), not a navigation library. Back destinations come from
`Screen.up`, and `depth` is *derived* from `up` so adding a screen is one line. Reconsider only when
a screen becomes reachable from two places with different back destinations.

### Network

`net/` never throws for a network failure — `AccountClient` and `KtorMatchSubmitter` return result
types, because a dead server is an ordinary state of the world. Match results are queued durably
(`TranscriptQueue`) and drained later, so the win screen never waits on a round trip. Ktor's API is
common; the engine is per-platform (OkHttp / CIO / Darwin). `ktor-client-mock` is how the whole layer
is tested in `commonTest` with no socket.

### Two versions that must not be conflated

- `clientVersion` in `gradle.properties` — the release number. Moves every release. The `:shared`
  `buildVersion` task generates `com.tripletriad.CLIENT_VERSION` from it (there is no `BuildConfig`
  in a KMP library), and `:androidApp` / `:desktopApp` derive their package fields from it.
- `com.tripletriad.protocol.CURRENT_VERSION`, in `:core` — the *protocol* version. Moves only on a
  replay-affecting break; it is what the server's gate compares.

### The Android asset workaround

Compose's plugin does not wire its resources into the Android variant under AGP 9's KMP library
plugin, so an APK builds green with an empty `assets/` and crashes on launch. `:shared` fills it by
hand (`androidComposeAssets` → the `androidComposeAssetsElements` configuration → `:androidApp`), and
`:androidApp:verifyComposeAssets` reads the built APK to prove it. Don't remove either without
checking a real APK; delete them only when the plugin does its own wiring.

## Conventions this repository enforces

1. **State what you verified, and how.** Not "tests pass" but the task, the counts and the result;
   not "works on Android" but the device and API level. When something is *not* verified, say so in
   the same breath — the `**Not verified:**` paragraphs in README and `docs/` are why the rest is
   trustworthy. Say it in the *file's own header* too when a file is untested.
2. **A test that cannot fail is worse than no test.** Break the code, watch the test fail, then call
   it done (`docs/development/testing-guide.md` § 4).
3. **A dependency earns its place by doing something hard.** Napier, Media3 and Compose Navigation
   were each declined in favour of less code than the integration would have cost; if you add one,
   say what it does that hand-written code cannot, and if you decline one, say what would change
   your mind.
4. **The data files are authored here.** `cards.json`, `npcs.json`, `campaigns.json`, the art under
   `composeResources/files/art/`, the `tto-*.json` locales, `androidApp/src/main/res/` (launcher
   icon, `raw/` sounds) were all extracted once from the AS3 original by scripts that are **not in
   this repository** and cannot be re-run. Edit them in place, and say in the commit what changed
   and against which source.
5. **`tto-*.json` is the exception, and `app-<tag>.json` is how it is corrected.** Those bundles are
   Square Enix's own wording, duplicate keys and mistranslations included. Correcting one in place
   would make the bundle look trustworthy without making it so; an override in `app-<tag>.json`
   wins the merge (`Strings.kt`) and keeps their text and ours separable.
6. `detekt` runs with `maxIssues = 0` and both tools are wired into `check`. Don't add a local
   `@Suppress` — change `detekt/detekt.yml` or `.editorconfig` and record the reason. Any
   suppression that survives must carry its reason inline.
7. No wildcard imports (there are four different `Card` types in play). `@Composable` functions are
   `PascalCase`; non-const design tokens (`val CardWidth = 88.dp`) are too.
8. `Dispatchers.IO` does not exist on Kotlin/Native — never use it in `commonMain`. No `GlobalScope`,
   no static mutable singletons, structured concurrency only.
9. `expect`/`actual` is for platform capabilities only (`OpenUrl`, `ReducedMotion`, `ServerStatus`,
   `MatchNetwork`). Resource loading goes through Compose resources in `commonMain`.

### Ce qu'un KDoc doit contenir

**Un KDoc dit ce que le code ne dit pas déjà.** Il apporte une précision, il n'annonce pas le
comportement. Paraphraser la signature est du bruit qui vieillit mal : le code change, la
paraphrase reste et devient un mensonge.

## Traps and stale documentation

- **Fixed 2026-08-17**: `docs/development/git-workflow.md` said the default branch was `master`,
  README's toolchain table quoted Kotlin 2.2.20 / Compose 1.9.3, and test counts across the docs
  disagreed with each other (165, 263, 432, 520, 523, 529 all appeared). All three are now
  corrected and dated against a measured run — `gradle/libs.versions.toml` remains the authority
  for versions, and `git branch -a` / a fresh `:shared:desktopTest` run remain the authority for
  branch name and test counts respectively. If you quote a test count, say which task produced it
  and when, the way the fixed docs now do — these numbers will drift again and are not worth
  trusting on sight.
- Coverage is JaCoCo, **not Kover** (Kover cannot be applied to this module at all), measured on the
  desktop target only, gated at 90% line / 75% branch as a floor rather than a target.
- ktlint plugin is pinned at 12.1.2 and Material 3 at 1.9.0 (it versions separately from the rest of
  Compose since 1.11). `iosX64` is gone because Compose stopped publishing it.
- A Kotlin/Native ABI or Apple-target breakage is invisible on Linux/Windows — those compilations are
  *skipped silently*, so a green local `build` says nothing about iOS.
- Three deprecation warnings on every build come from detekt and the KMP plugin; they are expected
  and not this repo's to fix.
- Legal: `cards.json` and the committed artwork/audio are Square Enix IP and **BR-003 is unresolved**
  — nothing here should be published or distributed, and do not add more third-party assets. A `.p12`
  private key is downloadable from this repository's history; treat it as compromised.

## Where the deeper docs are

`CONTRIBUTING.md` is the front door. `docs/development/` has setup, build, testing, git workflow,
coding standards and architecture guidelines.
