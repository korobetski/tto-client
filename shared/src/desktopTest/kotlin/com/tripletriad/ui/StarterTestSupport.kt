package com.tripletriad.ui

import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.Starter
import com.tripletriad.data.StarterPack
import com.tripletriad.data.loadStarterCatalog
import com.tripletriad.model.GameSave
import kotlinx.coroutines.runBlocking

internal fun starterFor(block: Int): Starter =
    runBlocking { loadStarterCatalog() }.forBlock(block)
        ?: error("no starter is authored for block $block")

internal val STARTER_DECK: List<Int> = starterFor(FF14_BLOCK).deck

internal fun freshSave(
    createdAt: Long = 0L,
    block: Int = FF14_BLOCK,
): GameSave = StarterPack.opened(
    GameSave.new(createdAt = createdAt),
    starterFor(block),
)
