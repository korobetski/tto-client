package com.tripletriad.ui

import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [npcCardRewards] is the table [OpponentRow] and [CampaignScreen]'s final reward line both draw
 * from, so a card an opponent is set up to give ought to show up here in the rate it was given at.
 */
class OpponentRewardsTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }

    private val master = npcs
        .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    @Test
    fun everyItemRewardWithACardIdIsCarriedAtItsOwnRate() {
        val rewards = npcCardRewards(master, cards.byId)

        val byId = rewards.associate { (card, rate) -> card.id to rate }
        assertEquals(setOf(FIRST_CARD_ID, SECOND_CARD_ID), byId.keys, "tt-master gives two cards")
        assertEquals(FIRST_CARD_RATE, byId.getValue(FIRST_CARD_ID))
        assertEquals(SECOND_CARD_RATE, byId.getValue(SECOND_CARD_ID))
    }

    @Test
    fun anItemRewardWithNoCardIdEarnsNoEntry() {
        val rewards = npcCardRewards(master, cards.byId)

        assertEquals(2, rewards.size, "the potion reward should not appear")
    }

    @Test
    fun aCardIdTheCatalogueDoesNotHoldIsDroppedRatherThanCrashing() {
        val thin = cards.byId.filterKeys { it != FIRST_CARD_ID }

        val rewards = npcCardRewards(master, thin)

        assertTrue(
            rewards.none { (card, _) -> card.id == FIRST_CARD_ID },
            "the missing card should be gone",
        )
        assertTrue(
            rewards.any { (card, _) -> card.id == SECOND_CARD_ID },
            "the rest should still be there",
        )
    }

    private companion object {
        const val FIRST_CARD_ID = 260
        const val SECOND_CARD_ID = 269
        const val FIRST_CARD_RATE = 0.25
        const val SECOND_CARD_RATE = 0.2
    }
}
