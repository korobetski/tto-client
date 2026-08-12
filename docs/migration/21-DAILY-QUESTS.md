# Daily quests

**Status: built and shipped in `:core` 0.3.0.** This document is the record of *why* it is shaped
the way it is, written after the fact, because nothing in the migration plan anticipates it.

> **Not a migration.** The AS3 has no daily anything: no dated field, no reset, no notion of today.
> Every other document here is a port with an original to be faithful to. This one has no original,
> which is why the reasoning is written down rather than inferred from a `.as` file.

| Piece | Where |
|---|---|
| `MatchEvent`, `Objective`, `DailyQuest`, `DailyQuests`, `DailyQuestCatalog` | `:core` `model/DailyQuest.kt` |
| `DailyQuestStatus`, `QuestAward`, `DailyQuestRepository` | `:core` `data/DailyQuestRepository.kt` |
| `isoDate`, `utcDayNumber` | `:core` `time/CivilDate.kt` — **moved** from `:shared` |
| The credit | `:core` `MatchRewards.credit`, after achievements |
| `GameSave.quests`, `withQuests`, `withServerOwnedFrom` | `:core` `model/GameSave.kt` |
| `RewardSummary.questIds` | `:core` `protocol/Accounts.kt` |
| The screen and the dashboard badge | `tto-client` `ui/QuestsScreen.kt`, `ui/DashboardScreen.kt` |
| The forgery refusal | `tto-server` `AccountRoutes.kt`, `put("/me/save")` |

## The decisions

Four were taken before any code, and each rules out an implementation that would otherwise have been
the obvious one.

**Server-owned.** The server is the authority on quest state, not the client.

**Objectives**: matches played, matches won, beat a named opponent, win with a rule in force, and
play a PvP match.

**Reset at UTC midnight**, everywhere, for every player.

**Shipped in one `:core` release** with formats and the `MODE` removal, rather than three.

## Why the state is on `GameSave`

`MatchRewards.credit` is a pure `GameSave -> GameSave` and is **the one authoritative credit path**
— the client runs it optimistically, the server runs it again against the stored profile. State that
is not on the save cannot be read or written there.

So putting quest state anywhere else forces a second credit path, which is the exact thing that
arrangement exists to prevent. It would also leave an offline profile — which has no server at all —
with nowhere to keep progress.

**The alternative that was rejected**, and it was not a bad one: derive quest progress from the
server's `matches` table. It has `played_at`, its own column comment says the right thing (*today is
the only one of the two dates the server can vouch for*), and it would have been immune to
`PUT /me/save` by construction. It was rejected because it makes quests invisible to `:core` —
unevaluable offline, and duplicated in the server. The `PUT /me/save` hole is closed another way,
below.

**`GameSave`'s KDoc claimed to be `Save.DATAS` field for field.** `QUESTS` is the first field the
AS3 never had, so the claim is now *"`Save.DATAS`, plus a named list of fields this port added"* with
`QUESTS` on it. A comment, not a thing — nothing outside these three repositories reads this format
— but the next person deserves to know a list exists.

## Why an objective reads a match and not a save

`Requirement.progress(save)` — what an achievement uses — is a function of **lifetime** totals:
`npcWinsTotal`, `rulesWins[key]`, `cards.size`. That is right for a ratchet and **structurally
incapable** of expressing "three wins *today*": no field on the save is date-bucketed, and no
expression over one can mean "since midnight".

So a daily objective observes the *match*:

```kotlin
data class MatchEvent(
    val result: MatchResult,
    val opponentIconId: String,   // as NPC_W and NpcCatalog.byIcon key it
    val ruleKeys: List<String>,   // as RULES_W and GameRules.activeRuleKeys produce them
    val isPvp: Boolean = false,
)
```

Both keys are the ones the rest of the codebase already uses, so nothing introduces a third naming
scheme. `Requirement.Progress` is **reused** for display: only the evaluation rule is new, which is
the right seam.

**The catalogue is Kotlin, not JSON**, for the reason `AchievementCatalog` is: a quest carries an
`Objective`, which is a rule rather than a value, and `RewardSummary` sends ids only *because* both
ends hold the catalogue. JSON would also need a third copy under the server's own resources, beside
the two of `cards.json` — a duplication the server's `Catalogs` already flags as unsolved.

## The draw: derived, then pinned

```
Random(utcDayNumber(at) * SEED_MIX xor characterCreatedAt)  →  3 of DailyQuestCatalog.assignable
```

Deterministic, so both ends compute the same three quests with **no endpoint and no write**, and the
screen can show them before a single match.

`creationDate` is the salt because it is the only identifier that is on the save, set once by the
server, and never changes. Not `username` — case-normalised and one day renameable. Not an account
id — `:core` does not have one and an offline profile has none either. And **nothing volatile**: a
level or a purse would be an assignment that changes in the middle of the day a player is working
through it.

At the first credit of a new day the draw is **pinned** into the record, and from then on the record
is the truth. A catalogue that changed under a player's feet at noon would otherwise move the
goalposts on quests they had already half-finished.

The screen shows the *derived* draw while `save.quests.day` is not today, and the *recorded* one
otherwise — `DailyQuestRepository.statuses` never writes. That is what makes a character who has
never played see three quests at zero rather than an empty list, at the one moment they are most
likely to look.

## The order of crediting

```kotlin
val award  = AchievementRepository().credit(updated, at)
val quests = DailyQuestRepository().credit(award.save, MatchEvent(...), at)
```

**Achievements first, and it matters**: a quest's MGP can satisfy `Requirement.MgpHeld`. Crediting
quests first would pay such an achievement one match earlier than the current behaviour, which is a
silent change to something already shipped. `MatchRewardsTest` pins the order.

**One round is enough, and that is a property of the data, not a shortcut.** Paying a quest cannot
complete another, because no `Objective` reads the save at all — they read a `MatchEvent`, so MGP,
XP and bag items cannot move any of them. `DailyQuestRepositoryTest` asserts this, so the day
somebody adds a reward-reading objective the assumption fails loudly instead of paying one match
late.

Idempotent in the same sense `AchievementRepository.credit` is: an id already in `completed` is
skipped, so a queue that drains twice cannot pay twice. **Not** idempotent per match, and must not
be — two matches are two units, which is the point.

## Rewards

A win pays 64 MGP at the median of the FFXIV table (48 FFVIII, 182 at the top). The cheapest card on
the shelf is 120 and a bronze pack 520. Quests pay **150–250**, so a day's three come to roughly
three to five matches' worth: worth returning for, not enough to make the shop irrelevant.

## The forgery hole, and how far it is closed

`PUT /me/save` is a blind overwrite whose own KDoc concedes a player can edit their MGP — a
concession explicitly bounded to *"nothing a match established"*. A completed quest is one of those.

```kotlin
fun GameSave.withServerOwnedFrom(stored: GameSave): GameSave = copy(quests = stored.quests)
```

applied in the route: the incoming document is accepted, and the server-owned fields are taken from
what is stored. One function, so the list of server-owned fields is stated **once** rather than
scattered across routes.

`achievements` belongs on that list too and is **deliberately left off**: adding it changes the
behaviour of an existing route for existing players, which is a separate decision, not a detail of
this one.

The client stays a *predictor*: it credits locally, then `AccountSession.adopt` replaces the whole
`PlayerState` with the server's — the same arrangement MGP and XP already have.

## The PvP objective, sealed and undrawable

`Objective.PlayPvpMatch` is in the sealed hierarchy and **excluded from `DailyQuestCatalog.assignable`**
until PvP exists. An unfinishable quest in a draw of three is a third of every day wasted, for every
player — worse than the dashboard's greyed Multiplayer card, which costs a row and nothing else.

Sealing it now is the cheap moment: every `when` over `Objective` and every stored id is decided
once, instead of in a later release that also has to reason about a catalogue changing mid-day. And
`credits(event) = if (event.isPvp) 1 else 0` is correct forever — `MatchRewards.credit` takes an
`Npc` and is PvE by construction, so it always passes `isPvp = false`.

`DailyQuestCatalogTest.everyAssignableQuestIsCompletable` is the guard: no PvP objective in
`assignable`, and every `BeatOpponent.iconId` resolves in `NpcCatalog`.

Delete the filter the day PvP ships. See
[09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) § PvP through the server.

## `CivilDate` moved into `:core`

It began in `:shared`, where the achievements screen was the only thing that needed a date. Quests
need the same arithmetic in two places the client cannot reach: `MatchRewards`, and the server
deciding what day it is. **Two implementations of a day boundary is the divergence that ends with
the client believing it is a new day and the server disagreeing**, so there is one.

The move keeps the package, so no client import changed. `Clock` did **not** move: it is the host
seam, and `localHour()` needs a time zone. A pure function from milliseconds to a date reads no
clock, which is why it can live in a module whose rule is that nothing reads the wall time.

⚠️ The client's copy had to be deleted **in the same commit as the version pin**, or
`com.tripletriad.time.isoDate` is declared twice on the classpath.

## Why the release is a minor

`0.3.0` (artifact) and `1.1.0` (protocol) are two different numbers and both moved.

Major would mean *replay can now give a different answer*. Quests do not touch it: `MatchTranscript`
is unchanged, so `MatchCrediting.fingerprint` is unchanged, and every already-credited transcript
hashes the same. `ReplayDeterminismTest` passing **untouched** is the evidence, and is the thing to
check before believing this again.

Both wire additions are additive with defaults, read under `ignoreUnknownKeys` and written under
`encodeDefaults`.

## ⚠️ The deployment order

The *pin* order is core → client → server. The **deployment** order is the other way round: the
server must be live before the client build reaches players.

A server still on 0.2.0 parses an incoming `GameSave` with `ignoreUnknownKeys`, **silently drops
`QUESTS`**, and writes the amputated document back on every credited match. A client shipped ahead
of it would lose its quest progress every game, without a word.

## What is tested

| Where | The claim |
|---|---|
| `:core` | Every `Objective` credits what it should and nothing else, over a `MatchEvent` per case |
| `:core` | The day rolls over, and yesterday's progress and completions do not come with it |
| `:core` | Crediting twice pays once; crediting two matches counts two |
| `:core` | Achievements are credited before quests, pinned by an MGP-threshold achievement |
| `:core` | A JSON save with **no** `QUESTS` loads with the default |
| `:core` | No assignable quest is unfinishable, and every named opponent resolves |
| `tto-server` | A `PUT /me/save` carrying fabricated `QUESTS` does not change the stored ones |
| `tto-client` | A character who has never played sees the day's three quests at zero |
| `tto-client` | A pinned record renders as it stands; a finished quest reads as finished |
| `tto-client` | A record pinned to yesterday does not leak into today |
| `tto-client` | A match played through the real UI reaches the quest record on disk |

The client deliberately does **not** re-test whether a match advances a quest — that is `:core`'s,
and asserting it through a rendered board would be slower, flakier and no more convincing. What only
the client level can catch is a screen and a repository that are each correct and not connected.

## What is not done

- **No notification, no reminder.** Nothing tells a player their quests reset.
- **No streak, no weekly.** One day at a time, and no memory of yesterday beyond what the save
  happens to still hold.
- **The reward is MGP only.** `QuestReward` carries an `Item?` and no shipped quest uses it.
- **No countdown.** The screen shows the UTC day and says the boundary out loud; nothing on it ticks,
  and a stale "4 h left" would be worse than a date that cannot go stale.
