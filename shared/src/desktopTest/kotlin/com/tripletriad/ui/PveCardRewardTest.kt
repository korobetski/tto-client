package com.tripletriad.ui

import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.Inventory
import com.tripletriad.data.MatchRewards
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.protocol.RewardSummary
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A won match against an opponent with a drop table pays a card, and the card survives the
 * wire.**
 *
 * The whole payout moved to the server in the refereed-PvE work, and it now reaches the panel by a
 * longer road than it used to: `MatchRewards.credit` rolls it, `RewardSummary` carries it across,
 * and [asMatchReward] turns it back into what `OutcomePanel` draws. Each of those is a place a card
 * can be dropped silently, and a dropped card looks exactly like a bad roll — which is why this
 * pins the roll instead of playing one.
 *
 * These are `:core`'s functions, tested here for the client's claim about them rather than for
 * their own sake: `:core` proves the arithmetic, and this proves the road to the panel is open.
 */
class PveCardRewardTest {
    private val npcs = runBlocking { loadNpcCatalog() }

    private val master = npcs
        .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    @Test
    fun aWinRollsTheCardsTheOpponentAdvertises() {
        val credited = MatchRewards.credit(
            save = GameSave.new(createdAt = 0L),
            npc = master,
            result = MatchResult.WIN,
            rules = GameRules(),
            at = FixedClock.DEFAULT_MILLIS,
            random = AlwaysDrops(),
        )

        val won = credited.reward.items.filterIsInstance<CardItem>().map { it.cardId }
        assertEquals(
            master.itemRewards.mapNotNull { it.cardId },
            won,
            "the drop table and the roll should name the same cards",
        )
        assertTrue(won.isNotEmpty(), "a win against tt-master should be able to pay a card")
    }

    /** The drop lands in the bag, which is where a card reward lives until it is used. */
    @Test
    fun aWonCardReachesTheBag() {
        val credited = MatchRewards.credit(
            save = GameSave.new(createdAt = 0L),
            npc = master,
            result = MatchResult.WIN,
            rules = GameRules(),
            at = FixedClock.DEFAULT_MILLIS,
            random = AlwaysDrops(),
        )

        for (item in credited.reward.items.filterIsInstance<CardItem>()) {
            assertTrue(
                Inventory.count(credited.save, item) > 0,
                "${item.cardId} was announced but never banked",
            )
        }
        assertTrue(
            credited.save.bag.any { it is CardItem },
            "the bag should hold the card that was won: ${credited.save.bag}",
        )
    }

    /** A defeat rolls nothing — the asymmetry `MatchRewards` documents, restated as a check. */
    @Test
    fun aDefeatPaysNoCards() {
        val credited = MatchRewards.credit(
            save = GameSave.new(createdAt = 0L),
            npc = master,
            result = MatchResult.LOSE,
            rules = GameRules(),
            at = FixedClock.DEFAULT_MILLIS,
            random = AlwaysDrops(),
        )

        assertTrue(credited.reward.items.isEmpty(), "a loss should drop nothing")
        assertTrue(credited.save.bag.none { it is CardItem }, "and bank nothing")
    }

    /**
     * The trip the refereed path added: the server's summary, back into the panel's own type.
     *
     * `items` is the field that carries a `CardItem` **with its card id** — the KDoc on
     * `RewardSummary` says so — so this is the assertion that the panel is handed a card it can
     * draw rather than a reward it can only count.
     */
    @Test
    fun theSummaryCarriesTheCardThroughToThePanel() {
        val credited = MatchRewards.credit(
            save = GameSave.new(createdAt = 0L),
            npc = master,
            result = MatchResult.WIN,
            rules = GameRules(),
            at = FixedClock.DEFAULT_MILLIS,
            random = AlwaysDrops(),
        )
        val summary = RewardSummary(
            result = credited.reward.result,
            mgp = credited.reward.mgp,
            xp = credited.reward.xp,
            items = credited.reward.items,
            achievementIds = credited.reward.achievements.map { it.id },
            questIds = credited.reward.quests.map { it.id },
        )

        val panel = summary.asMatchReward()

        assertEquals(
            credited.reward.items.filterIsInstance<CardItem>().map { it.cardId },
            panel.items.filterIsInstance<CardItem>().map { it.cardId },
            "the panel should be offered the same cards the match paid",
        )
        assertTrue(panel.items.any { it is CardItem }, "the card did not survive the summary")
    }

    /**
     * Every draw is the lowest one there is, so each entry beats its own rate.
     *
     * `rollRewards` keeps an entry when `nextDouble() < rate`, and every rate in `npcs.json` is
     * positive — so this is the roll where the whole drop table lands, and the one that tells a
     * missing card apart from an unlucky one.
     */
    private class AlwaysDrops : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
