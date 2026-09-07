package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.time.Clock
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.delay

/** The test tag of the strip, and of the button that leaves for the multiplayer board. */
const val PVP_WAITING_STRIP_TAG: String = "pvp-waiting-strip"
const val PVP_WAITING_JOIN_TAG: String = "pvp-waiting-join"

/**
 * Whether a paired multiplayer match should be announced over the board being played.
 *
 * The condition is "somewhere else", not "not multiplayer": the strip's whole job is to be seen
 * from a board that is *not* the paired one, and `Screen.PVP_MATCH` is where the player already
 * is. A settled match is not announced either — there is nothing to hurry to.
 */
internal fun shouldOfferWaitingMatch(pvp: PvpSession?, screen: Screen): Boolean {
    val match = pvp?.match ?: return false
    return match.status == PvpMatchStatus.PLAYING &&
        screen in PLAYING_SCREENS &&
        screen != Screen.PVP_MATCH
}

/**
 * The countdown as `m:ss`, floored, and never below zero.
 *
 * Digits and a colon rather than a translated phrase: this number changes every second in front of
 * a player who is trying to decide whether they have time to finish a board, and "4 minutes left"
 * rounded to the minute is exactly the reading that would get them forfeited. The auction rows
 * round to the minute for the opposite reason — see [shortCountdown].
 */
internal fun waitingCountdown(millisLeft: Long): String {
    val seconds = (millisLeft.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val minutes = seconds / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

/**
 * "An opponent is waiting", over whatever board the player is on, with the way to go to them.
 *
 * ### Why this exists at all
 *
 * A table is polled from everywhere, so hosting one and playing something else while it fills is
 * the obvious thing to do — and until this strip the only sign that it *had* filled was an
 * operating-system notification, which is silent on a phone in the player's hand and absent
 * entirely on desktop. The board they were on said nothing.
 *
 * ### Why it is not a dialog
 *
 * The player is mid-placement on a board that matters to them, and a modal would take the decision
 * out of their hands at whatever moment the join happened to land. The server no longer starts the
 * turn clock until both sides have opened the match (`PvpRoutes.attend`), so there is genuinely a
 * choice here rather than an emergency — the strip states it and leaves it.
 */
@Composable
internal fun PvpWaitingStrip(
    pvp: PvpSession?,
    clock: Clock,
    strings: Strings,
    onJoin: () -> Unit,
) {
    val match = pvp?.match ?: return
    val deadline = match.deadline

    // Re-read once a second, and only while there is a deadline to count. The clock is a parameter
    // for the same reason it is everywhere else in this file's neighbours: the tests run a stopped
    // one, and a countdown reading `Clock.System` would be a different picture every run.
    var now by remember(match.matchId) { mutableStateOf(clock.nowMillis()) }
    LaunchedEffect(match.matchId, deadline) {
        while (deadline != null) {
            delay(WAITING_TICK_MILLIS)
            now = clock.nowMillis()
        }
    }

    val colors = LocalTtoColors.current
    val left = deadline?.let { waitingCountdown(it - now) }
    val heading = strings[StringKeys.PVP_WAITING_NOW]
    val kept = strings[StringKeys.PVP_WAITING_KEPT]

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .testTag(PVP_WAITING_STRIP_TAG)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(SpaceSm)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(SpaceSm))
                .border(HairlineWidth, colors.positive, RoundedCornerShape(SpaceSm))
                .padding(SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(TEXT_SHARE)
                    // One sentence to a screen reader. Read field by field it announces a number
                    // that changes every second, which is a countdown read as an interruption.
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(heading, kept, left).joinToString(" · ")
                    },
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(text = heading, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = kept,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                )
            }

            if (left != null) {
                Text(
                    text = left,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.positive,
                    textAlign = TextAlign.End,
                )
            }

            Button(
                onClick = onJoin,
                modifier = Modifier.testTag(PVP_WAITING_JOIN_TAG),
            ) {
                Text(text = strings[StringKeys.PVP_JOIN])
            }
        }
    }
}

/** How wide the two lines of text may be, leaving the number and the button their room. */
private const val TEXT_SHARE = 0.55f

private const val WAITING_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
