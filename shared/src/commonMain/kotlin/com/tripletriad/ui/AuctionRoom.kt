package com.tripletriad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.ln

const val AUCTION_SEARCH_TEST_TAG: String = "auction-search"

const val AUCTION_SEARCH_CLEAR_TEST_TAG: String = "auction-search-clear"

const val AUCTION_NO_MATCH_TEST_TAG: String = "auction-no-match"

/** The countdown inside a lot's ring. Per lot, because a room is many deadlines at once. */
fun auctionRingTestTag(lotId: String): String = "auction-ring-$lotId"

/** The word a lot is about, when there is one. See [AuctionPill]. */
fun auctionPillTestTag(lotId: String): String = "auction-pill-$lotId"

// `internal` for the same reason `cardSortTestTag` is: a public function may not name an internal
// type, and [AuctionSort] has no business being public to buy this one its `public`.
internal fun auctionSortTestTag(sort: AuctionSort): String = "auction-sort-${sort.slug}"

/**
 * The orders a sale room can be read in.
 *
 * [ENDING] is the default and that is the whole point of the room: a house where the next thing to
 * happen is not at the top is a list of prices rather than an auction. [PRICE] answers the other
 * question a buyer arrives with, and answers it cheapest-first — "what can I afford today" is the
 * question a purse asks; "what is the most expensive thing here" is idle curiosity.
 *
 * [MISSING] is the one that is not only an order. It **narrows** to lots whose card this player
 * does not own, because that is the single reason a collector opens the house at all — and a
 * narrowing that only sorted would still bury three interesting lots under two hundred duplicates
 * as soon as the room was busy. It is a chip in the same row as the other two rather than a
 * separate toggle: they are all one answer to "how is the room shown", only one of them holds at a
 * time, and what it hides it says so ([AUCTION_NO_MATCH_TEST_TAG]) with the chip still lit beside
 * the sentence, which is what makes it undoable.
 *
 * There is no *Newest* here, which the design called for: [AuctionLot] carries no listing time —
 * only [AuctionLot.endsAt] — and the three durations a seller may pick (6 h, 12 h, 24 h) mean the
 * end is not a proxy for the beginning. It needs a field in `:core` that the server fills, which
 * is a coordinated release of three repositories for one chip.
 */
internal enum class AuctionSort(val slug: String, val labelKey: String) {
    ENDING("ending", StringKeys.AUCTION_SORT_ENDING),
    PRICE("price", StringKeys.AUCTION_SORT_PRICE),
    MISSING("missing", StringKeys.AUCTION_MISSING),
}

/**
 * The room as it is shown: what the search admits, in the order the chip names.
 *
 * Pure, and kept apart from the composable for the reason the rest of `AuctionText` is: this is
 * the part a test can hold a list of lots against without a Compose rule, and a wrong order is
 * invisible to every test that only checks the rows exist.
 *
 * @param nameOf what the card is called in the language on screen — searching is done on the name
 *   the player can see, never on an id or an English key.
 * @param missing whether this player is without the card the lot is for.
 */
internal fun roomLots(
    lots: List<AuctionLot>,
    query: String,
    sort: AuctionSort,
    nameOf: (AuctionLot) -> String,
    missing: (AuctionLot) -> Boolean,
): List<AuctionLot> {
    val needle = query.trim()
    val kept = lots
        .filter { needle.isEmpty() || nameOf(it).contains(needle, ignoreCase = true) }
        .filter { sort != AuctionSort.MISSING || missing(it) }

    // Every order breaks its ties on the deadline, so two lots at the same price never swap places
    // between two polls — and the tie-break is the order the room is really about.
    return when (sort) {
        AuctionSort.ENDING, AuctionSort.MISSING -> kept.sortedBy { it.endsAt }
        AuctionSort.PRICE -> kept.sortedWith(compareBy({ it.currentPrice }, { it.endsAt }))
    }
}

/**
 * The two controls above the room: a card to look for, and how the rest is ordered.
 *
 * Drawn even when the room came back empty, and even when the chips have narrowed it to nothing:
 * a control that disappears when it has hidden everything is a room a player cannot get back out
 * of.
 */
@Composable
internal fun AuctionRoomControls(
    query: String,
    onQuery: (String) -> Unit,
    sort: AuctionSort,
    onSort: (AuctionSort) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        TtoSearchField(
            value = query,
            onValueChange = onQuery,
            tag = AUCTION_SEARCH_TEST_TAG,
            clearTag = AUCTION_SEARCH_CLEAR_TEST_TAG,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for (candidate in AuctionSort.entries) {
                TtoFilterChip(
                    label = strings[candidate.labelKey],
                    tag = auctionSortTestTag(candidate),
                    selected = candidate == sort,
                    onClick = { onSort(candidate) },
                )
            }
        }
    }
}

/**
 * A deadline as a shape.
 *
 * ### Why the ring is not a fraction of the sale
 *
 * A progress ring normally shows elapsed over total, and there is no total here: a lot carries
 * when it ends and nothing about when it opened. So the ring is a fraction of *attention* rather
 * than of time — see [ringFill] — which is what the row needed anyway. A player scanning the room
 * is not asking "how much of this sale has run", they are asking "is this one about to go".
 *
 * The number stays inside it, in the shortest form that is still true. A ring alone would be a
 * shape nobody can act on: "soon" is not a decision, "20 min" is.
 */
@Composable
internal fun CountdownRing(lotId: String, millisLeft: Long, urgent: Boolean) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val ink = if (urgent) colors.error else colors.onSurface.copy(alpha = SUBDUED)
    val track = colors.onSurface.copy(alpha = DISABLED)
    val sentence = strings.format(StringKeys.AUCTION_ENDS_IN, countdownText(strings, millisLeft))

    Box(
        modifier = Modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = ink,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN * ringFill(millisLeft),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke),
            )
        }
        Text(
            text = shortCountdown(strings, millisLeft),
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            // The sentence the row used to carry, kept for anyone who cannot see the ring: "20 m"
            // read out on its own says nothing about what it is 20 minutes to.
            modifier = Modifier
                .testTag(auctionRingTestTag(lotId))
                .semantics { contentDescription = sentence },
        )
    }
}

/**
 * How full the ring is: nothing a day out, nearly closed in the last two minutes.
 *
 * Logarithmic between the two, and deliberately so. Linear over a day would draw every lot in its
 * final hour as the same all-but-closed circle — the hour in which the ring is the only thing on
 * the row worth looking at — while spending a whole half of it distinguishing "18 h" from "12 h",
 * which is a distinction nobody acts on. This curve spends its first tenth on the twelve hours
 * from a day out to half a day, and its last tenth on the two minutes before the end.
 *
 * Anchored on the same two numbers the row already uses: [URGENT_MILLIS], the window in which a
 * bid pushes the deadline back, and the longest listing a seller may open.
 */
internal fun ringFill(millisLeft: Long): Float {
    if (millisLeft <= 0L) return 1f
    val left = ln(1.0 + millisLeft.toDouble() / URGENT_MILLIS)
    val horizon = ln(1.0 + LONGEST_MILLIS.toDouble() / URGENT_MILLIS)
    return (1.0 - left / horizon).coerceIn(0.0, 1.0).toFloat()
}

/**
 * The countdown in as few characters as fit inside a ring.
 *
 * The row's own sentence is [countdownText] and it stays in the semantics; this is the number
 * alone, in one unit. Rounding is [countdownText]'s — down, always, for the reason given there.
 */
internal fun shortCountdown(strings: Strings, millisLeft: Long): String {
    if (millisLeft <= 0L) return strings[StringKeys.AUCTION_ENDED]

    val minutes = millisLeft / MILLIS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> strings.format(StringKeys.AUCTION_HOURS, "$hours")
        minutes > 0 -> strings.format(StringKeys.AUCTION_LEFT_MINUTES, "$minutes")
        else -> strings.format(StringKeys.AUCTION_LEFT_SECONDS, "${millisLeft / MILLIS_PER_SECOND}")
    }
}

/**
 * The one word this row is about, when there is one: whose lot it is, who is winning it, or that
 * the card on it is one this player has never owned.
 *
 * A bordered pill rather than the coloured word it was, because *outbid* is the one thing on this
 * screen that asks for a reaction and it read as a caption. The colours are the app's own three
 * verdicts — the purse's green for leading, the error red for outbid, the experience blue for a
 * card that is missing — and a lot this player is not engaged in wears no pill at all.
 */
@Composable
internal fun AuctionPill(lot: AuctionLot, missing: Boolean) {
    val strings = LocalStrings.current
    val colors = LocalTtoColors.current
    val (label, ink) = when {
        lot.yours ->
            strings[StringKeys.AUCTION_YOUR_LOT] to
                MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED)

        lot.youLead -> strings[StringKeys.AUCTION_YOU_LEAD] to colors.positive
        lot.yourBid != null -> strings[StringKeys.AUCTION_OUTBID] to MaterialTheme.colorScheme.error
        missing -> strings[StringKeys.AUCTION_MISSING] to colors.experience
        else -> return
    }

    Text(
        text = label,
        color = ink,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .testTag(auctionPillTestTag(lot.id))
            .border(HairlineWidth, ink.copy(alpha = SUBDUED), MaterialTheme.shapes.small)
            .padding(horizontal = SpaceXs, vertical = 2.dp),
    )
}

/** The window in which the row turns red — [com.tripletriad.data.AuctionRules.extendedEnd]'s. */
internal const val URGENT_MILLIS: Long = 120_000L

/** `AuctionDuration.LONG`, in millis: the longest a lot can have left. */
private const val LONGEST_MILLIS = 24L * 60L * 60L * 1_000L

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** Twelve o'clock, and clockwise from there, as every dial in the world is read. */
private const val START_ANGLE = -90f
private const val FULL_TURN = 360f

private val RingSize = 44.dp
private val RingStroke = 3.dp
