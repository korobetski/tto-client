package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.DailyQuestRepository
import com.tripletriad.data.DailyQuestStatus
import com.tripletriad.data.NpcCatalog
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

/** The card at the top, present only when the server is holding something for this player. */
const val DASHBOARD_RESUME_TEST_TAG: String = "dashboard-resume"

const val DASHBOARD_AUCTION_TEST_TAG: String = "dashboard-auction"

/** The overflow the three commands that leave the session live behind. */
const val DASHBOARD_MENU_TEST_TAG: String = "dashboard-menu"

const val DASHBOARD_OPTIONS_TEST_TAG: String = "dashboard-options"
const val DASHBOARD_QUIT_TEST_TAG: String = "dashboard-quit"

/**
 * Something the server is holding that this player has not dealt with.
 *
 * A live match, or a prize on a deadline. Both were reachable only by walking into the multiplayer
 * screen and noticing — `PvpSession.resume()` has answered this question since it was written and
 * nothing outside its own tests ever asked it at the lobby.
 */
@Immutable
internal class LobbyResume(
    val label: String,
    val note: String,
    val onOpen: () -> Unit,
)

/**
 * The lobby, as a report on what is happening rather than a menu of places to go.
 *
 * It used to be eight cards over a four-entry navigation bar, and half the cards were the bar said
 * twice. What is left is arranged by *when it matters*: what the server is holding for you, what
 * resets tonight, what you came here to do, and everything else. The three commands that end a
 * session — settings, sign out, quit — moved into the overflow, because a control that leaves is
 * not a destination and should not be shaped like one.
 *
 * There is no back arrow. There was one, and it signed the player out.
 */
@Composable
@Suppress("LongParameterList")
internal fun DashboardScreen(
    profile: GameSave,
    at: Long,
    opponents: NpcCatalog?,
    formatId: String,
    resume: LobbyResume?,
    onPlay: () -> Unit,
    onPvp: (() -> Unit)?,
    // Null when there is nothing to confirm. See the multiplayer card, which is the one
    // place an unconfirmed address costs a player anything.
    onConfirmEmail: (() -> Unit)?,
    onStats: () -> Unit,
    onQuests: () -> Unit,
    onDecks: () -> Unit,
    onInventory: () -> Unit,
    onHelp: () -> Unit,
    onLessons: () -> Unit,
    lessonsBadge: String,
    onAuction: () -> Unit,
    onOptions: () -> Unit,
    onLogout: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    // Read, never written: `statuses` derives the day's draw when the save has not been credited
    // today, so the count is right on a character who has not played yet. See [QuestsScreen].
    val quests = remember(profile, at) { DailyQuestRepository().statuses(profile, at) }
    // This deployment's thresholds, not this build's — see [LocalUnlocks].
    val unlocks = LocalUnlocks.current
    val multiplayerOpen = unlocks.allowsMultiplayer(profile)

    ScreenScaffold(
        title = profile.username,
        // The root of a signed-in session has nothing above it. What used to be here was
        // `onLogout` behind a chevron, which is the one thing a back arrow must never mean.
        onBack = null,
        actions = {
            CharacterActions(profile)
            LobbyMenu(
                onOptions = onOptions,
                onLogout = onLogout,
                onQuit = onQuit,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpaceMd),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            // The lobby's only way into the record, so the badge answers to the lobby's
            // name for it rather than to the portrait's.
            LevelBar(profile, onAvatar = onStats, avatarTag = DASHBOARD_STATS_TEST_TAG)

            resume?.let {
                SectionHeader(strings[StringKeys.LOBBY_RESUME], Modifier.padding(top = SpaceSm))
                ResumeCard(it)
            }

            SectionHeader(strings[StringKeys.LOBBY_TODAY], Modifier.padding(top = SpaceSm))
            QuestsCard(
                quests = quests,
                opponents = opponents,
                formatId = formatId,
                onOpen = onQuests,
            )

            SectionHeader(strings[StringKeys.PLAY], Modifier.padding(top = SpaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.PLAY],
                        icon = TtoIcons.Play,
                        tag = DASHBOARD_PLAY_TEST_TAG,
                        accented = true,
                        onClick = onPlay,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.MULTIPLAYER],
                        icon = if (multiplayerOpen) TtoIcons.Person else TtoIcons.Lock,
                        tag = DASHBOARD_PVP_TEST_TAG,
                        // Two different reasons to be shut, and the badge says which. Without a
                        // server there is nobody to play — see `PvpClient`; below the level there
                        // is, and the answer is "not yet" rather than "not here".
                        badge = when {
                            // The level first, deliberately, and it is the *opposite* order to the
                            // server's refusal. That one names the address first because a player
                            // who has hit the door can act on it immediately; here the door is
                            // usually still far away, and telling a level-one player to confirm an
                            // address would suggest that confirming is what opens it.
                            !multiplayerOpen -> strings.format(
                                StringKeys.LOCKED_LEVEL,
                                unlocks.multiplayer.toString(),
                            )

                            // Levelled, and stopped by the one thing left. Without this the card
                            // would be a dead end: shut, with the remedy on a screen the player
                            // last saw when they registered and no way back to it.
                            onConfirmEmail != null -> strings[StringKeys.CONFIRM_NEEDED]

                            else -> null
                        },
                        enabled = onPvp != null && multiplayerOpen,
                        onClick = { onConfirmEmail?.invoke() ?: onPvp?.invoke() },
                    )
                }
            }

            SectionHeader(strings[StringKeys.LOBBY_MORE], Modifier.padding(top = SpaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.CARD_DECKS],
                        icon = TtoIcons.Collection,
                        tag = DASHBOARD_DECKS_TEST_TAG,
                        onClick = onDecks,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.INVENTORY],
                        icon = TtoIcons.Shop,
                        tag = DASHBOARD_INVENTORY_TEST_TAG,
                        onClick = onInventory,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.LESSONS],
                        icon = TtoIcons.Quest,
                        tag = DASHBOARD_LESSONS_TEST_TAG,
                        badge = lessonsBadge,
                        onClick = onLessons,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomeCard(
                        label = strings[StringKeys.HELP],
                        icon = TtoIcons.Help,
                        tag = DASHBOARD_HELP_TEST_TAG,
                        onClick = onHelp,
                    )
                }
            }

            // Under "Also", with no heading of its own. It had one — "Coming soon" — from
            // when the house was a placeholder, and a section that says a working screen is not
            // here yet is worse than no section at all.
            AuctionBanner(open = unlocks.allowsAuction(profile), onClick = onAuction)
        }
    }
}

@Composable
private fun ResumeCard(resume: LobbyResume) {
    TtoCard(
        modifier = Modifier.testTag(DASHBOARD_RESUME_TEST_TAG).fillMaxWidth(),
        onClick = resume.onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Icon(
                imageVector = TtoIcons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(IconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resume.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = resume.note,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The day's three quests, with their meters rather than with a count of them.
 *
 * The count is still there — `QuestsUiTest` reads it, and it is what a glance wants — but under it
 * are the three lines that say *which* one is nearly done. A badge saying `0 / 3` is a number a
 * player has to open a screen to act on.
 */
@Composable
private fun QuestsCard(
    quests: List<DailyQuestStatus>,
    opponents: NpcCatalog?,
    formatId: String,
    onOpen: () -> Unit,
) {
    val strings = LocalStrings.current

    TtoCard(
        modifier = Modifier.testTag(DASHBOARD_QUESTS_TEST_TAG).fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings[StringKeys.QUESTS],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${quests.count { it.isCompleted }} / ${quests.size}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(DASHBOARD_QUESTS_BADGE_TEST_TAG),
                )
            }

            for (status in quests) {
                QuestLine(status = status, opponents = opponents, formatId = formatId)
            }
        }
    }
}

@Composable
private fun QuestLine(
    status: DailyQuestStatus,
    opponents: NpcCatalog?,
    formatId: String,
) {
    val strings = LocalStrings.current
    val done = status.isCompleted

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = status.quest.label(strings, opponents, formatId),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done) SUBDUED else MUTED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Meter(
            fraction = if (done) 1f else status.progress.fraction,
            modifier = Modifier.width(MeterWidth),
            colour = if (done) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun AuctionBanner(open: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current

    TtoCard(
        modifier = Modifier.testTag(DASHBOARD_AUCTION_TEST_TAG).fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Icon(
                imageVector = TtoIcons.Shop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(
                    alpha = if (open) 1f else SUBDUED,
                ),
                modifier = Modifier.size(IconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[StringKeys.AUCTION],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Nothing under the name once the house is open. The second line is where a
                // *reason it is shut* goes, and an open door has none — the card then reads like
                // the rest of the lobby's, which are their own labels and nothing else.
                if (!open) {
                    Text(
                        text = strings.format(
                            StringKeys.LOCKED_LEVEL,
                            LocalUnlocks.current.auction.toString(),
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Settings, sign out and quit, behind one button.
 *
 * They are together because they are the same kind of thing — none of them is a place in the game —
 * and because the sign-out used to be a text button at the bottom of a scrolling grid, which is a
 * control that is sometimes off the screen.
 */
@Composable
private fun RowScope.LobbyMenu(
    onOptions: () -> Unit,
    onLogout: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }

    IconButton(onClick = { open = true }, modifier = Modifier.testTag(DASHBOARD_MENU_TEST_TAG)) {
        Icon(
            imageVector = TtoIcons.More,
            contentDescription = strings[StringKeys.SETTINGS],
            modifier = Modifier.size(IconMd),
        )
    }

    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        MenuEntry(
            label = strings[StringKeys.SETTINGS],
            icon = TtoIcons.Options,
            tag = DASHBOARD_OPTIONS_TEST_TAG,
        ) {
            open = false
            onOptions()
        }
        MenuEntry(
            label = strings[StringKeys.LOGOUT],
            icon = TtoIcons.Logout,
            tag = DASHBOARD_LOGOUT_TEST_TAG,
        ) {
            open = false
            onLogout()
        }
        MenuEntry(
            label = strings[StringKeys.QUIT],
            icon = TtoIcons.Logout,
            tag = DASHBOARD_QUIT_TEST_TAG,
        ) {
            open = false
            onQuit()
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    val audio = LocalAudio.current

    DropdownMenuItem(
        text = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        modifier = Modifier.testTag(tag),
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(IconSm))
        },
    )
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

private val CARD_MIN_HEIGHT = 68.dp

private val MeterWidth = 56.dp
