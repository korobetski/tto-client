package com.tripletriad.ui

import com.tripletriad.model.Card
import com.tripletriad.model.CardType
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

        filters.rarity = 1
        assertTrue(filters.isNarrowed)
    }

    private fun filters(nameOf: (Card) -> String = { it.name }) = CardFilters(
        blockGroups = mapOf(1 to 1),
        sets = listOf(1),
        types = CardType.entries,
        rarities = listOf(1, 5),
        nameOf = nameOf,
    )

    private fun card(number: Int, power: Int, rarity: Int, name: String = "Test $number") = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = name,
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = rarity,
    )

    private val cards: List<Card> = listOf(
        card(1, power = 9, rarity = 5, name = "Ifrit"),
        card(2, power = 4, rarity = 1, name = "Dodo"),
        card(3, power = 6, rarity = 1, name = "Tonberry"),
    )

    private companion object {
        val IFRIT = Card.idFor(block = 1, number = 1)
    }
}
