package com.tripletriad.desktop

import com.tripletriad.time.Clock
import java.time.LocalTime

object JvmClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun localHour(): Int = LocalTime.now().hour
}
