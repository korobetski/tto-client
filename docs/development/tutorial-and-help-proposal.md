# Teaching the game: the academy and the sandbox

**Status: part built.** Options B and C of the first draft were chosen together — a curriculum of
short lessons *and* a rules sandbox. Steps 1 to 3 of §8 are done: eight lessons, a list screen with
progress, and a dashboard entry. The rest of §8 is not.

**What is verified, and how.** The claims about `tto-core`'s API are read from the `tto-core`
sources at `eb6b213` (v0.7.3, the version `gradle/libs.versions.toml` pins). The lesson positions in
§4 are computed by `tools/find_lesson_positions.py` against the shipped `cards.json` and hand-checked
in this document. **Nothing here has been compiled or run**: the environment this was written in
cannot reach GitHub Packages or the Google Maven repo, so neither `com.tripletriad:core` nor Compose
resolves and no Gradle task runs at all. That applies to the code committed against steps 1 and 2 of
§8 as much as to the document — it is written against the sources cited and reviewed by reading, and
`./gradlew :shared:desktopTest` has never been run on it. Every effort estimate is a reading
estimate.

---

## 1. The spike, and its answer

The first draft said the design hinged on one question — *can a `MatchState` be built from a
pre-filled board in `:core`?* — and listed seven things `MatchScript` cannot express, most of them
guessed to need a core release.

**The answer is yes, and the wider answer is that the whole academy needs no core change at all.**
`MatchState`, `Board` and `MatchSetup` are plain data classes with public constructors:

```kotlin
data class Board(cells: List<PlacedCard?>, elements: List<CardType?>)          // Board.kt:50
data class MatchState(rules, options, board, hands, order, placement, tally, lastPlay)
                                                                               // MatchState.kt:55
data class MatchSetup(state, opponentVisibility, coinFlip, intro)              // MatchSetup.kt:184
data class GameRules(open, order, typeRule, suddenDeath, random, reverse,
                     fallenAce, same, sameWall, plus, swap, roulette)          // GameRules.kt:56
```

`MatchState.start()` is a *convenience* that requires two five-card hands; the constructor imposes
nothing. A puzzle is therefore an ordinary `MatchState` with cards already on the board and a
`placement` count to match. So the seven gaps resolve like this:

| Gap | Answer | Where |
|---|---|---|
| Forced rules | `GameRules(same = true)` — build the value | client |
| The opponent's hand | construct `MatchSetup` directly instead of `PveMatches.assemble` | client |
| Pre-filled board | `MatchState(board = Board(cells = …), placement = n, …)` | client |
| Scripted opponent moves | `state.play(card, position)` — the AI is not compulsory | client |
| Lines that react | **the engine already reports why** — see below | client |
| Move constraints | a `Set<Int>` of allowed cells, enforced in the UI | client |
| Progress | `SettingsStore` | client |

The one that matters most: `RulesEngine.resolve` returns

```kotlin
data class Capture(val position: Int, val kind: CaptureKind, val wave: Int)    // RulesEngine.kt:115
enum class CaptureKind { BASIC, SAME, SAME_WALL, PLUS, COMBO }                 // RulesEngine.kt:7
```

and every `MatchState` carries the last one in `lastPlay.captures`. The first draft's largest risk —
"the engine may not report *why* a capture happened, so both the lesson lines and the demo captions
degrade to scripted text" — **does not exist**. A lesson can say "that was a Plus" because the
engine said so, and `wave` distinguishes the direct captures from the combo that followed them.

`MatchAi` is equally usable as a teaching aid rather than an opponent: `evaluate(state, card,
position)` scores a hypothetical placement without touching the state, and `candidates(state)`
returns every legal move ranked. That is a hint engine already written, and it is what makes the
sandbox (§5) cheap.

**Consequence for sequencing:** nothing in this document is blocked on a `tto-core` release, a
protocol version, or a `tto-server` deployment. The whole of it is `:shared`.

---

## 2. What the academy is

Ten to twelve named lessons in a recommended order — **not** gated behind one another; see
`LessonsScreen` for why nothing is locked — each teaching one rule and ending in a sentence that
names it. Eight are built. Most are **puzzles**: a board already filled to one
move from the rule firing, the right card in hand, one cell to find. A nine-placement match to teach
Plus takes four minutes and can be derailed at every step; a puzzle takes fifteen seconds and cannot
fail to teach it.

| # | Lesson | Teaches | Shape |
|---|---|---|---|
| 1 | The board | the existing nine lines | full match, All Open |
| 2 | Where to place | edges expose fewer sides | puzzle, 2 moves |
| 3 | Same | `RULE_SAME` | puzzle, 1 move |
| 4 | Plus | `RULE_PLUS` | puzzle, 1 move |
| 5 | Chains | combo — which is not a rule; see §4 | puzzle, 1 move |
| 6 | The walls | `RULE_SAME_WALL` | puzzle, 1 move |
| 7 | Upside down | `RULE_REVERSE`, `RULE_FALLEN_ACE`, then both | 3 puzzles |
| 8 | The elements | `RULE_ELEMENTAL` | puzzle, 2 moves |
| 9 | Rising and falling | `RULE_ASCENSION`, `RULE_DESCENSION` | short match |
| 10 | Whose hand is it | `RULE_RANDOM`, `RULE_SWAP`, `RULE_CHAOS`, `RULE_ORDER` | short match |
| 11 | Before the first card | `RULE_ROULETTE`, `RULE_THREE_OPEN`, `RULE_SUDDEN_DEATH` | short match, drawn on purpose |
| 12 | The exam | three random rules, a real opponent | full match |

Lessons are free and pay nothing, and **none of them goes on the player's record** — see
`MatchScript.counted`. The exam is meant to pay, once. That settles the question the tutorial's KDoc
used to argue about: it charged the tt-master's fee and paid a full reward, which was defensible for
one match and is not for eight.

---

## 3. How a puzzle is built

A puzzle is a `MatchState` whose board is already filled, whose hands hold what the lesson needs, and
whose `placement` equals the number of cards on the board. Two invariants follow from
`MatchState` and must be respected or the lesson wedges:

- **`isFinished` is `placement >= 9`**, and `currentPlayer` is `order.colorAt(placement)`. So
  `placement` must equal `board.placedCount` or the wrong side moves.
- **The hands must fill the remaining cells exactly**: `blue.size + red.size == 9 - placedCount`.
  Fewer, and the match reaches a placement with an empty hand and `play` throws; more, and it ends
  with cards still held.

A one-move puzzle is therefore eight cards on the board, one card in the player's hand, none in the
opponent's, `placement = 8`, `order` set so blue moves. It ends by the ordinary route — the ninth
placement finishes the match — so the outcome panel, the banners and the crediting path all work
unchanged.

Two consequences worth stating, because both look like defects otherwise:

- **A one-move puzzle's score is meaningless.** The score counts unplayed cards for their owner, so
  a lesson board stacked with red cards would open at a heavy loss and the banner would say so. The
  boards are composed blue-heavy instead, so every lesson ends 9-1 and the banner is a
  congratulation; `TutorialPuzzleTest.everyLessonIsWon` is what holds that.
- **`MatchScreen` re-deals on `remember(matchIndex, npc.iconId)`.** A curriculum whose lessons share
  one tutor would keep the previous board when moving between two of them, exactly as
  `CampaignMatchScreen` documents at `CampaignScreen.kt:235`. The same fix applies: `key(lesson)`.

---

## 4. The positions, and how they were chosen

**A lesson is only honest if the rule it teaches is the only explanation for the capture.** If the
placement would have won on raw power too, the player learns nothing about Plus and is told
something false. `tools/find_lesson_positions.py` searches `cards.json` for positions that fire the
wanted rule *and* capture nothing with every special rule switched off.

Its output for block 1 — the block the current tutorial already deals — hand-checked below. Cells are
0..8 row-major; powers are top/right/bottom/left.

**Same** — play Dodo (4/2/3/4) into the centre:

| Cell | Card | Why |
|---|---|---|
| 5 | Tonberry (2/2/7/2) | Dodo's right 2 meets Tonberry's left 2 — equal |
| 7 | Bomb (3/4/3/3) | Dodo's bottom 3 meets Bomb's top 3 — equal |

Two matches fire Same; neither would fall to raw power, since both are ties.

**Plus** — play Dodo into the centre:

| Cell | Card | Sum |
|---|---|---|
| 1 | Tonberry (2/2/7/2) | Dodo's top 4 + Tonberry's bottom 7 = 11 |
| 3 | Chocobo (3/7/2/1) | Dodo's left 4 + Chocobo's right 7 = 11 |

Equal sums fire Plus. Dodo loses both sides on power — 4 against 7 twice — so this is the position
that teaches what Plus is *for*: a weak card taking two strong ones.

**Combo** — play Dodo into the centre, under **Same**:

| Cell | Card | Why |
|---|---|---|
| 5 | Tonberry (2/2/7/2) | right 2 = left 2 → Same |
| 7 | Coblyn (3/3/3/4) | bottom 3 = top 3 → Same |
| 6 | Sabotender (4/3/3/3) | Coblyn, now blue, beats it: Coblyn's left 4 > Sabotender's right 3 → Combo |

Note what this lesson has to say out loud: **combo is not a rule.** `GameRules.comboEnabled` is
always true and `RULE_COMBO` is a dead constant everywhere but the help screen
(`HelpScreen.kt:50-57`). The lesson is played under Same, and the third card falls to the chain.

**Same Wall** — play Nanamo Ul Namo (10/6/4/8) into cell 1, the top edge:

| Cell | Card | Why |
|---|---|---|
| — | the wall above | the ace on top counts as a matching card |
| 4 | Dodo (4/2/3/4) | Nanamo's bottom 4 meets Dodo's top 4 — equal |

Same Wall **cannot be taught from the centre**, which is the one cell with no wall at all — the tool
encodes that, and it is the sort of thing a hand-written lesson would get wrong once and never
notice.

### The tool is a search, not an oracle

`find_lesson_positions.py` is a second implementation of rules that live in `tto-core`, and it will
drift from them. It exists to *find* candidates cheaply; **each lesson must then be pinned by a test
that replays its position through the real `RulesEngine`** and asserts the captures and their kinds.
That test is also the one that earns its place under convention 2: change the engine's Same and it
fails, which is exactly when a lesson has started teaching something untrue.

---

## 5. The sandbox

The spike makes this far cheaper than the first draft assumed. A sandbox needs three things, and the
engine supplies all three:

- **place anything anywhere** — `Board.place`, `MatchState.play`;
- **explain what happened** — `lastPlay.captures`, each with its `CaptureKind` and `wave`, so the
  sandbox can say "two cards fell to Plus, a third to the combo behind it" rather than "3 captures";
- **say what would have happened** — `MatchAi.evaluate(state, card, position)` scores a hypothetical
  placement without mutating anything, and `candidates(state)` ranks every legal move. So "why was
  that a bad move" is answerable: show the move the AI would have played and what it captures.

What is left to build is UI and a history stack: a rule panel (`GameRules` toggles, the same shape
`PvpTableScreen.kt:135` already draws), a card picker, a board that accepts placements for either
colour, step-back, and a running explanation of each resolution. `MatchState` being immutable makes
undo a list of states rather than an inverse operation.

**Order it after the academy.** A sandbox teaches a player who already knows what to ask; a
curriculum teaches one who does not. But the explanation layer they share is the academy's too — a
lesson that says "that was a Plus" and a sandbox that says it are the same function over
`lastPlay.captures`, and it should be written once, in `:shared`, for both.

---

## 6. The help screen

`RuleDemo`: a small non-interactive board that replays a two-or-three-placement scene on a loop,
inside the accordion row. Same principle as the lessons and the same source of truth — the scene is a
ruleset, a board and a list of placements, replayed through `RulesEngine`, so a demo cannot drift
from the rule it claims to show.

This is the one repair available for a real defect in the data: `RULE_SAME_WALL_HELP`,
`RULE_COMBO_HELP` and `RULE_ELEMENTAL_HELP` resolve to the rule's own name in all four imported
bundles (`HelpScreen.kt:62-71`), so those three rows explain nothing today, and convention 6 rules
out writing replacement text into Square Enix data. A demo is this port's own content, so it fills
the gap without forging a translation.

- Reuse `CardFace`/`BoardCard` at a small `scale`, not `BoardGrid` — that one registers drop targets
  and takes a `BoardDragState`, none of which a demo wants.
- One caption per beat, which is also the screen-reader text.
- `rememberReducedMotion()` (`platform/ReducedMotion.kt:32`): with it on, show the final state and
  the captions as static text, no loop.
- A replay control; a demo looping under a paragraph is a distraction while the paragraph is read.
- Tag each `help-demo-<ruleKey>`, and pin each scene the same way the lessons are pinned.

Priority: Same, Plus, Combo, Same Wall (the three empty texts are in that set), then Reverse, Fallen
Ace, Elemental, then Ascension/Descension. The hand-and-order rules are about what happens *before* a
placement and are poorly served by a board animation — leave them as text.

**"Try it"** on a rule row opens its lesson. That is what stops the help screen being a glossary, and
it is also the moment `Screen.kt:12-15` names as the reason to reconsider the navigation enum: a
lesson would then be reachable from two places with different back destinations. Cheapest answer is
to carry the origin into the lesson screen; the honest alternative is to look at Compose Navigation
again.

---

## 7. Costs that are not code

- **Strings.** New `APP_ACADEMY_*` keys go in `StringKeys`, in its `appOwned` list
  (`StringKeys.kt:732`), and in `app-en_US.json` and `app-fr_FR.json` **only** — `StringsBundleTest`
  asserts app-owned keys are translated in exactly those two and fall through to English in `de_DE`
  and `ja_JA` (`StringsBundleTest.kt:92`), and it pins total key counts, so `TRANSLATED_KEYS` and
  `UNION_KEYS` must move in the same commit. Four to six lines a lesson is roughly 60 keys.
- **Copy is the part that cannot be refactored.** Write lesson 1's four lines and read them aloud
  before writing the other eleven.
- **Bubble pacing.** `LessonBubbles` advances on a 6.1-second timer and the turn clock runs behind it
  (`MatchScript.kt:160-168`, deliberately, following the AS3). Fine for nine lines, wrong for sixty:
  make a lesson bubble advance on tap with the timer as a fallback, and pause the turn clock while a
  line is up. A deliberate deviation, so it is documented as one — the original had nine lines.
- **`MatchScreen` has no complexity budget left** (`MatchScript.kt:39-41`). Everything in §1 arrives
  as data on `MatchScript`, read through a nullable extension like `deckFor`/`flip`/`aiOptions`
  already are. No new branch in the composable.

## 8. Order of work

1. ~~**The bubble pacing fix.**~~ **Done.** A lesson bubble advances on a tap, with the five-second
   timer as the fallback, and the turn clock is held while a line is up — a deliberate deviation
   from the original, which doubles the turn instead. `lessonPause` is gone with it: the opponent
   now waits on `LessonSpeech.isSpeaking` rather than on the line count times 6.1 seconds, because
   arithmetic over a fixed cadence stops being true the moment a line can be dismissed early.
2. ~~**`MatchScript.rules` + `MatchScript.opening`, and the three verified puzzles.**~~ **Done.**
   Behind the existing `TUTORIAL` row as a straight sequence — no list screen, no persistence.
   `TutorialPuzzleTest` pins all three positions through the real engine.

   Also `MatchScript.counted`, which was not in the plan: **no lesson goes on the player's record**
   — not the win, the defeat or the draw, not the counters behind `forfeits`, not the rule and
   opponent tallies, not achievements or quests, and nothing is paid. A course of four that counted
   would open every character on four wins. It is one flag rather than two because
   `MatchRewards.credit` computes the payout and writes the stats in the same pass, so "pays nothing
   but still counts" is not a state that could be asked for without reimplementing it.

   It has to be applied at **both ends** — never counted as started, never credited when it ends —
   or the lesson leaves exactly the mark it was avoiding: counted as started and never ended is a
   forfeit. `LessonRecordTest` walks the pair across the whole course.

   This changes the first lesson, which used to pay the tt-master's full reward — a choice
   `TutorialScreen`'s KDoc argued for at five MGP a match, and which does not survive the
   requirement that a tutorial not touch the record.
3. ~~**The list, and progress in `SettingsStore`.**~~ **Done.** `LessonsScreen`, reached from a
   dashboard card carrying a `done / total` badge, with `UserSettings.lessonsDone` behind it.
   Nothing is locked — the order is a recommendation, not a gate — and progress is recorded when a
   lesson's **result lands**, so it counts however the player leaves the screen and an abandoned
   lesson counts for nothing.

   The tutorial's row on the opponent list is **gone**. A course of eight with an order and a place
   you are up to is not a row on a list of opponents, and the alternative — the same screen reached
   from two places — is precisely the condition `Screen.kt` names as the point to stop using an
   enum for navigation. One entry point costs nothing.

   Four more lessons with it: **Same Wall, Reverse, Fallen Ace, and the two together.** Eight in
   total, against the twelve planned. The pair lesson needed something the others did not — see §9.

4. ~~**Showing the working.**~~ **Done.** Two changes from playing the Same lesson: the outcome
   panel is held back until the last placement's captions have run plus a beat, because it is a
   scrim over the whole board and in a lesson it was covering the one move the lesson is about; and
   the board **rings the two facing digits that decided each capture** (`captureHighlights`), so
   "your 2 met my 2" is visible on the cards rather than only stated in a bubble.

   The rings are lesson-only (`MatchScript.explains`) — a player who knows the rules is reading the
   board, not being taught it. The pause is for every match.

   **The chain is ringed too**, against the card that actually took it rather than against the
   placement. Its attacker is found from two facts the engine already states — `Capture.wave`
   numbers the generations, and combo never chains off a basic capture — so it is a lookup, not a
   re-derivation. Where more than one candidate is adjacent the pair is genuinely ambiguous without
   re-running the comparison, and that card is left dark rather than guessed at.

   **And a combo now turns a generation late** (`COMBO_WAVE_MS`), so a chain reads as a wave rather
   than as one big capture. One constant for every mode, PvP included: `MatchView.lastPlay` carries
   the same captures with the same waves, so a combo the referee resolved animates exactly as one
   this client did. The opponent's thinking time and the outcome pause both wait for the cascade,
   or the stagger would be undone by the next thing that moves.

5. **The remaining lessons: Elemental, Ascension/Descension, the hand-and-order rules, the exam.**
6. **`RuleDemo` for Same, Plus, Combo, Same Wall**, sharing the explanation function with the
   lessons — `captureHighlights` is the beginning of it.
7. **The sandbox**, on the same explanation layer, plus `MatchAi.evaluate` for hints.
8. **The remaining demos and the "Try it" link.**

## 9. What could still go wrong

- **A one-move puzzle cannot be failed, and cannot be explored.** The exam is the mitigation: one
  real match, real AI, three random rules, nothing constrained.
- **Elemental, Ascension and Descension change effective power**, so a position that is "pure" under
  printed powers may not be under them. `find_lesson_positions.py` models none of the three — it
  does now model Reverse and Fallen Ace, which work through the basic comparison — so those lessons
  must be composed against the real engine rather than searched with this tool. That is why the
  course stops at eight.

- **"Raw power captures nothing" is the wrong question for a lesson about two rules.** An ace
  captures plenty on raw power, so the pair lesson can never satisfy it, and asking anyway returned
  a position with no ace in it at all — a plain Reverse capture dressed up as an interaction. Each
  puzzle now carries the rule sets it must be dead under (`TutorialPuzzle.baselines`); the pair's
  are Reverse and Fallen Ace *one at a time*, which is the claim it actually makes.
- **The `SpecialPowerBasis` default is `PRINTED`** (`RulesEngine.kt:86`) — Same and Plus compare
  printed values, ignoring Elemental. A lesson that combines Elemental with Same would be teaching an
  interaction most players will get wrong, and this port's default is not FF14's. Keep them apart.
