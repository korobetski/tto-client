# Card sets and formats

**Decision, taken. Not yet implemented.** It supersedes the collection model in
[06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md) and changes part of the schema proposed in
[18-MULTI-CHARACTER-ACCOUNTS.md](./18-MULTI-CHARACTER-ACCOUNTS.md), which must not be implemented
before this one is settled.

> ⚠️ **Extended by
> [20-CARD-COPIES-AND-PLATFORM-ACCOUNTS.md](./20-CARD-COPIES-AND-PLATFORM-ACCOUNTS.md).** Ownership
> stops being *"a plain set of ids"* and becomes a multiset — a card may be owned several times, and
> a deck may not use more copies than are owned. 20 also splits `Card.type` into a shared `element`
> and a per-set `group`, which this document leaves undecided, and says what the collection browser
> becomes once `MODE` is gone. It is where the mythology sets are costed. Settle it alongside this
> one; both feed the same migration and the same version bump.

> **The decision.** A set is a property of a **card**, not of a character. A player owns cards from
> any number of sets. Restriction to a set is a property of a **match** — its format — and is
> checked when a match is entered, not when a card is acquired or a deck is saved.

Prompted by removing the FFXIV and FFVIII card tables for the IP reasons BR-003 records. Replacing
the cards is the one moment this can be changed without a second reset, so it is being changed now.

## Why the collection stops existing

The collection is not a design; it is a workaround for an identifier collision. The AS3 bolted two
independently numbered card tables together — FFXIV runs 1..153, FFVIII runs 1..110 — so all 110
FFVIII ids also name an FFXIV card. `GameSave.MODE` exists to say which table `CARDS: [1, 3, 6, 7,
10]` indexes, and nothing else. Everything downstream of it — the irreversible choice at creation,
the duplicated shop, 60 opponents on one side and 25 on the other, two campaigns, two rule pools —
is that collision propagating outward.

Authoring our own cards means choosing our own identifiers. **Globally unique ids remove the
collision, and with it the only reason a character was ever bound to one table.**

What was worth keeping in the idea — grouping cards, releasing them in waves, a booster that yields
cards of one theme — is what a card game calls an *expansion*. That is a label on a card.

## Three layers, kept apart

The whole decision is that these were one thing and become three:

| Layer | What it is | Where it lives |
|---|---|---|
| **Identity** | which card this is | `cards.id`, globally unique, never reused |
| **Ownership** | that this player has it | `character_cards`, a plain set of ids |
| **Legality** | that it may be played *here* | the match's format, resolved at entry |

Owning cards from several sets does not make a restricted match impossible — it makes it a filter.
A tournament that admits one set is a format that names that set; a player brings a deck drawn from
the cards they own that the format admits.

## What a format is

Not new machinery. It is the four things that are *already* per-collection today, named once and
generalised:

| Today | Becomes |
|---|---|
| `Roulette.pools[collection]` — 13 rules for FFXIV, 8 for FFVIII | the format's legal rule pool |
| `NpcCatalog.available(collection, hour, level)` | the opponents that play this format |
| `ShopCatalog.offers(collection)` | boosters, which yield cards of a set |
| `campaigns.forCollection(mode)` | a tournament, which *is* a format plus a ladder |

The rule pools are the evidence that this is real and not decorative: FFVIII's pool carries
Elemental and Same Wall, FFXIV's carries Ascension, Descension, Order, Chaos, Swap, Fallen Ace and
Reverse. Which rules may be drawn is genuinely a property of the pool of cards being played with.

### Formats live in data

Beside `campaigns.json`, and for the reason a campaign is already there: a tournament *is* a format
plus a ladder, and both are content rather than logic. `formats.json`:

```json
{
  "formats": [
    {
      "id": "standard",
      "nameKey": "APP_FORMAT_STANDARD",
      "blocks": [1, 2],
      "rules": ["RULE_ALL_OPEN", "RULE_PLUS", "RULE_SAME", "RULE_RANDOM"]
    }
  ]
}
```

A format names the **blocks** it admits rather than the slugs, so legality is `card.id shr 8 in
format.blocks` — a shift and a set lookup per card, with nothing to resolve. The slug stays for
paths and for anything a human types.

**Opponents declare their formats, not the reverse.** `npcs.json` gains `"formats": ["standard"]`
per opponent, and `campaigns.json` replaces its `collection` with a `format`. One list rather than
two facing each other: opponents are authored one at a time, and a format holding its own roster
would be a second place for the same fact to be written differently. "Who plays this format" is a
scan over 85 rows.

A **free-play format admitting every released block** is one row like any other, and is the default
a new player lands in — see the open questions.

The three existing loaders — `loadCardCatalog`, `loadNpcCatalog`, `loadCampaigns` — gain a fourth
beside them, and `Roulette.pools`, which is a `Map<CardCollection, List<String>>` compiled into
`:core` today, becomes the `rules` field above. That is the one piece of *logic* this moves into
data, and it is the piece most likely to be tuned without a release.

## The starter pack

**A new character is created by choosing a starter pack, offered per released set.** It replaces
`GameSave.DEFAULT_CARDS` — the AS3's hard-coded five, `Save.as:30` — which names ids that are about
to stop existing.

### It is not the collection choice coming back

This looks like the thing the rest of this document removes, and it is the opposite of it, so the
difference is worth stating before anything else: **choosing the starter of set A does not restrict
you to set A.** It is the box you open first, not the half of the game you are assigned to. A
player who starts with set A buys set B boosters the same afternoon and owns both; what they may
*play* in a given match is the format's business, and always was.

The old `MODE` was irreversible, partitioned the shop, the opponents and the campaign, and could
never be revisited. The starter grants cards once and then has no further existence.

### Where it lives

Beside the other authored content, in `starters.json`, and read by **both ends from the same
parser** — as `cards.json` and `npcs.json` already are, via `Catalogs` on the server and
`loadCardCatalog` on the client.

```json
{
  "starters": [
    {
      "id": "core-beasts",
      "block": 1,
      "nameKey": "APP_STARTER_CORE_BEASTS",
      "cards": [257, 259, 262, 263, 266, 271, 274, 280, 281, 288],
      "deck": [257, 259, 262, 263, 266]
    }
  ]
}
```

- **`block`** is what makes "in function of the available sets" fall out for free: the starters on
  offer are those whose block is released. No second release flag to keep in step.
- **`cards`** is what the character owns on its first frame; **`deck`** is the five of them that
  fill its first deck slot, replacing `Deck(DEFAULT_DECK_NAME, DEFAULT_CARDS)`.
- **Fixed, never random.** A random starter makes two new players' first hour incomparable, makes
  a support question unanswerable and makes a test fixture a lottery. Boosters are where randomness
  belongs, and they already are.

### The composition is fixed: ten cards, one of them rarity 2

**Exactly ten cards: nine of rarity 1 and one of rarity 2.** The AS3 grants five, all rarity 1
(`Save.as:30` — Dodo, Sabotender, Bomb, Mandragora, Coeurl, every one a one-star), so this is twice
the cards and one card above the floor.

The composition is a rule and not a per-starter decision, and that is the point of writing it down:
**every set's starter is then worth the same.** A player choosing between the starters of three sets
is choosing a flavour, not a power level, which is the only way that choice stays a real one as sets
accumulate. The moment one set ships an eleven-card starter with two rarity-2s, the choice collapses
into "take the strongest".

**The rarity-2 card belongs in the five-card deck.** It is the card the starter is *about*, and a
first deck that leaves it in the collection is one the player would have to discover and fix without
being told there was anything to fix.

This constrains what a set must contain to be startable: **at least nine rarity-1 cards and one
rarity-2**. Worth knowing while authoring — the current tables would both pass (FFXIV has 22
one-stars, FFVIII has 33), but a small themed set could easily be written without enough commons to
open with.

### The server grants it, not the client

`NewCharacter` gains `starterId`. The client sends **the choice, never the cards** — the server
resolves the id against its own copy of `starters.json` and writes the grant, exactly as it refuses
to take the client's word for what a card is worth when replaying a match. A starter id that does
not exist, or whose block is unreleased, is refused with a modelled error.

This is also why the grant belongs to character creation and not to registration: under
[18](./18-MULTI-CHARACTER-ACCOUNTS.md) an account begins empty and owns nothing, and cards are owned
by a character. From the player's side the two coincide — the first thing a new account does is
create a character — but the row the cards land in is the character's.

**Once, at creation, and never again.** There is no endpoint to claim a starter afterwards, which is
the whole of its abuse surface.

### What an importer should refuse

Cheap checks at authoring time, each of which is a content bug that would otherwise reach a player:

- a starter whose cards are not all in its own block;
- a starter that is not exactly nine rarity-1 cards and one rarity-2;
- a starter whose `deck` is not a subset of its `cards`, is not exactly `HAND_SIZE` long, or does
  not contain the rarity-2 card;
- a starter legal in **no** format, which is a character created unable to play;
- a released block with no starter at all, which is a set the player cannot begin with.

### Open

- **One starter per set, or several?** The schema above allows several — `id` is the key and `block`
  is a field — and nothing depends on there being one. Whether to author more than one per set can
  wait until there is a second set. If there are several, the composition rule applies to each, for
  the same reason it applies across sets.

## Decks

**Eight slots, up from five.** `Save.as:31`'s comment caps them at five and this port has honoured
that; it is now a deliberate departure, recorded here and in `GameSave.MAX_DECKS`. The reason is the
decision above: a player who owns three sets and plays two formats needs more than five slots before
they start deleting decks to make room, which is a chore invented by a number.

**A deck is free-form. Legality is computed, never stored.** A deck is five card ids and a name;
nothing tags it with a format. Legality is a question asked about *a deck and a format*, at the
moment a match is entered.

That is the important half, and the alternative is worse. Tagging a deck with a format means every
new format either invalidates stored decks or cannot see them, and a player's decks quietly rot as
the game grows. Computing legality means a deck built before a format existed is legal in it the
moment it qualifies.

Two consequences to carry into the UI:

- **The deck selector filters rather than fails.** It already handles "no complete deck" by offering
  Random; it gains "no *legal* deck", with the same answer. See `DeckSelectorScreen`.
- **The deck editor must say what a deck is legal for**, or a player builds one they cannot use. A
  line under each deck naming its formats is the cheapest version.

**Random hands narrow too.** Under `RULE_RANDOM` the hand is dealt from the whole collection —
`MatchPreparation.randomHand` already takes a `List<Card>` rather than a collection, so the caller
simply passes the owned cards the format admits. No signature changes.

## Card identifiers

**A block of 255 per set, encoded in the id itself.**

```
id = (block << 8) | number        block >= 1        number in 1..255
```

So the id is readable in hex at a glance — `0x0207` is card 7 of block 2 — and both halves come back
out with a shift and a mask rather than a lookup:

```kotlin
val Card.block: Int get() = id shr 8
val Card.number: Int get() = id and 0xFF
```

Three properties this buys, each of which is the reason to prefer it to a plain sequence:

**Blocks start at 1, so no real card id is below 256.** That makes the entire range 1..255 poison,
and every legacy id — the current tables run 1..153 and 1..110 — is *detectably* invalid rather than
silently remapped onto the first set. Given that this change lands with a card reset, a save or a
transcript still carrying old ids should fail loudly, and this is what makes that free.

**`0` stays reserved and keeps its existing meaning.** `saveDeck_Handler` pushes `0` for each deck
slot left empty, and this port preserves that — `character_deck_cards.card_id >= 0` with zero
meaning "empty clip". Excluding number 0 from every block keeps that hole open and unambiguous.

**A set can grow after release** without touching anything it has already published: 255 is a
ceiling on the block, and the largest table today is 153, so the headroom is real.

The escape hatch, stated so it is not discovered later: **if a set ever needs more than 255 cards,
it takes a second block.** A set is therefore a *list* of blocks and not one block, which costs one
extra row and no rethinking. It is not the expected case and the schema below does not optimise for
it, but it is why `sets` is keyed by block rather than the reverse.

## The data model

Two new tables, and a foreign key that is impossible today.

```sql
CREATE TABLE sets (
    block       SMALLINT    PRIMARY KEY,          -- the high byte of every id in it
    slug        TEXT        NOT NULL,             -- 'core', 'beasts' — what a path and a URL use
    name_key    TEXT        NOT NULL,             -- localised, like every other name
    released_at TIMESTAMPTZ,                      -- null until it is out
    sort_order  SMALLINT    NOT NULL,
    CONSTRAINT sets_block CHECK (block >= 1)
);

CREATE UNIQUE INDEX sets_slug_idx ON sets (slug);

CREATE TABLE cards (
    id       INTEGER  PRIMARY KEY,                -- globally unique, never reused
    block    SMALLINT NOT NULL REFERENCES sets (block),
    number   SMALLINT NOT NULL,
    name_key TEXT     NOT NULL,
    top      SMALLINT NOT NULL,
    "right"  SMALLINT NOT NULL,
    bottom   SMALLINT NOT NULL,
    "left"   SMALLINT NOT NULL,
    rarity   SMALLINT NOT NULL,
    type     TEXT,
    -- The two halves are stored as well as encoded, so the block can carry a foreign key and the
    -- number can be indexed. These checks are what stop the three from ever disagreeing.
    CONSTRAINT cards_block_matches_id  CHECK (block = id / 256),
    CONSTRAINT cards_number_matches_id CHECK (number = id % 256),
    CONSTRAINT cards_number_range      CHECK (number BETWEEN 1 AND 255),
    CONSTRAINT cards_powers CHECK (top BETWEEN 1 AND 10 AND "right" BETWEEN 1 AND 10
                               AND bottom BETWEEN 1 AND 10 AND "left" BETWEEN 1 AND 10),
    CONSTRAINT cards_rarity CHECK (rarity BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX cards_block_number_idx ON cards (block, number);
```

`block` and `number` are derivable from `id` and are stored anyway, on purpose: a generated column
cannot carry the foreign key portably, and a `CHECK` cannot reach another table. Storing both and
constraining them against the id gives the reference *and* the guarantee that they agree, using
nothing exotic.

**Artwork is named by id, in hex.** `art/cards/0207.png` — four lowercase hex digits, so a file
sorts by set then by number and the importer derives the name rather than being told it. The
`ff14_` / `ff8_` texture prefix disappears with the collection it named.

**This is the precondition for the relational character.** In
[18](./18-MULTI-CHARACTER-ACCOUNTS.md) the ownership table is:

```sql
card_id INTEGER NOT NULL,
CONSTRAINT character_cards_id CHECK (card_id > 0)
```

— a check, not a reference, because id 12 is two different cards and there is nothing to point at.
With globally unique ids it becomes what it should be:

```sql
card_id INTEGER NOT NULL REFERENCES cards (id)
```

and the same for `character_deck_cards.card_id` and `character_items.card_id`. Referential integrity
across the profile is the thing the move to a relational schema is *for*, and it is unreachable
without this decision.

## What changes in document 18

Nothing in its shape; four things in its detail.

1. **`characters.collection` is removed.** With it goes the `CHECK (collection IN ('ff14_','ff8_'))`
   and the backfill from `save ->> 'MODE'`.
2. **`character_decks.slot`** widens: `CHECK (slot BETWEEN 0 AND 7)`.
3. **`character_cards`, `character_deck_cards`, `character_items`** gain real foreign keys to
   `cards`, as above.
4. **`matches`** keeps `collection` but it becomes `format_id` — what the match was played under,
   which is the fact worth recording and is no longer derivable from the character.

Its multi-character strand is unaffected in substance, but its *motivation* changes: the original
case was "an FFVIII character and an FFXIV one under one login", and that need disappears here. A
second character becomes a want — a fresh start, another progression — rather than the only way to
play both sets. Worth re-deciding its priority; the plan itself still stands.

## The model in `:core`

- **`CardCollection` is deleted.** Its two members are the two tables, and `forPrefix` exists to
  turn `"ff14_"` back into one of them.
- **`Card.collection: String`** — the texture prefix — goes with it. `Card.block` and `Card.number`
  are computed from the id, so the card gains no field to replace the one it loses, and
  `Card.textureId`, today `"$collection$id"`, becomes the id in hex.
- **`Roulette.pools`** stops being a compiled `Map<CardCollection, List<String>>` and becomes a
  format's `rules` field, read from `formats.json`.
- **`GameSave.MODE` is removed.** No irreversible choice at creation, no post-registration
  collection step — `CollectionChoiceScreen` was already slated for deletion in 18, and this removes
  its last justification.
- **`GameSave.MAX_DECKS` becomes 8.**
- **`GameSave.DEFAULT_CARDS`** — the AS3's five, `Save.as:30` — **is removed**, along with the
  starter deck built from it. A character's first cards come from its starter pack instead, so the
  model no longer holds a card id at all. Six tests reference it as a fixture and move to the
  starter.
- **`CARDS`, `DECKS`, `BAG`** are unchanged in shape. Their ids simply stop being ambiguous.
- **Achievements become set-scoped rather than collection-scoped.** `Achievement.collection` and
  `Requirement.CardSetOwned(CardCollection, …)` keep their shape and change their key — "own every
  card in this set" is the same requirement it always was.

## Opponents

Opponents stop belonging to a collection and belong to **formats**, declared per opponent as above.
Two details:

- **`queen-of-cards` exists in both current groups** and is the only shared `iconID`. `NPC_W` is
  keyed by `iconID` precisely because opponent *ids* are not unique across the two tables; merging
  the namespaces would collide those two win counts. A unification pass over opponent identifiers
  belongs in the same change.
- An opponent's deck is drawn from cards, so its format is derivable — but it is **declared**
  rather than inferred, so that an opponent can be restricted to a format narrower than its deck.
  The importer should still check the two agree: an opponent whose deck is illegal in the format it
  claims is a content error, and finding it at import is free.

## Migration

**One reset, not two.** If the cards are replaced for IP reasons then every existing save's card
list, decks and card-items are void whatever else happens. That is the moment to renumber, drop
`MODE` and widen the deck slots — doing it later would mean asking players to absorb a second break.

This lands as one migration alongside document 18's, not after it. The V2 in 18 should not be
written until this document is agreed, or it will be written twice.

## Decided

- **Card ids** are a 255-wide block per set, encoded as `(block << 8) | number` with `block >= 1`
  and `number` in 1..255. See § Card identifiers.
- **A starter pack** is ten cards — nine rarity 1 and one rarity 2, the rare one in its five-card
  deck — chosen at character creation from the released blocks. See § The starter pack.
- **Formats live in data**, in `formats.json` beside `campaigns.json`, and opponents declare which
  they play. See § Formats live in data.

## Open

1. **Does a set gate ownership at all?** Proposal: no. A booster from set X yields cards of set X,
   but nothing stops a player owning cards from every set. Restriction is the match's job.
2. **Can a deck mix sets?** Proposal: yes, and it is simply illegal in single-set formats. The
   alternative — refusing to save a mixed deck — punishes building ahead of a format's release.
3. **What is the default format?** Proposal: one admitting every released block, so a new player has
   somewhere to play before any tournament exists. Its blocks are then a function of `released_at`
   rather than a literal list, which is the one place a format is not purely authored data — worth
   naming, because "every released set" changes meaning on the day a set ships.

## What to test

| Where | The claim |
|---|---|
| tto-core | Every shipped card id decodes to a declared block and a number in 1..255, and no id is below 256 — which also proves no legacy id survived the reset. |
| tto-core | Two cards never share an id, and no block holds more than 255 — asserted over the shipped bundle, as `CardBundleTest` already asserts the tables parse. |
| tto-core | Every format's rule pool names real `RuleKeys` constants, and every opponent's declared format exists — the two ways authored data can dangle. |
| tto-core | Every starter is ten cards — nine rarity 1, one rarity 2 — all in its own block, with a `HAND_SIZE` deck drawn from them that includes the rarity-2 card. |
| tto-core | Every released block offers at least one starter, and holds enough rarity-1 cards to compose one. |
| tto-server | A starter id that does not exist, or whose block is unreleased, is refused — and the cards granted are the server's, not any list the client sent. |
| tto-client | A new character is offered exactly the starters of released blocks, and lands owning that starter's cards with its deck in slot 0. |
| tto-core | A deck legal in a format contains only cards the format admits, and the check is total: an empty format admits nothing rather than everything. |
| tto-core | A random hand under a restricted format draws only from the legal, owned subset. |
| tto-server | Every `character_cards.card_id` references a real card — which the foreign key enforces, so the test is that the migration produces no orphans. |
| tto-server | A match records the format it was played under, and a transcript naming a card illegal in it is refused by the verifier. |
| tto-client | A character can hold eight decks, and the editor offers eight slots. |
| tto-client | The deck selector offers only legal decks, and falls back to Random when none is. |
