package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.TradeRule
import com.tripletriad.protocol.PvpPresence
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpTable

/**
 * What a table says about itself in words: its clock, and what is being played for.
 *
 * Split out of `PvpScreen` when the rebuild pushed that file past twenty declarations. These four
 * are the ones with no Compose in them at all — they are read by the lobby, by the table screen
 * and by the deck question, and two of them are asserted directly in `PvpTablesUiTest` without a
 * composition at all.
 */

/** Whole minutes since the table was opened, floored — "open for 0 min" is a table just opened. */
internal fun minutesSince(table: PvpTable, now: Long): Int =
    ((now - table.openedAt).coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()

internal fun minutesLeft(table: PvpTable, now: Long): Int {
    val left = (table.expiresAt - now).coerceAtLeast(0L)
    return ((left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * How many other people are about, in words — or null when nobody has answered that yet.
 *
 * Null rather than "nobody is online" for the unanswered case, because the two readings lead
 * somewhere different: "nobody is here" is a reason to play solo, and "we have not asked" is a
 * reason to say nothing at all. See `PvpSession.presence`.
 */
internal fun presenceLine(presence: PvpPresence?, strings: Strings): String? = when {
    presence == null -> null
    presence.others <= 0 -> strings[StringKeys.PVP_ONLINE_NOBODY]
    presence.others == 1 -> strings[StringKeys.PVP_ONLINE_ONE]
    else -> strings.format(StringKeys.PVP_ONLINE_MANY, "${presence.others}")
}

internal fun stakeLine(stake: PvpStake, strings: Strings): String {
    if (stake.isFree) return strings[StringKeys.PVP_TABLE_FREE]

    val parts = buildList {
        if (stake.mgp > 0) add(strings.format(StringKeys.PVP_STAKE_MGP, "${stake.mgp}"))
        if (stake.trade != TradeRule.NONE) add(strings[tradeKey(stake.trade)])
    }
    return parts.joinToString(" $DOT_SEPARATOR ")
}

internal fun tradeKey(trade: TradeRule): String = when (trade) {
    TradeRule.NONE -> StringKeys.PVP_TRADE_NONE
    TradeRule.ONE -> StringKeys.PVP_TRADE_ONE
    TradeRule.DIFF -> StringKeys.PVP_TRADE_DIFF
    TradeRule.DIRECT -> StringKeys.PVP_TRADE_DIRECT
    TradeRule.ALL -> StringKeys.PVP_TRADE_ALL
}
