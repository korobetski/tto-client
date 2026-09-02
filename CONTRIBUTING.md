# Contributing

This repository holds the Kotlin Multiplatform + Compose Multiplatform client for Triple Triad.
What is actually built and proven is [README.md](README.md).

Start here:

| | |
|---|---|
| Get it building | [docs/development/project-setup.md](docs/development/project-setup.md) |
| What each task builds | [docs/development/build-guide.md](docs/development/build-guide.md) |
| Run and write tests | [docs/development/testing-guide.md](docs/development/testing-guide.md) |
| Branches, commits, PRs | [docs/development/git-workflow.md](docs/development/git-workflow.md) |
| Formatting, naming, docs | [docs/development/coding-standards.md](docs/development/coding-standards.md) |
| Layering, state, DI | [docs/development/architecture-guidelines.md](docs/development/architecture-guidelines.md) |
| Colours, type, spacing, shared controls | [docs/development/design-system.md](docs/development/design-system.md) |

## Before you start: one blocking legal issue

`cards.json` carries the names and stats of all 263 cards, and the repository
contains Square Enix artwork and audio. This is not a demonstration that the IP problem can be
side-stepped, and nothing here should be published or distributed while that stands. Contribute code
with that in mind; do not add more third-party assets.

## The loop

```bash
./gradlew :desktopApp:run                # see it
```

```bash
./gradlew :shared:desktopTest            # 856 tests, 0 failures (measured 2026-08-17)
```

```bash
./gradlew ktlintFormat && ./gradlew build   # what CI will run
```

`build` runs ktlint, detekt and the coverage gate. All three fail the build, deliberately: a
formatting argument in review is a waste of a reviewer.

## What this project holds you to

The rules below are not style preferences. Each exists because its absence cost something here.

### 1. Say what you verified, and how

The single most important convention in the repository. A previous proof of concept in this
repository was reported COMPLETE with "technology stack validated" and had **never been compiled** —
12 build-blocking defects. Everything written since states its evidence.

So: not "tests pass" but "`./gradlew :shared:desktopTest` green, 856 tests, 0 failures"; not "works on
Android" but "installed on a Pixel 6a, API 37; both orientations verified by screenshot". And when
something is *not* verified, say that too, in the same breath. README and the development docs are
full of **Not verified:** paragraphs, and they are the reason the rest can be trusted.

### 2. A test that cannot fail is worse than no test

Break the code and watch the test fail before you call it done. The procedure, and three real
mutations from this repository — including one that a UI test driving nine placements through the
real app **could not detect** — are in
[testing-guide.md § 4](docs/development/testing-guide.md#4-the-mutation-check--the-standard-for-a-non-trivial-test).

### 3. A dependency earns its place by doing something hard

Three the plan called for were not added, each replaced by less code than the integration would have
cost:

| Planned | Instead | Why |
|---|---|---|
| Napier (logging) | ~80 lines + 4 in `MainActivity` | its job was forwarding to `android.util.Log`; `:shared` now keeps no Android import |
| Media3 (audio) | `SoundPool` + `MediaPlayer` | platform classes older than `minSdk 24`, and they do everything `SoundManager` did |
| Compose Navigation | a `remember`ed enum | four destinations, no deep links, no arguments |

Each of those is written up with the point at which it should be reconsidered. That is the standard:
if you add a dependency, say what it does that hand-written code cannot, and if you decline one, say
what would change your mind.

### 5. The data files are authored here

`cards.json`, the artwork, the `tto-*.json` bundles, the launcher icon and `res/raw` were extracted
once from the AS3 original by scripts that are not in this repository and cannot be re-run. They are
ordinary source files now: edit them in place, and say in the commit what changed and against which
source. The provenance of each is in
[project-setup.md § 7](docs/development/project-setup.md#7-where-the-data-files-came-from).

### 6. `tto-*.json` is the exception, and `app-<tag>.json` is how it is corrected

The imported locale bundles contain duplicate keys, unreachable typos and outright mistranslations —
`STR_PLAY` is untranslated in German and means *playback* in Japanese. None of it is patched inside
the imported files. An override in `app-<tag>.json` wins the merge and is the documented way to fix
one; editing machine-translated Square Enix wording in place would make the bundle look trustworthy
without making it so, and would lose the record of what they actually shipped.

## Pull requests

Full rules in [git-workflow.md § 4](docs/development/git-workflow.md#4-pull-requests). The short
version: under ~400 changed lines, all five CI jobs green, and a description saying what changed,
**what was verified and how**, and what was deliberately not done.

Two reviewers for anything touching the rules engine or the network protocol. One for a port of
existing behaviour.
