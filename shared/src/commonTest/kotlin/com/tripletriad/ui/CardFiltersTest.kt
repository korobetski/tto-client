package com.tripletriad.ui

import com.tripletriad.model.ACE_POWER
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two questions [CardFilters] gained — what is this called, and in what order do I want it —
 * against a table small enough to state the expected answer by hand.
 *
 * The three chip filters are not re-tested here: they were already covered through the screens that
 * draw them, and nothing about them moved.
 */
class CardFiltersTest {
    @Test
    fun anEmptyQueryAdmitsEverything() {
        val filters = filters()

        assertTrue(cards.all(filters::matches), "a blank field is not a filter")
        filters.query = "   "
        assertTrue(cards.all(filters::matches), "whitespace is a blank field")
    }

    @Test
    fun aQueryMatchesPartOfTheNameInAnyCase() {
        val filters = filters()
        filters.query = "FRI"

        assertEquals(listOf(IFRIT), cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun aQueryIsTrimmedBeforeItIsCompared() {
        // A soft keyboard's autocomplete leaves a trailing space, and a search that goes blank the
        // moment the player finishes typing a word is a search nobody trusts.
        val filters = filters()
        filters.query = "  Ifrit "

        assertEquals(listOf(IFRIT), cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun theEnglishNameMatchesEvenWhenTheAppIsNotInEnglish() {
        // `nameOf` here answers in French, as a French bundle would. A player who knows the card as
        // "Ifrit" — which is what `Card.name` carries — still finds it.
        val filters = filters(nameOf = { "Carte ${it.number}" })
        filters.query = "ifrit"

        assertEquals(listOf(IFRIT), cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun theDisplayedNameMatchesToo() {
        val filters = filters(nameOf = { "Zzz${it.number}" })
        filters.query = "zzz"

        assertEquals(cards.map { it.id }, cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun aQueryThatMatchesNothingMatchesNothing() {
        val filters = filters()
        filters.query = "bahamut"

        assertTrue(cards.none(filters::matches))
    }

    @Test
    fun accentsAreNotFolded() {
        // Stated as a test rather than left to the KDoc: folding needs a table this does not have,
        // and a half-done job would be worse. When that changes, this is the test that should fail.
        val filters = filters(nameOf = { "Ténèbres" })
        filters.query = "tenebres"

        assertTrue(cards.none(filters::matches), "accent folding arrived without a table")
    }

    @Test
    fun aNumberFindsTheCardWithThatNumberWithOrWithoutItsZeros() {
        val filters = filters()

        filters.query = "003"
        assertEquals(listOf(TONBERRY), cards.filter(filters::matches).map { it.id })
        filters.query = "3"
        assertEquals(listOf(TONBERRY), cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun aNameTheRoomIsHidingDoesNotFindItsCard() {
        // The collection draws a card nobody owns as "?". Found by name, it would be the one
        // tile left in the grid — the name, read out.
        val filters = filters()
        filters.query = "ifrit"

        assertTrue(cards.none { filters.matches(it, known = false) }, "the name was matched")
        filters.query = "1"
        assertEquals(
            listOf(IFRIT),
            cards.filter { filters.matches(it, known = false) }.map { it.id },
            "the number is still searchable",
        )
    }

    @Test
    fun anElementTheRoomIsHidingDoesNotAnswerTheElementMenu() {
        // Ifrit is the one fire card. Left in the grid under "Fire", the "?" would be read as one.
        val filters = filters()
        filters.pickedTypes = setOf(CardType.FIRE)

        assertEquals(listOf(IFRIT), cards.filter(filters::matches).map { it.id })
        assertTrue(cards.none { filters.matches(it, known = false) }, "the element was matched")

        // The rarity is printed on the "?" itself, so that filter still answers.
        filters.pickedTypes = emptySet()
        filters.pickedRarities = setOf(5)
        assertEquals(
            listOf(IFRIT),
            cards.filter { filters.matches(it, known = false) }.map { it.id },
            "the rarity stopped answering",
        )
    }

    @Test
    fun aCardWhoseSidesAreHiddenIsRankedAfterTheRestAndNotByThem() {
        // Only Dodo, the weakest, is known. Ranked by their own totals, Ifrit and Tonberry would
        // come in the order their sides put them in, which is those sides read out.
        val known: (Card) -> Boolean = { it.id == DODO }
        val filters = filters()

        filters.sort = CardSort.POWER
        assertEquals(listOf(DODO, IFRIT, TONBERRY), filters.sorted(cards, known).map { it.id })

        // The stars still lead, because the "?" prints them; within one star the power does not.
        filters.sort = CardSort.RARITY
        assertEquals(listOf(IFRIT, DODO, TONBERRY), filters.sorted(cards, known).map { it.id })
    }

    @Test
    fun theDefaultOrderIsTheCatalogueOrder() {
        val filters = filters()

        assertEquals(CardSort.NUMBER, filters.sort)
        assertEquals(
            cards.sortedBy { it.number }.map { it.id },
            filters.sorted(cards.shuffled()).map { it.id },
        )
    }

    @Test
    fun powerOrdersByTheFourEdgesAddedUp() {
        val filters = filters()
        filters.sort = CardSort.POWER

        val totals = filters.sorted(cards).map { it.total }
        assertEquals(totals.sortedDescending(), totals, "strongest first: $totals")
    }

    @Test
    fun rarityOrdersByStarsAndBreaksTiesOnPower() {
        val filters = filters()
        filters.sort = CardSort.RARITY

        val sorted = filters.sorted(cards)
        val ranks = sorted.map { it.rarity }
        assertEquals(ranks.sortedDescending(), ranks, "rarest first: $ranks")
        // Two one-star cards in the table, and the stronger of them comes first.
        val plain = sorted.filter { it.rarity == 1 }.map { it.total }
        assertEquals(plain.sortedDescending(), plain, "ties are not broken on power: $plain")
    }

    @Test
    fun everyOrderIsStableOnCardsThatTieCompletely() {
        // Two identical-strength cards must not swap places between recompositions, or the grid
        // flickers. The catalogue order is the last comparator in all three.
        val twins = listOf(card(90, power = 5, rarity = 1), card(91, power = 5, rarity = 1))
        for (sort in CardSort.entries) {
            val filters = filters()
            filters.sort = sort
            assertEquals(
                listOf(twins[0].id, twins[1].id),
                filters.sorted(twins.reversed()).map { it.id },
                "$sort is not stable",
            )
        }
    }

    @Test
    fun narrowedSaysWhetherAnythingIsBeingHeldBack() {
        val filters = filters()
        assertFalse(filters.isNarrowed, "nothing is set yet")

        filters.query = "if"
        assertTrue(filters.isNarrowed)

        filters.query = ""
        assertFalse(filters.isNarrowed)

        // An order is not a filter: it changes what comes first, never what is there.
        filters.sort = CardSort.POWER
        assertFalse(filters.isNarrowed, "a sort is not a narrowing")

        filters.pickedRarities = setOf(1)
        assertTrue(filters.isNarrowed)
    }

    @Test
    fun severalAnswersToOneQuestionAdmitEitherButTheQuestionsStillCombine() {
        val filters = filters()

        filters.pickedRarities = setOf(1)
        assertEquals(listOf(DODO, TONBERRY), cards.filter(filters::matches).map { it.id })

        filters.pickedRarities = setOf(1, 5)
        assertEquals(listOf(IFRIT, DODO, TONBERRY), cards.filter(filters::matches).map { it.id })

        // Fire *and* one star: Ifrit is the only fire card and has five.
        filters.pickedTypes = setOf(CardType.FIRE)
        filters.pickedRarities = setOf(1)
        assertEquals(emptyList(), cards.filter(filters::matches).map { it.id })
    }

    @Test
    fun resetUndoesEveryNarrowingItCountsAndLeavesTheOrder() {
        val filters = filters()
        filters.pickedRarities = setOf(1, 5)
        filters.pickedTypes = setOf(CardType.FIRE)
        filters.query = "if"
        filters.pickedSources = setOf(SourceKind.SHOP)
        filters.setMinimum(Side.TOP, 5)
        filters.sort = CardSort.POWER
        filters.reversed = true

        assertEquals(6, filters.narrowings, "two rarities, an element, a query, a source, a side")

        filters.reset()

        assertEquals(0, filters.narrowings)
        assertEquals(emptyMap(), filters.minimums)
        assertEquals(cards.map { it.id }, cards.filter(filters::matches).map { it.id })
        assertEquals(CardSort.POWER, filters.sort, "the order is not a narrowing")
        assertTrue(filters.reversed, "nor is its direction")
    }

    @Test
    fun aReversedOrderReadsFromTheOtherEndAndStillKeepsTheQuestionMarksLast() {
        val filters = filters()
        filters.reversed = true

        filters.sort = CardSort.NUMBER
        assertEquals(listOf(TONBERRY, DODO, IFRIT), filters.sorted(cards).map { it.id })

        filters.sort = CardSort.POWER
        assertEquals(listOf(DODO, TONBERRY, IFRIT), filters.sorted(cards).map { it.id })

        // Dodo, the weakest, is a "?". Weakest-first would put it on top by its own sides — or,
        // ranked below every total and then flipped, on top for being hidden. Neither: it is last.
        val known: (Card) -> Boolean = { it.id != DODO }
        assertEquals(listOf(TONBERRY, IFRIT, DODO), filters.sorted(cards, known).map { it.id })

        filters.sort = CardSort.RARITY
        assertEquals(listOf(TONBERRY, DODO, IFRIT), filters.sorted(cards, known).map { it.id })
    }

    @Test
    fun aSourceAdmitsWhatAnyLitTableOffersAndStillAnswersUnderTheQuestionMark() {
        val offers = mapOf(
            IFRIT to setOf(SourceKind.SHOP),
            DODO to setOf(SourceKind.OPPONENT, SourceKind.BOOSTER),
        )
        val filters = filters(offers = offers)

        filters.pickedSources = setOf(SourceKind.SHOP)
        assertEquals(listOf(IFRIT), cards.filter(filters::matches).map { it.id })

        filters.pickedSources = setOf(SourceKind.SHOP, SourceKind.BOOSTER)
        assertEquals(listOf(IFRIT, DODO), cards.filter(filters::matches).map { it.id })
        // The "?" panel lists where its card is found, so this is not an answer the "?" hides.
        assertEquals(
            listOf(IFRIT, DODO),
            cards.filter { filters.matches(it, known = false) }.map { it.id },
        )
    }

    @Test
    fun aMinimumIsAskedOfTheSideItIsSetOnAndOfNoQuestionMark() {
        // An ace on top and ones elsewhere: a filter that summed the sides, or took their best,
        // would let it through on the left as well.
        val lopsided = Card(
            id = Card.idFor(block = 1, number = 4),
            nameKey = "STR_TEST_4",
            name = "Lopsided",
            top = ACE_POWER,
            right = 1,
            bottom = 1,
            left = 1,
            rarity = 1,
            type = null,
        )
        val table = cards + lopsided
        val filters = filters()

        filters.setMinimum(Side.TOP, 7)
        assertEquals(listOf(IFRIT, lopsided.id), table.filter(filters::matches).map { it.id })

        filters.setMinimum(Side.LEFT, 7)
        assertEquals(listOf(IFRIT), table.filter(filters::matches).map { it.id })
        assertEquals(2, filters.narrowings)

        // Left in the grid under "top 7 or more", a "?" would be its top read out.
        assertTrue(table.none { filters.matches(it, known = false) }, "a hidden side answered")

        filters.setMinimum(Side.LEFT, 0)
        assertEquals(1, filters.narrowings, "a side lowered to nothing is still counted")
        filters.setMinimum(Side.TOP, ACE_POWER + 1)
        assertEquals(ACE_POWER, filters.minimumOf(Side.TOP), "no side carries more than an ace")
    }

    private fun filters(
        nameOf: (Card) -> String = { it.name },
        offers: Map<Int, Set<SourceKind>> = emptyMap(),
    ) = CardFilters(
        blockGroups = mapOf(1 to 1),
        sets = listOf(1),
        types = CardType.entries,
        rarities = listOf(1, 5),
        nameOf = nameOf,
        offers = offers,
    )

    private fun card(
        number: Int,
        power: Int,
        rarity: Int,
        name: String = "Test $number",
        type: CardType? = null,
    ) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = name,
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = rarity,
        type = type,
    )

    private val cards: List<Card> = listOf(
        card(1, power = 9, rarity = 5, name = "Ifrit", type = CardType.FIRE),
        card(2, power = 4, rarity = 1, name = "Dodo"),
        card(3, power = 6, rarity = 1, name = "Tonberry"),
    )

    private companion object {
        val IFRIT = Card.idFor(block = 1, number = 1)
        val DODO = Card.idFor(block = 1, number = 2)
        val TONBERRY = Card.idFor(block = 1, number = 3)
    }
}
