package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.CardValue
import com.tripletriad.data.Format
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.GameSave
import com.tripletriad.model.powerLabel
import kotlinx.coroutines.launch

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

const val CARD_FILTERS_TEST_TAG: String = "card-filters"
const val CARD_SELL_TEST_TAG: String = "card-sell"

fun setFilterTestTag(block: Int?): String = "card-filter-set-${block ?: "all"}"

fun typeFilterTestTag(type: CardType?): String = "card-filter-type-${type?.name ?: "all"}"

fun cardCellTestTag(cardId: Int): String = "card-cell-$cardId"

fun cardCopiesTestTag(cardId: Int): String = "card-copies-$cardId"

@Composable
internal fun ColumnScope.CardListBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    onIntent: suspend (Intent) -> IntentOutcome,
    note: NoteHost,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val admitted = remember(catalog, format) { catalog.admittedBy(format) }
    val owned = profile.cards
    var selected by remember(format) { mutableStateOf<Card?>(null) }
    var set by remember(format) { mutableStateOf<Int?>(null) }
    var type by remember(format) { mutableStateOf<CardType?>(null) }

    val cards = remember(admitted, set, type) {
        admitted.filter { (set == null || it.block == set) && (type == null || it.type == type) }
    }

    Text(
        // Counted over what is **on screen**, so the line answers the question the grid is
        // currently asking. Filtered to fire, "Owned · 3 / 21" is a fact about fire cards; the
        // unfiltered total is the same sentence with no filter applied.
        text = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
            "${cards.count { owned.containsKey(it.id) }} / ${cards.size}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(CARD_TOTAL_TEST_TAG).padding(bottom = SpaceSm),
    )

    CardFilters(
        sets = remember(admitted) { admitted.map { it.block }.distinct().sorted() },
        types = remember(
            admitted,
        ) { CardType.entries.filter { t -> admitted.any { it.type == t } } },
        set = set,
        type = type,
        onSet = { set = it },
        onType = { type = it },
    )

    // Selling takes the copy out of the collection and pays for it. Asked rather than computed:
    // a card's worth is its **rarity**, and on an account it is the server's card table that says
    // so — a client that worked the price out itself could work out a better one.
    //
    // One at a time, and answered. Both for the reasons the bag's buttons are — see the `busy` flag
    // in [InventoryBody]: two taps were two sales of two copies, and `perform` answered `Unit`, so
    // a sale the server declined took the tap and said nothing at all.
    var selling by remember(format) { mutableStateOf(false) }
    val sell: (Card) -> Unit = { card ->
        if (!selling) {
            selling = true
            scope.launch {
                // The flag is dropped before the note is shown, not after: `NoteHost.show` suspends
                // for as long as the line is on screen, and a button held disabled for those four
                // seconds would look like the refusal had also broken it.
                val outcome = try {
                    onIntent(Intent.SellCard(card.id))
                } finally {
                    selling = false
                }
                sellCardNote(strings, outcome)?.let { note.show(it) }
            }
        }
    }

    val grid: @Composable (Modifier) -> Unit = { modifier ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ThumbWidth + 4.dp),
            modifier = modifier.testTag(CARD_GRID_TEST_TAG),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                CardCell(
                    card = card,
                    copies = owned[card.id] ?: 0,
                    isSelected = selected?.id == card.id,
                    onClick = { selected = if (selected?.id == card.id) null else card },
                )
            }
        }
    }

    if (LocalWideLayout.current) {
        // The grid and the card side by side — `card_list.jpg`'s own arrangement, which the
        // original could take for granted on a 1024-wide stage. The detail is fixed-width and the
        // grid takes the rest, so widening the window adds columns rather than stretching a card.
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grid(Modifier.weight(1f).fillMaxHeight())
            CardDetail(selected, profile, sell, Modifier.width(DetailPaneWidth).fillMaxHeight())
        }
    } else {
        // Above the grid rather than beside it, and a fixed height whether or not anything is
        // selected, so the grid does not jump under the finger that just tapped it.
        CardDetail(selected, profile, sell)
        grid(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp))
    }
}

private fun sellCardNote(strings: Strings, outcome: IntentOutcome): String? = when (outcome) {
    // Silent on purpose, as in the bag: the copy badge and the purse in the app bar have both
    // already said it.
    IntentOutcome.APPLIED -> null
    IntentOutcome.REFUSED -> strings[StringKeys.NOTHING_HAPPENED]
    IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
}

@Composable
private fun CardCell(card: Card, copies: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(cardCellTestTag(card.id))
            .rowSurface(selected = isSelected)
            // Tapping a cell opens the card beside the grid, and tapping it again closes it — so
            // it is a toggle, and the selected state is what the detail pane is showing.
            .ttoClickable(selected = isSelected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Every card is tappable, owned or not: the original made unowned thumbs untouchable,
        // which meant the description of a card you were hunting for was the one thing you could
        // not read.
        //
        // The thumbnail and not a shrunk card face. A 104x128 face scaled to a third is a face
        // with unreadable digits on it; the thumbnail is art drawn for this size, and 263 of them
        // are three decoded sheets rather than 263 decoded images — see `UiArt`.
        CardThumb(
            card = card,
            size = ThumbWidth,
            modifier = if (copies > 0) Modifier else Modifier.alpha(UNOWNED_ALPHA),
        )

        // A badge and not a second cell. The grid answers "what is there, and what do I have",
        // and two identical thumbnails answer it worse — the second one reads as a different card
        // until you look twice. Absent at one copy, because "x1" on 200 cells is noise.
        if (copies > 1) {
            Text(
                text = "$COPIES_PREFIX$copies",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .testTag(cardCopiesTestTag(card.id))
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(CopiesBadgeCorner),
                    )
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun CardDetail(
    card: Card?,
    profile: GameSave,
    onSell: (Card) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(DetailHeight),
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier.rowSurface().padding(8.dp),
        contentAlignment = if (card == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (card == null) {
            EmptyNote(strings[StringKeys.PICK_CARD], CARD_DETAIL_EMPTY_TEST_TAG)
        } else {
            Row(
                modifier = Modifier.testTag(CARD_DETAIL_TEST_TAG).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                CardFace(card = card, scale = DETAIL_SCALE)
                // **`fillMaxHeight` is what makes the rest of this work.** Without it the column
                // was as tall as its own contents, so `weight(1f)` on the description had no
                // remainder to take, the column overflowed the panel, and what fell off the bottom
                // was the Sell button — the one control the panel exists to offer. The description
                // was cut mid-sentence with nothing to say it could be scrolled.
                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpaceXs),
                ) {
                    Text(
                        text = strings[card.nameKey],
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cardFacts(strings, card),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The `ff8_` bundle has names for all 110 cards and descriptions for none, so
                    // this really is absent rather than merely untranslated — leaving the key on
                    // screen would read as a defect in the port.
                    val description = "${card.nameKey}_DESC"
                    if (strings.has(description)) {
                        Text(
                            // Quoted speech, with emphasis and the odd line break — the prose most
                            // likely to carry markup. See [markup].
                            text = markup(strings[description]),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                            // `bodySmall` and not `labelSmall`: this is the only prose in the game
                            // and it was being drawn at the size the app uses for a stack count.
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    SellButton(card, profile, onSell)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SellButton(card: Card, profile: GameSave, onSell: (Card) -> Unit) {
    val strings = LocalStrings.current
    if (profile.spareCopiesOf(card.id) < 1) return

    // Compact, and not the full-width [WideButton] this used to be. A 56 dp bar across a 184 dp
    // panel is the loudest thing on a screen whose subject is the card beside it, and every dp it
    // spans is a dp the description does not get. Selling is an occasional action on a duplicate,
    // not the reason anybody opened the collection.
    FilledTonalButton(
        onClick = { onSell(card) },
        modifier = Modifier.testTag(CARD_SELL_TEST_TAG).align(Alignment.End),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceSm),
    ) {
        Text(
            text = "${strings[StringKeys.SELL]} ${CardValue.resaleOf(
                card.id,
                mapOf(card.id to card),
            )}",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CardFilters(
    sets: List<Int>,
    types: List<CardType>,
    set: Int?,
    type: CardType?,
    onSet: (Int?) -> Unit,
    onType: (CardType?) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(CARD_FILTERS_TEST_TAG).fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A set row only when there is a choice to make. One set admitted is not a filter, it is a
        // row of one chip that does nothing.
        if (sets.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
                TtoFilterChip(
                    label = strings[StringKeys.ALL],
                    tag = setFilterTestTag(null),
                    selected = set == null,
                ) { onSet(null) }
                for (block in sets) {
                    TtoFilterChip(
                        label = "$BLOCK_PREFIX$block",
                        tag = setFilterTestTag(block),
                        selected = set == block,
                        onClick = { onSet(block.takeIf { it != set }) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TtoFilterChip(
                label = strings[StringKeys.ALL],
                tag = typeFilterTestTag(null),
                selected = type == null,
            ) { onType(null) }
            for (candidate in types) {
                TypeChip(candidate, isOn = type == candidate) {
                    onType(candidate.takeIf { it != type })
                }
            }
        }
    }
}

@Composable
private fun TypeChip(type: CardType, isOn: Boolean, onClick: () -> Unit) {
    val icon = LocalCardArt.current?.typeIcon(type)

    Box(
        modifier = Modifier
            .testTag(typeFilterTestTag(type))
            .rowSurface(selected = isOn)
            .ttoClickable(role = Role.Checkbox, selected = isOn, onClick = onClick)
            .padding(horizontal = SpaceSm, vertical = SpaceXs),
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
                contentDescription = type.name,
                modifier = Modifier.size(TypeChipSize).alpha(if (isOn) 1f else MUTED),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private fun cardFacts(strings: Strings, card: Card): String = listOf(
    "${strings[StringKeys.SIDES]} " + listOf(card.top, card.right, card.bottom, card.left)
        .joinToString(" ", transform = ::powerLabel),
    "${strings[StringKeys.RARITY]} ${"★".repeat(card.rarity)}",
).joinToString(DOT_SEPARATOR)

private val ThumbWidth = 40.dp

private const val DETAIL_SCALE = 1f

private val DetailHeight = 196.dp

private val DetailPaneWidth = 260.dp

private const val UNOWNED_ALPHA = 0.28f

private const val BLOCK_PREFIX = "Set "
private val TypeChipSize = 16.dp

private const val COPIES_PREFIX = "\u00d7"
private val CopiesBadgeCorner = 3.dp
