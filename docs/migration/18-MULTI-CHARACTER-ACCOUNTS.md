# Accounts: several characters, real tables, and a way back in

**Status: proposed, not started.** A contract for review before any code is written. It spans three
repositories and a database migration, so the cost of getting it wrong is paid twice.

> ⚠️ **Amended by [19-CARD-SETS-AND-FORMATS.md](./19-CARD-SETS-AND-FORMATS.md), which is a
> prerequisite.** The collection stops being a property of the character, so
> `characters.collection` goes, the deck slots widen to eight, and the ownership tables gain real
> foreign keys to a `cards` table. **Do not write the V2 migration before that document is
> settled**, or it will be written twice. Document 19 § "What changes in document 18" lists the
> four differences.

Three strands, deliberately in one document because they touch the same two tables and must ship
behind the same version gate:

1. **One account, several characters** — an account currently *is* a character.
2. **A relational character** — the profile is one JSONB blob today.
3. **Password recovery** — which needs an email address, which the account does not have.

---

## 1. One account, several characters

### The problem

`tto-core`'s `Accounts.kt` says it in as many words — *"The account **is** the character"* —
`Credentials.username` is documented as "the account name, and also the character's name", the
client's sign-in form labels that field **Character Name**, and `V1__accounts_and_matches.sql` makes
it structural: `characters.account_id` is the primary key, so there is exactly one character per
account. A player wanting an FFXIV character *and* an FFVIII one needs two logins.

### Why not "one character, both collections"

Considered, and it is the more expensive of the two.

**The card ids collide completely.** Both tables are numbered from 1 — FFXIV runs 1..153, FFVIII
runs 1..110 — so all 110 FFVIII ids also name an FFXIV card. `CARDS: [1, 3, 6, 7, 10]` only means
something read through `MODE`, and the same holds for the five decks, whose slots carry those same
integers. One character owning both collections therefore changes the save's own shape, and drags in
every local `.sav`, every server row, and the thirty client sites that read `mode`.

It is also a game-design change: the collection selects the available opponents, the shop shelves
and the campaign, so a single character spanning both would put one level, one purse and one record
across two progressions the original keeps apart.

**Several characters leaves the save's fields alone.** The change is "an account owns N of these
instead of one". Everything below `ProfileGate` still receives one `GameSave`.

### Decisions taken

| Question | Answer |
|---|---|
| Login name | Stays the **account** name. |
| Existing accounts | Keep their one character, named after the account. Nothing is asked of the player, no login changes. |
| Character name | Unique per account, case-insensitively. Two accounts may both hold a "Kuplu". |
| Collection | Gone — see [19](./19-CARD-SETS-AND-FORMATS.md). A character is created by choosing a **starter pack** instead, and owns cards from any set thereafter. |

### Open

1. **A cap per account.** Proposal: **8**, refused with a modelled error, the same number
   [19](./19-CARD-SETS-AND-FORMATS.md) gives `GameSave.MAX_DECKS`.
2. **Deleting a character** takes its matches with it (`ON DELETE CASCADE`), as the local flow
   already does via `MatchReporter.forget`. The alternative — orphaned rows for a leaderboard — is a
   decision about what the history is for.
3. **An empty roster** becomes reachable once the last character can be deleted. Proposal: an empty
   list, and the client opens its creation screen.

---

## 2. A relational character

### What changes, and what it costs

V1 stores the profile as one JSONB document and argues for it: *"Not 23 columns... nothing reads a
profile by column, because the game reads a whole profile or none of it."* That argument is now
overridden by decision. It is worth writing down what the trade actually is, because it is not free
in either direction.

**Gained:** the profile becomes queryable — leaderboards, "how many players own this card", anomaly
detection across histories — without parsing every document. Constraints become the database's job:
a card id that does not exist, a deck with six cards, a negative stack are all refusable in SQL. And
a save write stops being a blind blob replacement.

**Paid:** the server must now *understand* every field. `PUT /me/save` currently accepts a whole
`GameSave` and stores it opaquely; relationally it becomes an upsert across seven tables in one
transaction, plus a mapper both ways. A field added to `GameSave` becomes a migration rather than
nothing at all. And unknown fields stop round-tripping — today an older server hands back whatever
a newer client sent; after this it hands back only what it has columns for.

That last point is the one to be sure about: **the schema becomes the definition of a save**, and
`GameSave` has to follow it rather than the other way round.

### The tables

```sql
CREATE TABLE characters (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    collection      TEXT        NOT NULL,
    avatar_id       TEXT        NOT NULL,
    -- `Save.DATAS` scalars, one column each.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_save       TIMESTAMPTZ NOT NULL DEFAULT now(),
    save_number     INTEGER     NOT NULL DEFAULT 0,
    admin           SMALLINT    NOT NULL DEFAULT 0,
    mgp             INTEGER     NOT NULL DEFAULT 100,
    xp              BIGINT      NOT NULL DEFAULT 0,
    level           INTEGER     NOT NULL DEFAULT 1,
    pvp_xp          BIGINT      NOT NULL DEFAULT 0,
    rank            INTEGER     NOT NULL DEFAULT 1,
    wins            INTEGER     NOT NULL DEFAULT 0,
    defeats         INTEGER     NOT NULL DEFAULT 0,
    draws           INTEGER     NOT NULL DEFAULT 0,
    boon_mgp        INTEGER     NOT NULL DEFAULT 0,
    boon_xp         INTEGER     NOT NULL DEFAULT 0,
    boon_luck       INTEGER     NOT NULL DEFAULT 0,
    started_matches INTEGER     NOT NULL DEFAULT 0,
    ended_matches   INTEGER     NOT NULL DEFAULT 0,
    pve_matches     INTEGER     NOT NULL DEFAULT 0,
    pvp_matches     INTEGER     NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT characters_collection CHECK (collection IN ('ff14_', 'ff8_')),
    CONSTRAINT characters_name_length CHECK (char_length(name) BETWEEN 3 AND 24),
    CONSTRAINT characters_counters CHECK (mgp >= 0 AND xp >= 0 AND level >= 1)
);

CREATE UNIQUE INDEX characters_name_per_account_idx ON characters (account_id, lower(name));
CREATE INDEX characters_account_idx ON characters (account_id);

-- Owned cards. A set, which is what `CARDS` is — the AS3 stores each id once.
CREATE TABLE character_cards (
    character_id BIGINT  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    card_id      INTEGER NOT NULL,
    PRIMARY KEY (character_id, card_id),
    CONSTRAINT character_cards_id CHECK (card_id > 0)
);

-- Five slots, sparse: `DECKS[n] = deck` on an AS3 array leaves holes, so the slot is stored and
-- not inferred from row order.
CREATE TABLE character_decks (
    id           BIGINT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    slot         SMALLINT NOT NULL,
    name         TEXT     NOT NULL,
    CONSTRAINT character_decks_slot CHECK (slot BETWEEN 0 AND 4)
);

CREATE UNIQUE INDEX character_decks_slot_idx ON character_decks (character_id, slot);

-- Position matters and `0` means "empty clip" — `saveDeck_Handler` pushes it for each slot left
-- blank, so it is a real value here and not a missing row.
CREATE TABLE character_deck_cards (
    deck_id  BIGINT   NOT NULL REFERENCES character_decks (id) ON DELETE CASCADE,
    position SMALLINT NOT NULL,
    card_id  INTEGER  NOT NULL,
    PRIMARY KEY (deck_id, position),
    CONSTRAINT character_deck_cards_position CHECK (position BETWEEN 0 AND 4),
    CONSTRAINT character_deck_cards_id CHECK (card_id >= 0)
);

-- The bag. `Item` is a sealed hierarchy of four, three of which carry one payload each, so the
-- discriminator is a column and the payloads are nullable columns guarded by a CHECK — one table
-- rather than four, because the bag is always read whole and in order.
CREATE TABLE character_items (
    id           BIGINT   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT   NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    kind         TEXT     NOT NULL,
    card_id      INTEGER,
    booster_type TEXT,
    potion_type  TEXT,
    stack        INTEGER  NOT NULL DEFAULT 1,
    CONSTRAINT character_items_kind CHECK (kind IN ('card', 'booster', 'potion', 'misc')),
    CONSTRAINT character_items_stack CHECK (stack > 0),
    CONSTRAINT character_items_payload CHECK (
        (kind = 'card'    AND card_id IS NOT NULL AND booster_type IS NULL AND potion_type IS NULL)
     OR (kind = 'booster' AND booster_type IS NOT NULL AND card_id IS NULL AND potion_type IS NULL)
     OR (kind = 'potion'  AND potion_type IS NOT NULL AND card_id IS NULL AND booster_type IS NULL)
     OR (kind = 'misc'    AND card_id IS NULL AND booster_type IS NULL AND potion_type IS NULL)
    )
);

CREATE INDEX character_items_character_idx ON character_items (character_id, id);

-- `ACHIEVEMENTS` is id -> the instant it was earned, which is what lets the record say when.
CREATE TABLE character_achievements (
    character_id   BIGINT      NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    achievement_id TEXT        NOT NULL,
    earned_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, achievement_id)
);

-- `NPC_W`, keyed by the opponent's iconID and not their id — ids are not unique across the two
-- tables, icon ids are. See `GameSave.npcWins`.
CREATE TABLE character_npc_wins (
    character_id BIGINT  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    npc_icon_id  TEXT    NOT NULL,
    wins         INTEGER NOT NULL,
    PRIMARY KEY (character_id, npc_icon_id),
    CONSTRAINT character_npc_wins_count CHECK (wins > 0)
);

-- `RULES_W`, keyed by the `GameRules` constant.
CREATE TABLE character_rule_wins (
    character_id BIGINT  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    rule_key     TEXT    NOT NULL,
    wins         INTEGER NOT NULL,
    PRIMARY KEY (character_id, rule_key),
    CONSTRAINT character_rule_wins_count CHECK (wins > 0)
);
```

`matches` gains `character_id`, and replay protection moves onto it — per account, two characters
playing the same seed against the same opponent would collide and the second honest match would be
refused:

```sql
ALTER TABLE matches ADD COLUMN character_id BIGINT REFERENCES characters (id) ON DELETE CASCADE;
-- backfilled from the one character each account has, then SET NOT NULL
DROP INDEX matches_transcript_idx;
CREATE UNIQUE INDEX matches_transcript_idx ON matches (character_id, transcript_hash);
DROP INDEX matches_account_played_idx;
CREATE INDEX matches_character_played_idx ON matches (character_id, played_at DESC);
```

`matches.account_id` is **kept**: redundant with `character_id`, but it is the column an "everything
this account played" query wants, and dropping a populated column is the one step here that cannot
be undone.

### The migration

V2 renames the existing table aside, creates the new ones, and explodes each document into them —
Postgres can do all of it in SQL, `jsonb_array_elements` for the lists and `jsonb_each` for the two
maps. The two fiddly parts, both worth a test of their own:

- **The bag**, because the discriminator in the document is kotlinx's `type` field
  (`item-type-card` and friends) and has to become the `kind` column with its payload split out.
- **Timestamps**, because `CREATION_DATE`, `LAST_SAVE` and the achievement instants are epoch
  millis in the document and `TIMESTAMPTZ` in the schema — `to_timestamp(x / 1000.0)`, and the
  mapper converts back. Postgres holds microseconds, so milliseconds round-trip exactly.

`MigrationTest` runs V1 then V2 against a real Postgres. The claim worth asserting is
round-tripping: **a `GameSave` written under V1, migrated, and read back through the new mapper is
equal to the original.**

---

## 3. Password recovery

The client's `AccountScreen` currently documents its absence: password recovery *"needs a channel
the server does not have"*. This gives it one.

### The account gains an email

```sql
ALTER TABLE accounts ADD COLUMN email            TEXT;
ALTER TABLE accounts ADD COLUMN email_key        TEXT GENERATED ALWAYS AS (lower(email)) STORED;
ALTER TABLE accounts ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Partial, because existing accounts have none and several NULLs are not a collision.
CREATE UNIQUE INDEX accounts_email_key_idx ON accounts (email_key) WHERE email_key IS NOT NULL;

-- A reset token, stored as a hash for the same reason a session token is: a dump of this table
-- must not be replayable against the server.
CREATE TABLE password_resets (
    token_hash TEXT        PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX password_resets_account_idx ON password_resets (account_id);
```

**Email is optional**, and that has a consequence to state plainly: an account created before this
ships cannot recover its password until its owner adds one. `PUT /me/email` exists for exactly that,
and the client should prompt once for an account that has none.

### Endpoints

| Method | Path | Body | Answer |
|---|---|---|---|
| `POST` | `/accounts/password-reset` | `{ "email": … }` | `202`, always |
| `POST` | `/accounts/password-reset/confirm` | `{ "token": …, "password": … }` | `204` |
| `PUT` | `/me/email` | `{ "email": … }` | `204` |

### Rules that are not optional

- **`202` whatever happens.** A different answer for a known and an unknown address turns this
  endpoint into a way to enumerate which emails hold accounts. Same reasoning as `signIn`, which
  already answers identically for a wrong username and a wrong password.
- **The token is 256 bits of `SecureRandom`, single-use, and short-lived** — one hour. Stored
  hashed; what the player receives is the pre-image.
- **Using it closes every session.** A password reset is the response to a suspected compromise, and
  leaving thirty-day tokens alive would make it a gesture.
- **Rate-limited per address and per IP**, or it is a way to have the server send mail on demand.
- **The mail names the account and does not contain the password**, which the server could not send
  if it wanted to: it holds a bcrypt digest.

### Open

- **How mail is sent.** SMTP host and credentials are configuration and a secret, in the shape
  `ServerConfig`/`Secrets` already uses. Which provider is a decision this document cannot make.
  Until one exists, the endpoint can be built and tested against a recording `Mailer` interface —
  which is where its tests want to run anyway.
- **Whether a new registration requires an email.** Proposal: optional, so nothing about signing up
  gets longer, with the client saying once that recovery needs one.
- **Verification.** `email_verified_at` is in the schema and unused at first. A reset to an
  unverified address is still safe — it proves control of the mailbox by delivery — but a *change*
  of address should be verified before it takes effect, or an account taken over briefly can be
  redirected permanently.

---

## tto-core — the protocol

### New

```kotlin
@Serializable
data class CharacterSummary(
    val id: Long,
    val name: String,
    val level: Int,
    val mgp: Int,
    val stats: Stats,
    /** How many cards it owns — the roster's stand-in for the collection it no longer has. */
    val cardsOwned: Int,
)

@Serializable
data class AccountState(
    val username: String,
    val email: String?,
    val characters: List<CharacterSummary>,
)

@Serializable
data class NewCharacter(val name: String, val starterId: String) {
    fun looksValid(): Boolean = name.trim().length in NAME_LENGTH && starterId.isNotBlank()
    companion object { val NAME_LENGTH = 3..24 }
}

@Serializable
data class Registration(val credentials: Credentials, val email: String? = null)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetConfirm(val token: String, val password: String)
```

### Changed

- `Session.player: PlayerState` → `Session.account: AccountState`. Signing in no longer loads a
  character, because the server no longer knows which one was meant.
- `PlayerState` gains `characterId: Long`; `MatchTranscript` gains it too — without it a transcript
  names an account, and an account now has several progressions to credit.
- `AccountError` gains `CHARACTER_NAME_TAKEN`, `TOO_MANY_CHARACTERS`, `NO_SUCH_CHARACTER`,
  `NO_SUCH_STARTER`, `EMAIL_TAKEN`, `RESET_TOKEN_INVALID`.
- `Credentials` keeps both its fields and loses "and also the character's name" from its KDoc.
  `POST /accounts` takes `Registration`; sign-in still takes `Credentials`.

### Character endpoints

| Method | Path | Body | Answer |
|---|---|---|---|
| `GET` | `/me` | — | `AccountState` |
| `POST` | `/me/characters` | `NewCharacter` | `201` + `PlayerState`, the starter already granted |
| `GET` | `/me/characters/{id}` | — | `PlayerState` |
| `DELETE` | `/me/characters/{id}` | — | `204` |
| `PUT` | `/me/characters/{id}/save` | `GameSave` | `204` |

`PUT /me/save` and the single-character `GET /me` are **replaced, not kept alongside**: two ways to
write a profile, one of them unable to say which, is how they drift. `CURRENT_VERSION` bumps and the
deployed server raises `minimumClient` to it, so an older client gets `426` before its body is read
— which is what the gate is for, and better than a client silently writing to the wrong character.

---

## tto-server

- **`AccountStore`** — `register` stops creating a character; an account begins empty.
  `createCharacter` resolves the starter against the server's own catalog and writes the granted
  cards and the first deck itself: the client sends a choice, never a card list. `playerState`,
  `saveFor`, `replaceSave` and `creditMatch` take a **character** id, each with an ownership check:
  a character id from a request is never trusted without confirming the session's account owns it,
  or one player writes another's profile. New: `roster`, `createCharacter`, `deleteCharacter`,
  `setEmail`, `openPasswordReset`, `consumePasswordReset`.
- **The save mapper** is new and is the bulk of the work: `GameSave` ↔ seven tables, both ways, in
  one transaction. It belongs in its own file with its own tests, not inside `AccountStore`.
- **`AccountRoutes` / `MatchRoutes`** — a shared `authorizeCharacter(store, id)` helper resolves the
  path parameter *and* the ownership check in one place, so no route can forget it. `/me`'s "account
  with no character" `500` becomes an ordinary empty roster.

## tto-client

The client already has these screens. `ProfileListScreen` lists characters with their level, MGP and
record; `ProfileCreateScreen` takes a name and one tile out of a row. The two flows stop diverging
and converge on the shape the local one already had — the row that named a collection names a card
count, and the tiles that offered two collections offer the released starters.

- **`AccountSession`** holds `AccountState` plus the selected `PlayerState`; `restore()` restores the
  session *and* the character last played on this server.
- **`SessionStore`** stores the selected character id per server, beside the token. Nothing else can
  answer "which character was I" after a relaunch.
- **`ProfileGate.queueKey`** becomes server + **character** id. Today it is
  `accountQueueKey(serverId, username)`; left alone, two characters share one offline queue and
  credit each other's matches. This is the single most important line in the change.
- **`Screen`** gains `CHARACTERS` and `CHARACTER_NEW`; sign-in lands on the roster. Creation takes a
  name and a **starter pack** — the shape `ProfileCreateScreen` already has, with the two collection
  tiles becoming the starters of the released blocks.
- **New**: a password-reset screen behind a "forgot it?" link on the sign-in form, and a prompt to
  add an email to an account that has none.
- **Deleted**: `CollectionChoiceScreen`, `COLLECTION_CONFIRM_TEST_TAG`, and the `onSignedIn(isNew)`
  flag on `AccountScreen`. They exist only because registration could not carry a collection; once a
  character is created explicitly they have nowhere to be.
- **Corrected**: `AccountScreen`'s KDoc, which says password recovery is missing.

---

## Order of work

Each step compiles and tests before the next; none is useful alone.

1. **tto-core** — types, client methods, version bump. `publishToMavenLocal`, which is what
   `settings.gradle.kts` orders `mavenLocal` first for.
2. **tto-server** — V2, the save mapper, the routes, the mailer behind an interface.
3. **tto-client** — roster screens, gate, queue key, recovery screen.
4. **Release together.** The gate refuses the old client on purpose, so the server must not be
   deployed before the client it requires is ready to ship.

## What to test, per repository

| Repository | The claim worth a test |
|---|---|
| tto-core | A transcript without a character id does not deserialise — required, not defaulted. |
| tto-server | A `GameSave` written under V1, migrated to V2 and read back through the mapper equals the original. |
| tto-server | A character id belonging to **another** account is refused, on every route that takes one. |
| tto-server | Two characters may submit the same transcript hash; one character may not submit it twice. |
| tto-server | A deck with six cards, a card id that names no card, and a stack of zero are all refused by the database. |
| tto-server | A reset for an unknown address answers `202` and sends nothing; a used token is refused; a completed reset closes every session. |
| tto-client | Two characters on one account keep separate offline queues. |
| tto-client | Signing in lands on the roster, and choosing a character lands on **that** character's dashboard. |
