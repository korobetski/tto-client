package com.tripletriad.time

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The calendar arithmetic behind an achievement's unlock date.
 *
 * Worth its own file because it is the one piece of this app that reimplements something every
 * platform already has: a transcription of `civil_from_days` that nothing else would catch if a
 * digit moved. The cases are the ones that break a naive implementation — the epoch itself, the
 * turn of a leap year, the 1900/2000 century rule, and a day before 1970.
 */
class CivilDateTest {
    @Test
    fun theEpochIsTheFirstOfJanuary1970() {
        assertEquals("1970-01-01", isoDate(0L))
        assertEquals("1970-01-01", isoDate(DAY - 1))
        assertEquals("1970-01-02", isoDate(DAY))
    }

    @Test
    fun theDefaultClockRendersTheDateItsKdocClaims() {
        // `FixedClock.DEFAULT_MILLIS` is documented as 2026-01-01T12:00:00Z, and this is the only
        // thing in the build that can check the claim.
        assertEquals("2026-01-01", isoDate(FixedClock.DEFAULT_MILLIS))
    }

    @Test
    fun leapDaysAreWhereTheyBelong() {
        assertEquals("2024-02-29", isoDate(days(19_782)))
        assertEquals("2024-03-01", isoDate(days(19_783)))
        // 2000 is a leap year (divisible by 400) and 1900 was not (divisible by 100).
        assertEquals("2000-02-29", isoDate(days(11_016)))
        assertEquals("2100-02-28", isoDate(days(47_540)))
        assertEquals("2100-03-01", isoDate(days(47_541)))
    }

    @Test
    fun monthEndsAreExact() {
        assertEquals("1970-12-31", isoDate(days(364)))
        assertEquals("1971-01-01", isoDate(days(365)))
        assertEquals("2026-08-11", isoDate(days(20_676)))
    }

    /**
     * A timestamp before 1970 rounds down to its own day rather than up to the next one.
     *
     * Reachable only from a save with a nonsense date in it. Asserted because truncating division
     * gets this wrong silently, and "wrong by one day, but only for negative instants" is the kind
     * of defect that survives every casual check.
     */
    @Test
    fun instantsBeforeTheEpochRoundDown() {
        assertEquals("1969-12-31", isoDate(-1L))
        assertEquals("1969-12-31", isoDate(-DAY))
        assertEquals("1969-12-30", isoDate(-DAY - 1))
    }

    @Test
    fun everyPartIsTwoDigitsExceptTheYear() {
        assertEquals("2003-04-05", isoDate(days(12_147)))
    }

    private companion object {
        const val DAY = 86_400_000L

        fun days(count: Long): Long = count * DAY
    }
}
