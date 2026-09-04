package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardSet
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardType

const val CARD_FILTERS_TEST_TAG: String = "card-filters"

const val CARD_SEARCH_TEST_TAG: String = "card-search"
const val CARD_SEARCH_CLEAR_TEST_TAG: String = "card-search-clear"
const val CARD_SORT_TEST_TAG: String = "card-sort"

// `internal`, unlike every other tag helper here, because [CardSort] is: a public function may not
// name an internal type, and the enum has no business being public to buy this one its `public`.
internal fun cardSortTestTag(sort: CardSort): String = "card-sort-${sort.slug}"

/**
 * The orders 565 cards can be read in.
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
     */
    internal val comparator: Comparator<Card>
        get() = when (this) {
            NUMBER -> CATALOGUE
            POWER -> compareByDescending<Card> { it.total }.then(CATALOGUE)
            RARITY -> compareByDescending<Card> { it.rarity }
                .thenByDescending { it.total }
                .then(CATALOGUE)
        }

    private companion object {
        val CATALOGUE: Comparator<Card> = compareBy({ it.block }, { it.number })
    }
}

fun setFilterTestTag(block: Int?): String = "card-filter-set-${block ?: "all"}"

fun typeFilterTestTag(type: CardType?): String = "card-filter-type-${type?.name ?: "all"}"

fun rarityFilterTestTag(rarity: Int?): String = "card-filter-rarity-${rarity ?: "all"}"

/**
 * The three questions a list of cards is narrowed by — which set, which element, how good — and
 * the state of the answers.
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
) {
    var set: Int? by mutableStateOf(null)

    var type: CardType? by mutableStateOf(null)

    var rarity: Int? by mutableStateOf(null)

    /** What the player has typed, verbatim. Trimmed and folded only at the point of comparison. */
    var query: String by mutableStateOf("")

    var sort: CardSort by mutableStateOf(CardSort.NUMBER)

    /** True while any of the five is narrowing the list — what a "clear" control would undo. */
    val isNarrowed: Boolean
        get() = set != null || type != null || rarity != null || query.isNotBlank()

    fun matches(card: Card): Boolean =
        (set == null || blockGroups[card.block] == set) &&
            (type == null || card.type == type) &&
            (rarity == null || card.rarity == rarity) &&
            matchesQuery(card)

    /** [cards] in the order [sort] asks for. */
    fun sorted(cards: List<Card>): List<Card> = cards.sortedWith(sort.comparator)

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
     */
    private fun matchesQuery(card: Card): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return nameOf(card).contains(needle, ignoreCase = true) ||
            card.name.contains(needle, ignoreCase = true)
    }
}

/**
 * The filters for one list of cards, forgotten when the list itself changes.
 *
 * Keyed on [cards] rather than on whatever the screen thinks makes them change: a chip that
 * selects a set no longer on offer is a grid that reads as empty for a reason nothing on screen
 * states. A caller whose list is rebuilt on every recomposition must `remember` it first, which
 * both callers already do.
 */
@Composable
internal fun rememberCardFilters(cards: List<Card>, sets: List<CardSet>): CardFilters {
    val strings = LocalStrings.current
    // Keyed on the bundle as well as on the cards, so a card searched for by name is searched for
    // in the language on screen. It resets the chips when the language changes, which is the right
    // trade: the alternative is a filter object holding a resolver for a locale nobody is reading.
    return remember(cards, sets, strings) {
        // A card's block folds down to the block that speaks for its whole *set* before it is
        // grouped or compared — FFXIV spans two blocks and a filter should still offer one
        // "FFXIV" chip, not one per block it happens to occupy. See `representativeBlocks`.
        val blockGroups = representativeBlocks(sets)
        CardFilters(
            blockGroups = blockGroups,
            sets = cards.mapNotNull { blockGroups[it.block] }.distinct().sorted(),
            types = CardType.entries.filter { candidate -> cards.any { it.type == candidate } },
            rarities = cards.map { it.rarity }.distinct().sorted(),
            nameOf = { strings[it.nameKey] },
        )
    }
}

/**
 * The search field, and the order beside it.
 *
 * ### Why these two share a line
 *
 * The collection is 565 cards and the header above the grid was already five rows of chips deep on
 * a phone. Search earns a row of its own; an order does not, and a menu behind one icon costs
 * nothing when it is not open. They belong together anyway — both are about *reaching* a card
 * rather than about which cards are admitted, which is what every chip below them decides.
 *
 * ### Not drawn in the consignment picker
 *
 * That list is the spare copies of one collection, which is a handful of cards, and [CardFilters]
 * carries the state whether or not this is rendered — an unsearched query matches everything and
 * the default order is the catalogue's. So the picker is unchanged and can adopt this by adding one
 * line, rather than by growing a second copy of the rule.
 */
@Composable
internal fun CardSearchRow(filters: CardFilters) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = { filters.query = it.take(MAX_QUERY) },
            placeholder = { Text(strings[StringKeys.SEARCH_CARDS]) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = TtoIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(IconSm),
                )
            },
            trailingIcon = {
                // Only once there is something to clear. A permanently lit × on an empty field is
                // a control that does nothing, next to the one place on this screen a tap is
                // expensive — the keyboard is open and the grid is behind it.
                if (filters.query.isNotEmpty()) {
                    IconButton(
                        onClick = { filters.query = "" },
                        modifier = Modifier.testTag(CARD_SEARCH_CLEAR_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = TtoIcons.Back,
                            contentDescription = strings[StringKeys.CANCEL],
                            modifier = Modifier.size(IconSm),
                        )
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.testTag(CARD_SEARCH_TEST_TAG).weight(1f),
        )

        SortMenu(filters)
    }
}

@Composable
private fun SortMenu(filters: CardFilters) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag(CARD_SORT_TEST_TAG),
        ) {
            Icon(
                imageVector = TtoIcons.Sort,
                // Names the order in force rather than the word "sort", so a screen reader — and
                // a long-press tooltip — say which one it is without opening the menu.
                contentDescription = strings[filters.sort.labelKey],
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (candidate in CardSort.entries) {
                DropdownMenuItem(
                    text = { Text(strings[candidate.labelKey]) },
                    onClick = {
                        filters.sort = candidate
                        open = false
                    },
                    trailingIcon = {
                        if (candidate == filters.sort) {
                            Icon(
                                imageVector = TtoIcons.Done,
                                contentDescription = null,
                                modifier = Modifier.size(IconSm),
                            )
                        }
                    },
                    modifier = Modifier.testTag(cardSortTestTag(candidate)),
                )
            }
        }
    }
}

/** Longer than the longest card name in any of the four bundles, and short of a paste bomb. */
private const val MAX_QUERY = 40

/** The chips those three questions are answered with. */
@Composable
internal fun CardFilterChips(filters: CardFilters) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(CARD_FILTERS_TEST_TAG).fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A set row only when there is a choice to make. One set admitted is not a filter, it is a
        // row of one chip that does nothing.
        if (filters.sets.size > 1) {
            FilterRow {
                TtoFilterChip(
                    label = strings[StringKeys.ALL],
                    tag = setFilterTestTag(null),
                    selected = filters.set == null,
                ) { filters.set = null }
                for (block in filters.sets) {
                    TtoFilterChip(
                        label = setLabel(strings, block),
                        tag = setFilterTestTag(block),
                        selected = filters.set == block,
                        onClick = { filters.set = block.takeIf { it != filters.set } },
                    )
                }
            }
        }

        FilterRow {
            TtoFilterChip(
                label = strings[StringKeys.ALL],
                tag = typeFilterTestTag(null),
                selected = filters.type == null,
            ) { filters.type = null }
            for (candidate in filters.types) {
                TypeChip(candidate, isOn = filters.type == candidate) {
                    filters.type = candidate.takeIf { it != filters.type }
                }
            }
        }

        // Rarity on its own row rather than folded into the elements. The two answer different
        // questions — "which tribe" and "how good" — and a card list is read for both at once,
        // which is why they are `and`ed rather than exclusive. Only when there is a choice, for
        // the same reason the set row is: a table with one rarity is not a filter.
        if (filters.rarities.size > 1) {
            FilterRow {
                TtoFilterChip(
                    label = strings[StringKeys.ALL],
                    tag = rarityFilterTestTag(null),
                    selected = filters.rarity == null,
                ) { filters.rarity = null }
                for (candidate in filters.rarities) {
                    RarityChip(candidate, isOn = filters.rarity == candidate) {
                        filters.rarity = candidate.takeIf { it != filters.rarity }
                    }
                }
            }
        }
    }
}

/**
 * A row of chips that wraps instead of scrolling.
 *
 * Twelve elements do not fit across a phone, and a `horizontalScroll` answered that by hiding
 * the ends of the row: the first chip was clipped at the left edge, the last at the right, and
 * nothing on screen said there was more. Wrapping puts every filter in front of the player at
 * the cost of a second line, which is the right trade for a control they choose from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
        content = content,
    )
}

/**
 * One rarity, drawn as the star plate the card itself wears.
 *
 * At [RarityChipWidth] x [RarityChipHeight] — the art's own size, the same numbers `CardView`
 * uses. Stars rather than a number because that is what the player is looking at on the card,
 * and a count of them is a translation of something already legible.
 */
@Composable
private fun RarityChip(rarity: Int, isOn: Boolean, onClick: () -> Unit) {
    val stars = LocalCardArt.current?.starsFor(rarity)

    TtoIconChip(
        tag = rarityFilterTestTag(rarity),
        description = starsOf(rarity),
        selected = isOn,
        onClick = onClick,
    ) {
        if (stars == null) {
            Text(
                text = "$STAR$rarity",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Image(
                bitmap = stars,
                contentDescription = null,
                modifier = Modifier
                    .size(width = RarityChipWidth, height = RarityChipHeight)
                    .alpha(if (isOn) 1f else MUTED),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun TypeChip(type: CardType, isOn: Boolean, onClick: () -> Unit) {
    val icon = LocalCardArt.current?.typeIcon(type)

    TtoIconChip(
        tag = typeFilterTestTag(type),
        // The enum's own name, because nothing translates the elements yet — `app-*.json` has no
        // key for any of the twelve. It is what a screen reader reads out; a word in the wrong
        // language beats the silence an undescribed icon leaves.
        description = type.name,
        selected = isOn,
        onClick = onClick,
    ) {
        if (icon == null) {
            Text(
                text = type.name.take(1),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(TypeChipSize).alpha(if (isOn) 1f else MUTED),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private val TypeChipSize = 16.dp

/** The star plate's own size, as `CardView` draws it. Scaling it would blur five small stars. */
private val RarityChipWidth = 29.dp
private val RarityChipHeight = 28.dp
