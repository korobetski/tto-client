# Contributing

This repository is a migration in progress: an abandoned Adobe AIR / ActionScript 3 game
being rebuilt as Kotlin Multiplatform + Compose Multiplatform. The AS3 original is *not*
here — it stays in [AS3-Triple-Triad](https://github.com/korobetski/AS3-Triple-Triad), under
`sources/`, and this repository holds only the Kotlin client. Every path spelled `sources/…`
below or in `docs/` is a citation of that tree, not of a directory you will find in this
checkout; `tools/as3_tree.py` says how the import scripts locate it. The plan is
[docs/migration/00-INDEX.md](docs/migration/00-INDEX.md); what is actually built and proven is
[README.md](README.md).

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

**BR-003 in [docs/migration/16-RISK-ASSESSMENT.md](docs/migration/16-RISK-ASSESSMENT.md) is
unresolved.** `cards.json` carries the names and stats of all 263 cards, and the repository now
contains Square Enix artwork and audio. This is not a demonstration that the IP problem can be
side-stepped, and nothing here should be published or distributed while that stands. Contribute code
with that in mind; do not add more third-party assets.

There is also a **private key (`.p12`) publicly downloadable** from this repository's history — see
[git-workflow.md § A private key is publicly downloadable](docs/development/git-workflow.md#-a-private-key-is-publicly-downloadable).
Treat it as compromised.

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

### 4. Cite the AS3 source when you port from it

`Card.as:316-330`, `TTOCore.as:87`, `CardDigits.positions`. Every constant in `CardColors.kt` names
its origin, so "why is the badge at (28, 88)?" is answerable in six months without reading
ActionScript. Commit messages do this too —
[git-workflow.md § 3](docs/development/git-workflow.md#3-commits).

**And when you deviate, document the deviation and the reason.** The rules engine departs from the
original in two ways that change real games; both are recorded, both are flags, and
`RulesEngineOptions.FAITHFUL` still reproduces the original *including its defects*. A silent
"improvement" is indistinguishable from a bug.

### 5. Do not hand-edit generated files

Five directories are produced by the scripts in `tools/` — `cards.json`, the artwork, the imported
`tto-*.json` bundles, the launcher icon, `res/raw`. They are committed as generated output because CI
runs no Python. The list, and which script owns each, is in
[project-setup.md § 7](docs/development/project-setup.md#7-generated-files-and-the-scripts-that-generate-them).
Change the script, re-run it, commit both.

### 6. Report data defects; do not quietly fix them

The imported locale bundles contain duplicate keys, unreachable typos and outright mistranslations —
`STR_PLAY` is untranslated in German and means *playback* in Japanese. None of it is patched inside
the imported files: the importer reports all of it on every run, and `app-<tag>.json` is the
documented override mechanism. Editing machine-translated Square Enix wording in place would make the
bundle look trustworthy without making it so.

## Picking something up

`docs/migration/0X-PHASE-*.md` carries the task list, and each task is annotated with what was
actually delivered against it — including where the implementation deliberately differs from the
plan. The game is playable and has shipped through `v1.1.2`; the honest list of what is still
missing is:

- **a peer-to-peer or same-device PvP transport.** Online PvP is built and server-mediated
  (`net/PvpClient.kt`); a transport that does not go through the server is undecided. Note that the
  AS3 original's `net/Socket.as` is largely dead code, 27 of its 29 handlers unreachable
  ([network-protocol.md](docs/analysis/network-protocol.md)), so it is not a design to copy
- **iOS**: the shared framework links and its tests pass on the macOS CI runner; there is no
  `.xcodeproj` and no app has ever run
- **frame-timing measurement**, **dependency scanning**, **branch protection**

Nobody has reviewed any Phase 0 output. If you are looking for high-value work that is not code,
that is it.

> **This list was badly stale until 2026-08-17.** It claimed the pre-match phase chain, Roulette,
> Sudden Death, the AI, save games, drag and drop and release signing were all missing. Every one
> of them is built — `MatchAi` and the phase chain in
> [`tto-core`](https://github.com/korobetski/tto-core), `data/SaveRepository.kt`,
> `ui/BoardDragState.kt`, and the signing in `.github/workflows/release.yml`. If you are adding to
> this list, delete from it too.

## Pull requests

Full rules in [git-workflow.md § 4](docs/development/git-workflow.md#4-pull-requests). The short
version: under ~400 changed lines, all five CI jobs green, and a description saying what changed,
**what was verified and how**, and what was deliberately not done.

Two reviewers for anything touching the rules engine or the network protocol. One for a port of
existing behaviour.
