package com.tripletriad.ui

import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.Starter
import com.tripletriad.data.StarterPack
import com.tripletriad.data.loadStarterCatalog
import com.tripletriad.model.GameSave
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

internal fun starterFor(block: Int): Starter =
    runBlocking { loadStarterCatalog() }.forBlock(block)
        ?: error("no starter is authored for block $block")

internal val STARTER_DECK: List<Int> = starterFor(FF14_BLOCK).deck

/**
 * The seed every fixture box is dealt from.
 *
 * Four of a starter's nine cards are drawn (`StarterPack.drawn`), so a fixture without a seed would
 * be a different collection on every run and the tests reading [STARTER_CARDS] would be asserting
 * against whatever fell out. One fixed seed for the whole suite: the box is still the real box,
 * dealt by the real code, and it is the same box twice.
 */
internal const val STARTER_SEED: Int = 20260904

/**
 * A character holding a starter box, as a creation path would leave it.
 *
 * `GameSave.new` deals **nothing** — a profile that has not opened a box owns no cards and has no
 * decks — so a fixture that wants a playable character has to open one, which is also what both
 * creation paths do.
 */
internal fun freshSave(
    createdAt: Long = 0L,
    block: Int = FF14_BLOCK,
    seed: Int = STARTER_SEED,
): GameSave = StarterPack.opened(
    GameSave.new(createdAt = createdAt),
    starterFor(block),
    pvpCards.byId,
    Random(seed),
)
