package com.tripletriad.ui

import androidx.compose.runtime.Composable
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.time.Clock

@Composable
@Suppress("LongParameterList")
internal fun ProgressDestination(
    destination: Screen,
    profile: GameSave,
    onConfirmEmail: (() -> Unit)?,
    pvp: PvpSession?,
    onLogout: () -> Unit,
    choice: Choice,
    startup: StartupState,
    clock: Clock,
    settings: SettingsHolder?,
    onOptions: () -> Unit,
    onQuit: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val strings = LocalStrings.current
    // Null only before the settings file has been read, which is behind the splash — so this is
    // unreachable rather than a degraded mode, and "no lesson finished" is the right answer for a
    // player whose progress is not known yet either way.
    val lessonsDone = settings?.value?.lessonsDone ?: 0

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
            // For the quest lines, and read here rather than inside the screen so the lobby and
            // [QuestsScreen] cannot disagree about what day it is.
            at = clock.nowMillis(),
            // So a quest that names an opponent can name them rather than print an icon id. Both
            // are behind the splash, so the fallbacks are unreachable in practice.
            opponents = startup.opponents,
            formatId = startup.formats?.default?.id.orEmpty(),
            resume = lobbyResume(pvp, strings, onNavigate),
            onPlay = { onNavigate(Screen.OPPONENTS) },
            onPvp = pvp?.let { { onNavigate(Screen.PVP) } },
            onConfirmEmail = onConfirmEmail,
            onStats = { onNavigate(Screen.STATS) },
            onQuests = { onNavigate(Screen.QUESTS) },
            // The collection and the shelf are the navigation bar's own two entries; these two open
            // the *other* tab of each, which the bar cannot reach in one tap.
            onDecks = { onNavigate(Screen.DECKS) },
            onInventory = { onNavigate(Screen.INVENTORY) },
            onHelp = { onNavigate(Screen.HELP) },
            onLessons = { onNavigate(Screen.LESSONS) },
            lessonsBadge = "${lessonsDone.coerceAtMost(LAST_LESSON + 1)} / ${LAST_LESSON + 1}",
            onAuction = { onNavigate(Screen.AUCTION) },
            onOptions = onOptions,
            onLogout = onLogout,
            onQuit = onQuit,
        )
    }
}

/**
 * What the server is holding for this player, if anything.
 *
 * A prize first: it is the one of the two that expires. `PvpMatchRow.CLAIM_MILLIS` runs out and the
 * server picks for them, so a card nobody was told about is a card somebody else's algorithm
 * chooses — which is the whole reason this is on the lobby rather than two screens deep.
 */
private fun lobbyResume(
    pvp: PvpSession?,
    strings: com.tripletriad.i18n.Strings,
    onNavigate: (Screen) -> Unit,
): LobbyResume? = when {
    pvp == null -> null

    pvp.claims.isNotEmpty() -> LobbyResume(
        label = strings[StringKeys.PVP_CLAIM_TITLE],
        note = strings.format(StringKeys.PVP_CLAIM_PENDING, pvp.claims.size.toString()),
        onOpen = { onNavigate(Screen.PVP_CLAIM) },
    )

    pvp.match != null -> LobbyResume(
        label = strings.format(
            StringKeys.MATCH_RESUME,
            pvp.match?.opponentName.orEmpty(),
        ),
        note = strings[StringKeys.MULTIPLAYER],
        onOpen = { onNavigate(Screen.PVP_MATCH) },
    )

    else -> null
}
