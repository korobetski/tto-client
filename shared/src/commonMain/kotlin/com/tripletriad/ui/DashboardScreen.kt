package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.DailyQuestRepository
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val DASHBOARD_PLAY_TEST_TAG: String = "dashboard-play"
const val DASHBOARD_PVP_TEST_TAG: String = "dashboard-pvp"
const val DASHBOARD_STATS_TEST_TAG: String = "dashboard-stats"
const val DASHBOARD_QUESTS_TEST_TAG: String = "dashboard-quests"

const val DASHBOARD_QUESTS_BADGE_TEST_TAG: String = "dashboard-quests-badge"
const val DASHBOARD_DECKS_TEST_TAG: String = "dashboard-decks"
const val DASHBOARD_INVENTORY_TEST_TAG: String = "dashboard-inventory"
const val DASHBOARD_HELP_TEST_TAG: String = "dashboard-help"
const val DASHBOARD_LESSONS_TEST_TAG: String = "dashboard-lessons"
const val DASHBOARD_LOGOUT_TEST_TAG: String = "dashboard-logout"

@Composable
@Suppress("LongParameterList")
internal fun DashboardScreen(
    profile: GameSave,
    at: Long,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    onQuests: () -> Unit,
    onPvp: (() -> Unit)?,
    pvpBadge: String? = null,
    onDecks: () -> Unit,
    onInventory: () -> Unit,
    onHelp: () -> Unit,
    onLessons: () -> Unit,
    lessonsBadge: String,
    onLogout: () -> Unit,
) {
    val strings = LocalStrings.current
    // Read, never written: `statuses` derives the day's draw when the save has not been credited
    // today, so the badge is right on a character who has not played yet. See [QuestsScreen].
    val quests = remember(profile, at) { DailyQuestRepository().statuses(profile, at) }

    // The title is the character's name rather than a screen name: the original had no title here
    // either — the `UserBar` in the corner was the only thing identifying whose dashboard it was.
    ScreenScaffold(
        title = profile.username,
        onBack = onLogout,
        actions = { CharacterActions(profile) },
    ) {
        LevelBar(profile)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
            // A scrolling grid has no natural bottom margin, and the last row would otherwise sit
            // against the edge of a short window.
            contentPadding = PaddingValues(bottom = SpaceMd),
        ) {
            // Play is on the navigation bar too, and is the one repetition worth keeping: it is
            // what the screen is *for*, and a hero action that also has a bar entry is what every
            // Material app with a primary task does.
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeCard(
                    label = strings[StringKeys.PLAY],
                    icon = TtoIcons.Play,
                    tag = DASHBOARD_PLAY_TEST_TAG,
                    accented = true,
                    onClick = onPlay,
                )
            }
            // The two that are a tab *inside* a bar destination. The collection and the shelf are
            // not here: the bar reaches both in one tap, and a home screen that repeats its own
            // navigation bar is a home screen with nothing of its own to say.
            item {
                HomeCard(
                    label = strings[StringKeys.CARD_DECKS],
                    icon = TtoIcons.Collection,
                    tag = DASHBOARD_DECKS_TEST_TAG,
                    onClick = onDecks,
                )
            }
            item {
                HomeCard(
                    label = strings[StringKeys.INVENTORY],
                    icon = TtoIcons.Shop,
                    tag = DASHBOARD_INVENTORY_TEST_TAG,
                    onClick = onInventory,
                )
            }
            item {
                HomeCard(strings[StringKeys.PROFILE], TtoIcons.Person, DASHBOARD_STATS_TEST_TAG) {
                    onStats()
                }
            }
            item {
                HomeCard(
                    label = strings[StringKeys.QUESTS],
                    icon = TtoIcons.Quest,
                    tag = DASHBOARD_QUESTS_TEST_TAG,
                    badge = "${quests.count { it.isCompleted }} / ${quests.size}",
                    onClick = onQuests,
                )
            }
            item {
                HomeCard(
                    label = strings[StringKeys.HELP],
                    icon = TtoIcons.Help,
                    tag = DASHBOARD_HELP_TEST_TAG,
                    onClick = onHelp,
                )
            }
            // Beside the rule book, which is what the course ends at and what a player who wants
            // one rule rather than a lesson about it should reach in the same tap. The badge is the
            // quests card's, for the same reason: a course is a thing you are part-way through, and
            // the number is what says so without opening it.
            item {
                HomeCard(
                    label = strings[StringKeys.LESSONS],
                    icon = TtoIcons.Quest,
                    tag = DASHBOARD_LESSONS_TEST_TAG,
                    badge = lessonsBadge,
                    onClick = onLessons,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeCard(
                    label = strings[StringKeys.MULTIPLAYER],
                    icon = TtoIcons.Person,
                    tag = DASHBOARD_PVP_TEST_TAG,
                    // A match still running, or a prize still uncollected. The quests card has
                    // carried a badge from the start; this one had nothing to say until a PvP
                    // match could end owing somebody a card on a timer.
                    badge = pvpBadge,
                    // Enabled only with a server behind it. Playing another person is the one
                    // thing in this game that cannot happen offline — see `PvpClient` — so a
                    // local profile gets the row and an explanation rather than a dead tap.
                    enabled = onPvp != null,
                    onClick = { onPvp?.invoke() },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LogoutRow(label = strings[StringKeys.LOGOUT], onClick = onLogout)
            }
        }
    }
}

@Composable
internal fun HomeCard(
    label: String,
    icon: ImageVector,
    tag: String,
    accented: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val audio = LocalAudio.current
    val container = if (accented) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        accented -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = CARD_MIN_HEIGHT).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            // Dimmed rather than a different colour: a disabled card is the same destination, and
            // `Multiplayer` on a local profile has to stay readable enough to be understood as
            // "not now" instead of as a rendering fault. See `DashboardScreen`'s own note.
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = SpaceMd, vertical = SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconMd),
            )
            // The badge sits **under** the label rather than beside it. Beside it, the two shared
            // the width of a half-grid card and the label lost: `Quêtes journalières` came out as
            // `Quêtes jo / urnalières`, broken mid-word, because the longest word in it no longer
            // fitted the column the badge had left. Under it the label gets the full width and the
            // count reads as what it is — a subtitle about the destination above it.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = SUBDUED),
                        maxLines = 1,
                        softWrap = false,
                        // Derived from the card's own tag rather than passed: a badge belongs to a
                        // card, and one tag is one thing to keep in step instead of two.
                        modifier = Modifier.testTag("$tag-badge"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoutRow(label: String, onClick: () -> Unit) {
    val audio = LocalAudio.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = {
                audio.play(Sound.UI_CLICK)
                onClick()
            },
            modifier = Modifier.testTag(DASHBOARD_LOGOUT_TEST_TAG),
        ) {
            Icon(
                imageVector = TtoIcons.Logout,
                contentDescription = null,
                modifier = Modifier.size(IconMd),
            )
            Text(text = label, modifier = Modifier.padding(start = SpaceSm))
        }
    }
}

private val CARD_MIN_HEIGHT = 72.dp
