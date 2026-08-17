package com.tripletriad.time

interface Clock {
    fun nowMillis(): Long

    fun localHour(): Int
}

class FixedClock(
    private val millis: Long = DEFAULT_MILLIS,
    private val hour: Int = DEFAULT_HOUR,
) : Clock {
    init {
        require(hour in 0 until HOURS_IN_DAY) { "hour must be in 0..23, was $hour" }
    }

    override fun nowMillis(): Long = millis

    override fun localHour(): Int = hour

    companion object {
        const val DEFAULT_MILLIS: Long = 1_767_268_800_000L
        const val DEFAULT_HOUR: Int = 12
        private const val HOURS_IN_DAY = 24
    }
}
