package com.tripletriad.time

/**
 * Epoch milliseconds as `YYYY-MM-DD`, for the one place the game has a date to show.
 *
 * ### Why this is arithmetic and not a library
 *
 * [Clock] explains at length why `kotlinx-datetime` was dropped: as of 0.6.2 its `Instant` is a
 * deprecated typealias onto the stdlib's in the metadata view and a distinct type in the platform
 * views, so common code that formats one compiles and then fails to link. The hosts supply the
 * *clock* for that reason, and that answer does not extend here — an achievement's timestamp is
 * already in the save, and threading a formatter down from three platform modules to render a date
 * a screen already holds would be a seam per host for a dozen lines of division.
 *
 * ### It is UTC, and says so by being ISO
 *
 * There is no time zone in `commonMain`, and [Clock.localHour] is the whole of what the hosts
 * provide — an hour, not an offset. A date rendered a day early for a player east of Greenwich who
 * earned something just after midnight is the cost. `YYYY-MM-DD` is chosen partly because it reads
 * as a calendar date rather than as a local timestamp, and partly because it is the one written
 * order that is unambiguous in all four of the app's locales: `08/11/2026` is two different days
 * depending on who is reading it.
 */
fun isoDate(epochMillis: Long): String {
    val (year, month, day) = civilFromDays(floorDiv(epochMillis, MILLIS_PER_DAY))
    return "$year-${month.padded()}-${day.padded()}"
}

/**
 * Year, month `1..12` and day `1..31` for a count of days since 1970-01-01.
 *
 * Howard Hinnant's `civil_from_days`, transcribed. It is exact for every day a `Long` of
 * milliseconds can name and needs nothing but integer arithmetic. The trick that makes it a dozen
 * lines rather than a table of month lengths is the shift: the era is taken to begin on March 1st,
 * so February's variable length falls at the *end* of a year and every month's length comes out of
 * the linear `(5 * dayOfYear + 2) / 153`.
 *
 * `MagicNumber` is suppressed rather than answered with constants. The 5, the 2 and the 153 are
 * terms of one published formula, not independent quantities: naming them would invent meanings the
 * algorithm does not give them and make the transcription impossible to check against its source.
 */
@Suppress("MagicNumber")
private fun civilFromDays(days: Long): Triple<Long, Int, Int> {
    val shifted = days + DAYS_TO_MARCH_ERA
    val era = floorDiv(shifted, DAYS_PER_ERA)
    val dayOfEra = shifted - era * DAYS_PER_ERA
    val yearOfEra =
        (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthOfMarchYear = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * monthOfMarchYear + 2) / 5 + 1).toInt()
    // 0..9 are March..December; 10 and 11 are the January and February that follow, which belong
    // to the next calendar year.
    val month = (if (monthOfMarchYear < 10) monthOfMarchYear + 3 else monthOfMarchYear - 9).toInt()
    val year = yearOfEra + era * YEARS_PER_ERA + if (month <= 2) 1 else 0
    return Triple(year, month, day)
}

/**
 * Division that rounds towards negative infinity, which `/` does not.
 *
 * `java.lang.Math.floorDiv` is not in `commonMain`, and truncation would put every instant before
 * 1970 on the wrong day. Reachable only from a save with a nonsense timestamp in it, and handled
 * because a corrupt date should render as a wrong date rather than as an off-by-one nobody can
 * explain.
 */
private fun floorDiv(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    return if (value % divisor != 0L && (value xor divisor) < 0) quotient - 1 else quotient
}

private fun Int.padded(): String = if (this < FIRST_TWO_DIGIT) "0$this" else "$this"

/** Below this a month or a day needs its leading zero. */
private const val FIRST_TWO_DIGIT = 10

private const val MILLIS_PER_DAY = 86_400_000L

/** Days from 1970-01-01 back to 0000-03-01, the epoch the era arithmetic counts from. */
private const val DAYS_TO_MARCH_ERA = 719_468L

/** A 400-year era: `400 * 365` days plus its 97 leap days. */
private const val DAYS_PER_ERA = 146_097L
private const val YEARS_PER_ERA = 400L
