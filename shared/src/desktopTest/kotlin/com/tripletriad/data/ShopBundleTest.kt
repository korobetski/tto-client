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

/**
 * The **shipped** shelf: what it sells, at what price, and whether any of it can be gamed.
 *
 * ### The half `:core` cannot state
 *
 * `CardValueTest` asserts the *structure* — resale is a smaller fraction of worth than the shop's
 * markup, so buying and reselling loses money at every rarity. That is a claim about two constants
 * and it holds by construction.
 *
 * It says nothing about the **authored** prices. `ShopCatalog` prices thirteen single cards by
 * hand, and a hand-typed price does not know what it is selling: before this, the shop sold a one-
 * star Tonberry for 120 MGP that `cardId * 4` bought back for 1 032, and an FFVIII Tonberry for 350
 * that came back at 2 176. Three FFXIV rows and all three FFVIII rows were profitable to buy and
 * immediately resell, which is an unbounded MGP loop and therefore an economy with no prices in it.
 *
 * Checking that needs the shipped rarities. `:core` ships no card table, so this is where it lives
 * — and it is the assertion that would have caught the original defect the day it was introduced.
 *
 * ### And the parts a formula cannot promise
 *
 * Pack prices are computed, so they cannot drift; what they *can* do is come out absurd, because
 * they are an integral over a pool somebody authored. A pool naming a card that does not exist, a
 * guarantee the pool cannot honour, a pack that costs less than a match pays — all of those are
 * content faults that produce numbers rather than errors.
 */
class ShopBundleTest {
    private val catalog = runBlocking { loadCardCatalog() }
    private val cards = catalog.all.associateBy { it.id }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private val shelf = ShopCatalog.shelf(cards)

    // ---- The loop, over what actually ships ---------------------------------

    /**
     * **No card on the shelf is worth buying to sell back.**
     *
     * The defect this file exists for, over the real prices and the real rarities.
     */
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

    /**
     * **No pack is worth buying to open and sell back.**
     *
     * ### What "worth" means here, and what it does not
     *
     * The first version of this refused any pack whose *luckiest* opening beat its price. That was
     * wrong, and the shipped data said so: a lucky Bronze returns 592 against a price of 500, and a
     * lucky Silver 1 776 against 1 550.
     *
     * A gamble whose best case beats its price is a gamble. It is not a loop. A loop needs positive
     * expectation, and a player cannot choose their seed — they buy, and the draw happens. Refusing
     * variance outright would also refuse the thing the reveal exists for: a pack that can never
     * surprise you is a purchase, not an opening.
     *
     * So what is asserted is the **mean**, which is the loop condition, and it comes out exact:
     * resale is `RESALE_RATE` of worth and the price is `MARKUP` of the same worth, so a pack
     * returns `0.4 / 1.2` — a third — for every pack on the shelf. Anything else means the two
     * constants have drifted apart, or a pool has been priced by something other than its contents.
     */
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

    /**
     * And no pack is a jackpot: the **best opening it could ever have** stays near its price.
     *
     * The bound variance does need, and the one the mean cannot give. A pack returning a third on
     * average but ten times its price once in a thousand would be worth farming for the tail alone,
     * and a player who saw it happen once would be right to keep buying.
     *
     * Computed rather than sampled — the best possible opening is the dearest card of the pool in
     * every ordinary slot and the dearest of the guaranteed range in the last, which is exact and
     * does not depend on how long the test runs.
     */
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

    /** Every pool names cards that exist — a pack cannot deal an id the table does not hold. */
    @Test
    fun everyPoolResolvesToRealCards() {
        for (type in BoosterType.entries) {
            val missing = type.pool.filterNot { it in cards }

            assertTrue(missing.isEmpty(), "${type.name} names $missing, which no set holds")
            assertTrue(type.pool.size >= type.size, "${type.name} cannot fill its own ${type.size}")
        }
    }

    /**
     * A pack draws from **one** set, so the shop can put it on the right shelf.
     *
     * `ShopOffer.block` derives an offer's set from the first card of its pool. A pool spanning two
     * blocks would make that derivation a coin toss and the pack would appear in a format that does
     * not admit half of what it deals.
     */
    @Test
    fun everyPackBelongsToOneSet() {
        for (type in BoosterType.entries) {
            val blocks = type.pool.mapNotNull { cards[it]?.block }.distinct()

            assertEquals(1, blocks.size, "${type.name} draws from blocks $blocks")
        }
    }

    /**
     * **The guarantee the shop advertises is one the draw cannot break.**
     *
     * Both directions of the same fact: the floor is derived from the guaranteed range, and the
     * draw never leaves that range. A shelf promising "one card 4★ or better" and dealing a two is
     * the kind of lie a player only catches after paying for it — twice.
     */
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

    /**
     * The advertised five-star odds are the odds the draw gives.
     *
     * The shop's row is a *claim about probability*, which is the one kind of claim a player cannot
     * check for themselves — they would need hundreds of packs and a notebook. Sampled against the
     * closed form rather than trusted, and this is the only place the shipped pools are put through
     * it.
     */
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

    /** Every row can be read: a name and a description, in the fallback bundle at least. */
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

    /**
     * Nothing on the shelf is free, and the cheapest pack is reachable.
     *
     * The second half is the one worth stating. A shop whose entry price is thirty wins is a shop a
     * new character cannot use, and the packs are priced by a formula that has no idea what a match
     * pays — so this is the only thing tying the two economies together. `NpcRating` pays 25 MGP
     * for beating the easiest opponent and 250 for the hardest.
     */
    @Test
    fun theCheapestPackIsWithinANewCharactersReach() {
        assertTrue(shelf.all { it.price > 0 }, "nothing on the shelf is free")

        val cheapest = BoosterType.entries.minOf { BoosterPricing.priceOf(it, cards) }
        assertTrue(
            cheapest <= AFFORDABLE_AFTER_A_FEW_MATCHES,
            "the cheapest pack costs $cheapest, more than a new character can plausibly earn",
        )
    }

    /**
     * Both sets have a full ladder of packs, cheap to dear.
     *
     * The FFVIII shelf sold **no packs at all** — right while every pool named ids that resolved
     * against whichever table `MODE` selected, and a plain gap once ids went global. It was then
     * briefly three packs, all premium, which left a new FFVIII collection nothing it could afford.
     */
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

        /** Two thousand samples of a proportion are good to about a point. */
        const val ODDS_TOLERANCE = 0.03

        /** The mean is exact bar the rounding of a price to the nearest ten. */
        const val RAKE_TOLERANCE = 0.02

        /** A lucky pack may beat its price. Twice its price would make the tail worth farming. */
        const val JACKPOT_CEILING = 2

        /** A win pays 25 to 250 MGP — see `NpcRating.mgpFor`. Ten wins buys a first pack. */
        const val AFFORDABLE_AFTER_A_FEW_MATCHES = 1_000

        /** Three rungs, top at least five times the bottom: the least that is a ladder. */
        const val LADDER_RUNGS = 3
        const val LADDER_SPREAD = 5
    }
}
