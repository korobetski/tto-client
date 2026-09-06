package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.notify.Note
import com.tripletriad.notify.Notifier
import kotlinx.coroutines.delay

/**
 * How often the app asks whether anybody is waiting on this player, when it is not their screen.
 *
 * Twenty seconds against the lobby's one, and the difference is what each poll is for: on the
 * lobby the player is watching for a table to appear and a stale second shows, whereas this one
 * only decides whether to ring, and a notification twenty seconds late is a notification.
 */
internal const val ALERT_MILLIS = 20_000L

/**
 * Watches, from wherever the player happens to be, for the two things that wait on them.
 *
 * This is the whole of what "notifications" means here, and it is worth being exact about its
 * limit: it is a loop in this process, not push. Close the app and nothing rings — see [Notifier].
 *
 * ### One turn, and the loop around it
 *
 * [turn] is separate from [watch] so the decisions can be tested without a twenty-second wait and
 * without virtual time: the requests this makes settle on the client's own dispatchers, which a
 * `TestScope` does not control, so a test that advanced the clock would be a test that asserted
 * against answers that had not arrived yet.
 *
 * @param looking whether the player is already looking at the multiplayer screens, and so has
 *   already been told everything a note could tell them.
 */
internal class PvpAlerts(
    private val session: PvpSession,
    private val notifier: Notifier,
    private val strings: Strings,
    private val looking: () -> Boolean,
) {
    private var announcedMatch: String? = null

    suspend fun watch() {
        while (true) {
            delay(ALERT_MILLIS)
            turn()
        }
    }

    suspend fun turn() {
        session.refreshChallenges()
        session.poll()

        // Read on every turn and announced only on some, which is not a wasted read:
        // `PvpSession.arrivals` marks what it returns as seen, so reading while the player is on
        // the lobby is what stops those same invitations ringing the moment they leave it.
        val arrivals = session.arrivals()
        val match = session.match?.matchId
        // The transition, not the state: a match already under way is not news on every turn, and
        // the id is remembered even when nothing is announced, so leaving the board and coming
        // back does not ring for the match the player is playing.
        val started = match != null && match != announcedMatch
        announcedMatch = match

        if (looking()) return

        arrivals.forEach { challenge ->
            notifier.post(
                Note(
                    id = "challenge-${challenge.id}",
                    title = strings[StringKeys.NOTIFY_CHALLENGE_TITLE],
                    body = strings.format(StringKeys.NOTIFY_CHALLENGE_BODY, challenge.fromName),
                ),
            )
        }
        if (started) {
            notifier.post(
                Note(
                    id = "match-$match",
                    title = strings[StringKeys.NOTIFY_MATCH_TITLE],
                    body = strings[StringKeys.NOTIFY_MATCH_BODY],
                ),
            )
        }
    }
}

/**
 * The alert loop, hung on the composition so it lives exactly as long as the app is on screen.
 *
 * Keyed on the session alone and not on [screen], so that changing screens does not restart the
 * twenty-second wait — a player flicking between two screens would otherwise be a player who is
 * never told anything. Which screen they are on is read through a lambda instead, at the moment
 * the loop has something to say.
 */
@Composable
internal fun AlertWatch(pvp: PvpSession?, notifier: Notifier, strings: Strings, screen: Screen) {
    if (pvp == null) return
    // Read inside the lambda below, which is why it is a state holder and not a captured value:
    // the effect is not restarted when the screen changes, so a captured `screen` would be
    // whichever one was showing when the loop started.
    val here = rememberUpdatedState(screen)
    LaunchedEffect(pvp, notifier, strings) {
        PvpAlerts(pvp, notifier, strings) { here.value in MULTIPLAYER_SCREENS }.watch()
    }
}

/** Where an invitation or a taken table is already on screen, and a note would be a second copy. */
private val MULTIPLAYER_SCREENS =
    setOf(Screen.PVP, Screen.PVP_MATCH, Screen.PVP_TABLE, Screen.PVP_CLAIM)
