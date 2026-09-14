package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.tripletriad.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The interface size through the whole app, at density 1 so a window's pixels are its dp.
 * `InterfaceScaleTest` has the thresholds; this is whether the factor reaches what is drawn.
 *
 * Sizes are read in pixels: a node's bounds in dp are divided by the density it was laid out
 * with, which is the enlarged one, so they come back unchanged whatever the size.
 */
@OptIn(ExperimentalTestApi::class)
class InterfaceScaleUiTest {
    @Test
    fun autoEnlargesALargeWindowAndAChosenSizeOverridesIt() {
        val laptop = titleButtonHeight(LAPTOP, "auto")
        val monitor = titleButtonHeight(MONITOR, "auto")
        val chosen = titleButtonHeight(MONITOR, "100")

        val auto = monitor / laptop
        assertEquals(MONITOR_AUTO, auto, SCALE_TOLERANCE, "auto: $laptop, then $monitor")
        assertEquals(1f, chosen / laptop, SCALE_TOLERANCE, "100 % was not kept: $chosen")
    }

    /** 1000 dp is a wide window, and at 175 % it is 571 dp for the screens drawn in it. */
    @Test
    fun aLargeSizeOnAMidSizedWindowTakesThePhoneLayout() = runSkikoComposeUiTest(
        size = MID_SIZED,
        density = Density(1f),
    ) {
        setContent { TestApp(store = store("175")) }
        newCharacter()

        assertTrue(exists(NAV_BAR_TEST_TAG), "571 dp should carry the bar")
        assertFalse(exists(NAV_RAIL_TEST_TAG), "and not the rail")
    }

    private fun titleButtonHeight(window: Size, scale: String): Float {
        var height = 0f
        runSkikoComposeUiTest(size = window, density = Density(1f)) {
            setContent { TestApp(store = store(scale)) }
            awaitTitleChoice("new")
            height = onNodeWithTag(TITLE_OPTIONS_TEST_TAG).fetchSemanticsNode().size.height
                .toFloat()
        }
        return height
    }

    private fun store(scale: String) =
        InMemorySettingsStore("""{"language":"en_US","ui_scale":"$scale"}""")

    private companion object {
        val LAPTOP = Size(1280f, 720f)
        val MONITOR = Size(1920f, 1080f)
        val MID_SIZED = Size(1000f, 900f)

        /** What auto gives [MONITOR] and not [LAPTOP]. */
        const val MONITOR_AUTO = 1.25f
    }
}

/** Pixels round, so a factor read back off a laid-out node is a few hundredths either way. */
internal const val SCALE_TOLERANCE = 0.04f
