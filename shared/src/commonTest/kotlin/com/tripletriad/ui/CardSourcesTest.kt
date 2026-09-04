package com.tripletriad.ui

import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.ShopCatalog
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.ItemReward
import com.tripletriad.model.Npc
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The index over the four tables that already shipped: who drops a card, which pack holds it, what
 * it costs, and which haut fait pays it.
 *
 * The shop, the hauts faits and the boosters are real — they are plain Kotlin in `:core` and asking
 * a fixture to stand in for them would be testing the fixture. The roster is synthetic, because the
 * one thing worth pinning about it is the *shape* of a drop table and `npcs.json` is authored data
 * that will keep moving.
 */
class CardSourcesTest {
    @Test
    fun aCardNothingOffersHasNoSources() {
        assertTrue(cardSources(UNOBTAINABLE, NpcCatalog(emptyList())).isEmpty())
    }

    @Test
    fun aNullRosterCostsTheDropsAndNothingElse() {
        // The roster loads in its own startup phase, so a panel opened a frame early must still
        // answer with the shop and the packs rather than with nothing at all.
        val sources = cardSources(ON_THE_SHELF, opponents = null)

        assertTrue(sources.isNotEmpty(), "a shelf price does not depend on the roster")
        assertTrue(sources.none { it is CardSource.Opponent })
    }

    @Test
    fun whatIsCertainComesBeforeWhatIsAChance() {
        // A price is a decision; a drop is a hope. A list sorted purely by probability would put
        // six opponents in front of the shelf the card is simply sitting on.
        val roster = NpcCatalog(listOf(npc("dropper", ON_THE_SHELF, rate = 0.9)))

        val sources = cardSources(ON_THE_SHELF, roster)

        assertIs<CardSource.Shelf>(sources.first(), "the shop should lead")
        assertTrue(
            sources.any { it is CardSource.Opponent },
            "the fixture's dropper should still be listed",
        )
    }

    @Test
    fun aHautFaitIsCertainTooAndSaysSoByItsPlace() {
        val sources = cardSources(REWARDED, NpcCatalog(listOf(npc("x", REWARDED, rate = 1.0))))

        assertIs<CardSource.Reward>(sources.first())
    }

    @Test
    fun droppersAreOrderedByHowLikelyTheyAre() {
        val roster = NpcCatalog(
            listOf(
                npc("thin", DROPPED, rate = 0.05),
                npc("fat", DROPPED, rate = 0.4),
                npc("middling", DROPPED, rate = 0.2),
            ),
        )

        val order = cardSources(DROPPED, roster)
            .filterIsInstance<CardSource.Opponent>()
            .map { it.npc.iconId }

        assertEquals(listOf("fat", "middling", "thin"), order)
    }

    @Test
    fun anOpponentWithTwoEntriesForOneCardIsListedOnceAtTheirBestRate() {
        // Authored data does this: a card can appear twice in one table, and an opponent named
        // twice in a six-line panel is a panel that has spent two of its six lines saying one
        // thing.
        val roster = NpcCatalog(
            listOf(
                Npc(
                    id = 1,
                    nameKey = "STR_TEST",
                    iconId = "twice",
                    itemRewards = listOf(
                        ItemReward(type = "card", rate = 0.1, cardId = DROPPED),
                        ItemReward(type = "card", rate = 0.35, cardId = DROPPED),
                    ),
                ),
            ),
        )

        val dropped = cardSources(DROPPED, roster).filterIsInstance<CardSource.Opponent>()

        assertEquals(1, dropped.size)
        assertEquals(0.35, dropped.single().rate)
    }

    @Test
    fun aPotionDropIsNotACardDrop() {
        val roster = NpcCatalog(
            listOf(
                Npc(
                    id = 1,
                    nameKey = "STR_TEST",
                    iconId = "potions",
                    itemRewards = listOf(
                        ItemReward(type = "potion", rate = 0.5, potion = PotionType.MGP),
                    ),
                ),
            ),
        )

        assertTrue(cardSources(DROPPED, roster).isEmpty())
    }

    @Test
    fun aBoosterPoolIsFoundAndCarriesThatCardsOwnOdds() {
        val card = BoosterType.BRONZE.pool.first()

        val pack = cardSources(card, NpcCatalog(emptyList()))
            .filterIsInstance<CardSource.Booster>()
            .firstOrNull { it.type == BoosterType.BRONZE }

        assertTrue(pack != null, "the bronze pack's own first card was not found in it")
        // The weight belonging to *that* card, not the pool's average: `oddsOf` is index-aligned
        // with `pool`, and reading the wrong index is the one way this can be silently wrong.
        val expected = BoosterType.BRONZE.let { type ->
            type.weights.first() / type.weights.sum()
        }
        assertEquals(expected, pack.odds, 1e-9)
    }

    @Test
    fun everySourceIsNamedOnceInTheTree() {
        // The rows are addressed by slug, so two sources sharing one would be two nodes a test
        // could not tell apart — and, in a `LazyColumn`, one that never draws.
        val roster = NpcCatalog(
            listOf(npc("a", ON_THE_SHELF, 0.3), npc("b", ON_THE_SHELF, 0.2)),
        )

        val slugs = cardSources(ON_THE_SHELF, roster).map { it.slug }

        assertEquals(slugs.distinct().size, slugs.size, "duplicate slugs: $slugs")
    }

    private fun npc(iconId: String, cardId: Int, rate: Double) = Npc(
        id = 1,
        nameKey = "STR_TEST_$iconId",
        iconId = iconId,
        itemRewards = listOf(ItemReward(type = "card", rate = rate, cardId = cardId)),
    )

    private companion object {
        /** A card no shelf, pack or haut fait offers — checked below rather than assumed. */
        val UNOBTAINABLE: Int = (1..10_000)
            .first { candidate ->
                ShopCatalog.shelf.none { (it.item as? CardItem)?.cardId == candidate } &&
                    BoosterType.entries.none { candidate in it.pool } &&
                    AchievementCatalog.all.none {
                        (it.reward as? CardItem)?.cardId == candidate
                    }
            }

        /** A card the shop really sells, read off the catalogue rather than written down. */
        val ON_THE_SHELF: Int = ShopCatalog.shelf.firstNotNullOf {
            (it.item as? CardItem)?.cardId
        }

        /** A card a haut fait really pays. */
        val REWARDED: Int = AchievementCatalog.all.firstNotNullOf {
            (it.reward as? CardItem)?.cardId
        }

        /** Something to hang a synthetic drop table on, with no other source to interleave. */
        val DROPPED: Int = UNOBTAINABLE
    }
}
