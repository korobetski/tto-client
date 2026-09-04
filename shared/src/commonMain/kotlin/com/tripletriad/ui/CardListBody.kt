package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.launch

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

/** The narrow layout's detail sheet. There is no such node in the wide one — see the panel. */
const val CARD_SHEET_TEST_TAG: String = "card-sheet"

const val CARD_SELL_TEST_TAG: String = "card-sell"

const val CARD_OWNED_FILTER_TEST_TAG: String = "card-filter-owned"

const val CARD_MISSING_FILTER_TEST_TAG: String = "card-filter-missing"

const val CARD_NO_MATCH_TEST_TAG: String = "card-no-match"

/**
 * Whether the grid is showing the collection, what is in it, or what is not.
 *
 * Three states and not two booleans: "owned" and "missing" are answers to one question, and holding
 * them apart would admit a fourth state — both on — that means an empty grid for no reason the
 * player could see. [MISSING] is the half that was absent, and it is the one a collection is read
 * for once it is mostly full: 564 tiles with 30 gaps in them is not a list of what is left to find.
 */
private enum class Held(val tag: String, val labelKey: String) {
    ANY("any", StringKeys.ALL),
    OWNED(CARD_OWNED_FILTER_TEST_TAG, StringKeys.OWNED),
    MISSING(CARD_MISSING_FILTER_TEST_TAG, StringKeys.MISSING),
    ;

    fun admits(copies: Int): Boolean = when (this) {
        ANY -> true
        OWNED -> copies > 0
        MISSING -> copies <= 0
    }

    /** The chip's own state: tapping the one that is on turns it off rather than doing nothing. */
    fun toggled(to: Held): Held = if (this == to) ANY else to
}

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
    var held by remember(format) { mutableStateOf(Held.ANY) }
    val sheet = rememberModalBottomSheetState()

    // Set, element, rarity, name and order, asked the way the auction's consignment picker asks
    // the first three — see [CardFilters]. What stays here is what only this room admits: a secret
    // card nobody owns, and the owned/missing pair beside the count.
    val filters = rememberCardFilters(admitted, catalog.sets)
    val cards = remember(
        admitted,
        filters.set,
        filters.type,
        filters.rarity,
        filters.query,
        filters.sort,
        held,
        owned,
    ) {
        filters.sorted(
            admitted.filter {
                (it.id !in SECRET_CARD_IDS || owned.containsKey(it.id)) &&
                    held.admits(owned[it.id] ?: 0) &&
                    filters.matches(it)
            },
        )
    }

    // The count and the toggles that change it, on one line. "Owned · 33 / 263" and "show only
    // what I own" are the same sentence twice, so the controls belong beside the fact rather
    // than at the end of the elements, where it was the chip the narrow layout cut in half.
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
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

        for (candidate in listOf(Held.OWNED, Held.MISSING)) {
            TtoFilterChip(
                label = strings[candidate.labelKey],
                tag = candidate.tag,
                selected = held == candidate,
            ) { held = held.toggled(candidate) }
        }
    }

    CardSearchRow(filters)
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
        // Said rather than left blank. Every way this list empties is now something the player did
        // — a name that matches nothing, a set filtered to an element it has none of, "missing" on
        // a tribe that is complete — and an empty grid under five rows of controls looks like a
        // screen that failed to load. One sentence covers all of them because they are all the
        // same fact: nothing here answers to what was asked.
        if (cards.isEmpty()) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                EmptyNote(strings[StringKeys.NO_CARD_MATCH], CARD_NO_MATCH_TEST_TAG)
            }
        } else {
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
                        .height(CardPanelHeight)
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
    modifier: Modifier = Modifier.fillMaxWidth().height(CardPanelHeight),
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier.rowSurface().padding(8.dp),
        contentAlignment = if (card == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (card == null) {
            EmptyNote(strings[StringKeys.PICK_CARD], CARD_DETAIL_EMPTY_TEST_TAG)
        } else {
            // The panel the auction's lectern reads a card in too — see [CardPanel] for why the
            // sprite is at full size and why the height has to come from here.
            CardPanel(card = card, tag = CARD_DETAIL_TEST_TAG) {
                SellButton(card, profile, onSell)
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

private val DetailPaneWidth = 260.dp
