package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys

const val PLAY_TABS_TEST_TAG: String = "play-tabs"

/**
 * The three ways to start a match, as tabs of one root.
 *
 * They are three *screens* and not three bodies of one, unlike the profile's or the collection's
 * tabs, because each of them owns a long-lived effect the others must not pay for: the roster asks
 * the server whether a solo match is still open on the way in, and the multiplayer lobby holds a
 * poll for tables, invitations and claims that has to keep running while a deck is being chosen.
 * Switching tab is therefore a navigation, and [Screen.up] sends all three back to the lobby as
 * peers — the chevron must not undo a tab press.
 */
internal enum class PlayTab(val screen: Screen, val labelKey: String, val slug: String) {
    SOLO(Screen.OPPONENTS, StringKeys.SOLO, "solo"),
    MULTIPLAYER(Screen.PVP, StringKeys.MULTIPLAYER, "multiplayer"),
    TOURNAMENTS(Screen.CAMPAIGNS, StringKeys.CAMPAIGNS, "tournaments"),
}

/**
 * The header all three wear, so that the tab row does not move between them.
 *
 * [waiting] is what the multiplayer tab has to say from the other two: an invitation and a prize
 * both expire, and a player looking at the solo roster has no other way to learn one arrived. Zero
 * draws nothing rather than a "0" — a badge that is always there stops being read.
 */
@Composable
internal fun PlayTabs(current: PlayTab, waiting: Int, onSelect: (PlayTab) -> Unit) {
    val strings = LocalStrings.current

    ScreenTabs(
        tabs = PlayTab.entries.map { tab ->
            val label = strings[tab.labelKey]
            val badge = tab == PlayTab.MULTIPLAYER && waiting > 0
            val badged = if (badge) "$label  $waiting" else label
            badged to screenTabTestTag(tab.slug)
        },
        selected = current.ordinal,
        onSelect = { index -> PlayTab.entries[index].let { if (it != current) onSelect(it) } },
        modifier = Modifier.testTag(PLAY_TABS_TEST_TAG),
    )
}
