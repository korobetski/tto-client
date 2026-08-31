package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.ui.theme.LocalTtoColors

const val AUCTION_BOARD_TEST_TAG: String = "auction-board"

const val AUCTION_MINE_TEST_TAG: String = "auction-mine"

const val AUCTION_DESK_SHEET_TEST_TAG: String = "auction-desk-sheet"

fun auctionLotTestTag(lotId: String): String = "auction-lot-$lotId"

/**
 * The room: a column of lots, and the desk the selected one is read at.
 *
 * ### Why the desk is a pane on a wide screen and a sheet on a narrow one
 *
 * The same split, for the same reason, as the collection's card panel — see `CardListBody`. What
 * is different here is that the desk is not optional reading: it is where the money is committed,
 * and its card is drawn at full size because a player about to spend four figures on a card should
 * be looking at the card and not at a thumbnail of it. On a phone that means the desk takes the
 * screen, which is correct — at that point the player has stopped browsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun ColumnScope.AuctionBoardBody(
    session: AuctionSession,
    lots: List<AuctionLot>,
    state: ListState,
    profile: GameSave,
    cards: Map<Int, Card>,
    now: Long,
    tag: String,
    emptyText: String,
    onRefresh: () -> Unit,
) {
    val strings = LocalStrings.current
    val sheet = rememberModalBottomSheetState()
    val selected = session.selected?.takeIf { chosen -> lots.any { it.id == chosen.id } }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        when {
            state == ListState.LOADING && lots.isEmpty() -> LoadingNote("$tag-loading")

            state == ListState.FAILED && lots.isEmpty() ->
                FailedNote(strings[StringKeys.AUCTION_FAILED], "$tag-failed", onRefresh)

            lots.isEmpty() -> EmptyNote(emptyText, "$tag-empty")

            else -> LazyColumn(
                modifier = modifier.testTag(tag),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(lots, key = { it.id }) { lot ->
                    AuctionLotRow(
                        lot = lot,
                        card = cards[lot.cardId],
                        now = session.remaining(lot, now),
                        selected = selected?.id == lot.id,
                        onClick = { session.select(lot.id) },
                    )
                }
            }
        }
    }

    if (LocalWideLayout.current) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            list(Modifier.weight(1f).fillMaxHeight())
            AuctionDesk(
                session = session,
                lot = selected,
                card = selected?.let { cards[it.cardId] },
                profile = profile,
                cards = cards,
                now = now,
                modifier = Modifier.width(DeskWidth).fillMaxHeight(),
            )
        }
    } else {
        list(Modifier.fillMaxWidth().weight(1f))

        selected?.let { lot ->
            ModalBottomSheet(
                onDismissRequest = { session.select(null) },
                sheetState = sheet,
                modifier = Modifier.testTag(AUCTION_DESK_SHEET_TEST_TAG),
            ) {
                AuctionDesk(
                    session = session,
                    lot = lot,
                    card = cards[lot.cardId],
                    profile = profile,
                    cards = cards,
                    now = now,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpaceMd, vertical = SpaceSm),
                )
            }
        }
    }
}

/**
 * One lot, at a glance.
 *
 * Three lines and no more: what it is, what it costs now, and how long is left. Everything else a
 * bidder needs — the reserve, the seller, the bid count — is a reason to open the desk, and a row
 * that answered all of it would be a desk that nobody opens and a list nobody can scan.
 */
@Composable
private fun AuctionLotRow(
    lot: AuctionLot,
    card: Card?,
    now: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalTtoColors.current

    Row(
        modifier = Modifier
            .testTag(auctionLotTestTag(lot.id))
            .fillMaxWidth()
            .rowSurface(selected = selected)
            .ttoClickable(selected = selected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        card?.let { CardFace(card = it, scale = ROW_CARD_SCALE) }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = card?.let { strings[it.nameKey] } ?: "#${lot.cardId}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Icon(
                    imageVector = TtoIcons.Chip,
                    contentDescription = strings[StringKeys.MGP],
                    tint = colors.currency,
                    modifier = Modifier.size(IconSm),
                )
                Text(
                    text = "${lot.currentPrice}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = bidCountText(strings, lot),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = statusText(strings, lot) ?: strings.format(
                    StringKeys.AUCTION_ENDS_IN,
                    countdownText(strings, now),
                ),
                // Red for the last stretch, and only for it: a colour every row wears is a
                // colour that says nothing. `AuctionRules.extendedEnd` uses the same window, so
                // this is also exactly the period in which a bid moves the deadline.
                color = if (lot.status.isOpen && now <= URGENT_MILLIS) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }

        AuctionBadge(lot)
    }
}

/** The one word this row is about, when there is one: whose lot it is, or who is winning it. */
@Composable
private fun AuctionBadge(lot: AuctionLot) {
    val strings = LocalStrings.current
    val label = when {
        lot.yours -> strings[StringKeys.AUCTION_YOUR_LOT]
        lot.youLead -> strings[StringKeys.AUCTION_YOU_LEAD]
        lot.yourBid != null -> strings[StringKeys.AUCTION_OUTBID]
        else -> return
    }

    Box(
        modifier = Modifier.width(BadgeWidth),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = label,
            color = when {
                lot.youLead -> LocalTtoColors.current.currency
                lot.yours -> MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED)
                else -> MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

internal fun bidCountText(strings: Strings, lot: AuctionLot): String = if (lot.bidCount == 0) {
    strings[StringKeys.AUCTION_NO_BIDS]
} else {
    strings.format(StringKeys.AUCTION_BIDS, "${lot.bidCount}")
}

/** The window in which the row turns red — [com.tripletriad.data.AuctionRules.extendedEnd]'s. */
private const val URGENT_MILLIS = 120_000L

private const val ROW_CARD_SCALE = 0.42f

private val BadgeWidth = 72.dp
