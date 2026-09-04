package com.tripletriad.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchSpeedTest {
    @Test
    fun theShippedPaceIsTheIdentity() {
        assertEquals(1.0, MatchSpeed.Default.scale, "the default must not change any duration")
        assertEquals(MatchSpeed.NORMAL, MatchSpeed.Default)
    }

    @Test
    fun theCransGetFasterInDeclarationOrder() {
        // The settings sheet draws `MatchSpeed.entries` in order, and a row of crans that is not
        // monotonic is a row nobody can read. Strictly decreasing, so no two mean the same thing.
        val scales = MatchSpeed.entries.map { it.scale }
        assertEquals(scales.sortedDescending(), scales, "the crans are not in order: $scales")
        assertEquals(scales.distinct().size, scales.size, "two crans share a factor: $scales")
    }

    @Test
    fun instantIsActuallyZero() {
        // Not "very small". Every consumer multiplies a duration by this and hands the result to
        // `delay` or `tween`, both of which take zero; a 0.01 would leave a frame of caption on a
        // setting whose whole promise is that there is none.
        assertEquals(0.0, MatchSpeed.INSTANT.scale)
    }

    @Test
    fun everyCranHasItsOwnTagAndTheTagsRoundTrip() {
        val tags = MatchSpeed.entries.map { it.tag }
        assertEquals(tags.distinct().size, tags.size, "two crans share a tag: $tags")
        for (speed in MatchSpeed.entries) {
            assertEquals(speed, MatchSpeed.forTag(speed.tag), "${speed.tag} did not round-trip")
        }
    }

    @Test
    fun aTagThisBuildDoesNotHaveIsNotInvented() {
        // `forTag` answers null and the *caller* decides what to do about it — `UserSettings.speed`
        // falls back, which is a decision that belongs there and not here.
        assertNull(MatchSpeed.forTag("blistering"))
        assertNull(MatchSpeed.forTag(""))
        assertNull(MatchSpeed.forTag("NORMAL"), "the tag is the stored form, not the enum name")
    }

    @Test
    fun everyCranNamesItselfThroughABundleKey() {
        // The four labels are reached this way rather than through `StringKeys`, so nothing in the
        // key list holds them — `DerivedKeysTest` is what proves they resolve. This only proves the
        // keys are distinct and shaped like the rest.
        val keys = MatchSpeed.entries.map { it.labelKey }
        assertEquals(keys.distinct().size, keys.size, "two crans share a label: $keys")
        assertTrue(keys.all { it.startsWith("APP_SPEED_") }, "unexpected label keys: $keys")
    }
}
