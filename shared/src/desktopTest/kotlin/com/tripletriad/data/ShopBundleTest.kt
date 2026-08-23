package com.tripletriad.data

import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopBundleTest {
    private val catalog = runBlocking { loadCardCatalog() }
    private val cards = catalog.all.associateBy { it.id }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private val shelf = ShopCatalog.shelf(cards)

    // ---- The loop, over what actually ships ---------------------------------

    @Test
    fun noSingleCardIsWorthMoreResoldThanItCosts() {
        val singles = shelf.mapNotNull { offer ->
            (offer.item as? CardItem)?.let { it.cardId to offer.price }
        }
        assertTrue(singles.isNotEmpty(), "the shelf should sell single cards")

        for ((cardId, price) in singles) {
            val resale = CardValue.resaleOf(cardId, cards)
            assertTrue(
                resale < price,
                "card $cardId costs $price and resells at $resale",
            )
        }
    }

    @Test
    fun everyPackReturnsExactlyTheShopsRakeOnResale() {
        val expected = CardValue.RESALE_RATE / BoosterPricing.MARKUP

        for (type in BoosterType.entries) {
            val price = BoosterPricing.priceOf(type, cards)
            val pack = BoosterItem(type)

            val returned = (0 until SAMPLES).map { seed ->
                pack.open(Random(seed)).sumOf { CardValue.resaleOf(it, cards) }
            }
            val ratio = returned.average() / price

            assertTrue(
                returned.average() < price,
                "${type.name} costs $price and returns ${returned.average()} on resale",
            )
            assertTrue(
                kotlin.math.abs(ratio - expected) < RAKE_TOLERANCE,
                "${type.name} returns $ratio of its price, not the shop's rake of $expected",
            )
        }
    }

    @Test
    fun noPackIsAJackpot() {
        for (type in BoosterType.entries) {
            val price = BoosterPricing.priceOf(type, cards)
            // The luckiest possible open: every one of the pack's draws lands its dearest card.
            // `cardCount` is 1 for everything on the shelf today, so this is that one card — but
            // the multiplication is what keeps the bound honest if a pack ever draws more.
            val dearest = type.pool.maxOf { CardValue.resaleOf(it, cards) }
            val best = dearest * type.cardCount

            assertTrue(
                best < price * JACKPOT_CEILING,
                "${type.name} costs $price and could return $best",
            )
        }
    }

    // ---- The packs themselves ----------------------------------------------

    @Test
    fun everyPoolResolvesToRealCards() {
        for (type in BoosterType.entries) {
            val missing = type.pool.filterNot { it in cards }

            assertTrue(missing.isEmpty(), "${type.name} names $missing, which no set holds")
            assertTrue(type.pool.isNotEmpty(), "${type.name} has nothing to draw from")
        }
    }

    @Test
    fun everyPackBelongsToOneSet() {
        for (type in BoosterType.entries) {
            val blocks = type.pool.mapNotNull { cards[it]?.block }.distinct()

            assertEquals(1, blocks.size, "${type.name} draws from blocks $blocks")
        }
    }

    /**
     * A pack deals exactly what its row says it deals, out of its own pool.
     *
     * This used to assert a **guaranteed rarity floor** — the old pack's last draw was restricted
     * to the top of the pool, and the shop row advertised it. Both are gone: a pack is one weighted
     * draw over the whole pool now, so there is no floor to keep and `APP_PACK_GUARANTEE` was
     * removed rather than left saying something untrue. What is left to check is the count and the
     * pool; the odds are the next test's business.
     */
    @Test
    fun everyPackDealsWhatItsRowSaysItDeals() {
        for (type in BoosterType.entries) {
            val pack = BoosterItem(type)

            repeat(SAMPLES) { seed ->
                val drawn = pack.open(Random(seed))
                assertEquals(type.cardCount, drawn.size, "${type.name} dealt ${drawn.size} cards")
                assertTrue(
                    drawn.all { it in type.pool },
                    "${type.name} dealt $drawn, which is outside its pool",
                )
            }
        }
    }

    @Test
    fun theAdvertisedOddsAreTheRealOdds() {
        for (type in BoosterType.entries) {
            val claimed = BoosterPricing.fiveStarChance(type, cards)
            val pack = BoosterItem(type)

            val observed = (0 until SAMPLES).count { seed ->
                pack.open(Random(seed)).any { cards[it]?.rarity == TOP_RARITY }
            }.toDouble() / SAMPLES

            assertTrue(
                kotlin.math.abs(observed - claimed) < ODDS_TOLERANCE,
                "${type.name} advertises $claimed and delivers $observed",
            )
        }
    }

    // ---- What the shelf says ------------------------------------------------

    @Test
    fun everyOfferNamesAndDescribesItself() {
        for (offer in shelf) {
            assertTrue(
                strings.has(offer.item.descriptionKey),
                "no description for ${offer.item.descriptionKey}",
            )
        }
        for (type in BoosterType.entries) {
            assertTrue(strings.has(type.nameKey), "no name for ${type.nameKey}")
        }
    }

    @Test
    fun theCheapestPackIsWithinANewCharactersReach() {
        assertTrue(shelf.all { it.price > 0 }, "nothing on the shelf is free")

        val cheapest = BoosterType.entries.minOf { BoosterPricing.priceOf(it, cards) }
        assertTrue(
            cheapest <= AFFORDABLE_AFTER_A_FEW_MATCHES,
            "the cheapest pack costs $cheapest, more than a new character can plausibly earn",
        )
    }

    @Test
    fun bothSetsHaveAPackLadder() {
        val bySet = BoosterType.entries.groupBy { type -> cards.getValue(type.pool.first()).block }

        assertEquals(
            setOf(FF14_BLOCK, FF8_BLOCK),
            bySet.keys,
            "both shipped sets should sell packs",
        )
        for ((block, packs) in bySet) {
            val prices = packs.map { BoosterPricing.priceOf(it, cards) }

            assertTrue(packs.size >= LADDER_RUNGS, "set $block has only ${packs.size} pack(s)")
            assertTrue(
                prices.max() >= prices.min() * LADDER_SPREAD,
                "set $block's packs run $prices, which is not a ladder",
            )
        }
    }

    private companion object {
        const val SAMPLES = 2_000
        const val TOP_RARITY = 5

        const val ODDS_TOLERANCE = 0.03

        const val RAKE_TOLERANCE = 0.02

        const val JACKPOT_CEILING = 2

        const val AFFORDABLE_AFTER_A_FEW_MATCHES = 1_000

        const val LADDER_RUNGS = 3
        const val LADDER_SPREAD = 5
    }
}
