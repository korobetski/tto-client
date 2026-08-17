package com.tripletriad.data

import com.tripletriad.SINGLE_SET_FORMATS
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchResult
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignBundleTest {
    private val catalog = runBlocking { loadCampaignCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    @Test
    fun bothLaddersShipInFull() {
        assertEquals(listOf("cc", "gs"), catalog.all.map { it.key })
        assertEquals(CARD_CLUB_RUNGS, assertNotNull(catalog.byKey("cc")).steps.size)
        assertEquals(GOLD_SAUCER_RUNGS, assertNotNull(catalog.byKey("gs")).steps.size)
    }

    @Test
    fun eachSingleSetFormatHasExactlyOneLadder() {
        val formats = runBlocking { loadFormatCatalog() }

        for (id in SINGLE_SET_FORMATS) {
            requireNotNull(formats[id]) { "$id is not authored" }
            assertEquals(1, catalog.playing(id).size, "$id should have one ladder")
        }
    }

    @Test
    fun enteringCostsFiveHundred() {
        for (campaign in catalog.all) {
            assertEquals(ENTRY_FEE, campaign.fee, campaign.key)
        }
    }

    @Test
    fun everyRungCanFieldAHand() {
        val formats = runBlocking { loadFormatCatalog() }
        for (campaign in catalog.all) {
            val ids = cards.block(blockOf(campaign.format, formats)).mapTo(mutableSetOf()) { it.id }
            for ((step, entry) in campaign.steps.withIndex()) {
                val hand = entry.npc.randomHand(Random(step))
                assertEquals(HAND_SIZE, hand.size, "${campaign.key}/${entry.npc.iconId}")
                assertTrue(
                    ids.containsAll(hand),
                    "${campaign.key}/${entry.npc.iconId} draws cards outside its collection",
                )
            }
        }
    }

    @Test
    fun onlyOneRungDeclaresAFeeAndNothingCollectsIt() {
        val charging = catalog.all.flatMap { campaign ->
            campaign.steps.filter { it.npc.matchFee > 0 }.map { "${campaign.key}/${it.npc.iconId}" }
        }
        assertEquals(listOf("cc/spade"), charging)
    }

    @Test
    fun onlyTheGoldSaucerSpeaks() {
        val speaking = catalog.all.flatMap { campaign ->
            campaign.steps.filterNot { it.messages.isSilent }.map { campaign.key }
        }
        assertEquals(List(SPEAKING_RUNGS) { "gs" }, speaking)
        assertTrue(
            catalog.all.flatMap { it.steps }.all { it.messages.draw == null },
            "no shipped rung has a line for a draw",
        )
    }

    @Test
    fun winningWalksTheLadderAndLosingSendsYouBack() {
        val campaign = assertNotNull(catalog.byKey("gs"))
        val last = campaign.steps.size - 1

        assertEquals(1, campaign.nextStep(0, MatchResult.WIN))
        assertEquals(0, campaign.nextStep(last, MatchResult.LOSE))
        assertEquals(last, campaign.nextStep(last, MatchResult.DRAW))
        assertNull(
            campaign.stepAt(campaign.nextStep(last, MatchResult.WIN)),
            "winning the last rung should end the ladder",
        )
    }

    private fun blockOf(formatId: String, formats: FormatCatalog): Int =
        requireNotNull(formats[formatId]) { "no such format: $formatId" }.blocks.first()

    private companion object {
        const val CARD_CLUB_RUNGS = 7
        const val GOLD_SAUCER_RUNGS = 6
        const val ENTRY_FEE = 500
        const val SPEAKING_RUNGS = 3
    }
}
