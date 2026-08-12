# Copies, one account across games, and the set browser

**Status: proposed. § 1 is implemented; § 2 and § 3 are not.** A contract for review.

| Strand | State |
|---|---|
| § 1 — card copies and the deck rule | **Built**, on `feature/card-copies` in all three repositories. `:core` carries the multiset, the affordability rule and the position-indexed `HandVisibility`; the server verifies the rule; the client shows copies and bounds the editor. The `copies` column is *not* written — it belongs to 18's V2, which does not exist yet, and the character is still one opaque JSONB document. |
| § 2 — one account, several games | Not started. It needs 18's V2, which needs 19. |
| § 3 — the set browser | Not started. It needs 19, which removes `MODE`. |

Document 19 is part-built on `feature/global-card-ids`, which branches off the above. The
`(block << 8) | number` id scheme is in across all three repositories and green — 442 / 43 / 530
tests — with the shipped data and artwork migrated and card faces named `art/cards/013e.png`.
`CardCollection` survives as a placeholder keyed by block. **Starters and formats have since landed
on `main`** — the starter pack fully, formats as data plus a test holding them identical to
`Roulette.pools`, with the engine still reading the compiled pools. **The `MODE` removal is what
remains of 19**, and it is the part this document's § 2 and § 3 are waiting on. See 19 § Where this
stands.

`AppVersion` is already at **1.0.0** and `TRANSCRIPT_VERSION` at **2** on that branch. Per § Order
of work that is the one major bump for all of 18, 19 and 20 — the strands still to land ride this
number and must not bump it again.

> ⚠️ **Reads on top of [19-CARD-SETS-AND-FORMATS.md](./19-CARD-SETS-AND-FORMATS.md) and
> [18-MULTI-CHARACTER-ACCOUNTS.md](./18-MULTI-CHARACTER-ACCOUNTS.md), and amends both.** 19 is a
> prerequisite: two of the three strands below are unstatable while a card id means nothing without
> `MODE`. Nothing here should be implemented before 19 is settled, and the V2 migration 18 defines
> must not be written until this document is agreed either — it would be written twice.

Three strands, in one document because two of them change the same two tables and all three ship
behind the same version gate:

1. **A card can be owned several times**, and a deck may not use more copies than are owned —
   ownership stops being a set, which is what 18 and 19 both currently say it is.
2. **One account, several games** — a new axis above the character, orthogonal to 18's roster.
3. **The set browser** — with `MODE` gone, the card list has no single table to draw, and 19 does
   not say what replaces it.

Plus a short section on the **mythology sets**, which are content rather than design and are what
prompted all of the above.

---

## 0. Why the mythology sets need almost no decisions

Three sets — Norse, Egyptian, Greek — are exactly the thing 19's model is for. They are also the
direct answer to the risk that prompted 19 in the first place: BR-003 accepts the Square Enix
exposure only on condition of no distribution, and public-domain mythology is not encumbered. Sets
authored here are the first content this project could actually ship.

Under 19 each is a block, each declares a starter pack, each is admitted or not by a format, and
none of it needs new machinery. **One thing is genuinely undecided, and it is `CardType`.**

### `CardType` splits into two fields

Today it is one enum holding two unrelated vocabularies, and its own KDoc says so: four FFXIV
tribes that drive `RULE_TYPE`, eight FFVIII elements that drive `RULE_ELEMENTAL`, sharing a field
because `TTOCore.as:48-49` compares both against `tile.element` with the same two lines. That wart
is inherited, not chosen, and three new sets is the moment it becomes expensive — each would have to
decide whether its vocabulary is "tribes" or "elements" and could not have both.

**Proposal: `Card.type` becomes `Card.element` and `Card.group`.**

| Field | Values | Drives | Scope |
|---|---|---|---|
| `element` | the eight, unchanged | `RULE_ELEMENTAL`, versus `tile.element` | shared by every set |
| `group` | authored per set | `RULE_TYPE`, Ascension and Descension | the set's own |

The eight elements stay a compiled enum: they are matched against board tiles, the tile artwork is
one shared atlas, and a set inventing a ninth element would need a tile texture nothing can author.
Fire, ice, water, wind, earth, lightning, holy and poison map onto all three pantheons without
strain.

`group` is a string from the set's own data — Aesir / Vanir / Jotnar, Olympians / Titans /
Chthonic — because Ascension only ever counts cards sharing a group and never compares one group
against another. There is nothing for the engine to know about the values.

This is a change to `RulesEngine`'s inputs and therefore a replay change. It rides the same major
bump as everything else here.

### What one set costs, so three is costed honestly

Per set: at least ten cards meeting 19's starter composition rule, artwork for every card, name and
description keys in four locales, opponents that play its format, a shop shelf, a rule pool, and a
starter. The code changes in this document are perhaps three weeks; **the content is the schedule.**

**Recommendation: author one set end to end before starting the second.** Which one is open. The
pipeline — importer checks, art naming, locale bundles, the format wiring — is unproven until a set
has been through it, and discovering a hole in it three times over is the avoidable version of this
work.

---

## 1. A card can be owned several times

### What it contradicts

18's ownership table is `PRIMARY KEY (character_id, card_id)` with the comment *"A set, which is
what `CARDS` is — the AS3 stores each id once."* 19 restates it: ownership is *"a plain set of
ids"*. Both are accurate about the original and both change here.

### Why now, and not later

Because it rides the reset. 19 replaces every card and makes every legacy id detectably invalid, so
there is no existing `CARDS` array to migrate — the shape of that field is free to change in the
same break. Adding copies afterwards means a second migration over a populated `character_cards`,
and a second version gate. **This is the cheap moment and the only one.**

It also gives the duplicate a purpose it does not currently have. `ItemUse.PackOpened`'s KDoc
observes that resale is *"the only sink for a duplicate the game has"*, and `Inventory.use` notes
that the inventory screen disables Use on a card already owned (`InventoryScreen.as:111`). Under
this decision a second copy is worth keeping, and that disabled button becomes wrong — see the
punch list below.

### A count, not a row per copy

```sql
CREATE TABLE character_cards (
    character_id BIGINT   NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    card_id      INTEGER  NOT NULL REFERENCES cards (id),
    copies       SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (character_id, card_id),
    CONSTRAINT character_cards_copies CHECK (copies > 0)
);
```

One row per card with a count, rather than one row per copy, because **two copies are
indistinguishable**. `Card` is a value type; nothing on it identifies an instance; `captured()`
returns a copy with the owner flipped and no identity of its own. A per-copy row would invent an
identity the game has no use for, and then every read would have to collapse it again.

`CHECK (copies > 0)` rather than `>= 0`: a row saying "zero copies" is the same fact as no row, and
allowing both is how the two states come to disagree.

The primary key is unchanged, so the foreign key 19 introduces and the `PRIMARY KEY (character_id,
card_id)` shape 18 relies on both survive. This is one added column.

### In `:core`

```kotlin
/** Card id to how many copies are held. `CARDS`, which was a list of ids. */
@SerialName("CARDS") val cards: Map<Int, Int> = emptyMap()
```

Five things follow, and each is a place the old shape's assumption is written down:

- **`GameSave.sane()` loses its `distinct()`.** The line is
  `cards.filter { it > 0 }.distinct().sorted()`, commented *"Distinct and ascending, so the
  collection screen cannot show a card twice."* That comment states the exact behaviour being
  removed. It becomes: drop entries whose count is not positive, and drop ids naming no card.
- **`ownsCard(id): Boolean` becomes `copiesOf(id): Int`**, with `ownsCard` kept as
  `copiesOf(id) > 0` because a dozen call sites want the boolean and nothing is gained by making
  each of them write the comparison.
- **`withCard(id)` stops being a no-op when the card is held** and increments instead. Its current
  body is `if (ownsCard(cardId)) this else …`; that early return is the whole change.
- **`Achievement.Requirement.CardsOwned`** reads `save.cards.size`. It must keep counting **distinct
  cards**, not copies — "own 50 cards" is a collection milestone, and paying it out for fifty copies
  of one common would be a new and worse achievement. `save.cards.size` on the map happens to be
  right; it is written down here because it is right by accident and a later refactor could
  plausibly "fix" it to a sum.
- **`PveMatches.playerDeck`** falls back to `profile.cards.take(HAND_SIZE)` when no deck is
  complete. Over a map this needs a defined order; take the ids sorted, as the list was.

### Deck legality: a multiset, and it is a model rule

**A deck may not name a card more times than the character owns copies of it.**

`Deck.plusCard`'s KDoc currently argues the opposite, and argues it deliberately: *"A duplicate is
allowed through: nothing in the original prevents the same card appearing twice in a deck either."*
That reasoning was sound when a duplicate could not be owned. It is superseded, and the KDoc must be
rewritten rather than left contradicting the code.

The check belongs in `:core` beside `Deck.isComplete`, not in the editor:

```kotlin
/** True when this deck uses no card more times than [owned] holds copies of it. */
fun Deck.isAffordable(owned: Map<Int, Int>): Boolean =
    cards.groupingBy { it }.eachCount().all { (id, used) -> used <= (owned[id] ?: 0) }
```

**Two consequences worth stating before someone asks.**

- **Across decks, no.** Two saved decks may both name the single copy you own. Decks are not
  simultaneous — one is loaded into one match — so a global budget would be a restriction with no
  fiction behind it, and would make saving a deck depend on the contents of four others.
- **Mid-match duplicates are still legal.** `RULE_SWAP` can hand you a card you already hold, and
  `MatchState.suddenDeathRematch` regroups whatever each side ended up owning. This rule governs
  what a *deck* may be built from, not what a hand may contain.

### The server must check it, or it is decoration

`TranscriptVerifier` today refuses a transcript whose collection disagrees with the profile's
`MODE`, and that is the only claim it makes about the hand's provenance. Under 19 that check
disappears with `MODE`; under this document it is replaced by a stronger one:

> **The opening hand must be covered by the character's owned multiset**, and by the format's
> admitted blocks.

Without it the deck rule lives only in the editor, and the editor is the client — which is the
arrangement the whole transcript design exists to end. The check is a multiset containment against
`character_cards`, which the server already loads to credit the match.

### The trap: `HandVisibility` breaks

This is the one consequence that is not a rename, and it is worth the rest of this section.

`HandVisibility` is a `Set<Int>` of card ids, and its KDoc justifies that choice explicitly:

> *Ids are unique within a hand — [randomHand] draws without replacement and [Npc.randomHand]
> cannot repeat a card either — so nothing is lost by the change.*

That premise stops holding the moment a deck may hold two copies. Under `RULE_THREE_OPEN` the rule
reveals three of five by taking three cards and collecting their ids; with a duplicate in hand,
`hand.shuffled().take(3).map { it.id }.toSet()` yields a set of two, and `visible()` then filters
the hand by id membership and shows **two or four cards, never three**. It is silent, it is
rule-visible to the opponent, and it is exactly the sort of thing that surfaces as a bug report six
months later.

**`HandVisibility` becomes slot-indexed**: `Set<Int>` of positions into the hand, not of card ids.
The KDoc's original argument against slots — that a slot index starts naming a different card once
cards are played and `MatchState.hands` closes the gap — is real, so the visibility must be
re-indexed as the hand shrinks, or held as an identity set alongside the hand. Either is a small
change; picking one without noticing the problem is not available.

Two neighbours, checked and deliberately left alone:

- **`MatchState.play`** finds the card with `indexOfFirst { it.id == card.id }` and so plays the
  first of two identical copies. That is correct, because they are identical. Recorded here so it is
  not "fixed" later by someone who assumes it is the same defect.
- **`MatchPreparation.randomHand`** shuffles the collection and takes five, and requires at least
  five to draw from. The caller must now pass the **expanded** multiset — a character owning three
  copies of one card and two others can field a legal hand, and a caller passing distinct cards
  would refuse it. The signature does not change; 19 already notes it takes a `List<Card>`.

### Open

1. **Is there a cap on copies?** Proposal: none in the model, `SMALLINT` in the schema. A cap is a
   balance decision that wants tuning, and the natural limit is `HAND_SIZE` — a sixth copy is
   already unplayable in one deck. If one is wanted later it belongs in the format, beside the
   blocks it admits, not in `GameSave`.
2. **Does the shop stop refusing a card you own?** It must, or copies are unobtainable except from
   boosters. `InventoryScreen`'s disabled Use goes with it.
3. **Does anything consume copies?** Card fusion, upgrading, a trade-in — all plausible, none
   required, and all are new features rather than parts of this one. Naming them here so the schema
   above is understood to permit them.

---

## 2. One account, several games

18 gives an account several **characters** of one game. This strand adds the axis above it: an
account is an identity on a platform, and a game is one of the things it plays.

### What it contradicts, and how much

Less than it looks, in the schema; more than it looks, everywhere else.

`accounts`, `sessions` and `password_resets` are already game-agnostic — a username, a bcrypt
digest, an email, a bearer token. Nothing in V1's account half knows what Triple Triad is. **The
split runs exactly where 18 already draws a line**, between the account and the character.

What does not survive is the assumption underneath `:core` and underneath `AppVersion`.

### The version gate is the hard part

`AppVersion`'s KDoc makes a promise that is load-bearing:

> *`:core` is the artifact they share … so a build of the client and a build of the server that link
> the **same** `:core` necessarily agree about this number.*

With one server and two games, that guarantee inverts into a hazard. A major bump means *the replay
can reach a different answer* — and a replay change in game 2 has nothing to do with game 1. A
single `CURRENT_VERSION` would make deploying game 2 refuse every game 1 client, and the KDoc
already names that failure mode as *"nasty and misdiagnosed"*.

**`CURRENT_VERSION` and `minimumClient` become per app.** `VersionGate` resolves them from the app
the request names, and refuses with `426` before the body is read exactly as it does now.

### `:core` splits in two

That is the structural consequence, and it should be taken deliberately rather than discovered.

| Artifact | Holds | Who links it |
|---|---|---|
| `:platform-core` | `Credentials`, `Session`, `AccountState`, `AccountError`, the `AppVersion` machinery, the transcript **envelope** | the server, and every game client |
| `:tto-core` | `Card`, `GameSave`, `RulesEngine`, `MatchState`, `Roulette`, `MatchAi`, the transcript **payload** and its verifier | the server's TTO module, and the TTO client |

The server's verification is game-specific by definition: it re-runs *the real rules*, and game 2's
rules are different code. So the server gains a small registry — a `GameModule` per app, holding
that app's catalogs, its verifier, its crediting and its version — and `MatchRoutes` dispatches on
the app rather than calling `Catalogs` and `TranscriptVerifier` directly.

`Catalogs` today is an `object` reading `/catalog/cards.json` from the classpath at startup, and
`preload()` fails fast if it is missing. That shape survives; it becomes one per module, and
`preload()` walks the registry.

**One deployable, not two.** Splitting the process buys isolation nothing here needs and costs a
second deployment, a second set of secrets and a shared database with no shared transaction.

### The schema

```sql
CREATE TABLE apps (
    id          TEXT        PRIMARY KEY,        -- 'tto', short and stable; it appears in paths
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT apps_id_shape CHECK (id ~ '^[a-z][a-z0-9-]{1,15}$')
);

ALTER TABLE characters ADD COLUMN app_id TEXT NOT NULL REFERENCES apps (id);

-- 18 makes the character name unique per account. It becomes unique per account *and app*:
-- one player may be "Kuplu" in two games, and forbidding it would be a rule with no purpose.
DROP INDEX characters_name_per_account_idx;
CREATE UNIQUE INDEX characters_name_per_account_app_idx
    ON characters (account_id, app_id, lower(name));

CREATE INDEX characters_account_app_idx ON characters (account_id, app_id);
```

Everything hanging off `characters` — the cards, the decks, the bag, the achievements, the two win
maps — needs no change at all. They are per character, and a character is now per app. That is the
whole benefit of putting the axis here rather than on the account.

**`matches` does not get an `app_id`.** It is derivable through `character_id`, and the query that
wants it ("everything played in this app") is already grouping by character. This departs from 18's
decision to keep the redundant `matches.account_id`, and the reason it departs is the one 18 gives:
that column is kept because *dropping a populated column cannot be undone*. `app_id` would be new,
so there is nothing to be stuck with.

**18's per-account character cap becomes per account and app.** Eight characters in each of three
games is not "too many characters"; eight across all of them would make installing a second game
cost you a character in the first.

### The protocol

18 changes `Session.player: PlayerState` to `Session.account: AccountState`, on the grounds that
signing in no longer knows which character was meant. The same reasoning applies one level up: it no
longer knows which **game** was meant either.

- **`AccountState` loses its `characters` list**, which is app-scoped, and keeps the account-scoped
  fields: `username`, `email`. The roster moves behind the app.
- **The app is a path segment, not a header.** `GET /apps/{appId}/me` returns that app's roster;
  18's character endpoints move underneath it. A header would be optional-looking, and a forgotten
  one would return the wrong game's roster rather than an error.
- **One token, every app.** That is the feature. The session is an identity, and scoping it to an
  app would mean signing in twice on the same device.
- **`AccountError` gains `NO_SUCH_APP`.**

### What is shared and what is not

| | Shared | Per app |
|---|---|---|
| Username, password, email, sessions | ✅ | |
| Characters, and everything they own | | ✅ |
| Avatar | | ✅ — it is already `GameSave.AVATAR_ID`, and the 27 portraits are this game's art |
| Match history | | ✅ via the character |
| Client version and the gate | | ✅ — see above |

**A cross-game profile — one display name, one avatar, an aggregate record — is deliberately not
here.** It is a product feature, it needs art that belongs to no game, and adding it later is
additive. Building it now would mean deciding what a level means across two games with different
XP curves.

### Migration

One `INSERT INTO apps VALUES ('tto', …)` and a backfill of `characters.app_id` to `'tto'`, both
inside 18's V2. **Do not ship this as a V3.** It touches `characters`, which V2 creates, and doing
it afterwards means writing the same table's constraints and indexes twice — the same argument 19
already makes to 18 about its own amendments.

### Open

1. **Does registration name an app?** Proposal: yes, recorded as the account's origin, and it grants
   nothing. Useful for support and for knowing which game brought someone in; it must not gate
   anything, or the account is not really shared.
2. **Do the offline queue and `SessionStore` key on the app?** They key on server + character id
   under 18, and a character already implies its app, so no — but `ProfileGate.queueKey` is
   described in 18 as *"the single most important line in the change"* and deserves the same
   attention here. A test that two games on one device keep separate queues is cheap.
3. **Is there a platform-level ban or lock?** An account suspended in one game — suspended
   everywhere, or in that game? Not needed on day one, but the answer changes where the flag lives.

---

## 3. The set browser

19 removes `MODE` and says nothing about what the collection screen then shows. This fills that in.
It is the smallest strand and the only one a player sees directly.

### What stops compiling

- **`CardListBody`** opens with `catalog.collection(profile.mode.prefix)` and counts owned against
  that one table. Both go.
- **`cardCellTestTag`**'s KDoc — *"Ids are per-collection, and only one collection is ever on
  screen"* — stops being true, and stops needing to be: ids are globally unique under 19.
- **`DecksBody`** has the same problem in its editor: `profile.cards.mapNotNull(cards::get)` over
  one collection's map.

### Sets are a filter, not a second tab bar

`CollectionScreen` already spends its tab bar on Cards / Decks, and that pairing is argued for at
length — they are one activity, and the merge is what makes a four-destination navigation bar
possible. Putting sets in a second row of tabs would undo the reason the first row exists.

**Sets are a segmented filter inside the Cards tab**, with an "All" position first and one per
released set. It is one row of chips, it scrolls horizontally when there are six sets, and it does
not compete with the tab bar above it.

The same filter appears in the deck editor's pick grid, for the same reason and with one addition:
the editor must also show **how many copies remain unspent** in the deck being built, or the
affordability rule refuses a card with no visible explanation.

### What the grid shows

- **Copies as a badge.** `×2` in the corner of the thumbnail, absent at one copy. Not a second cell:
  the grid answers *what is there and what do I have*, and two identical thumbnails answer it worse.
- **Unowned stays dimmed**, as it is now — `UNOWNED_ALPHA`, the alpha standing in for the
  colour-matrix filter Compose has no portable equivalent of.
- **The owned counter becomes two numbers**: the filtered set's `owned / total`, and the overall
  total when the filter is "All". It is currently counted over the table rather than over `CARDS`,
  deliberately, so an id naming no card cannot push the count past the size — that reasoning
  survives unchanged.
- **The filter is remembered for the session, not persisted.** A player browsing the Norse set
  should still be there after opening a card's detail; they should not still be there next week
  having forgotten they set it.

### Open

- **Sort order within a set.** Card number is what 19's ids give for free. By rarity, or owned-first,
  are both plausible and both are a second control. Proposal: number, and revisit when a set is
  large enough for it to hurt.
- **Does the browser show unreleased sets greyed out?** Proposal: no — it advertises content that
  does not exist and dates badly.

---

## Order of work

This document does not stand alone and does not ship alone. Folded into 18 and 19's sequence:

1. **19** — global ids, sets, formats, starters. Everything else is unstatable first.
2. **§0's `CardType` split** — with 19, since it changes the card record and the replay.
3. **§1 in `:core`** — the multiset, the deck rule, `HandVisibility`, the verifier check.
   Publishable and testable before any schema exists.
4. **18's V2, carrying §1's `copies` column and §2's `apps` table.** One migration, not three.
5. **§2's server split** — `:platform-core`, the `GameModule` registry, the per-app gate.
6. **The client** — 18's roster screens, then §3's browser.
7. **Content** — one mythology set end to end, then the other two.

**One major `AppVersion` bump for all of it.** Every strand here either changes the replay or
changes the save; four bumps would be four gates and four windows in which a client and a server can
be wrong about each other.

## Decided

- **Ownership is a multiset.** `character_cards` gains `copies`, `GameSave.CARDS` becomes
  `Map<Int, Int>`, and `GameSave.sane()` loses the `distinct()` that enforced the old shape. See §1.
- **A deck may not use more copies of a card than are owned**, checked in `:core` and enforced by
  the server against the opening hand. Across decks there is no budget. See §1.
- **`HandVisibility` becomes slot-indexed**, because its uniqueness premise fails under duplicates
  and `RULE_THREE_OPEN` would silently reveal the wrong number of cards. See §1.
- **The app is an axis above the character**, not above the account: `characters.app_id`, one
  session across all apps, and the roster behind `/apps/{appId}/me`. See §2.
- **`:core` splits** into a platform half and a game half, and the version gate becomes per app. See
  §2.
- **`Card.type` splits** into a shared `element` (the eight, compiled) and a per-set `group`
  (authored). See §0.
- **Sets are a filter inside the Cards tab**, not a second tab bar. See §3.

## Open

Collected from the sections above, in the order they would need answering:

1. Which mythology set is authored first (§0).
2. Whether copies are capped, and if so by the format rather than the model (§1).
3. What else consumes copies — fusion, trade-in, nothing (§1).
4. Whether registration records an originating app (§2).
5. Whether a suspension is per platform or per app (§2).
6. Sort order within a set, and whether unreleased sets are shown (§3).

## What to test

| Where | The claim |
|---|---|
| tto-core | A profile holding three copies of a card round-trips, and `sane()` neither collapses nor drops them. |
| tto-core | A deck naming a card twice is affordable with two copies and unaffordable with one, and the check is total: an unknown id is unaffordable rather than free. |
| tto-core | Two decks may each name the single copy owned. |
| tto-core | Under `RULE_THREE_OPEN`, a hand containing two copies of one card reveals exactly three cards. This is the regression the current `Set<Int>` would fail. |
| tto-core | A random hand can be dealt from a collection of five copies spanning fewer than five distinct cards. |
| tto-core | "Own N cards" counts distinct cards, not copies. |
| tto-server | A transcript whose opening hand exceeds the copies owned is refused, and refused with a modelled reason rather than as a replay mismatch. |
| tto-server | A transcript naming a card the format does not admit is refused. |
| tto-server | `copies = 0` is refused by the database. |
| tto-server | A character id belonging to another **app** is refused on every route that takes one — 18 asserts this for another account; the same hole exists one axis over. |
| tto-server | Two apps may each hold a character named "Kuplu" for one account; one app may not hold two. |
| tto-server | A client whose version is below one app's `minimumClient` is refused for that app and served for another. |
| tto-client | Two games on one device keep separate offline queues and separate selected characters. |
| tto-client | The card grid shows a copy badge at two copies and none at one. |
| tto-client | The deck editor refuses a card whose copies are all spent, and says why. |
| tto-client | The set filter narrows both the grid and the owned counter, and "All" restores both. |
