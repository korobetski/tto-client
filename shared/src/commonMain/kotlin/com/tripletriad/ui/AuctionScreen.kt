package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.time.Clock
import kotlinx.coroutines.launch

const val AUCTION_SCREEN_TEST_TAG: String = "auction-screen"

const val AUCTION_LOCK_TEST_TAG: String = "auction-lock"

const val AUCTION_TABS_TEST_TAG: String = "auction-tabs"

const val AUCTION_OFFLINE_TEST_TAG: String = "auction-offline"

internal enum class AuctionTab {
    BOARD,

    MINE,

    SELL,
}

/**
 * The auction house.
 *
 * ### Three tabs, and the middle one is the one with a deadline on it
 *
 * The room, this player's own lots, and the consignment desk. `MINE` carries a count when a lot is
 * waiting on a decision, because that decision expires — `AuctionPolicy.sellerDecisionHours` — and
 * a seller who never opens the tab loses the sale to a timeout. It is the only badge here, for the
 * same reason the lobby only badges an expiring claim.
 *
 * ### Two ways this screen has nothing to show, and they are different
 *
 * A level too low is a door that will open ([AUCTION_LOCK_TEST_TAG], stating the level once — the
 * placeholder this replaced existed to say that and the sentence is kept). No server at all is a
 * different fact: the house is other people, and there is nobody to trade with off a local `.sav`.
 * Saying "coming soon" to either would be wrong in a different direction.
 */
@Composable
@Suppress("LongParameterList")
internal fun AuctionScreen(
    profile: GameSave,
    session: AuctionSession?,
    cards: Map<Int, Card>,
    clock: Clock,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val unlocks = LocalUnlocks.current
    val open = unlocks.allowsAuction(profile)

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.AUCTION],
        onBack = onBack,
        wide = true,
    ) {
        Column(
            modifier = Modifier
                .testTag(AUCTION_SCREEN_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                !open ->
                    AuctionClosed(strings.format(StringKeys.LOCKED_LEVEL, "${unlocks.auction}"))

                session == null -> AuctionUnserved()
                else -> AuctionRoom(session, profile, cards, clock)
            }
        }
    }
}

@Composable
private fun ColumnScope.AuctionRoom(
    session: AuctionSession,
    profile: GameSave,
    cards: Map<Int, Card>,
    clock: Clock,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val now = rememberNow(clock)
    var tab by remember { mutableStateOf(AuctionTab.BOARD) }

    // One poll for the whole screen rather than one per tab: the lots behind a tab the player is
    // not looking at are the lots they will look at next, and a tab switch that starts a fresh
    // read is a tab switch that shows a spinner over a list it already had.
    LaunchedEffect(session) {
        session.refresh()
        session.watch()
    }

    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.AUCTION_ROOM] to screenTabTestTag("auction-board"),
                mineTabLabel(session) to screenTabTestTag("auction-mine"),
                strings[StringKeys.AUCTION_SELL] to screenTabTestTag("auction-sell"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = AuctionTab.entries[index] },
            modifier = Modifier.testTag(AUCTION_TABS_TEST_TAG),
        )

        when (tab) {
            AuctionTab.BOARD -> AuctionBoardBody(
                session = session,
                lots = session.board,
                state = session.boardState,
                profile = profile,
                cards = cards,
                now = now,
                tag = AUCTION_BOARD_TEST_TAG,
                emptyText = strings[StringKeys.AUCTION_EMPTY],
                onRefresh = { scope.launch { session.refreshBoard() } },
            )

            AuctionTab.MINE -> AuctionBoardBody(
                session = session,
                lots = session.mine,
                state = session.mineState,
                profile = profile,
                cards = cards,
                now = now,
                tag = AUCTION_MINE_TEST_TAG,
                emptyText = strings[StringKeys.AUCTION_MINE_EMPTY],
                onRefresh = { scope.launch { session.refreshMine() } },
            )

            // Counted from the same list the tab is named after, rather than tracked separately:
            // an open lot of this player's is exactly a row in `mine` that has not finished, and
            // the ceiling the server enforces counts the same thing.
            AuctionTab.SELL -> AuctionSellBody(
                session = session,
                profile = profile,
                cards = cards,
                openLots = session.mine.count { it.yours && !it.status.isFinished },
            )
        }
    }
}

/** The tab's name, plus what is waiting behind it when something is. */
@Composable
private fun mineTabLabel(session: AuctionSession): String {
    val strings = LocalStrings.current
    val waiting = session.awaitingMe.size
    return if (waiting == 0) {
        strings[StringKeys.AUCTION_MINE]
    } else {
        "${strings[StringKeys.AUCTION_MINE]} ($waiting)"
    }
}

/** The door that will open, with the level on it. */
@Composable
private fun ColumnScope.AuctionClosed(reason: String) {
    val strings = LocalStrings.current

    AuctionNotice(TtoIcons.Lock, strings[StringKeys.AUCTION_BLURB])

    Row(
        modifier = Modifier
            .testTag(AUCTION_LOCK_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Icon(
            imageVector = TtoIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            modifier = Modifier.size(IconMd),
        )
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The house with nobody in it: a character playing off a local save has no counterparty. */
@Composable
private fun ColumnScope.AuctionUnserved() {
    val strings = LocalStrings.current

    Column(modifier = Modifier.testTag(AUCTION_OFFLINE_TEST_TAG)) {
        AuctionNotice(TtoIcons.Shop, strings[StringKeys.AUCTION_NEEDS_SERVER])
    }
}

@Composable
private fun ColumnScope.AuctionNotice(
    icon: ImageVector,
    text: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceXl, bottom = SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceLg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = SUBDUED),
            modifier = Modifier.size(HeroIcon),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
        )
    }
}

private val HeroIcon = 56.dp
