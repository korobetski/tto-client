package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.ACE_POWER
import com.tripletriad.model.Side
import com.tripletriad.model.powerLabel

const val CARD_FILTER_PANEL_TEST_TAG: String = "card-filter-panel"
const val CARD_FILTER_RESET_TEST_TAG: String = "card-filter-reset"
const val CARD_SORT_REVERSE_TEST_TAG: String = "card-sort-reverse"

// `internal` for the reason `cardSortTestTag` is: [SourceKind] is.
internal fun sourceFilterTestTag(kind: SourceKind): String = "card-filter-source-${kind.slug}"

/** The value a side's stepper shows. The two buttons either side of it are named after it. */
fun sideMinimumTestTag(side: Side): String = "card-minimum-${side.name.lowercase()}"

fun sideRaiseTestTag(side: Side): String = "${sideMinimumTestTag(side)}-raise"

fun sideLowerTestTag(side: Side): String = "${sideMinimumTestTag(side)}-lower"

/**
 * The filters of [CardFilterMenus], laid open down the side of a window wide enough to spare it.
 *
 * ### Why a second drawing of the same filters
 *
 * The menus exist because a phone has no room for five bands of chips. A landscape window has that
 * room down its left edge, where it costs the grid a couple of columns instead of its first rows.
 * Open, every answer is in view and several can be lit at once — "FFVIII and FFIX", "four and five
 * stars" — which a menu that closes on each tap cannot ask.
 *
 * The same [CardFilters] and the same tag per answer: which drawing is on screen is a matter of
 * width, and what a player picked survives the window crossing it.
 *
 * ### Two questions the menus do not ask
 *
 * Where a card is found, and the least power wanted on each side. The first would be a sixth menu
 * on a phone's two bands; the second is four numbers, and a menu that closes on each tap is no way
 * to set a number. Both are asked here only — see [CardFilterMenus] for what a narrowed window
 * keeps of them.
 *
 * ### No "All" per section
 *
 * Nothing lit in a section already means all of it. The one reset at the top undoes every section,
 * and says how many things that is before it is pressed.
 */
@Composable
internal fun CardFilterPanel(filters: CardFilters, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .testTag(CARD_FILTER_PANEL_TEST_TAG)
            .rowSurface()
            .verticalScroll(rememberScrollState())
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings[StringKeys.FILTERS],
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(
                onClick = filters::reset,
                enabled = filters.isNarrowed,
                modifier = Modifier.testTag(CARD_FILTER_RESET_TEST_TAG),
            ) {
                Text(
                    text = strings.format(StringKeys.FILTERS_RESET, filters.narrowings.toString()),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // Offered where there is a choice, as the menus are: one set admitted is not a filter.
        if (filters.sets.size > 1) {
            PanelSection(strings[StringKeys.CARD_SET]) {
                for (block in filters.sets) {
                    TtoFilterChip(
                        label = setLabel(strings, block),
                        tag = setFilterTestTag(block),
                        selected = block in filters.pickedSets,
                    ) { filters.pickedSets = filters.pickedSets.toggled(block) }
                }
            }
        }

        if (filters.rarities.size > 1) {
            PanelSection(strings[StringKeys.RARITY]) {
                for (rarity in filters.rarities) {
                    TtoFilterChip(
                        label = starsOf(rarity),
                        tag = rarityFilterTestTag(rarity),
                        selected = rarity in filters.pickedRarities,
                    ) { filters.pickedRarities = filters.pickedRarities.toggled(rarity) }
                }
            }
        }

        PanelSection(strings[StringKeys.CARD_TYPE]) {
            for (type in filters.types) {
                TtoFilterChip(
                    label = type.name,
                    tag = typeFilterTestTag(type),
                    selected = type in filters.pickedTypes,
                    leading = { TypeIcon(type) },
                ) { filters.pickedTypes = filters.pickedTypes.toggled(type) }
            }
        }

        if (filters.sources.size > 1) {
            PanelSection(strings[StringKeys.FILTER_SOURCES]) {
                for (kind in filters.sources) {
                    TtoFilterChip(
                        label = strings[kind.labelKey],
                        tag = sourceFilterTestTag(kind),
                        selected = kind in filters.pickedSources,
                    ) { filters.pickedSources = filters.pickedSources.toggled(kind) }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(strings[StringKeys.FILTER_SIDES])
            SideMinimums(filters, Modifier.align(Alignment.CenterHorizontally))
        }

        PanelSection(strings[StringKeys.SORT]) {
            for (sort in CardSort.entries) {
                TtoFilterChip(
                    label = strings[sort.labelKey],
                    tag = cardSortTestTag(sort),
                    selected = sort == filters.sort,
                ) { filters.sort = sort }
            }
            // A chip of its own rather than a second tap on the lit order: a tap that sometimes
            // picks and sometimes flips is two controls sharing one look.
            TtoFilterChip(
                label = strings[StringKeys.SORT_REVERSE],
                tag = CARD_SORT_REVERSE_TEST_TAG,
                selected = filters.reversed,
            ) { filters.reversed = !filters.reversed }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelSection(title: String, chips: @Composable () -> Unit) {
    Column {
        SectionHeader(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            chips()
        }
    }
}

/**
 * A stepper on each edge of an empty card, where that side's number is printed on a real one.
 *
 * Laid out as a card rather than as four labelled rows because the place is the label: "top",
 * "right" are words a player has to map back onto a card, and the card is the thing they know.
 * The words are still there for a screen reader, on every value and button.
 */
@Composable
private fun SideMinimums(filters: CardFilters, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SideStepper(filters, Side.TOP, vertical = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideStepper(filters, Side.LEFT, vertical = true)
            Box(
                modifier = Modifier
                    .size(StepSize * STEPPER_CELLS)
                    .padding(SpaceXs)
                    .border(
                        HairlineWidth,
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.small,
                    ),
            )
            SideStepper(filters, Side.RIGHT, vertical = true)
        }
        SideStepper(filters, Side.BOTTOM, vertical = false)
    }
}

/**
 * Lower, the value, raise — left to right on a horizontal edge, and bottom to top on a vertical
 * one, so "up" is up and "more" is to the right wherever the stepper sits.
 */
@Composable
private fun SideStepper(filters: CardFilters, side: Side, vertical: Boolean) {
    val strings = LocalStrings.current
    val name = strings[side.labelKey]
    val least = filters.minimumOf(side)

    val lower: @Composable () -> Unit = {
        StepButton(
            glyph = "−",
            tag = sideLowerTestTag(side),
            description = strings.format(StringKeys.SIDE_LOWER, name),
            enabled = least > 0,
        ) { filters.setMinimum(side, least - 1) }
    }
    val raise: @Composable () -> Unit = {
        StepButton(
            glyph = "+",
            tag = sideRaiseTestTag(side),
            description = strings.format(StringKeys.SIDE_RAISE, name),
            enabled = least < ACE_POWER,
        ) { filters.setMinimum(side, least + 1) }
    }
    val value: @Composable () -> Unit = {
        // Boxed, unlike the buttons either side: unset, the value is a dash between a minus and a
        // plus, and "− – +" unboxed is three glyphs of which a player cannot tell the one to read.
        Box(
            modifier = Modifier
                .size(StepSize)
                .border(
                    HairlineWidth,
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // The ace as the card prints it. A dash rather than a zero for no minimum: no side
                // carries a zero, so a "0" would read as a power rather than as "any".
                text = if (least > 0) powerLabel(least) else "–",
                color = if (least > 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .testTag(sideMinimumTestTag(side))
                    .semantics {
                        contentDescription = if (least > 0) {
                            strings.format(StringKeys.SIDE_MINIMUM, name, powerLabel(least))
                        } else {
                            strings.format(StringKeys.SIDE_ANY, name)
                        }
                    },
            )
        }
    }

    if (vertical) {
        Column {
            raise()
            value()
            lower()
        }
    } else {
        Row {
            lower()
            value()
            raise()
        }
    }
}

@Composable
internal fun StepButton(
    glyph: String,
    tag: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = Modifier
            .testTag(tag)
            .size(StepSize)
            .clip(shape)
            .ttoClickable(enabled = enabled, shape = shape, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) MUTED else FAINT),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

internal val Side.labelKey: String
    get() = when (this) {
        Side.TOP -> StringKeys.SIDE_TOP
        Side.RIGHT -> StringKeys.SIDE_RIGHT
        Side.BOTTOM -> StringKeys.SIDE_BOTTOM
        Side.LEFT -> StringKeys.SIDE_LEFT
    }

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** Under the 32 dp of a chip: four steppers and a card between them fit the panel's 216 dp. */
private val StepSize = 28.dp

/** A stepper is three cells long, and the card in the middle is as wide as one. */
private const val STEPPER_CELLS = 3
