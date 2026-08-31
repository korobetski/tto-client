package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tripletriad.data.AuctionRules
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AuctionDuration
import com.tripletriad.protocol.AuctionPolicy
import kotlinx.coroutines.launch

const val AUCTION_SELL_TEST_TAG: String = "auction-sell"

const val AUCTION_SELL_EMPTY_TEST_TAG: String = "auction-sell-empty"

const val AUCTION_START_FIELD_TEST_TAG: String = "auction-start-field"

const val AUCTION_RESERVE_FIELD_TEST_TAG: String = "auction-reserve-field"

const val AUCTION_LIST_TEST_TAG: String = "auction-list"

fun auctionSellCardTestTag(cardId: Int): String = "auction-sell-card-$cardId"

fun auctionDurationTestTag(duration: AuctionDuration): String =
    "auction-duration-${duration.name.lowercase()}"

/**
 * The consignment desk: pick a card, name two prices, say how long.
 *
 * ### Only spare copies are offered
 *
 * `GameSave.spareCopiesOf` and not `copiesOf`, which is the same rule the collection's Sell button
 * follows: a card a saved deck is built on is not spare, and listing it would leave the player
 * with a deck they cannot field — discovered later, from the deck screen, with no way back. The
 * card is *held* by the server the moment the lot opens, so this is not advice.
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
    openLots: Int,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    // Recomputed only when the collection moves. A listing takes a copy out of it, so the row a
    // seller just listed from disappears on the next profile — which is the correct behaviour and
    // the reason this is keyed on the map rather than remembered once.
    // Card *ids*, not `ownedCardIds()`, which is one entry per copy — a shelf built from that
    // offers the same card twice to anybody who owns two of it, and `LazyRow`'s key would collide
    // on the second. One row per card; how many are spare is what the row is filtered on.
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
    val chosen = sellable.firstOrNull { it.id == chosenId } ?: sellable.first()
    val floor = AuctionRules.floorPriceOf(chosen.id, cards)
    val ceiling = AuctionRules.ceilingPriceOf(chosen.id, cards, AuctionPolicy())

    // Seeded from the floor and re-seeded when the card changes: the floor is the only number the
    // house can state for the seller, and an empty field would make the first action on this
    // screen a guess at what is allowed.
    var start by remember(chosen.id) { mutableStateOf("$floor") }
    var reserve by remember(chosen.id) { mutableStateOf("$floor") }
    var duration by remember { mutableStateOf(AuctionDuration.MEDIUM) }

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
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(SellShelfHeight),
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            items(sellable, key = { it.id }) { card ->
                Column(
                    modifier = Modifier
                        .testTag(auctionSellCardTestTag(card.id))
                        .rowSurface(selected = card.id == chosen.id)
                        .ttoClickable(
                            selected = card.id == chosen.id,
                            onClick = { chosenId = card.id },
                        )
                        .padding(SpaceXs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CardFace(card = card, scale = SHELF_CARD_SCALE)
                }
            }
        }

        AmountField(
            value = start,
            onValueChange = { start = it },
            label = strings[StringKeys.AUCTION_START_PRICE],
            tag = AUCTION_START_FIELD_TEST_TAG,
            supporting = strings.format(StringKeys.AUCTION_FLOOR_HINT, "$floor"),
            isError = start.digits < floor,
        )

        AmountField(
            value = reserve,
            onValueChange = { reserve = it },
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

private const val SHELF_CARD_SCALE = 0.6f

private val SellShelfHeight = 92.dp
