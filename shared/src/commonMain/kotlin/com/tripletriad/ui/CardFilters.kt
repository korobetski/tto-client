package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardSet
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardType

const val CARD_FILTERS_TEST_TAG: String = "card-filters"

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
) {
    var set: Int? by mutableStateOf(null)

    var type: CardType? by mutableStateOf(null)

    var rarity: Int? by mutableStateOf(null)

    fun matches(card: Card): Boolean =
        (set == null || blockGroups[card.block] == set) &&
            (type == null || card.type == type) &&
            (rarity == null || card.rarity == rarity)
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
internal fun rememberCardFilters(cards: List<Card>, sets: List<CardSet>): CardFilters =
    remember(cards, sets) {
        // A card's block folds down to the block that speaks for its whole *set* before it is
        // grouped or compared — FFXIV spans two blocks and a filter should still offer one
        // "FFXIV" chip, not one per block it happens to occupy. See `representativeBlocks`.
        val blockGroups = representativeBlocks(sets)
        CardFilters(
            blockGroups = blockGroups,
            sets = cards.mapNotNull { blockGroups[it.block] }.distinct().sorted(),
            types = CardType.entries.filter { candidate -> cards.any { it.type == candidate } },
            rarities = cards.map { it.rarity }.distinct().sorted(),
        )
    }

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
