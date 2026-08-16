# Teaching the game: a proposal for the tutorial and the help screen

**Status: proposal. Nothing here is implemented, and nothing here has been verified against a
build** — this repository's `core` artifact could not be resolved in the environment the document
was written in, so every claim below about *current* behaviour comes from reading the sources cited,
not from running them. File and line citations are to this checkout at `1291b08`.

The question this answers: the tutorial teaches one rule out of seventeen, and the help screen is
seventeen paragraphs of text — three of which explain nothing at all. What should replace them.

---

## 1. What exists today

### The lesson

`TutorialScreen` (`shared/src/commonMain/kotlin/com/tripletriad/ui/TutorialScreen.kt`) is a single
scripted match: a fixed five-card hand (`TUTORIAL_NUMBERS`, cards 1/3/6/7/10 of block 1), the
opponent forced to move first, a 60-second turn, `MatchAiOptions.TUTOR` so the tutor plays badly on
purpose, and nine lines of dialogue placed on placements 0, 1, 2, 3 and 8 (`tutorialLines`). It is
built as data — a `MatchScript` handed to the ordinary `MatchScreen` — which is the part of the
design worth keeping; everything proposed below extends that shape rather than replacing it.

What the nine lines actually cover, read off `app-fr_FR.json`:

| Concept | Line |
|---|---|
| 3×3 grid, random first player | `APP_TUTORIAL_1` |
| All Open exists, other rules exist (unnamed) | `APP_TUTORIAL_2` |
| The tutor demonstrates a placement | `APP_TUTORIAL_3` |
| Place adjacent to a card | `APP_TUTORIAL_4` |
| The four digits are the four sides | `APP_TUTORIAL_5` |
| A captured card changes colour | `APP_TUTORIAL_6` |
| Your own cards are safe on your turn | `APP_TUTORIAL_7` |
| Win condition, end condition | `APP_TUTORIAL_8`, `APP_TUTORIAL_9` |

So: the board, turn order, the digits, basic capture, and the win condition. **One named rule, All
Open, and it is named rather than taught.** The other sixteen rules in `HELP_FAMILIES`
(`HelpScreen.kt:206`) — Same, Same Wall, Plus, Combo, Reverse, Fallen Ace, Elemental, Ascension,
Descension, Sudden Death, Random, Order, Chaos, Swap, Roulette, Three Open — are never demonstrated
anywhere. A player meets Plus for the first time in a real match, against an opponent playing to
win, with money on the table.

### The help screen

`HelpScreen` is an accordion over `HELP_FAMILIES`: tap a rule, read `"${ruleKey}_HELP"`. Its own
KDoc (`HelpScreen.kt:62-71`) records that `RULE_SAME_WALL_HELP`, `RULE_COMBO_HELP` and
`RULE_ELEMENTAL_HELP` resolve to the rule's *own name* in all four imported bundles — the screen
shows the title twice and explains nothing — and that `RULE_ORDER`'s English label is an
untranslated French leftover. That is a defect in Square Enix's data, correctly reported rather than
papered over (convention 6). **An animated demonstration is the one repair that fills those three
gaps without inventing source text**, which is what makes the second half of this proposal more than
a nice-to-have.

### What the lesson machinery can and cannot express

`MatchScript` (`MatchScript.kt:65`) carries `speakerKey`, `deck`, `firstPlayer`, `turnLimit`,
`aiOptions`, `lesson`, `outcomeLines`. Everything a curriculum needs beyond the existing lesson is
missing from it:

| Needed for | Missing today |
|---|---|
| A lesson on Plus, Same, Elemental… | **forced rules.** Rules come from `PveMatches.rulesFor(npc, format, random)` (`MatchScreen.kt:254`); a script cannot say "this lesson is played under Plus". |
| Guaranteeing the rule fires | **the opponent's hand.** `deck` is the player's only. |
| Short lessons | **a pre-filled board.** Every lesson is nine placements from empty. |
| Demonstrating a rule | **scripted opponent moves.** `MatchAiOptions.TUTOR` loses on purpose; it cannot be told to play a specific card in a specific cell. |
| Reacting to what happened | **post-placement lines.** `Lesson.linesBefore(placement, blueScore)` fires *before* a placement and sees only the score, so "that was a Combo!" cannot be said. |
| Guided practice | **move constraints.** Nothing can restrict or highlight the legal move. |
| A curriculum at all | **progress.** No record of which lessons a player has finished. |

These seven are the real work. Which of them you need depends on which option below you pick.

---

## 2. Three options

### Option A — Extend the one lesson (small)

Keep a single match, lengthen the script: add lines for Same and Plus and let them fire if they
happen to occur. Cost: a handful of string keys, no structural change.

**Recommend against.** "If they happen to occur" is the whole problem — with an unforced ruleset
and a fixed hand, Same fires or it doesn't, and a lesson that sometimes teaches nothing is worse
than one that teaches one thing. It also does not touch the help screen, where the actual textual
gap is.

### Option B — An initiation campaign of short lessons *(recommended)*

An "Academy" — a list of ten to twelve named lessons, each a short scripted match under exactly one
rule, played in order, each unlocked by finishing the one before it, each ending in a sentence that
names what was just learned. This is what you described, and it fits the existing architecture
better than it has any right to: a ladder of scripted matches driven by a step counter is
`CampaignMatchScreen` (`CampaignScreen.kt:183`), already written and already tested.

**The key design decision: most lessons should be puzzles, not matches.** A nine-placement match to
teach Plus takes four minutes and can be derailed by the player at every step. A board pre-filled to
one move from a Plus, with the winning card in hand and the sentence "place the 5 here — two sides
add up on both neighbours at once", takes fifteen seconds and *cannot* fail to teach it. Reserve
full matches for the first lesson (which already is one) and the last (the exam).

A curriculum, ordered so each lesson only uses what the previous ones taught:

| # | Lesson | Rule taught | Shape |
|---|---|---|---|
| 1 | The board | — (the existing nine lines) | full match, All Open |
| 2 | Where to place | corners and edges: a card on an edge exposes fewer sides | puzzle, 2 moves |
| 3 | Same | `RULE_SAME` | puzzle, 1 move |
| 4 | Plus | `RULE_PLUS` | puzzle, 1 move |
| 5 | Chains | `RULE_COMBO` (needs Same or Plus to fire) | puzzle, 1 move |
| 6 | The walls | `RULE_SAME_WALL` | puzzle, 1 move |
| 7 | Upside down | `RULE_REVERSE`, then `RULE_FALLEN_ACE`, then both at once | 3 puzzles, 1 move each |
| 8 | The elements | `RULE_ELEMENTAL` | puzzle, 2 moves |
| 9 | Rising and falling | `RULE_ASCENSION`, `RULE_DESCENSION` | short match, 5 placements |
| 10 | Whose hand is it | `RULE_RANDOM`, `RULE_SWAP`, `RULE_CHAOS`, `RULE_ORDER` | short match, the rules announced by the pre-match banners that already exist |
| 11 | Before the first card | `RULE_ROULETTE`, `RULE_THREE_OPEN`, `RULE_SUDDEN_DEATH` | short match, ends in a draw on purpose so Sudden Death fires |
| 12 | The exam | three random rules, `MatchAiOptions()` — an opponent playing to win | full match |

Lessons 3 to 8 are one move each. The whole curriculum is fifteen to twenty minutes, and a player
who quits after lesson 5 has still learned Same, Plus and Combo.

**Rewards.** The existing tutorial charges the tt-master's entry fee and pays a full reward
(`TutorialScreen.kt:24-35`, deliberately). A curriculum should not pay twelve rewards — make the
lessons free and unpaid, and pay one lump at the end of the exam (MGP, or a card the player keeps).
That also removes the "repeatable for profit" question the current KDoc has to argue about.

**Where it goes.** A new `Screen.ACADEMY` (list) plus `Screen.ACADEMY_LESSON` (the running lesson),
both with `up` pointing at `OPPONENTS` — one line each in `Screen.kt:64`, and `depth` follows for
free. The existing `TUTORIAL` row on the opponent list becomes the entry to the list rather than to
a match; `Screen.TUTORIAL` either disappears or becomes lesson 1's id.

### Option C — A rules sandbox (the full refactor)

Drop the scripted-lesson idea entirely and ship a *laboratory*: a board the player composes by hand
— place any card anywhere, toggle any rule, step forward and backward — with the engine narrating
what each placement resolved to and why. This is the most powerful teaching tool of the three and by
far the most work: it needs an editor UI, an undo stack, and an explanation layer that can say *why*
a capture happened, which the engine does not currently expose (`RulesEngine` returns the resulting
state, not a justification).

**Recommend as a later addition, not instead of B.** A sandbox teaches a player who already knows
what to ask; a curriculum teaches one who doesn't. But the explanation layer it needs is worth
building anyway — see §4, where the same "why did this capture" data drives the help animations.

---

## 3. What Option B needs, and where each piece lives

Ordered by dependency. Items marked **core** cannot be done in this repository alone: they mean an
edit in `tto-core`, a `publishToMavenLocal`, a version bump in `gradle/libs.versions.toml`, and — if
they touch `MatchTranscript`, `GameSave` or the protocol — a matching release of `tto-server`.

1. **`MatchScript.rules: GameRules?`** — client-only. `MatchScreen.kt:254` becomes
   `script?.rules ?: PveMatches.rulesFor(...)`, following the existing `deckFor`/`flip`/`aiOptions`
   extension pattern so no branch is added to `MatchScreen` (which is already at detekt's complexity
   limit — see `MatchScript.kt:39-41`). `GameRules` is constructible and toggleable from the client
   already: `PvpTableScreen.kt:87` does `GameRules()` and `rules.toggling(key, on)`.
2. **`MatchScript.opponentDeck: List<Int>?`** — needs whatever `PveMatches.assemble` uses to build
   the red hand; likely **core**, unless `MatchPlan` can already carry it.
3. **`MatchScript.opening: Board?` (a pre-filled board)** — **core**. This is the one that unlocks
   puzzles, and the one to scope first, because if `MatchState` cannot be constructed mid-game
   cheaply, the whole puzzle idea collapses back to short matches and the curriculum above needs
   re-timing. **Check this before committing to anything else here.**
4. **Scripted opponent moves** — `MatchAiOptions` already selects a strategy; a `Scripted(moves)`
   variant is **core**, but a client-side alternative exists: since a puzzle is one player move
   long, the opponent may simply never move.
5. **`Lesson.linesAfter(placement, outcome)`** — client-only, and the shape matters: it should
   receive what the placement *did* (captures, and by which rule) rather than just a score, so a
   lesson can say "that was a Plus" instead of "well done". If the engine does not report the
   capture reason, this degrades to "lines after placement N" — still enough for the curriculum
   above, since each puzzle has exactly one intended move.
6. **Move constraints and highlighting** — client-only. A puzzle wants `allowed: Set<Int>` (cells)
   and optionally a required card, with the disallowed cells rendered inert. `BoardGrid` already
   distinguishes three cell states (`isTarget`, `isOpen`, plain — `MatchBoard.kt:224`); a fourth,
   "not this one", is a border colour and a `clickable` guard.
7. **Progress.** Two honest choices, and they differ in whether progress follows the account:
   - **`SettingsStore`** (client-only): a JSON blob keyed by profile. Zero core churn, works with
     `server == null`, but a player who signs in on a second device starts over.
   - **`GameSave`** (**core** + server): progress syncs. `GameSave` already carries `achievements`
     and `hasAchievement`, so the cheapest version of this is one achievement per lesson and no new
     field at all — worth checking whether the achievement table is open enough to take twelve
     entries.

   **Recommend `SettingsStore` first.** Lesson progress is not worth a protocol version, and moving
   it into the save later is a migration of a local file, not of a schema.

8. **Curriculum data.** `campaigns.json` is generated by `tools/extract_campaigns.py` from the AS3
   tree, and convention 5 forbids hand-editing it. **The academy is not in the AS3 original, so it
   must be authored, not generated** — put it in Kotlin, as a `TutorialCurriculum` value in the
   client's `data/` package (which already holds hand-written code such as
   `MatchHistoryRepository`), not in a new JSON file that would look generated. Card ids, board
   layouts and rule keys are all compile-time constants there, and a wrong id becomes a test failure
   rather than a runtime `null`.
9. **Strings.** New `APP_ACADEMY_*` keys: add to `StringKeys`, to its `appOwned` list
   (`StringKeys.kt:732`), and to `app-en_US.json` and `app-fr_FR.json` only — `StringsBundleTest`
   asserts that app-owned keys are translated in exactly those two and fall through to English in
   `de_DE`/`ja_JA` (`StringsBundleTest.kt:92`), and it pins the total key counts, so
   `TRANSLATED_KEYS`/`UNION_KEYS` must be updated in the same commit. Budget four to six lines per
   lesson: roughly 60 new keys.

### One UX fix worth doing regardless of the option chosen

`LessonBubbles` (`MatchScript.kt:176`) advances on a **6.1-second timer** and the turn clock keeps
running behind it (`MatchScript.kt:160-168`, deliberately, following the AS3). That is tolerable for
nine lines. For sixty it is not: a fast reader waits, a slow reader loses the sentence, and neither
can go back. **Make a lesson bubble advance on tap, with the timer as a fallback, and pause the turn
clock while a lesson line is up.** This is a small change to `TalkBubble`/`LessonBubbles` and it
deviates from the original — which convention 3 asks be documented, with the reason: the original
had nine lines and a lesson has sixty.

---

## 4. The help screen: animated examples

The proposal is a `RuleDemo` composable — a small, non-interactive 3×3 board that plays a two-or
three-placement scene on a loop, shown inside the accordion row under the rule's text.

**Why it is the right fix here specifically:** three of the seventeen `_HELP` texts explain nothing
(`HelpScreen.kt:62-71`), and the repository's own rule is not to invent Square Enix source text to
fill them. An animation is *this port's* content rather than a forged translation, so it fills the
gap without touching the imported bundles at all.

Design:

- **Driven by the real engine, not by hand-drawn frames.** A demo is a tiny fixed scenario — a
  ruleset, two hands, a list of placements — replayed through `RulesEngine`, one placement per
  beat, with the resulting `MatchState` rendered. That way a demo cannot drift from the rules: if
  Plus ever changes, the Plus demo changes with it. It also means each demo is ~6 lines of data.
- **Reuse the card and cell visuals, not `BoardGrid` itself.** `BoardGrid` takes a
  `BoardDragState` and an `onPlace` and registers drop targets; a demo wants none of that. A
  separate `DemoBoard` over `CardFace`/`BoardCard` at a small `scale` is less code than making
  `BoardGrid` optional-everything.
- **Captions per beat**, one short sentence — "the 6 meets a 6 and the 3 meets a 3: both are taken"
  — which is also what makes the demo readable with the sound off and by a screen reader. The whole
  demo should carry a text alternative for exactly that reason.
- **Respect reduced motion.** `rememberReducedMotion()` exists (`platform/ReducedMotion.kt:32`);
  with it on, the demo shows its final state and its captions as a static sequence, no loop.
- **Loop with a pause**, and a play/replay control. An animation that loops forever under a
  paragraph of text is a distraction while the paragraph is being read.
- **Test tags** per demo (`help-demo-<ruleKey>`) so `HelpUiTest` can assert that every rule with a
  demo shows one, and — per convention 2 — a test that fails if a demo's scenario stops producing
  the capture it claims to show. That last one is the test worth writing: it is the one that would
  catch a demo silently teaching the wrong thing after an engine change.

Which rules get a demo, in priority order: **Same, Plus, Combo, Same Wall** (the three placeholder
texts are in this set), then **Reverse, Fallen Ace, Elemental**, then **Ascension/Descension**. The
hand-and-order rules (Random, Order, Chaos, Swap, Roulette, Three Open) are poorly served by a board
animation — they are about what happens *before* a placement — and are better left as text, or
shown as a hand rather than a board.

**Connect the two halves:** a rule row whose lesson exists in the academy gets a "Try it" action
that opens that lesson directly. That is the point at which the help screen stops being a glossary.
Note that it makes a lesson reachable from two places with different back destinations, which is
precisely the condition `Screen.kt:12-15` names as the moment to reconsider the enum-based
navigation — so either pass the origin into the lesson screen (a `remember`ed field, the cheap
answer) or take it as the trigger to look at Compose Navigation again.

---

## 5. Suggested order of work

Each step is shippable on its own, and each one leaves the game better than it found it.

1. **Spike, half a day: can a `MatchState` be built from a pre-filled board in `:core`?** Everything
   else in Option B is timed off the answer. If it is cheap, the curriculum above stands; if it is
   not, lessons 3 to 8 become five-placement matches and the academy grows to ~30 minutes.
2. **The bubble UX fix** (§3, tap-to-advance, clock paused). Independent, improves the tutorial that
   ships today, and is the prerequisite for any lesson longer than nine lines.
3. **`MatchScript.rules`** + the first three puzzles (Same, Plus, Combo) behind the existing
   `TUTORIAL` row, with no list screen and no persistence — a straight sequence. This is the point
   at which the idea is either obviously good or obviously wrong, and it is reached with roughly
   one screen of new code.
4. **The academy list, progress in `SettingsStore`, the remaining lessons, the exam and its reward.**
5. **`RuleDemo` for Same, Plus, Combo and Same Wall**, which are the four where the existing text is
   weakest.
6. **The remaining demos, and the "Try it" link from a rule to its lesson.**
7. *Later, if it still looks worth it:* the sandbox (Option C).

## 6. What could go wrong

- **The engine may not report *why* a capture happened.** Both the "that was a Plus!" lines and the
  demo captions want it. If it doesn't, both degrade to scripted text that asserts what the scenario
  was designed to produce — which is fine as long as a test proves the scenario still produces it.
- **A puzzle with one legal move is a lesson the player cannot fail, and also one they cannot
  explore.** The mitigation is the exam: one real match, real AI, three random rules, where nothing
  is constrained.
- **Twelve lessons is a lot of copy in two languages**, and copy is the part that cannot be
  refactored later. Write lesson 1's four lines first and read them aloud before writing the other
  eleven.
- **`MatchScreen` has no complexity budget left** (`MatchScript.kt:39-41`). Every item in §3 must
  arrive as data on `MatchScript` read through an extension, never as a branch in the composable.
