package com.tripletriad.web

import com.tripletriad.time.Clock

/** The browser's `Date`, which is the player's own clock and time zone, as `JvmClock` is. */
object BrowserClock : Clock {
    // `Date.now()` is a JavaScript number; it holds every millisecond of this century exactly.
    override fun nowMillis(): Long = dateNow().toLong()

    override fun localHour(): Int = dateHour()
}

private fun dateNow(): Double = js("Date.now()")

private fun dateHour(): Int = js("new Date().getHours()")
