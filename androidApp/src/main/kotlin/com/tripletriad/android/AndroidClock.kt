package com.tripletriad.android

import com.tripletriad.time.Clock
import java.util.Calendar

object AndroidClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun localHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
