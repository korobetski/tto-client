package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.data.CardSet
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.ACE_POWER
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.Side
import com.tripletriad.model.power

const val CARD_FILTERS_TEST_TAG: String = "card-filters"

const val CARD_SEARCH_TEST_TAG: String = "card-search"
const val CARD_SEARCH_CLEAR_TEST_TAG: String = "card-search-clear"
const val CARD_SORT_TEST_TAG: String = "card-sort"

// `internal`, unlike every other tag helper here, because [CardSort] is: a public function may not
// name an internal type, and the enum has no business being public to buy this one its `public`.
internal fun cardSortTestTag(sort: CardSort): String = "card-sort-${sort.slug}"

/**
 * The orders 586 cards can be read in.
 *
 * Three, and each answers a different question a player actually has. [NUMBER] is the catalogue's
 * own order and therefore the one the gaps are visible in — a collection read for what is *missing*
 * has to be read in the order the set was printed. [POWER] and [RARITY] are the two ways of asking
 * "what is my best", and they are not the same question: rarity is what a card cost and what the
 * deck caps count, [Card.total] is what it does on a board.
 *
 * No alphabetical order. It is what the search field is for, and a name is the one property of a
 * card that changes with the language the app is in — an ordering that rearranges itself when the
 * player switches locale is an ordering nobody can learn.
 */
internal enum class CardSort(val slug: String, val labelKey: String) {
    NUMBER("number", StringKeys.SORT_NUMBER),

    // Named `Total` and not "power", because [Card.total] is exactly the number the card detail
    // panel already labels `STR_TOTAL` — two words for one figure would be two things to learn.
    POWER("power", StringKeys.TOTAL),

    // Likewise the word the rarity chips and the card panel already use.
    RARITY("rarity", StringKeys.RARITY),
    ;

    /**
     * Ties broken by the catalogue order in every case, so the grid is stable: two cards of equal
     * total that swapped places between recompositions would be two cards that flicker.
     *
     * @param known false for a card whose sides the room is not showing. It ranks after every
     *   known card whichever way the order runs — ranked by its own total, a "?" at the head of
     *   "by power" would be its sides read out, and at the head of the same order reversed it would
     *   be a column of "?" before the first card worth reading — and among the unknown ones the
     *   catalogue order decides.
     * @param reversed the same order read from its other end. The tie-break is not reversed with
     *   it: the catalogue only decides between cards the order itself cannot tell apart.
     */
    internal fun comparator(
        known: (Card) -> Boolean = { true },
        reversed: Boolean = false,
    ): Comparator<Card> {
        fun Comparator<Card>.facing(): Comparator<Card> = if (reversed) this.reversed() else this
        val power = compareBy<Card> { !known(it) }
            .then(compareByDescending<Card> { if (known(it)) it.total else 0 }.facing())
        return when (this) {
            NUMBER -> CATALOGUE.facing()
            POWER -> power.then(CATALOGUE)
            RARITY -> compareByDescending<Card> { it.rarity }.facing().then(power).then(CATALOGUE)
        }
    }

    private companion object {
        val CATALOGUE: Comparator<Card> = compareBy({ it.block }, { it.number })
    }
}

fun setFilterTestTag(block: Int?): String = "card-filter-set-${block ?: "all"}"

fun typeFilterTestTag(type: CardType?): String = "card-filter-type-${type?.name ?: "all"}"

fun rarityFilterTestTag(rarity: Int?): String = "card-filter-rarity-${rarity ?: "all"}"

/**
 * The questions a list of cards is narrowed by — which set, which element, how good, where from,
 * how strong on which side — and the state of the answers.
 *
 * ### Each answer is a set
 *
 * Nothing picked admits everything; several picked admit any of them — "FFVIII or FFIX", "four or
 * five stars". The questions still combine by *and*. The menus pick one answer at a time because
 * they close on a tap; the landscape panel, which shows every answer at once, can light several.
 * See [CardFilterPanel].
 *
 * ### Why this is an object and not three `remember`s per screen
 *
 * The collection and the auction's consignment picker ask exactly this of exactly the same
 * objects, and they had grown two copies of the answer: two `representativeBlocks` folds, two
 * predicates spelling out the same three null-checks, two derivations of which chips to offer.
 * Two copies of one rule is one rule that will disagree with itself — the picker learned the
 * FFXIV block fold a month after the collection did, and only because the bug was noticed twice.
 *
 * ### The vocabularies come from the whole list, not from what survives the filter
 *
 * [sets], [types] and [rarities] are computed once from the cards this was built on. Deriving
 * them from what is currently shown would take chips away as they are used, which leaves a player
 * who narrowed too far with no control to widen it again.
 *
 * ### What is *not* here
 *
 * Anything a single screen filters on top: the collection hides secret cards and can be reduced
 * to what is owned, and the auction picker only ever offers spare copies. That is why [matches]
 * is a predicate a caller `and`s into its own list-building rather than a list this hands back —
 * the shared part is the three questions, not each room's admission rules.
 */
@Stable
internal class CardFilters(
    private val blockGroups: Map<Int, Int>,
    val sets: List<Int>,
    val types: List<CardType>,
    val rarities: List<Int>,
    /**
     * A card's name as this locale writes it. A lambda rather than a `Strings`, so the only thing
     * this knows about the i18n layer is that a card has a name — and so a test can build one
     * without a bundle.
     */
    private val nameOf: (Card) -> String = { it.name },
    /**
     * The kinds of table offering each card, by id — see [sourceKindsByCard]. No entry for a card
     * nothing offers, and none at all where the room was built without the tables.
     */
    private val offers: Map<Int, Set<SourceKind>> = emptyMap(),
) {
    /** The kinds offering at least one card here, as [sets] is the sets among them. */
    val sources: List<SourceKind> =
        SourceKind.entries.filter { kind -> offers.values.any { kind in it } }

    /** Representative blocks, as in [sets]. */
    var pickedSets: Set<Int> by mutableStateOf(emptySet())

    var pickedTypes: Set<CardType> by mutableStateOf(emptySet())

    var pickedRarities: Set<Int> by mutableStateOf(emptySet())

    var pickedSources: Set<SourceKind> by mutableStateOf(emptySet())

    /**
     * The least power wanted on each side. A side with no entry admits any, and a minimum lowered
     * to nothing is removed rather than kept at zero, so the entries are exactly what
     * [narrowings] counts.
     */
    var minimums: Map<Side, Int> by mutableStateOf(emptyMap())
        private set

    fun minimumOf(side: Side): Int = minimums[side] ?: 0

    /** Clamped to an ace; zero or less lifts the side's minimum. */
    fun setMinimum(side: Side, power: Int) {
        minimums = if (power <= 0) {
            minimums - side
        } else {
            minimums + (side to power.coerceAtMost(ACE_POWER))
        }
    }

    /** What the player has typed, verbatim. Trimmed and folded only at the point of comparison. */
    var query: String by mutableStateOf("")

    var sort: CardSort by mutableStateOf(CardSort.NUMBER)

    var reversed: Boolean by mutableStateOf(false)

    /**
     * One per answer picked, one per side given a minimum, and one for a query. What the reset
     * control prints, so the number beside it is the number of things it is about to undo.
     */
    val narrowings: Int
        get() = pickedSets.size + pickedTypes.size + pickedRarities.size + pickedSources.size +
            minimums.size + if (query.isBlank()) 0 else 1

    val isNarrowed: Boolean
        get() = narrowings > 0

    /** Every narrowing undone, and not the order: an order hides nothing. */
    fun reset() {
        pickedSets = emptySet()
        pickedTypes = emptySet()
        pickedRarities = emptySet()
        pickedSources = emptySet()
        minimums = emptyMap()
        query = ""
    }

    /**
     * @param known false where the room draws [card] without its name, element and sides — the
     *   collection's "?". A filter that narrows the grid to one tile reads that tile's answer out,
     *   so such a card answers the query by its number only and no element at all; the set and
     *   the rarity still apply, because the "?" prints both, and so does where it is found, which
     *   its panel lists. No side minimum admits it: left under "top 7 or more", it is its top.
     */
    fun matches(card: Card, known: Boolean = true): Boolean =
        (pickedSets.isEmpty() || blockGroups[card.block]?.let { it in pickedSets } == true) &&
            (pickedTypes.isEmpty() || known && card.type in pickedTypes) &&
            (pickedRarities.isEmpty() || card.rarity in pickedRarities) &&
            (pickedSources.isEmpty() || offers[card.id]?.any { it in pickedSources } == true) &&
            (minimums.isEmpty() || known && reachesMinimums(card)) &&
            matchesQuery(card, known)

    private fun reachesMinimums(card: Card): Boolean =
        minimums.all { (side, least) -> card.power(side) >= least }

    /** [cards] in the order [sort] and [reversed] ask for. [known] as in [CardSort.comparator]. */
    fun sorted(cards: List<Card>, known: (Card) -> Boolean = { true }): List<Card> =
        cards.sortedWith(sort.comparator(known, reversed))

    /**
     * Whether a card answers to what has been typed.
     *
     * Matched against the **displayed** name and against [Card.name], which is the `en_US` one the
     * card table carries. Two names rather than one because the card table is the only place some
     * of these are written down in a language a search engine would have indexed: a player who
     * knows a card as "Ifrit" finds it in the German build, and one who knows it as "Bahamut Zéro"
     * finds it in that one. Neither is ever the wrong answer — a query that matches nothing still
     * matches nothing.
     *
     * Case-folded and no more. Accents are **not** folded: doing it properly needs a table this
     * does not have, and a half-done job that folds é and not ö would be worse than none.
     *
     * A query that is a number also matches the card's number in its set, `044` and `44` alike —
     * the one thing the collection still prints on a card nobody owns, and so the only way to
     * search for one.
     */
    private fun matchesQuery(card: Card, known: Boolean): Boolean {
        val needle = query.trim()
        val named = known &&
            listOf(nameOf(card), card.name).any { it.contains(needle, ignoreCase = true) }
        return needle.isEmpty() || needle.toIntOrNull() == card.number || named
    }
}

/**
 * The filters for one list of cards, forgotten when the list itself changes.
 *
 * Keyed on [cards] rather than on whatever the screen thinks makes them change: a chip that
 * selects a set no longer on offer is a grid that reads as empty for a reason nothing on screen
 * states. A caller whose list is rebuilt on every recomposition must `remember` it first, which
 * both callers already do.
 *
 * @param opponents the roster whose drop tables answer "from an opponent". Null leaves that answer
 *   off the offer — the consignment picker's case, which draws no panel to offer it in.
 */
@Composable
internal fun rememberCardFilters(
    cards: List<Card>,
    sets: List<CardSet>,
    opponents: NpcCatalog? = null,
): CardFilters {
    val strings = LocalStrings.current
    // Keyed on the bundle as well as on the cards, so a card searched for by name is searched for
    // in the language on screen. It resets the chips when the language changes, which is the right
    // trade: the alternative is a filter object holding a resolver for a locale nobody is reading.
    return remember(cards, sets, opponents, strings) {
        // A card's block folds down to the block that speaks for its whole *set* before it is
        // grouped or compared — FFXIV spans two blocks and a filter should still offer one
        // "FFXIV" chip, not one per block it happens to occupy. See `representativeBlocks`.
        val blockGroups = representativeBlocks(sets)
        val kinds = sourceKindsByCard(opponents)
        CardFilters(
            blockGroups = blockGroups,
            sets = cards.mapNotNull { blockGroups[it.block] }.distinct().sorted(),
            types = CardType.entries.filter { candidate -> cards.any { it.type == candidate } },
            rarities = cards.map { it.rarity }.distinct().sorted(),
            nameOf = { strings[it.nameKey] },
            // This room's cards only: a chip for a kind offering none would empty the grid.
            offers = cards.mapNotNull { card -> kinds[card.id]?.let { card.id to it } }.toMap(),
        )
    }
}

/**
 * The search field, and the count of what answers to it.
 *
 * ### Why the count shares this line
 *
 * "Owned · 3 / 21" is a fact about what the field and the menus below it have narrowed the list
 * to, and it used to sit on a band of its own above them — one more of the five bands the grid was
 * pushed down by. Beside the field it is next to the control that changes it and costs the screen
 * no band at all.
 *
 * Beside rather than *inside*: as the field's `supportingText` it would be swallowed by the text
 * field's own merged semantics, which is a screen reader announcing the size of the collection as
 * part of reading out a search box.
 *
 * ### Not drawn in the consignment picker
 *
 * That list is the spare copies of one collection, which is a handful of cards, and [CardFilters]
 * carries the query whether or not this is rendered — an unsearched query matches everything. So
 * the picker is unchanged and can adopt this by adding one line, rather than by growing a second
 * copy of the rule.
 *
 * @param count what the list currently holds, drawn beside the field. Null draws nothing, which is
 *   a room that has not got a count to give rather than a count that came out zero.
 */
@Composable
internal fun CardSearchRow(
    filters: CardFilters,
    count: String? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        TtoSearchField(
            value = filters.query,
            onValueChange = { filters.query = it },
            tag = CARD_SEARCH_TEST_TAG,
            clearTag = CARD_SEARCH_CLEAR_TEST_TAG,
            // The count already says what is searched, and leaves a phone too little for more.
            placeholder = strings[StringKeys.SEARCH],
            modifier = Modifier.weight(1f),
        )

        if (count != null) {
            Text(
                text = count,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(CARD_TOTAL_TEST_TAG),
            )
        }
    }
}
