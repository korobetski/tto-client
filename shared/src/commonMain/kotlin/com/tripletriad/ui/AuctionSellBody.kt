package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.tripletriad.data.AuctionRules
import com.tripletriad.data.CardSet
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AuctionDuration
import com.tripletriad.protocol.AuctionPolicy
import kotlinx.coroutines.launch

const val AUCTION_SELL_TEST_TAG: String = "auction-sell"

const val AUCTION_SELL_EMPTY_TEST_TAG: String = "auction-sell-empty"

/** The chosen card, which is also the control that opens the picker. */
const val AUCTION_SELL_PICK_TEST_TAG: String = "auction-sell-pick"

/** Leaving the picker without changing anything. */
const val AUCTION_SELL_BACK_TEST_TAG: String = "auction-sell-back"

const val AUCTION_SELL_GRID_TEST_TAG: String = "auction-sell-grid"

const val AUCTION_START_FIELD_TEST_TAG: String = "auction-start-field"

const val AUCTION_RESERVE_FIELD_TEST_TAG: String = "auction-reserve-field"

const val AUCTION_LIST_TEST_TAG: String = "auction-list"

fun auctionSellCardTestTag(cardId: Int): String = "auction-sell-card-$cardId"

fun auctionPriceTestTag(price: Int): String = "auction-price-$price"

fun auctionDurationTestTag(duration: AuctionDuration): String =
    "auction-duration-${duration.name.lowercase()}"

/**
 * The consignment desk: pick a card, name two prices, say how long.
 *
 * ### The card is chosen on its own screen, not off a strip
 *
 * This used to be a `LazyRow` of every spare copy, sorted by card id, at 0.6 of a card face. That
 * is a control for a handful of cards, and the collection holds 565: finding the one you meant to
 * sell was a horizontal drag through several hundred unlabelled pictures, past cards whose names
 * were nowhere on screen. Now the desk shows the *one* card being consigned — its name, its
 * rarity, how many copies are spare — and changing it opens [SellPicker], which is the same grid,
 * the same cells and the same filters as the collection, in a screen with room for them.
 *
 * A card is still chosen for the seller on arrival, so the desk is never a form with a hole in it,
 * and the picker is one tap away rather than the toll on every listing.
 *
 * ### Only spare copies are offered
 *
 * `GameSave.spareCopiesOf` and not `copiesOf`, which is the same rule the collection's Sell button
 * follows: a card a saved deck is built on is not spare, and listing it would leave the player
 * with a deck they cannot field — discovered later, from the deck screen, with no way back. The
 * card is *held* by the server the moment the lot opens, so this is not advice.
 *
 * ### The reserve follows the starting price until the seller has an opinion about it
 *
 * Both start at the floor. Typing a starting price above the reserve used to grey the button out
 * and explain, correctly, that the lot would cancel itself at a price the seller had already
 * refused — a true sentence about a form the player had no reason to think was two numbers. So the
 * reserve tracks the start until it is typed into, and stops the moment it is: a seller who has
 * stated a reserve owns it, and one who has not is not asked to restate the number they just
 * entered. `AuctionRules.validateListing` still refuses the crossed pair, and `AuctionUiTest`
 * still proves it does.
 *
 * ### The fee is shown before the button, and it is not refundable
 *
 * 5% of the reserve, charged when the lot opens whatever happens afterwards. A seller who reads it
 * for the first time in their purse has been surprised by a charge, which is the one thing a
 * marketplace must never do — so the number is on screen, in MGP, and it moves as they type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.AuctionSellBody(
    session: AuctionSession,
    profile: GameSave,
    cards: Map<Int, Card>,
    sets: List<CardSet>,
    openLots: Int,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    // Recomputed only when the collection moves. A listing takes a copy out of it, so the cell a
    // seller just listed from disappears on the next profile — which is the correct behaviour and
    // the reason this is keyed on the map rather than remembered once.
    // Card *ids*, not `ownedCardIds()`, which is one entry per copy — a grid built from that
    // offers the same card twice to anybody who owns two of it, and the key would collide on the
    // second. One cell per card; how many are spare is what the cell is filtered on.
    val sellable = remember(profile.cards, cards) {
        profile.cards.keys
            .sorted()
            .filter { profile.spareCopiesOf(it) > 0 }
            .mapNotNull { cards[it] }
    }

    if (sellable.isEmpty()) {
        EmptyNote(strings[StringKeys.AUCTION_SELL_EMPTY], AUCTION_SELL_EMPTY_TEST_TAG)
        return
    }

    var chosenId by remember(sellable) { mutableStateOf(sellable.first().id) }
    var picking by remember(sellable) { mutableStateOf(false) }
    val chosen = sellable.firstOrNull { it.id == chosenId } ?: sellable.first()

    if (picking) {
        SellPicker(
            sellable = sellable,
            profile = profile,
            sets = sets,
            chosen = chosen,
            onPick = {
                chosenId = it
                picking = false
            },
            onBack = { picking = false },
        )
        return
    }

    val floor = AuctionRules.floorPriceOf(chosen.id, cards)
    val ceiling = AuctionRules.ceilingPriceOf(chosen.id, cards, AuctionPolicy())

    // Seeded from the floor and re-seeded when the card changes: the floor is the only number the
    // house can state for the seller, and an empty field would make the first action on this
    // screen a guess at what is allowed.
    var start by remember(chosen.id) { mutableStateOf("$floor") }
    var reserve by remember(chosen.id) { mutableStateOf("$floor") }
    var reserveOwned by remember(chosen.id) { mutableStateOf(false) }
    var duration by remember { mutableStateOf(AuctionDuration.MEDIUM) }

    val setStart: (String) -> Unit = { typed ->
        start = typed
        if (!reserveOwned) reserve = typed
    }

    val refusal = AuctionRules.validateListing(
        cardId = chosen.id,
        startPrice = start.digits,
        reservePrice = reserve.digits,
        cards = cards,
        policy = AuctionPolicy(),
        spareCopies = profile.spareCopiesOf(chosen.id),
        purse = profile.mgp,
        openLots = openLots,
    )

    Column(
        modifier = Modifier
            .testTag(AUCTION_SELL_TEST_TAG)
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        SectionHeader(strings[StringKeys.AUCTION_SELL_CARD])
        ChosenCard(
            card = chosen,
            spares = profile.spareCopiesOf(chosen.id),
            onChange = { picking = true },
        )

        AmountField(
            value = start,
            onValueChange = setStart,
            label = strings[StringKeys.AUCTION_START_PRICE],
            tag = AUCTION_START_FIELD_TEST_TAG,
            supporting = strings.format(StringKeys.AUCTION_FLOOR_HINT, "$floor"),
            isError = start.digits < floor,
        )

        // Four numbers a seller might actually mean, rather than four positions on a scale. Typing
        // is still there for everything in between — see [AmountField] on why this is not a slider.
        PriceRungs(rungs = priceRungs(floor, ceiling), chosen = start.digits, onPick = setStart)

        AmountField(
            value = reserve,
            onValueChange = {
                reserve = it
                reserveOwned = true
            },
            label = strings[StringKeys.AUCTION_RESERVE],
            tag = AUCTION_RESERVE_FIELD_TEST_TAG,
            supporting = strings.format(StringKeys.AUCTION_RESERVE_HINT, "$ceiling"),
            isError = reserve.digits < start.digits || reserve.digits > ceiling,
            imeAction = ImeAction.Done,
        )

        SectionHeader(strings[StringKeys.AUCTION_DURATION])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
            AuctionDuration.entries.forEach { candidate ->
                TtoFilterChip(
                    label = strings.format(StringKeys.AUCTION_HOURS, "${candidate.hours}"),
                    selected = candidate == duration,
                    tag = auctionDurationTestTag(candidate),
                    onClick = { duration = candidate },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().rowSurface().padding(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            TermRow(
                label = strings[StringKeys.AUCTION_LISTING_FEE],
                value = priceText(strings, AuctionRules.listingFee(reserve.digits)),
                emphasis = true,
            )
            Text(
                text = strings[StringKeys.AUCTION_LISTING_FEE_NOTE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
            )
            TermRow(
                label = strings[StringKeys.AUCTION_OPEN_LOTS],
                value = "$openLots / ${AuctionPolicy().maxOpenLots}",
            )
        }

        session.refusal?.let { refused ->
            Text(
                text = refusalText(strings, refused),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("$AUCTION_LIST_TEST_TAG-refusal"),
            )
        }
        session.failure?.let { trouble ->
            Text(
                text = trouble.message(strings),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("$AUCTION_LIST_TEST_TAG-failure"),
            )
        }

        WideButton(
            label = strings[StringKeys.AUCTION_LIST],
            tag = AUCTION_LIST_TEST_TAG,
            enabled = !session.isBusy && refusal == null,
            onClick = {
                scope.launch {
                    session.listCard(chosen.id, start.digits, reserve.digits, duration)
                }
            },
        )

        // Why the button is grey, under the button. The refusal names the field the seller would
        // fix first — see `AuctionRules.validateListing`, whose checks are in form order for
        // exactly this reason.
        refusal?.let {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = refusalText(strings, it),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("$AUCTION_LIST_TEST_TAG-why"),
                )
            }
        }
    }
}

/**
 * The card on the desk: what is about to be consigned, and the way to consign another.
 *
 * The whole row is the control, not the word at the end of it. A seller who wants a different card
 * reaches for the card, which is the biggest thing on the line; the label beside it is there to
 * say that reaching for it does something, for anyone who does not try.
 *
 * The card itself is [CardLine], the same row the board reads a lot in — picture, powers, element
 * — with the one fact this screen adds under it: how good it is, which is what the floor and the
 * ceiling of its price are computed from.
 */
@Composable
private fun ChosenCard(card: Card, spares: Int, onChange: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(AUCTION_SELL_PICK_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onChange)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        CardLine(
            card = card,
            name = strings[card.nameKey],
            modifier = Modifier.weight(1f),
            count = spares.takeIf { it > 1 },
        ) {
            Text(
                text = starsOf(card.rarity),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Text(
            text = strings[StringKeys.AUCTION_CHANGE_CARD],
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Every spare copy, as a grid with the collection's own filters over it.
 *
 * Not *like* the collection's grid — it is the same one. [rememberCardFilters], [CardFilterChips],
 * [CardGrid] and [CardCell] are what the card list is built from, because this is the same
 * question asked in a different room — *which of my cards* — and a player who has learned to find
 * a card once should not have to learn it again. What differs is the count in the corner: here it
 * is how many copies are **spare**, which is how many could be listed, rather than how many are
 * owned.
 *
 * The grid takes the screen rather than sharing it with the form. A bounded grid inside the desk's
 * own scroll would be two scrolling surfaces one inside the other, which on a phone is a drag that
 * moves whichever one guesses first.
 */
@Composable
private fun ColumnScope.SellPicker(
    sellable: List<Card>,
    profile: GameSave,
    sets: List<CardSet>,
    chosen: Card,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val filters = rememberCardFilters(sellable, sets)
    val shown = remember(sellable, filters.set, filters.type, filters.rarity) {
        sellable.filter(filters::matches)
    }

    Column(modifier = Modifier.testTag(AUCTION_SELL_TEST_TAG).fillMaxWidth().weight(1f)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(strings[StringKeys.AUCTION_SELL_CARD], Modifier.weight(1f))
            TextButton(onClick = onBack, modifier = Modifier.testTag(AUCTION_SELL_BACK_TEST_TAG)) {
                Text(text = strings[StringKeys.BACK], style = MaterialTheme.typography.labelLarge)
            }
        }

        CardFilterChips(filters)

        CardGrid(
            cards = shown,
            tag = AUCTION_SELL_GRID_TEST_TAG,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { card ->
            CardCell(
                card = card,
                copies = profile.spareCopiesOf(card.id),
                selected = card.id == chosen.id,
                modifier = Modifier.testTag(auctionSellCardTestTag(card.id)),
                onClick = { onPick(card.id) },
            )
        }
    }
}

/** The prices, as chips. Absent when there is nothing to choose between. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceRungs(rungs: List<Int>, chosen: Int, onPick: (String) -> Unit) {
    if (rungs.size < 2) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
        rungs.forEach { price ->
            TtoFilterChip(
                label = "$price",
                selected = price == chosen,
                tag = auctionPriceTestTag(price),
                onClick = { onPick("$price") },
            )
        }
    }
}

/**
 * The prices offered as chips: the shop price, twice it, five times it, and the house's ceiling.
 *
 * Multiples of the floor rather than fractions of the ceiling, because the floor is the number the
 * seller can check — it is what the counter next door pays — while the ceiling is an
 * anti-laundering limit with nothing to say about what a card is worth. Anything already past the
 * ceiling falls out, and a card whose ceiling *is* its floor offers one rung, which [PriceRungs]
 * draws as none.
 */
internal fun priceRungs(floor: Int, ceiling: Int): List<Int> =
    listOf(floor, floor * PRICE_DOUBLE, floor * PRICE_QUINTUPLE, ceiling)
        .filter { it in floor..ceiling }
        .distinct()
        .sorted()

private const val PRICE_DOUBLE = 2

private const val PRICE_QUINTUPLE = 5
