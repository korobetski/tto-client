package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.data.AuctionRules
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionPolicy
import com.tripletriad.protocol.AuctionStatus
import kotlinx.coroutines.launch

const val AUCTION_DESK_TEST_TAG: String = "auction-desk"

/** The card being bid on, drawn by the panel the collection reads a card in. */
const val AUCTION_DESK_CARD_TEST_TAG: String = "auction-desk-card"

const val AUCTION_BID_FIELD_TEST_TAG: String = "auction-bid-field"

const val AUCTION_BID_TEST_TAG: String = "auction-bid"

const val AUCTION_WITHDRAW_TEST_TAG: String = "auction-withdraw"

const val AUCTION_ACCEPT_TEST_TAG: String = "auction-accept"

const val AUCTION_DECLINE_TEST_TAG: String = "auction-decline"

const val AUCTION_REFUSAL_TEST_TAG: String = "auction-refusal"

const val AUCTION_WITHDRAW_LOCKED_TEST_TAG: String = "auction-withdraw-locked"

/**
 * The lectern: one lot, read in full, with the one thing this player can do about it.
 *
 * ### The card is drawn at full size, and that is the layout's first constraint
 *
 * The player is deciding what a card is worth. Every other surface in the app can afford to shrink
 * its picture; this one cannot, because the numbers around it are meaningless without it — a five
 * at every edge and a five in the corner are the same row in a list and different cards on a desk.
 * [CardPanel] is what draws it — the same block the collection reads a card in, sprite at scale 1
 * with the powers and the rarity written out beside it — and the desk is [DeskWidth] wide because
 * that is what fits it plus a margin.
 *
 * ### Why the client checks a bid it is about to send anyway
 *
 * `AuctionRules.validateBid` runs twice: here, to say *why* a button is grey, and on the server,
 * where the answer counts. This is not a trust boundary and does not pretend to be — see the
 * rules' own KDoc. The client's copy exists so that "you are already leading" is on screen before
 * the player taps, rather than a round trip after it.
 *
 * **The policy here is the default one, not the server's.** Nothing on the wire carries
 * [AuctionPolicy] to the client yet, so a server running a tuned ceiling will refuse a bid this
 * screen thought was fine. That is the right way round — the refusal is shown, nothing is lost,
 * and the alternative is a client that decides prices.
 */
@Composable
@Suppress("LongParameterList")
internal fun AuctionDesk(
    session: AuctionSession,
    lot: AuctionLot?,
    card: Card?,
    profile: GameSave,
    cards: Map<Int, Card>,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    if (lot == null) {
        Box(
            modifier = modifier.testTag(AUCTION_DESK_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            EmptyNote(strings[StringKeys.AUCTION_PICK_LOT], "$AUCTION_DESK_TEST_TAG-empty")
        }
        return
    }

    Column(
        modifier = modifier
            .testTag(AUCTION_DESK_TEST_TAG)
            .verticalScroll(rememberScrollState())
            .padding(SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        // The collection's own card panel, which is where a card is read in this app: the sprite
        // at full size, its name, its powers and its rarity in writing, and whatever the game has
        // to say about it. The lectern used to draw a face and a name and stop there — the two
        // facts a bidder is pricing the card on were the two it left out.
        if (card == null) {
            // A card this client's catalogue does not have. The lot is still biddable and every
            // number below is still true, so the id is what it is called and nothing is drawn.
            Text(
                text = "#${lot.cardId}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        } else {
            CardPanel(
                card = card,
                tag = AUCTION_DESK_CARD_TEST_TAG,
                // The surface the collection's panel wears, for the same reason: this block and
                // the terms under it are two cards of a lectern that sits on the screen's own
                // background, and an unbacked one reads as loose text over the list beside it.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CardPanelHeight)
                    .rowSurface()
                    .padding(SpaceSm),
            )
        }

        DeskTerms(lot, now, session)

        session.refusal?.let { refusal ->
            Text(
                text = refusalText(strings, refusal),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(AUCTION_REFUSAL_TEST_TAG).fillMaxWidth(),
            )
        }
        session.failure?.let { trouble ->
            Text(
                text = trouble.message(strings),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("$AUCTION_REFUSAL_TEST_TAG-failure").fillMaxWidth(),
            )
        }

        if (lot.yours) {
            SellerActions(session, lot)
        } else {
            BidderActions(session, lot, profile, cards)
        }
    }
}

/** The numbers, in the order somebody deciding reads them. */
@Composable
private fun DeskTerms(lot: AuctionLot, now: Long, session: AuctionSession) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().rowSurface().padding(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        lot.sellerName?.let {
            TermRow(strings[StringKeys.AUCTION_SELLER], it)
        }
        TermRow(strings[StringKeys.AUCTION_START_PRICE], priceText(strings, lot.startPrice))
        TermRow(
            label = strings[StringKeys.AUCTION_CURRENT_PRICE],
            value = priceText(strings, lot.currentPrice),
            emphasis = true,
        )
        TermRow(strings[StringKeys.AUCTION_BID_COUNT], bidCountText(strings, lot))

        // The seller's own reserve is a number; everybody else gets the fact. Publishing it would
        // hand every bidder the exact figure to stop one MGP short of, which is the reserve doing
        // the opposite of its job — and the server only ever sends it to the seller anyway.
        lot.reservePrice
            ?.let { TermRow(strings[StringKeys.AUCTION_RESERVE], priceText(strings, it)) }
            ?: TermRow(
                label = strings[StringKeys.AUCTION_RESERVE],
                value = strings[
                    if (lot.reserveMet) {
                        StringKeys.AUCTION_RESERVE_MET
                    } else {
                        StringKeys.AUCTION_RESERVE_NOT_MET
                    },
                ],
            )

        lot.yourBid?.let {
            TermRow(strings[StringKeys.AUCTION_YOUR_BID], priceText(strings, it))
        }

        TermRow(
            label = strings[StringKeys.AUCTION_ENDS],
            value = statusText(strings, lot)
                ?: countdownText(strings, session.remaining(lot, now)),
            emphasis = true,
        )
    }

    if (lot.status.isOpen) {
        Text(
            text = strings.format(
                StringKeys.AUCTION_ANTI_SNIPE,
                "${AuctionPolicy().antiSnipeSeconds}",
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What a bidder may do.
 *
 * The amount is re-seeded whenever the standing bid moves, so a player who was outbid while the
 * desk was open finds the *new* minimum in the field rather than their own stale number — which
 * would otherwise be a bid guaranteed to be refused, typed by nobody.
 */
@Composable
private fun BidderActions(
    session: AuctionSession,
    lot: AuctionLot,
    profile: GameSave,
    cards: Map<Int, Card>,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    if (!lot.status.isOpen) {
        Text(
            text = statusText(strings, lot).orEmpty(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    var typed by remember(lot.id, lot.minimumBid) { mutableStateOf("${lot.minimumBid}") }
    val amount = typed.digits
    val refusal = AuctionRules.validateBid(
        amount = amount,
        startPrice = lot.startPrice,
        topBid = lot.topBid,
        cardId = lot.cardId,
        cards = cards,
        policy = AuctionPolicy(),
        purse = profile.mgp,
        isSeller = lot.yours,
        isTopBidder = lot.youLead,
    )

    AmountField(
        value = typed,
        onValueChange = { typed = it },
        label = strings[StringKeys.AUCTION_BID_AMOUNT],
        tag = AUCTION_BID_FIELD_TEST_TAG,
        supporting = strings.format(StringKeys.AUCTION_MINIMUM, "${lot.minimumBid}"),
        isError = refusal != null,
    )

    // The number that leaves the purse, on the line above the button that takes it. The bid alone
    // is what the auction is about; the total is what the player is actually agreeing to, and the
    // 3% is not a surprise anybody should meet on their next look at the purse.
    TermRow(
        label = strings.format(
            StringKeys.AUCTION_BUYER_FEE,
            "${AuctionRules.buyerFee(amount)}",
        ),
        value = priceText(strings, AuctionRules.totalDue(amount)),
        emphasis = true,
    )

    WideButton(
        label = strings[StringKeys.AUCTION_BID],
        tag = AUCTION_BID_TEST_TAG,
        // Busy is half the guard against a double tap and the other half is the operation id —
        // see `AuctionSession`. Neither alone is enough: this one stops the second press, that
        // one stops the second delivery.
        enabled = !session.isBusy && refusal == null,
        onClick = { scope.launch { session.bid(lot.id, amount) } },
    )

    refusal?.let {
        Text(
            text = refusalText(strings, it),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("$AUCTION_BID_TEST_TAG-why"),
        )
    }
}

/** What a seller may do: withdraw an untouched lot, or answer for one that fell short. */
@Composable
private fun SellerActions(session: AuctionSession, lot: AuctionLot) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    when {
        lot.status == AuctionStatus.AWAITING_SELLER -> {
            Text(
                text = strings.format(
                    StringKeys.AUCTION_DECIDE,
                    "${lot.topBid ?: lot.currentPrice}",
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                RowButton(
                    label = strings[StringKeys.AUCTION_ACCEPT],
                    tag = AUCTION_ACCEPT_TEST_TAG,
                    enabled = !session.isBusy,
                    onClick = { scope.launch { session.decide(lot.id, accept = true) } },
                )
                RowButton(
                    label = strings[StringKeys.AUCTION_DECLINE],
                    tag = AUCTION_DECLINE_TEST_TAG,
                    enabled = !session.isBusy,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { scope.launch { session.decide(lot.id, accept = false) } },
                )
            }
        }

        lot.isWithdrawable -> WideButton(
            label = strings[StringKeys.AUCTION_WITHDRAW],
            tag = AUCTION_WITHDRAW_TEST_TAG,
            enabled = !session.isBusy,
            filled = false,
            onClick = { scope.launch { session.withdraw(lot.id) } },
        )

        // Stated rather than left as a missing button. A seller who wants out of a lot somebody
        // has bid on will look for the control, and the useful answer is the rule, not silence.
        lot.status.isOpen -> Text(
            text = strings[StringKeys.AUCTION_WITHDRAW_LOCKED],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag(AUCTION_WITHDRAW_LOCKED_TEST_TAG),
        )

        else -> Text(
            text = strings[StringKeys.AUCTION_SETTLED_NOTE],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Wide enough for a full-size `CardFace` and the terms beside nothing. See the desk's KDoc. */
internal val DeskWidth = 300.dp
