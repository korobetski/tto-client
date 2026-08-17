package com.tripletriad.data

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
            val dearest = { pool: List<Int> -> pool.maxOf { CardValue.resaleOf(it, cards) } }
            val best = dearest(type.pool) * (type.size - 1) +
                dearest(type.pool.drop(type.rareFrom))

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
            assertTrue(type.pool.size >= type.size, "${type.name} cannot fill its own ${type.size}")
        }
    }

    @Test
    fun everyPackBelongsToOneSet() {
        for (type in BoosterType.entries) {
            val blocks = type.pool.mapNotNull { cards[it]?.block }.distinct()

            assertEquals(1, blocks.size, "${type.name} draws from blocks $blocks")
        }
    }

    @Test
    fun everyPackKeepsThePromiseItsRowMakes() {
        for (type in BoosterType.entries) {
            val floor = BoosterPricing.guaranteedFloor(type, cards)
            val pack = BoosterItem(type)

            assertTrue(floor in 1..TOP_RARITY, "${type.name} advertises a $floor★ floor")
            repeat(SAMPLES) { seed ->
                val prize = pack.open(Random(seed)).last()
                assertTrue(
                    (cards[prize]?.rarity ?: 0) >= floor,
                    "${type.name} promised $floor★ and dealt ${cards[prize]?.rarity}★",
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

        assertEquals(setOf(1, 2), bySet.keys, "both shipped sets should sell packs")
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
