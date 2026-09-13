package com.tripletriad.ui

import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [sourceKindsByCard] held to [cardSources], card by card, over the shipped tables.
 *
 * Here rather than in `commonTest` because the roster worth checking is `npcs.json`, which loads
 * through Compose resources. A synthetic roster would be written to agree with the index.
 */
class CardSourceIndexTest {
    private val catalog = runBlocking { loadCardCatalog() }
    private val opponents = runBlocking { loadNpcCatalog() }

    @Test
    fun theIndexNamesExactlyTheKindsTheListDoesForEveryCard() {
        val index = sourceKindsByCard(opponents)

        // The index's own keys as well as the catalogue's: an entry for a card the list knows
        // nothing about is a disagreement too.
        val ids = (catalog.all.map { it.id } + index.keys).distinct()
        val disagreeing = ids.filter { id ->
            index[id].orEmpty() != cardSources(id, opponents).map { it.kind }.toSet()
        }

        assertEquals(emptyList(), disagreeing, "the index and the list disagree on these ids")
        // Against a roster that loaded no drops the two would agree without ever being tested.
        assertTrue(
            index.values.any { SourceKind.OPPONENT in it },
            "the shipped roster answered no card",
        )
    }

    @Test
    fun withoutARosterNoCardIsSaidToComeFromAnOpponent() {
        val index = sourceKindsByCard(opponents = null)

        assertTrue(index.values.none { SourceKind.OPPONENT in it })
        assertTrue(index.values.any { SourceKind.SHOP in it }, "the shelf does not need a roster")
    }
}
