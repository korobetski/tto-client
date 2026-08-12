package com.tripletriad.ui

import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.Starter
import com.tripletriad.data.StarterPack
import com.tripletriad.data.loadStarterCatalog
import com.tripletriad.model.GameSave
import kotlinx.coroutines.runBlocking

/**
 * Reading the shipped starter packs from a UI test.
 *
 * Its own file rather than a member of [ComposeTestSupport], which is at the twenty functions
 * detekt allows in one — the same split, and for the same reason, as `MatchTestSupport`.
 */

/**
 * The **shipped** starter that opens [block]'s set.
 *
 * Read through the real loader rather than hand-built, because a test asserting that the app
 * granted the starter has to compare against the same file the app read. `StarterPackTest` uses a
 * fixture instead: it is testing the rule, not the content, and pinning it to `starters.json` would
 * make every one of its assertions fail the day a card is swapped for flavour.
 */
internal fun starterFor(block: Int): Starter =
    runBlocking { loadStarterCatalog() }.forBlock(block)
        ?: error("no starter is authored for block $block")

/**
 * The five cards of the ff14 starter's **opening deck**.
 *
 * Distinct from `STARTER_CARDS`, which is the ten a character owns. Confusing the two is what a
 * `Deck` built from the wrong one looks like: ten cards in a five-card slot, refused silently by
 * `Deck.plusCard`.
 */
internal val STARTER_DECK: List<Int> = starterFor(FF14_BLOCK).deck

/**
 * A character as the app really creates one — `GameSave.new` with the authored starter opened onto
 * it, which is exactly what `SaveRepository.create` does.
 *
 * Seeding a bare `GameSave.new` instead leaves a profile holding the AS3's five, which no creation
 * path produces any more: `GameSave.new` lives in `:core` and still seeds them, and document 19's
 * starter is applied over the top. A test that seeds the bare one is testing a state the app cannot
 * be in.
 */
internal fun freshSave(
    createdAt: Long = 0L,
    block: Int = FF14_BLOCK,
): GameSave = StarterPack.opened(
    GameSave.new(createdAt = createdAt),
    starterFor(block),
)
