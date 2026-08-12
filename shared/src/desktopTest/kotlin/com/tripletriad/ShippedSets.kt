package com.tripletriad

/**
 * The two sets this game ships, named once for every test that needs to say which one it means.
 *
 * ### Why this file exists
 *
 * `CardCollection` used to be this. It was an enum in `:core` with two entries, a block, a slug and
 * a storage key, and `CardCollection.entries` was how a test said "both tables" — which is why it
 * outlived its job by a whole release: it was the only name for a fact the tests kept needing.
 * Document 19 deleted it, because a card id carries its block and a match is decided by a *format*.
 *
 * What was left behind is genuinely small: four constants. They live in the test source set on
 * purpose. Nothing in the app needs them — the app reads `cards.json`, `formats.json` and
 * `starters.json` and never hard-codes a set — so putting them back in `:core` would be recreating
 * the enum under a new name. A test, unlike the app, is entitled to name the shipped content: that
 * is what makes it an assertion rather than a tautology.
 *
 * ### On the split between blocks and formats
 *
 * They are not two spellings of one thing. A **block** is where a card lives, and it is what
 * `Card.id` encodes. A **format** is what a match is played under, and it names a list of blocks.
 * The single-set formats below happen to be one block each; `free-play` is both, and it is the one
 * the app actually plays. A test naming `FF14_FORMAT` is deliberately narrowing to half the game.
 */

/** Block 1 — the FFXIV table, 153 cards. */
internal const val FF14_BLOCK: Int = 1

/** Block 2 — the FFVIII table, 110 cards. */
internal const val FF8_BLOCK: Int = 2

/** The single-set format admitting block 1 and nothing else. Authored in `formats.json`. */
internal const val FF14_FORMAT: String = "ff14-standard"

/** The single-set format admitting block 2 and nothing else. */
internal const val FF8_FORMAT: String = "ff8-standard"

/** The two single-set formats, which is what `CardCollection.entries` used to be spelled as. */
internal val SINGLE_SET_FORMATS: List<String> = listOf(FF14_FORMAT, FF8_FORMAT)
