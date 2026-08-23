package com.tripletriad

internal const val FF14_BLOCK: Int = 1

/**
 * FFVIII sits at block 8, not 2, and the gap is deliberate.
 *
 * FFXIV has 454 cards against a block's 255, so it needs a second block and the natural one is the
 * block after its first. FFVIII moved out of the way rather than FFXIV skipping over it, which
 * leaves blocks 1..7 — 1785 cards — for FFXIV to keep growing into. See `CardSet` in `:core`.
 */
internal const val FF8_BLOCK: Int = 8

internal const val FF14_FORMAT: String = "ff14-standard"

internal const val FF8_FORMAT: String = "ff8-standard"

internal val SINGLE_SET_FORMATS: List<String> = listOf(FF14_FORMAT, FF8_FORMAT)
