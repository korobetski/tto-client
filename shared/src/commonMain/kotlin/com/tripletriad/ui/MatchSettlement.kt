package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tripletriad.net.MatchReporter

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
