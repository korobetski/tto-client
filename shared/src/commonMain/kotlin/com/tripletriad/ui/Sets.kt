package com.tripletriad.ui

import com.tripletriad.data.CardSet
import com.tripletriad.i18n.Strings

/**
 * What a card block is called, read off the format that plays it alone.
 *
 * `formats.json` already names both tables — "FFXIV" and "FFVIII" — and two screens were each
 * calling them "Set 1" and "Set 2", the block number the ids carry rather than anything a player
 * recognises. Looked up by key rather than through the format catalog, which neither screen is
 * given; [BLOCK_PREFIX] is what an unnamed block still falls back to.
 *
 * Shared between the card list's set filter and the opponent list's collection filter — the same
 * fact told once rather than the two near-identical private copies it used to be.
 *
 * Keyed by block because FFVIII's did double duty as a set id back when every set fit in one — a
 * mapping [representativeBlocks] now folds down to before it ever reaches here, so a caller with a
 * set spanning several blocks still only asks this for one of them.
 */
internal fun setLabel(strings: Strings, block: Int): String {
    val key = SET_NAME_KEYS[block]
    return if (key != null && strings.has(key)) strings[key] else "$BLOCK_PREFIX$block"
}

/**
 * Every block in [sets], mapped to one block that speaks for its whole set — the set's own first
 * one.
 *
 * FFXIV outgrew 255 cards and now spans blocks 1 and 2 ([CardSet]), but a filter row should still
 * offer one "FFXIV" chip, not one per block. A caller folds a card's block through this map before
 * grouping or comparing, so both of FFXIV's blocks collapse onto block 1 and the row stays one
 * chip per *set*.
 */
internal fun representativeBlocks(sets: List<CardSet>): Map<Int, Int> =
    sets.flatMap { set -> set.blocks.map { block -> block to set.blocks.first() } }.toMap()

private val SET_NAME_KEYS = mapOf(
    1 to "APP_FORMAT_FF14_STANDARD",
    8 to "APP_FORMAT_FF8_STANDARD",
)

private const val BLOCK_PREFIX = "Set "
