package com.tripletriad.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.settings.UiScale

/**
 * What `App` multiplies the density by, for a window of [width] × [height] in the platform's dp.
 *
 * ### Where [UiScale.AUTO] steps up
 *
 * Both dimensions and not the width alone: a window stretched across two monitors is wide and
 * short, and enlarging it would take away the height the board is fitted to. The two steps are the
 * two common large monitors at a system scale of 100 %, less the taskbar and the title bar a
 * maximised window loses — some 60 to 80 dp. So 1920 × 1080 gets 125 % and 2560 × 1440 gets 150 %.
 *
 * In dp, so a monitor the system already scales is not scaled twice: a 4K screen at 200 % is a
 * 1920 dp window, and it gets 125 %.
 */
internal fun UiScale.factor(width: Dp, height: Dp): Float {
    percent?.let { return it / PERCENT_OF_ONE }
    return when {
        width >= LargerAutoWidth && height >= LargerAutoHeight -> AUTO_LARGER
        width >= LargeAutoWidth && height >= LargeAutoHeight -> AUTO_LARGE
        else -> 1f
    }
}

private val LargeAutoWidth = 1800.dp
private val LargeAutoHeight = 900.dp
private val LargerAutoWidth = 2400.dp
private val LargerAutoHeight = 1200.dp

private const val AUTO_LARGE = 1.25f
private const val AUTO_LARGER = 1.5f
private const val PERCENT_OF_ONE = 100f
