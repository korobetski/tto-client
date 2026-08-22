# Git Workflow

Phase 0, Task 1.6 deliverable.

---

## 1. Repository facts

Check these before writing any automation.

| Fact | Value |
|---|---|
| Default branch | **`main`** |
| Gradle root | **the repository root** |
| CI | [`.github/workflows/build.yml`](../../.github/workflows/build.yml), on push/PR to `main`, `paths-ignore` on `docs/`, `sources/` and `*.md` |
| Release | [`.github/workflows/release.yml`](../../.github/workflows/release.yml), on a `v*` tag push |

> **This document said `master` until 2026-08-17**, and warned that automation keying on `main`
> would never fire — the opposite of the truth. `.github/workflows/build.yml` has
> `branches: [main]`, and `git branch -a` lists `main` and nothing else. The
> `migration/kotlin-multiplatform` branch it also named is gone; the migration was merged and the
> branch deleted.

The Gradle build used to live in a `kotlin/` subdirectory, which meant Android Studio had
to be pointed at it and CI needed `working-directory: kotlin`. It was promoted to the root,
so both are now the obvious thing. CI switched from a `kotlin/**` allow-list to
`paths-ignore` at the same time: an allow-list at the root would have to name every module
and would silently stop building one that was added and not listed.

## 2. Branches

| Pattern | Purpose |
|---|---|
| `main` | released state |
| `feature/<slug>` | one deliverable, e.g. `feature/atlas-loader` |
| `fix/<slug>` | a defect |
| `spike/<slug>` | throwaway investigation; may be deleted unmerged |

Branch off `main`. The `migration/<topic>` pattern this table used to carry was for the
long-lived migration workstreams; those are merged and the pattern is retired.

Keep branches short-lived.

## 3. Commits

Conventional-commit style, with a scope that names the layer or module:

```
<type>(<scope>): <imperative summary under 72 chars>

<body: what changed and why. The why is the part that is not in the diff.>

<footer: refs, co-authors>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.

Scopes in this project: `shared`, `android`, `desktop`, `ios`, `model`, `data`, `ui`,
`build`, `docs`, `analysis`.

Do not commit:

- `local.properties` (machine-specific SDK path; it is in `.gitignore`)
- anything under `build/`, `.gradle/`, `.kotlin/`
- generated files that live in `build/` — but **do** commit
  [`cards.json`](../../shared/src/commonMain/composeResources/files/cards.json) and
  [`dependency-matrix.md`](../analysis/dependency-matrix.md), which are checked-in
  generator output; regenerate rather than edit them

### File modes on Windows

This project is developed on Windows, where `git config core.filemode` is `false`. Git
therefore never notices the executable bit, and a script committed from Windows lands in
the index as `100644` — **not executable**. On a Linux CI runner that fails immediately:

```
./gradlew: Permission denied
Error: Process completed with exit code 126.
```

That is exactly how the first CI run failed. The fix is to set the mode in the index
explicitly, then commit:

```bash
git update-index --chmod=+x gradlew
```

Check with `git ls-files -s <path>`: it must read `100755`. Any new shell script, hook or
wrapper added from Windows needs the same treatment. `gradlew.bat` correctly stays
`100644` — it is only ever run on Windows.

## 4. Pull requests

| Requirement | Rule |
|---|---|
| Size | under ~400 changed lines where possible; a mechanical rename may be larger if it is *only* a rename |
| CI | all five jobs green — `quality`, `shared`, `android`, `desktop`, `ios-framework` |
| Reviewers | 1 for a port of existing behaviour, 2 for anything touching the rules engine or the protocol |
| Description | what changed, what was verified **and how**, what was deliberately not done |

On that last point: state the verification, not the intent. "Tests pass" is not a claim
anyone can check. "`./gradlew build` green; 47 test executions, 0 failures; installed on a
Pixel 6a and the flip works under touch" is.

The PoC history in this repository is the cautionary tale — a first attempt was reported
COMPLETE and "technology stack validated" while never having been compiled, and had 12
build-blocking defects.

## 5. Merge strategy

- **Squash** for feature and fix branches: one logical change, one commit on the target.
- **Never rebase** a branch someone else has pulled.

## 6. Branch protection on `main`

To configure (**not verified** — checking requires the GitHub API and was last confirmed absent
in Phase 0):

- require the `quality`, `shared` and `android` checks
- require at least one approving review
- no force pushes, no deletions
- require branches to be up to date before merging

`ios-framework` runs on a macOS runner and costs 10× the minutes of a Linux one. Run it on
PRs but consider not making it a blocking check until the iOS app actually exists.

## 7. Tags and releases

`v<major>.<minor>.<patch>` on `main` only. **Pushing the tag is what ships** — updates are
delivered through GitHub Releases with an in-app version check.

[`.github/workflows/release.yml`](../../.github/workflows/release.yml) fires on `push: tags: ['v*']`
and derives `-PclientVersion` from the tag name, so the APK's `versionName`, its `versionCode` and
the release they hang under are one number by construction. It runs `verify` (ktlint, detekt,
tests), then `installers` (a `.deb`, `.msi` and `.dmg` matrix) and `publish` (the signed APK and
the GitHub release). Tags `v1.0.2` through `v1.1.2` have shipped this way.

The `.dmg` is unsigned — a signed macOS build needs a paid Apple account, and `docs/` records the
decision not to buy one.

## 8. Related

- [coding-standards.md](./coding-standards.md)
- [testing-strategy.md](./testing-strategy.md)
- [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
