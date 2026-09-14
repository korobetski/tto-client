package com.tripletriad.ui

import androidx.compose.ui.unit.dp
import com.tripletriad.settings.UiScale
import kotlin.test.Test
import kotlin.test.assertEquals

class InterfaceScaleTest {
    @Test
    fun autoLeavesPhonesTabletsAndLaptopsAtTheirOwnSize() {
        assertEquals(1f, UiScale.AUTO.factor(411.dp, 891.dp))
        assertEquals(1f, UiScale.AUTO.factor(1280.dp, 800.dp))
        assertEquals(1f, UiScale.AUTO.factor(1600.dp, 1000.dp))
    }

    @Test
    fun autoEnlargesAMaximisedWindowOnTheTwoCommonLargeMonitors() {
        // What is left of 1920 × 1080 and 2560 × 1440 under a taskbar and a title bar.
        assertEquals(1.25f, UiScale.AUTO.factor(1912.dp, 1000.dp))
        assertEquals(1.5f, UiScale.AUTO.factor(2552.dp, 1360.dp))
    }

    @Test
    fun autoNeedsTheHeightAsWellAsTheWidth() {
        // Stretched across two 1080p monitors: as wide as anything, and no taller than one.
        assertEquals(1.25f, UiScale.AUTO.factor(3832.dp, 1000.dp))
        assertEquals(1f, UiScale.AUTO.factor(2552.dp, 700.dp))
    }

    @Test
    fun aChosenSizeIsTheSameOnEveryWindow() {
        assertEquals(1f, UiScale.NORMAL.factor(2552.dp, 1360.dp))
        assertEquals(1.75f, UiScale.LARGEST.factor(411.dp, 891.dp))
        assertEquals(1.25f, UiScale.LARGE.factor(1280.dp, 800.dp))
    }
}
