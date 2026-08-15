package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.time.Clock
import kotlinx.coroutines.delay

/**
 * The current instant, as a value that **moves**.
 *
 * ### The bug this exists to fix
 *
 * Three screens take a `now` and count down against it: the PvP board's turn timer, the lobby's
 * "expires in n min", and the claim screen's deadline. All three were handed `clock.nowMillis()`
 * read straight from `App`'s routing table — a plain function call, in a composition nothing was
 * invalidating. `Clock` is not snapshot state, so reading it does not subscribe to anything: the
 * value was sampled once when the destination was composed and then stayed there.
 *
 * So the turn timer showed whatever the clock said when the board opened and never moved off it.
 * It was not that the deadline was wrong — the server's deadline was arriving correctly on every
 * poll — it was that the number it was being subtracted from was frozen. The same for the table
 * expiry, which is why a table appeared to sit at "5 min" for its whole life.
 *
 * ### A second and not a frame
 *
 * Everything reading this is displayed in whole seconds or whole minutes, so a faster tick would
 * buy nothing and cost a recomposition per frame of the screen it is on. This is deliberately the
 * same interval [PvpSession] polls at, and for the same reason.
 *
 * ### Held per caller
 *
 * Called at the destination rather than once at the top of [App], so only the screen that is
 * counting something down recomposes each second — a ticking value provided to the whole tree
 * would redraw the shop, the collection and the board alike for a number none of them show.
 *
 * A stopped clock — [com.tripletriad.time.FixedClock], which is what previews and tests get —
 * writes the same value every tick, and an equal write to a `mutableStateOf` notifies nobody. So
 * this costs a suspended coroutine and no recompositions there, which is what keeps it out of the
 * way of the UI tests.
 */
@Composable
internal fun rememberNow(clock: Clock): Long {
    var now by remember(clock) { mutableStateOf(clock.nowMillis()) }

    LaunchedEffect(clock) {
        while (true) {
            delay(TICK_MILLIS)
            now = clock.nowMillis()
        }
    }

    return now
}

private const val TICK_MILLIS = 1_000L
