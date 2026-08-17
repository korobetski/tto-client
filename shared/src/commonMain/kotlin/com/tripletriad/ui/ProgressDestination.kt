package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.tripletriad.model.GameSave
import com.tripletriad.time.Clock
import kotlinx.coroutines.launch

@Composable
@Suppress("LongParameterList")
internal fun ProgressDestination(
    destination: Screen,
    profile: GameSave,
    pvp: PvpSession?,
    account: AccountSession?,
    chooser: Screen,
    choice: Choice,
    clock: Clock,
    settings: SettingsHolder?,
    onNavigate: (Screen) -> Unit,
) {
    // Null only before the settings file has been read, which is behind the splash — so this is
    // unreachable rather than a degraded mode, and "no lesson finished" is the right answer for a
    // player whose progress is not known yet either way.
    val lessonsDone = settings?.value?.lessonsDone ?: 0
    val scope = rememberCoroutineScope()

    when (destination) {
        // The course. Its lessons are a match screen and so live with the matches in
        // `MatchDestinations`; this is only the list in front of them.
        Screen.LESSONS -> LessonsScreen(
            profile = profile,
            done = lessonsDone,
            onPlay = { lesson ->
                choice.lesson = lesson
                onNavigate(Screen.TUTORIAL)
            },
            onBack = { onNavigate(Screen.DASHBOARD) },
        )

        else -> DashboardScreen(
            profile = profile,
            // For the quest badge, and read here rather than inside the screen so the dashboard
            // and [QuestsScreen] cannot disagree about what day it is.
            at = clock.nowMillis(),
            onPlay = { onNavigate(Screen.OPPONENTS) },
            onStats = { onNavigate(Screen.STATS) },
            onQuests = { onNavigate(Screen.QUESTS) },
            onPvp = pvp?.let { { onNavigate(Screen.PVP) } },
            pvpBadge = pvpBadge(pvp),
            // The collection and the shelf are the navigation bar's own two entries and are not
            // repeated here; these two open the *other* tab of each — see [DashboardScreen].
            onDecks = { onNavigate(Screen.DECKS) },
            onInventory = { onNavigate(Screen.INVENTORY) },
            onHelp = { onNavigate(Screen.HELP) },
            onLessons = { onNavigate(Screen.LESSONS) },
            lessonsBadge = "${lessonsDone.coerceAtMost(LAST_LESSON + 1)} / ${LAST_LESSON + 1}",
            // With a server, Logout means *sign out*: the token is dropped and the session ended,
            // not merely the screen changed. That is the distinction the original never made — its
            // Logout navigated away and left `Game.PROFILE_DATAS` loaded — and here it matters,
            // because leaving the token behind on a shared device would leave the account behind.
            onLogout = {
                if (account != null) scope.launch { account.signOut() }
                onNavigate(chooser)
            },
        )
    }
}

private fun pvpBadge(pvp: PvpSession?): String? = when {
    pvp == null -> null
    pvp.claims.isNotEmpty() -> "${pvp.claims.size}"
    pvp.match != null -> DOT_SEPARATOR
    else -> null
}
