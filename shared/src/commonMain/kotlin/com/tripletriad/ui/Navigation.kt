package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys

fun navTestTag(tab: String): String = "nav-$tab"

const val NAV_BAR_TEST_TAG: String = "nav-bar"
const val NAV_RAIL_TEST_TAG: String = "nav-rail"

internal enum class Tab(val root: Screen, val labelKey: String, val icon: ImageVector) {
    HOME(Screen.DASHBOARD, StringKeys.HOME, TtoIcons.Home),
    PLAY(Screen.OPPONENTS, StringKeys.PLAY, TtoIcons.Play),
    CARDS(Screen.CARDS, StringKeys.CARDS, TtoIcons.Collection),
    STORE(Screen.SHOP, StringKeys.SHOP, TtoIcons.Shop),
}

internal val Screen.tab: Tab?
    get() = when (this) {
        // The course keeps the bar, like the rule book it sits beside: it is a list to read and
        // leave, not a board. Its *lessons* are matches and answer null below.
        Screen.DASHBOARD, Screen.STATS, Screen.QUESTS, Screen.HELP, Screen.AVATAR,
        Screen.LESSONS,
        -> Tab.HOME
        Screen.OPPONENTS, Screen.PVP, Screen.PVP_TABLE -> Tab.PLAY
        Screen.CARDS, Screen.DECKS -> Tab.CARDS
        // The auction house keeps the shop's tab lit, because that is what it is an extension of —
        // buying a card from a player and buying one from a shelf are the same errand.
        Screen.SHOP, Screen.INVENTORY, Screen.AUCTION -> Tab.STORE
        Screen.SPLASH, Screen.TITLE, Screen.PROFILES, Screen.PROFILE_NEW,
        Screen.ACCOUNT, Screen.SERVERS, Screen.COLLECTION_CHOICE,
        Screen.MATCH, Screen.TUTORIAL, Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH,
        Screen.PVP_MATCH, Screen.PVP_CLAIM,
        -> null
    }

@Immutable
internal class Navigation(val current: Tab?, val onSelect: (Tab) -> Unit)

internal val LocalNavigation = staticCompositionLocalOf<Navigation?> { null }

internal val LocalWideLayout = compositionLocalOf { false }

internal val WideLayoutThreshold = 600.dp

@Composable
internal fun SideNavigation(state: Navigation) {
    val strings = LocalStrings.current

    NavigationRail(
        modifier = Modifier.testTag(NAV_RAIL_TEST_TAG),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        for (tab in Tab.entries) {
            NavigationRailItem(
                selected = tab == state.current,
                onClick = { state.onSelect(tab) },
                modifier = Modifier.testTag(navTestTag(tab.name.lowercase())),
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = {
                    Text(
                        text = strings[tab.labelKey],
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
internal fun BottomNavigation(state: Navigation) {
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        NavigationBar(
            modifier = Modifier.testTag(NAV_BAR_TEST_TAG).widthIn(max = ContentMaxWidth),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            for (tab in Tab.entries) {
                NavigationBarItem(
                    selected = tab == state.current,
                    onClick = { state.onSelect(tab) },
                    modifier = Modifier.testTag(navTestTag(tab.name.lowercase())),
                    icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = strings[tab.labelKey],
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
