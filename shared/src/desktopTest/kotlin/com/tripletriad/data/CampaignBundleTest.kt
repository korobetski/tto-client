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
    fun allThreeLaddersShipInFull() {
        assertEquals(listOf("cc", "gs", "balamb"), catalog.all.map { it.key })
        assertEquals(CARD_CLUB_RUNGS, assertNotNull(catalog.byKey("cc")).steps.size)
        assertEquals(GOLD_SAUCER_RUNGS, assertNotNull(catalog.byKey("gs")).steps.size)
        assertEquals(BALAMB_RUNGS, assertNotNull(catalog.byKey("balamb")).steps.size)
    }

    @Test
    fun everySingleSetFormatHasAtLeastOneLadder() {
        val formats = runBlocking { loadFormatCatalog() }

        for (id in SINGLE_SET_FORMATS) {
            requireNotNull(formats[id]) { "$id is not authored" }
            assertTrue(catalog.playing(id).isNotEmpty(), "$id should have a ladder")
        }
        // ff8-standard carries two now: the Card Club and Balamb Garden Novices beside
        // it, both authored for the same single-set format.
        assertEquals(2, catalog.playing("ff8-standard").size)
    }

    @Test
    fun enteringCostsWhatEachLadderAsksFor() {
        val fees = mapOf("cc" to ENTRY_FEE, "gs" to ENTRY_FEE, "balamb" to BALAMB_FEE)
        for (campaign in catalog.all) {
            assertEquals(fees.getValue(campaign.key), campaign.fee, campaign.key)
        }
    }

    /**
     * Every rung names an opponent the referee can actually resolve.
     *
     * A ladder cannot invent an opponent. `PveMatchRequest` carries only the icon id, the format
     * and the deck, so the referee looks the opponent up in **npcs.json** — `npcs.byIcon(iconId,
     * formatId)`, the same call `PveStubServer.opened` makes — and refuses with `NO_SUCH_OPPONENT`
     * when there is none. A rung naming an icon the catalogue does not carry is therefore not a
     * cosmetic defect: it is a ladder that dies the moment the rung before it is won, which is what
     * an invented `quistis-fan` rung did to Balamb before this test existed.
     */
    @Test
    fun everyRungNamesAnOpponentTheRefereeCanResolve() {
        val npcs = runBlocking { loadNpcCatalog() }
        val unresolvable = catalog.all.flatMap { campaign ->
            campaign.steps
                .filter { npcs.byIcon(it.npc.iconId, campaign.format) == null }
                .map { "${campaign.key}/${it.npc.iconId} under ${campaign.format}" }
        }
        assertEquals(emptyList(), unresolvable, "the referee would refuse these rungs")
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

    /**
     * Which rungs declare a per-match fee on top of their ladder's entry fee.
     *
     * One of the ladders' thirteen does — `cc/spade`. All four Balamb rungs do as well, and for a
     * different reason: they are *copies* of catalogue opponents (see `authored_ladder()`), so they
     * carry the catalogue's own fees rather than the zeroes the other ladders were written with.
     *
     * **What actually gets collected is not decided here.** The referee resolves the opponent from
     * npcs.json and charges that record's fee, whatever this file says — so those rungs' zeroes
     * are a display-only claim, and Balamb's fees are honest precisely because they are copied.
     */
    @Test
    fun theRungsDeclaringAPerMatchFeeAreTheKnownOnes() {
        val charging = catalog.all.flatMap { campaign ->
            campaign.steps.filter { it.npc.matchFee > 0 }.map { "${campaign.key}/${it.npc.iconId}" }
        }
        assertEquals(
            listOf("cc/spade", "balamb/kid", "balamb/trepies", "balamb/ma-dincht", "balamb/jack"),
            charging,
        )
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
        const val BALAMB_RUNGS = 4
        const val ENTRY_FEE = 500
        const val BALAMB_FEE = 200
        const val SPEAKING_RUNGS = 3
    }
}
