package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tripletriad.net.MatchReporter

/**
 * Submits the matches that are waiting, and adopts the profile the server credited them to.
 *
 * Its own file rather than a twentieth function in `App.kt`, which is at detekt's limit for a
 * reason its own header gives: that file is the shell and the routing table, and this is neither.
 * The same split `MatchSeed.kt` made out of `MatchScreen.kt`.
 *
 * ### The state this exists to end
 *
 * A match against a program is credited **by the client**: [MatchScreen] runs
 * `MatchRewards.credit` and writes the result through [ProfileGate.persist]. On an account that
 * write goes out as a `PUT /me/save`, and the server applies it through
 * `GameSave.withServerOwnedFrom` — which takes `bag`, `cards`, `mgp`, `xp` and the rest **from its
 * own stored profile** and discards the client's. That is right: a client that could assert its own
 * drops could assert a better one. What it means is that the reward the player was just shown
 * exists only in the copy on screen until the match's transcript has been submitted and replayed.
 *
 * Nothing used to close that gap during a session. The drain ran once, when a character first came
 * into play, and the credited profile it came back with was handed to a callback **no host ever
 * passed** — so a card won from an opponent sat in a bag the server had never heard of for the rest
 * of the session. Tapping Use on it asked the server to spend an item it did not hold, and the
 * answer, `ItemEffect.NotUseable`, arrived with the server's own bag attached: the row vanished,
 * the collection gained nothing, and the screen said nothing at all.
 *
 * ### Why it fires on leaving a match rather than on finishing one
 *
 * Because the alternative puts a round trip on the path between a player and their result screen,
 * which is exactly what [MatchReporter.report] exists to avoid — the transcript is durable the
 * moment the match ends, and nothing is lost by sending it a few seconds later. Leaving the board
 * is the first moment the player is not waiting for anything, and it is also the moment before they
 * could possibly reach the bag.
 *
 * A drain with nothing queued is one directory listing and no request, so the cost of firing on
 * every exit rather than only after a win is not worth the branch that would avoid it.
 *
 * @param queueKey null before a character is in play, which is also when there is nothing queued
 *   and no key to queue it under.
 * @param account null on a build with no server, where [MatchReporter.None] answers null anyway and
 *   this whole composable does nothing.
 */
@Composable
internal fun MatchSettlement(
    reporter: MatchReporter,
    account: AccountSession?,
    queueKey: String?,
    screen: Screen,
) {
    val playing = screen in PLAYING_SCREENS

    // Not keyed on the profile: `persist` replaces the save object after every match, and
    // re-running on that would put a network call at the end of each one — the thing the queue
    // exists to avoid. Keyed on whether a board is up, so this fires at launch and again on the way
    // off each one.
    LaunchedEffect(queueKey, reporter, playing) {
        if (playing) return@LaunchedEffect
        queueKey?.let { key -> reporter.drain(key)?.let { account?.adopt(it) } }
    }
}
