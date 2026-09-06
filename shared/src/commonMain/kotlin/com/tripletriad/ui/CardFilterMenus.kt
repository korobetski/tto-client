package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.CardType

const val CARD_SET_MENU_TEST_TAG: String = "card-menu-set"
const val CARD_TYPE_MENU_TEST_TAG: String = "card-menu-type"
const val CARD_RARITY_MENU_TEST_TAG: String = "card-menu-rarity"

/**
 * The chevron a closed menu wears. A character rather than an icon, so the arrow sits on the
 * text's own baseline and inherits its colour without a second measurement pass.
 */
private const val CHEVRON = " ▾"

/**
 * The three questions a card list is narrowed by, as one line of menus.
 *
 * ### Why menus and not the five rows of chips this replaces
 *
 * Every filter was on screen at once: a row for the sets, a row for the elements, a row for the
 * rarities, each wrapping to two lines on a phone. Five bands of controls, and the grid — the
 * thing the screen exists to show — started at roughly two thirds of the way down. A closed menu
 * costs the width of its own word; opened, it has the room to write out each element's *name*,
 * which an icon chip 16 dp wide never had.
 *
 * The cost is stated plainly: an element is no longer legible without opening the menu. That is the
 * trade the card list is worth making and the consignment desk inherits, because both are read for
 * the grid rather than for the filters.
 *
 * ### The label is the answer, not the question
 *
 * A menu with a filter in force is titled with the filter — "FIRE ▾", "★★ ▾" — and lit like a
 * selected chip. So the row still says what is being hidden without being opened, which is the one
 * thing the chips did better and the only part of them worth keeping.
 *
 * @param trailing anything the room wants on the same line. The collection puts its
 *   All / Owned / Missing segments here; the consignment desk passes nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardFilterMenus(filters: CardFilters, trailing: @Composable () -> Unit = {}) {
    val strings = LocalStrings.current

    FlowRow(
        modifier = Modifier
            .testTag(CARD_FILTERS_TEST_TAG)
            .fillMaxWidth()
            .padding(bottom = SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        // A menu only where there is a choice to make, exactly as the rows it replaces were: one
        // set admitted is not a filter, it is a control that can only be put back where it was.
        if (filters.sets.size > 1) {
            FilterMenu(
                tag = CARD_SET_MENU_TEST_TAG,
                label = filters.set?.let { setLabel(strings, it) }
                    ?: strings[StringKeys.CARD_SET],
                on = filters.set != null,
            ) { close ->
                MenuChoice(strings[StringKeys.ALL], setFilterTestTag(null), filters.set == null) {
                    filters.set = null
                    close()
                }
                for (block in filters.sets) {
                    MenuChoice(
                        label = setLabel(strings, block),
                        tag = setFilterTestTag(block),
                        chosen = filters.set == block,
                    ) {
                        filters.set = block
                        close()
                    }
                }
            }
        }

        FilterMenu(
            tag = CARD_TYPE_MENU_TEST_TAG,
            // The enum's own name, because nothing translates the elements yet — `app-*.json` has
            // no key for any of the twelve. The same word the chip's description carried.
            label = filters.type?.name ?: strings[StringKeys.CARD_TYPE],
            on = filters.type != null,
        ) { close ->
            MenuChoice(strings[StringKeys.ALL], typeFilterTestTag(null), filters.type == null) {
                filters.type = null
                close()
            }
            for (candidate in filters.types) {
                MenuChoice(
                    label = candidate.name,
                    tag = typeFilterTestTag(candidate),
                    chosen = filters.type == candidate,
                    leading = { TypeIcon(candidate) },
                ) {
                    filters.type = candidate
                    close()
                }
            }
        }

        if (filters.rarities.size > 1) {
            FilterMenu(
                tag = CARD_RARITY_MENU_TEST_TAG,
                label = filters.rarity?.let { starsOf(it) } ?: strings[StringKeys.RARITY],
                on = filters.rarity != null,
            ) { close ->
                MenuChoice(
                    strings[StringKeys.ALL],
                    rarityFilterTestTag(null),
                    filters.rarity == null,
                ) {
                    filters.rarity = null
                    close()
                }
                for (candidate in filters.rarities) {
                    MenuChoice(
                        label = starsOf(candidate),
                        tag = rarityFilterTestTag(candidate),
                        chosen = filters.rarity == candidate,
                    ) {
                        filters.rarity = candidate
                        close()
                    }
                }
            }
        }

        FilterMenu(
            tag = CARD_SORT_TEST_TAG,
            // Named by the order in force rather than by the word "sort", the way the icon button
            // this grew out of named it to a screen reader. An order is always in force, so this
            // menu is never lit: there is no state of "unsorted" to distinguish it from.
            label = strings[filters.sort.labelKey],
            on = false,
        ) { close ->
            for (candidate in CardSort.entries) {
                MenuChoice(
                    label = strings[candidate.labelKey],
                    tag = cardSortTestTag(candidate),
                    chosen = candidate == filters.sort,
                ) {
                    filters.sort = candidate
                    close()
                }
            }
        }

        trailing()
    }
}

/**
 * One closed menu, and what it opens.
 *
 * @param on whether a filter is in force, which lights the chip. Not the same as "the menu is
 *   open" — an open menu is transient, and a lit chip is the state the row is read for.
 */
@Composable
private fun FilterMenu(
    tag: String,
    label: String,
    on: Boolean,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        TtoFilterChip(label = label + CHEVRON, tag = tag, selected = on) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

/** One line of a filter menu: what it selects, and a tick when it is what is selected. */
@Composable
private fun MenuChoice(
    label: String,
    tag: String,
    chosen: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = leading,
        trailingIcon = {
            if (chosen) {
                Icon(
                    imageVector = TtoIcons.Done,
                    contentDescription = null,
                    modifier = Modifier.size(IconSm),
                )
            }
        },
        modifier = Modifier.testTag(tag),
    )
}

/** The element's own badge, at the size the card itself wears it. Absent when the art is not. */
@Composable
private fun TypeIcon(type: CardType) {
    val icon = LocalCardArt.current?.typeIcon(type) ?: return

    Image(
        bitmap = icon,
        contentDescription = null,
        modifier = Modifier.size(TypeIconSize),
        filterQuality = FilterQuality.None,
    )
}

private val TypeIconSize = 16.dp
