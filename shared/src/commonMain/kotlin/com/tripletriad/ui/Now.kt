package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.time.Clock
import kotlinx.coroutines.delay

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
