package com.tripletriad.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.CardValue
import com.tripletriad.data.Format
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.powerLabel
import kotlinx.coroutines.launch

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

/** The narrow layout's detail sheet. There is no such node in the wide one — see the panel. */
const val CARD_SHEET_TEST_TAG: String = "card-sheet"

const val CARD_SELL_TEST_TAG: String = "card-sell"

const val CARD_OWNED_FILTER_TEST_TAG: String = "card-filter-owned"

fun cardCellTestTag(cardId: Int): String = "card-cell-$cardId"

fun cardCopiesTestTag(cardId: Int): String = "card-copies-$cardId"

@OptIn(ExperimentalMaterial3Api::class)
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
    var ownedOnly by remember(format) { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState()

    // Set, element and rarity, asked the way the auction's consignment picker asks them — see
    // [CardFilters]. What stays here is what only this room admits: a secret card nobody owns,
    // and the owned-only toggle beside the count.
    val filters = rememberCardFilters(admitted, catalog.sets)
    val cards = remember(admitted, filters.set, filters.type, filters.rarity, ownedOnly, owned) {
        admitted.filter {
            (it.id !in SECRET_CARD_IDS || owned.containsKey(it.id)) &&
                (!ownedOnly || owned.containsKey(it.id)) &&
                filters.matches(it)
        }
    }

    // The count and the toggle that changes it, on one line. "Owned · 33 / 263" and "show only
    // what I own" are the same sentence twice, so the control belongs beside the fact rather
    // than at the end of the elements, where it was the chip the narrow layout cut in half.
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Counted over what is **on screen**, so the line answers the question the grid is
            // currently asking. Filtered to fire, "Owned · 3 / 21" is a fact about fire cards;
            // the unfiltered total is the same sentence with no filter applied.
            text = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
                "${cards.count { owned.containsKey(it.id) }} / ${cards.size}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(CARD_TOTAL_TEST_TAG).weight(1f),
        )

        TtoFilterChip(
            label = strings[StringKeys.OWNED],
            tag = CARD_OWNED_FILTER_TEST_TAG,
            selected = ownedOnly,
        ) { ownedOnly = !ownedOnly }
    }

    CardFilterChips(filters)

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
        CardGrid(cards = cards, tag = CARD_GRID_TEST_TAG, modifier = modifier) { card ->
            CardCell(
                card = card,
                copies = owned[card.id] ?: 0,
                selected = selected?.id == card.id,
                modifier = Modifier.testTag(cardCellTestTag(card.id)),
                copiesTag = cardCopiesTestTag(card.id),
                onClick = { selected = if (selected?.id == card.id) null else card },
            )
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
        // The grid keeps the screen and the card arrives over it. The panel used to sit above the
        // grid at a fixed height whether or not anything was selected — a third of a phone screen
        // spent on the words "pick a card", and five rows of cards left under it. A sheet costs
        // nothing when nothing is picked, and covering the grid is fair once something is: the
        // player has stopped browsing and is reading one card.
        grid(Modifier.fillMaxWidth().weight(1f))

        selected?.let { card ->
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                sheetState = sheet,
                modifier = Modifier.testTag(CARD_SHEET_TEST_TAG),
            ) {
                CardDetail(
                    card = card,
                    profile = profile,
                    onSell = sell,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DetailHeight)
                        .padding(horizontal = SpaceMd, vertical = SpaceSm),
                )
            }
        }
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
            text = strings[StringKeys.SELL],
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.width(SpaceXs))
        // The coin, so the number reads as money. "Sell 16" was sixteen of something the button
        // did not name — and the same coin is what the purse in the top bar shows, which is the
        // number this one is about to change. [PriceTag] is that pairing, drawn here in the
        // button's own colour: this is the one price in the app that is money coming *in*.
        PriceTag(
            price = CardValue.resaleOf(card.id, mapOf(card.id to card)),
            color = LocalContentColor.current,
            style = MaterialTheme.typography.labelLarge,
            coinSize = IconSm,
        )
    }
}

private fun cardFacts(strings: Strings, card: Card): String = listOf(
    "${strings[StringKeys.SIDES]} " + listOf(card.top, card.right, card.bottom, card.left)
        .joinToString(" ", transform = ::powerLabel),
    "${strings[StringKeys.RARITY]} ${starsOf(card.rarity)}",
).joinToString(DOT_SEPARATOR)

/**
 * Cards this list hides until the profile actually owns one — an easter egg stops being one the
 * moment it is readable off a menu nobody has to earn anything to see. Mooba (`0x086f`) is the one
 * shipped so far.
 *
 * Purely a fact about how *this screen* lists cards, not one the rest of the game needs: a match
 * replay never reads this set, `:core` does not know it exists, and a secret card plays, sells and
 * trades exactly like any other the moment it is in the profile's collection.
 */
private val SECRET_CARD_IDS = setOf(0x086f)

private const val DETAIL_SCALE = 1f

private val DetailHeight = 196.dp

private val DetailPaneWidth = 260.dp
