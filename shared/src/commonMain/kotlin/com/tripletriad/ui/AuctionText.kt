package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.protocol.AuctionLot
import com.tripletriad.protocol.AuctionRefusal
import com.tripletriad.protocol.AuctionStatus

/*
 * The words the auction house is read in.
 *
 * Kept apart from the composables for one reason: every one of these is a pure function of a
 * refusal, a status or a number, so they are the part of this screen a test can assert without a
 * Compose rule — and the part that has to be complete, since a missing branch here is a screen
 * showing an enum constant to a player.
 */

/**
 * Why the house said no.
 *
 * Exhaustive over [AuctionRefusal] on purpose rather than through an `else`: the server may learn
 * a new refusal, and a compile error here is the cheapest possible way to be told about it.
 */
internal fun refusalText(strings: Strings, refusal: AuctionRefusal): String = when (refusal) {
    AuctionRefusal.LOCKED -> strings[StringKeys.AUCTION_REFUSED_LOCKED]
    AuctionRefusal.LOT_GONE -> strings[StringKeys.AUCTION_REFUSED_LOT_GONE]
    AuctionRefusal.NOT_YOURS -> strings[StringKeys.AUCTION_REFUSED_NOT_YOURS]
    AuctionRefusal.TOO_MANY_LOTS -> strings[StringKeys.AUCTION_REFUSED_TOO_MANY_LOTS]
    AuctionRefusal.BELOW_FLOOR -> strings[StringKeys.AUCTION_REFUSED_BELOW_FLOOR]
    AuctionRefusal.RESERVE_BELOW_START -> strings[StringKeys.AUCTION_REFUSED_RESERVE_BELOW_START]
    AuctionRefusal.ABOVE_CEILING -> strings[StringKeys.AUCTION_REFUSED_ABOVE_CEILING]
    AuctionRefusal.CANNOT_AFFORD -> strings[StringKeys.AUCTION_REFUSED_CANNOT_AFFORD]
    AuctionRefusal.BID_TOO_LOW -> strings[StringKeys.AUCTION_REFUSED_BID_TOO_LOW]
    AuctionRefusal.YOUR_OWN_LOT -> strings[StringKeys.AUCTION_REFUSED_YOUR_OWN_LOT]
    AuctionRefusal.ALREADY_LEADING -> strings[StringKeys.AUCTION_REFUSED_ALREADY_LEADING]
    AuctionRefusal.ALREADY_BID -> strings[StringKeys.AUCTION_REFUSED_ALREADY_BID]
    AuctionRefusal.NOT_YOUR_DECISION -> strings[StringKeys.AUCTION_REFUSED_NOT_YOUR_DECISION]
}

/**
 * How a finished lot ended, or null while it is still running.
 *
 * Null rather than "open", because an open lot's line is its countdown and saying both would be
 * saying the same thing twice in the same row.
 */
internal fun statusText(strings: Strings, lot: AuctionLot): String? =
    when (lot.status) {
        AuctionStatus.OPEN -> null
        AuctionStatus.AWAITING_SELLER -> strings[StringKeys.AUCTION_STATUS_AWAITING]
        AuctionStatus.SOLD -> strings.format(
            StringKeys.AUCTION_STATUS_SOLD,
            "${lot.soldFor ?: lot.currentPrice}",
        )

        AuctionStatus.UNSOLD -> strings[StringKeys.AUCTION_STATUS_UNSOLD]
        AuctionStatus.CANCELLED -> strings[StringKeys.AUCTION_STATUS_CANCELLED]
    }

/**
 * A countdown, in the largest unit that still says something useful.
 *
 * ### Why it rounds down and not up
 *
 * `PvpScreen.minutesLeft` rounds *up*, because a table that says "1 min" and expires is a table
 * nobody was going to join anyway. Here the number is the one a bidder decides against, and a lot
 * that reads "2 min" with ninety seconds on it invites a bid placed too late on purpose. Rounding
 * down under-promises, which is the only safe direction when somebody is spending money on it.
 *
 * Seconds appear only under a minute — the point at which the number stops being background and
 * becomes the thing the player is watching.
 */
internal fun countdownText(strings: Strings, millisLeft: Long): String {
    if (millisLeft <= 0L) return strings[StringKeys.AUCTION_ENDED]

    val seconds = millisLeft / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> strings.format(
            StringKeys.AUCTION_LEFT_HOURS,
            "$hours",
            "${minutes % MINUTES_PER_HOUR}",
        )

        minutes > 0 -> strings.format(StringKeys.AUCTION_LEFT_MINUTES, "$minutes")
        else -> strings.format(StringKeys.AUCTION_LEFT_SECONDS, "$seconds")
    }
}

/** A price with the game's own coin after it, the way every other price in the app is written. */
internal fun priceText(strings: Strings, amount: Int): String =
    "$amount ${strings[StringKeys.MGP]}"

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
