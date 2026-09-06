package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AuctionLot

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
    searchable: Boolean = false,
) {
    val strings = LocalStrings.current
    val sheet = rememberModalBottomSheetState()

    // The room's two controls, and the state behind them. Held here rather than in
    // [AuctionSession] because they are how *this* list is read and not what the house was asked:
    // `refreshBoard` takes a card id and the search is by name, in the language on screen.
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AuctionSort.ENDING) }

    val missing: (AuctionLot) -> Boolean = { (profile.cards[it.cardId] ?: 0) == 0 }
    // Unconditionally remembered, `searchable` being one of the keys: a `remember` inside a
    // branch is a slot that moves when the branch does, which is the one way this could hold a
    // list belonging to another tab.
    val shown = remember(lots, query, sort, strings, profile.cards, searchable) {
        if (!searchable) {
            lots
        } else {
            roomLots(
                lots = lots,
                query = query,
                sort = sort,
                nameOf = { lot -> cards[lot.cardId]?.let { strings[it.nameKey] } ?: "" },
                missing = missing,
            )
        }
    }

    val selected = session.selected?.takeIf { chosen -> shown.any { it.id == chosen.id } }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier) {
            if (searchable) {
                AuctionRoomControls(
                    query = query,
                    onQuery = { query = it },
                    sort = sort,
                    onSort = { sort = it },
                )
            }

            when {
                state == ListState.LOADING && lots.isEmpty() -> LoadingNote("$tag-loading")

                state == ListState.FAILED && lots.isEmpty() ->
                    FailedNote(strings[StringKeys.AUCTION_FAILED], "$tag-failed", onRefresh)

                lots.isEmpty() -> EmptyNote(emptyText, "$tag-empty")

                // A room that has lots but shows none is a different fact from an empty house,
                // and it is one the player caused: the chips above are still lit, and this says
                // which of them to undo.
                shown.isEmpty() ->
                    EmptyNote(strings[StringKeys.AUCTION_NO_MATCH], AUCTION_NO_MATCH_TEST_TAG)

                else -> LazyColumn(
                    modifier = Modifier.testTag(tag).fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpaceSm),
                ) {
                    items(shown, key = { it.id }) { lot ->
                        AuctionLotRow(
                            lot = lot,
                            card = cards[lot.cardId],
                            now = session.remaining(lot, now),
                            missing = missing(lot),
                            selected = selected?.id == lot.id,
                            onClick = { session.select(lot.id) },
                        )
                    }
                }
            }
        }
    }

    if (LocalWideLayout.current) {
        // A wide screen opens on the first lot, because the alternative is a blank half of the
        // window beside a full list. **Only a wide one.** This used to be done in
        // `AuctionSession.refreshBoard`, which cannot see the layout: on a phone the desk is a
        // modal sheet, so reading the board threw a lot nobody had picked over the list — and a
        // modal sheet takes the input as well as the screen, so the tabs behind it were dead
        // until it was dismissed. The room is a list until the player picks something out of it.
        LaunchedEffect(shown.firstOrNull()?.id) {
            if (session.selected == null) session.select(shown.firstOrNull()?.id)
        }

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
 *
 * The card is [CardLine] rather than a `CardFace` shrunk to 0.42, which is what this drew until
 * the digits on it stopped being legible at any size a list row can afford. The desk still shows
 * the full sprite — that is where the money is committed. See [CardLine].
 */
@Composable
private fun AuctionLotRow(
    lot: AuctionLot,
    card: Card?,
    now: Long,
    missing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

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
        CardLine(
            card = card,
            name = card?.let { strings[it.nameKey] } ?: "#${lot.cardId}",
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                // The shelf's own price tag, in the list's own colour: what a lot costs *now* is
                // not an offer the house is making, so nothing here says affordable or not — the
                // desk's Bid button is where a purse that cannot reach it is told so.
                PriceTag(
                    price = lot.currentPrice,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = bidCountText(strings, lot),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelMedium,
                )
                AuctionPill(lot, missing)
            }

            // How a finished lot ended. A running one says it in the ring instead, which is the
            // whole point of the ring: a deadline is a quantity that decreases.
            statusText(strings, lot)?.let { ended ->
                Text(
                    text = ended,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (lot.status.isOpen) {
            CountdownRing(lotId = lot.id, millisLeft = now, urgent = now <= URGENT_MILLIS)
        }
    }
}

internal fun bidCountText(strings: Strings, lot: AuctionLot): String = if (lot.bidCount == 0) {
    strings[StringKeys.AUCTION_NO_BIDS]
} else {
    strings.format(StringKeys.AUCTION_BIDS, "${lot.bidCount}")
}
