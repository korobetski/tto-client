package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.CardValue
import com.tripletriad.data.Format
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.settings.UnownedCards
import kotlinx.coroutines.launch

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

/** The narrow layout's detail sheet. There is no such node in the wide one — see the panel. */
const val CARD_SHEET_TEST_TAG: String = "card-sheet"

const val CARD_SELL_TEST_TAG: String = "card-sell"

const val CARD_ANY_FILTER_TEST_TAG: String = "card-filter-any"

const val CARD_OWNED_FILTER_TEST_TAG: String = "card-filter-owned"

const val CARD_MISSING_FILTER_TEST_TAG: String = "card-filter-missing"

const val CARD_DUPLICATES_FILTER_TEST_TAG: String = "card-filter-duplicates"

/** Owned, kept by a deck, free to sell — over the Sell button, on any card the player holds. */
const val CARD_COPIES_LINE_TEST_TAG: String = "card-copies-line"

const val CARD_SELL_FEWER_TEST_TAG: String = "card-sell-fewer"
const val CARD_SELL_MORE_TEST_TAG: String = "card-sell-more"

/** How many copies the Sell button will sell. Drawn only when there is more than one to choose. */
const val CARD_SELL_COUNT_TEST_TAG: String = "card-sell-count"

const val CARD_NO_MATCH_TEST_TAG: String = "card-no-match"

/**
 * The player's [UnownedCards], which `App` provides from the settings file.
 *
 * `static` for the reason `LocalCaptureHints` is: the settings sheet is the only thing that moves
 * it, and nothing gains from tracking reads of it. The default is the shipped mode, so a preview
 * or a test that mounts the collection without `App` draws the "?" the player would.
 */
val LocalUnownedCards = staticCompositionLocalOf { UnownedCards.Default }

/**
 * Whether the grid is showing the collection, what is in it, what is not, or what is in it twice.
 *
 * Three states and not two booleans: "owned" and "missing" are answers to one question, and holding
 * them apart would admit a fourth state — both on — that means an empty grid for no reason the
 * player could see. [MISSING] is the half that was absent, and it is the one a collection is read
 * for once it is mostly full: 585 tiles with 30 gaps in them is not a list of what is left to find.
 *
 * All of them are on screen together. Drawn as two chips, [ANY] had no control of its own — it was
 * whatever was left when neither of the other two was lit, which is a state a player reaches by
 * undoing rather than by choosing.
 */
private enum class Held(val tag: String, val labelKey: String) {
    ANY(CARD_ANY_FILTER_TEST_TAG, StringKeys.ALL),
    OWNED(CARD_OWNED_FILTER_TEST_TAG, StringKeys.OWNED),
    MISSING(CARD_MISSING_FILTER_TEST_TAG, StringKeys.MISSING),

    /**
     * Held more than once — what a player opens the list for before selling. Counted on copies and
     * not on `spareCopiesOf`: a second copy a deck keeps is still a duplicate, and hiding it would
     * make the filter disagree with the badge on its cell.
     */
    DUPLICATES(CARD_DUPLICATES_FILTER_TEST_TAG, StringKeys.DUPLICATES),
    ;

    fun admits(copies: Int): Boolean = when (this) {
        ANY -> true
        OWNED -> copies > 0
        MISSING -> copies <= 0
        DUPLICATES -> copies > 1
    }
}

fun cardCellTestTag(cardId: Int): String = "card-cell-$cardId"

fun cardCopiesTestTag(cardId: Int): String = "card-copies-$cardId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.CardListBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    opponents: NpcCatalog?,
    onIntent: suspend (Intent) -> IntentOutcome,
    note: NoteHost,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val admitted = remember(catalog, format) { catalog.admittedBy(format) }
    val owned = profile.cards
    val unowned = LocalUnownedCards.current
    var selected by remember(format) { mutableStateOf<Card?>(null) }
    var held by remember(format) { mutableStateOf(Held.ANY) }
    // Ticking cards for one sale. A mode rather than a long press, because a long press is a
    // gesture nothing on screen admits to, and the desktop has no such gesture to begin with.
    var picking by remember(format) { mutableStateOf(false) }
    var picked by remember(format) { mutableStateOf(emptySet<Int>()) }
    val sheet = rememberModalBottomSheetState()

    // Set, element, rarity, name and order, asked the way the auction's consignment picker asks
    // the first three — see [CardFilters]. What stays here is what only this room admits: a secret
    // card nobody owns, and the All / Owned / Missing / Duplicates segments beside the menus.
    val filters = rememberCardFilters(admitted, catalog.sets, opponents)
    // With the cards not owned left out, "Missing" is an empty grid by definition and "Owned" is
    // what "All" already shows — so the segments are not drawn, and a lit one stops counting.
    val shownHeld = if (unowned == UnownedCards.HIDDEN) Held.ANY else held
    val answering = remember(
        admitted,
        filters.pickedSets,
        filters.pickedTypes,
        filters.pickedRarities,
        filters.pickedSources,
        filters.minimums,
        filters.query,
        filters.sort,
        filters.reversed,
        shownHeld,
        owned,
        unowned,
    ) {
        // A card drawn as "?" is neither found by what it is hiding nor ranked by it — see
        // [CardFilters.matches] and [CardSort.comparator].
        val known = { card: Card -> unowned != UnownedCards.UNKNOWN || (owned[card.id] ?: 0) > 0 }
        filters.sorted(
            admitted.filter {
                (it.id !in SECRET_CARD_IDS || owned.containsKey(it.id)) &&
                    shownHeld.admits(owned[it.id] ?: 0) &&
                    filters.matches(it, known(it))
            },
            known,
        )
    }
    // Out of the grid and not out of the count: "Owned · 5 / 585" is still the collection's
    // progress, and a player who hid the rest asked for less to scroll past, not for that.
    val cards = remember(answering, unowned) {
        if (unowned != UnownedCards.HIDDEN) {
            answering
        } else {
            answering.filter { (owned[it.id] ?: 0) > 0 }
        }
    }

    // The count rides beside the search field rather than on a line of its own, because it is a
    // fact about what the field and the filters have narrowed the list to: counted over what is
    // **on screen**, so filtered to fire it reads "Owned · 3 / 21", a fact about fire cards.
    val searchRow: @Composable (Modifier) -> Unit = { modifier ->
        CardSearchRow(
            filters = filters,
            count = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
                "${answering.count { owned.containsKey(it.id) }} / ${answering.size}",
            modifier = modifier,
        )
    }
    val segments: @Composable () -> Unit = {
        if (unowned != UnownedCards.HIDDEN) HeldSegments(held) { held = it }
        SelectToggle(picking) {
            picking = !picking
            picked = emptySet()
            selected = null
        }
    }
    // Two bands of controls where there were five — see [CardFilterMenus].
    val controls: @Composable () -> Unit = {
        searchRow(Modifier)
        CardFilterMenus(filters, segments)
    }

    // Selling takes the copy out of the collection and pays for it. Asked rather than computed:
    // a card's worth is its **rarity**, and on an account it is the server's card table that says
    // so — a client that worked the price out itself could work out a better one.
    //
    // One at a time, and answered. Both for the reasons the bag's buttons are — see the `busy` flag
    // in [InventoryBody]: two taps were two sales of two copies, and `perform` answered `Unit`, so
    // a sale the server declined took the tap and said nothing at all.
    //
    // Several copies are several `SellCard` intents, one after the other, and the first that is not
    // applied ends the run: the protocol sells one copy per request, and a refusal halfway says the
    // count the stepper offered is no longer true.
    //
    // A bulk sale is the same run over several cards, and stops at the same first refusal.
    var selling by remember(format) { mutableStateOf(false) }
    val sellAll: (List<Pair<Card, Int>>) -> Unit = { run ->
        if (!selling) {
            selling = true
            scope.launch {
                // The flag is dropped before the note is shown, not after: `NoteHost.show` suspends
                // for as long as the line is on screen, and a button held disabled for those four
                // seconds would look like the refusal had also broken it.
                val outcome = try {
                    var last = IntentOutcome.APPLIED
                    for ((card, count) in run) {
                        repeat(count) {
                            if (last == IntentOutcome.APPLIED) {
                                last = onIntent(Intent.SellCard(card.id))
                            }
                        }
                    }
                    last
                } finally {
                    selling = false
                }
                sellCardNote(strings, outcome)?.let { note.show(it) }
            }
        }
    }
    val sell: (Card, Int) -> Unit = { card, count -> sellAll(listOf(card to count)) }
    // Re-read against the profile on every change, so a ticked card a deck has since claimed
    // drops out of the count instead of being sold from under the deck.
    val sale = remember(picked, profile) {
        picked.sorted().mapNotNull { id ->
            val count = profile.bulkSaleOf(id)
            catalog.byId[id]?.takeIf { count > 0 }?.let { it to count }
        }
    }

    val grid: @Composable (Modifier) -> Unit = { modifier ->
        // Said rather than left blank. Every way this list empties is now something the player did
        // — a name that matches nothing, a set filtered to an element it has none of, "missing" on
        // a tribe that is complete — and an empty grid under a row of controls looks like a
        // screen that failed to load. One sentence covers all of them because they are all the
        // same fact: nothing here answers to what was asked.
        if (cards.isEmpty()) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                EmptyNote(strings[StringKeys.NO_CARD_MATCH], CARD_NO_MATCH_TEST_TAG)
            }
        } else {
            CardGrid(cards = cards, tag = CARD_GRID_TEST_TAG, modifier = modifier) { card ->
                val copies = owned[card.id] ?: 0
                val tickable = picking && profile.bulkSaleOf(card.id) > 0
                Box {
                    CardCell(
                        card = card,
                        copies = copies,
                        selected = if (picking) card.id in picked else selected?.id == card.id,
                        modifier = Modifier.testTag(cardCellTestTag(card.id)),
                        copiesTag = cardCopiesTestTag(card.id),
                        unknown = copies < 1 && unowned == UnownedCards.UNKNOWN,
                        copiesLine = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR$copies",
                        onClick = {
                            when {
                                !picking -> selected = if (selected?.id == card.id) null else card
                                tickable -> picked = picked.toggled(card.id)
                            }
                        },
                    )
                    if (tickable) {
                        PickMark(
                            picked = card.id in picked,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .testTag(cardPickTestTag(card.id)),
                        )
                    }
                }
            }
        }
    }

    // The grid and the card side by side — `card_list.jpg`'s own arrangement, which the original
    // could take for granted on a 1024-wide stage. The detail is fixed-width and the grid takes the
    // rest, so widening the window adds columns rather than stretching a card.
    val panes: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grid(Modifier.weight(1f).fillMaxHeight())
            CardDetail(
                card = selected,
                profile = profile,
                opponents = opponents,
                onSell = sell,
                modifier = Modifier.width(DetailPaneWidth).fillMaxHeight(),
            )
        }
    }

    if (LocalWideLayout.current) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (maxWidth >= FilterPanelMinWidth) {
                // Wide enough for the filters to stay open down the left — see [CardFilterPanel].
                // The search and the segments stay over the grid: they are about what it shows,
                // and the panel would wrap three segments onto a line each.
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardFilterPanel(filters, Modifier.width(FilterPanelWidth).fillMaxHeight())
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                        ) {
                            searchRow(Modifier.weight(1f))
                            segments()
                        }
                        panes(Modifier.weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    controls()
                    panes(Modifier.weight(1f))
                }
            }
        }
    } else {
        controls()

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
                    opponents = opponents,
                    onSell = sell,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CardPanelHeight)
                        .padding(horizontal = SpaceMd, vertical = SpaceSm),
                )
            }
        }
    }

    if (picking) {
        BulkSellBar(
            sale = sale,
            busy = selling,
            onCancel = {
                picking = false
                picked = emptySet()
            },
            onSell = {
                sellAll(sale)
                picking = false
                picked = emptySet()
            },
        )
    }
}

private fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id

/**
 * All / Owned / Missing, as one control with three positions.
 *
 * A segmented row rather than the pair of chips this replaces. Two chips could say "owned" and
 * "missing" but never "all": the third state was the absence of the other two, so the way back to
 * the whole collection was to notice which chip was lit and tap it again. Three segments say what
 * the grid is showing and offer the way out of it in the same object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeldSegments(held: Held, onPick: (Held) -> Unit) {
    val strings = LocalStrings.current

    SingleChoiceSegmentedButtonRow {
        Held.entries.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = held == candidate,
                onClick = { onPick(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index, Held.entries.size),
                modifier = Modifier.testTag(candidate.tag),
                label = {
                    Text(
                        text = strings[candidate.labelKey],
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
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
    opponents: NpcCatalog?,
    onSell: (Card, Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(CardPanelHeight),
) {
    val strings = LocalStrings.current
    val unowned = LocalUnownedCards.current
    // Recomputed only when the card or the roster does, which is once per selection: the walk is
    // over 158 opponents, 15 booster pools and two catalogues, and the panel recomposes on every
    // scroll frame behind it.
    val sources = remember(card, opponents) {
        card?.let { cardSources(it.id, opponents) }.orEmpty()
    }

    Box(
        modifier = modifier.rowSurface().padding(8.dp),
        contentAlignment = if (card == null) Alignment.Center else Alignment.TopStart,
    ) {
        when {
            card == null -> EmptyNote(strings[StringKeys.PICK_CARD], CARD_DETAIL_EMPTY_TEST_TAG)

            // The same landmark as an owned card's panel, so "a card is open" is one fact to a
            // test and to the sheet — but none of what the grid's "?" is keeping back. Only in
            // that mode: a dimmed card is one the player chose to be able to read.
            (profile.cards[card.id] ?: 0) < 1 && unowned == UnownedCards.UNKNOWN ->
                UnknownCardPanel(card = card, tag = CARD_DETAIL_TEST_TAG, sources = sources)

            // The panel the auction's lectern reads a card in too — see [CardPanel] for why the
            // sprite is at full size and why the height has to come from here.
            else -> CardPanel(card = card, tag = CARD_DETAIL_TEST_TAG, sources = sources) {
                SellControls(card, profile, onSell)
            }
        }
    }
}

/**
 * What the collection holds of this card and what may leave it, then the way out.
 *
 * The copies line is drawn on every card held, sellable or not: a card with nothing to sell used
 * to show no button and no reason, and "2 owned · 2 in a deck" is the reason. "In a deck" is
 * copies less spares, which is the reservation [GameSave.spareCopiesOf] makes — the most any one
 * deck lists, not the sum over decks.
 *
 * The stepper appears only when there are two spares or more, and its count is keyed on the card
 * and on the spare count, so a sale that shrinks the spares starts it over at one rather than
 * leaving it pointing past the end.
 */
@Composable
private fun ColumnScope.SellControls(card: Card, profile: GameSave, onSell: (Card, Int) -> Unit) {
    val strings = LocalStrings.current
    val copies = profile.copiesOf(card.id)
    val spare = profile.spareCopiesOf(card.id)
    if (copies < 1) return

    Text(
        text = strings.format(StringKeys.CARD_COPIES, "$copies", "${copies - spare}", "$spare"),
        modifier = Modifier.testTag(CARD_COPIES_LINE_TEST_TAG),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (spare < 1) return

    var count by remember(card.id, spare) { mutableStateOf(1) }
    if (spare > 1) {
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                glyph = "−",
                tag = CARD_SELL_FEWER_TEST_TAG,
                description = strings[StringKeys.SELL_FEWER],
                enabled = count > 1,
            ) { count -= 1 }
            Text(
                text = "$count / $spare",
                modifier = Modifier.testTag(CARD_SELL_COUNT_TEST_TAG),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
            StepButton(
                glyph = "+",
                tag = CARD_SELL_MORE_TEST_TAG,
                description = strings[StringKeys.SELL_MORE],
                enabled = count < spare,
            ) { count += 1 }
        }
    }

    // Compact, and not the full-width [WideButton] this used to be. A 56 dp bar across a 184 dp
    // panel is the loudest thing on a screen whose subject is the card beside it, and every dp it
    // spans is a dp the description does not get. Selling is an occasional action on a duplicate,
    // not the reason anybody opened the collection.
    FilledTonalButton(
        onClick = { onSell(card, count) },
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
        // button's own colour: this is the one price in the app that is money coming *in*. The
        // total for the count, so the stepper's effect is read where the tap lands.
        PriceTag(
            price = CardValue.resaleOf(card.id, mapOf(card.id to card)) * count,
            color = LocalContentColor.current,
            style = MaterialTheme.typography.labelLarge,
            coinSize = IconSm,
        )
    }
}

/**
 * Cards this list hides until the profile actually owns one — an easter egg stops being one the
 * moment it is readable off a menu nobody has to earn anything to see. Mooba (`0x086f`) and
 * FFVIII's Gilgamesh (`0x0850`), whose only way in is the hidden Zantetsuken achievement — listing
 * the card would name the achievement in its sources.
 *
 * Purely a fact about how *this screen* lists cards, not one the rest of the game needs: a match
 * replay never reads this set, `:core` does not know it exists, and a secret card plays, sells and
 * trades exactly like any other the moment it is in the profile's collection.
 */
internal val SECRET_CARD_IDS = setOf(0x086f, 0x0850)

private val DetailPaneWidth = 260.dp

private val FilterPanelWidth = 240.dp

/**
 * The body width at which the filters come out of their menus: the panel and the detail take
 * 524 dp between them, and below this the grid left over is narrower than the panel that
 * displaced it. A 1280-wide window clears it with the rail up; the 1024-wide one does not.
 */
private val FilterPanelMinWidth = 1000.dp
