package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.DailyQuestRepository
import com.tripletriad.data.DailyQuestStatus
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.WeeklyQuestRepository
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import com.tripletriad.model.questWeekOf
import com.tripletriad.ui.theme.LocalTtoColors

/** The one action the lobby opens on. The tag survives the redesign: it still means "play". */
const val DASHBOARD_PLAY_TEST_TAG: String = "dashboard-play"

const val DASHBOARD_STATS_TEST_TAG: String = "dashboard-stats"
const val DASHBOARD_QUESTS_TEST_TAG: String = "dashboard-quests"

const val DASHBOARD_QUESTS_BADGE_TEST_TAG: String = "dashboard-quests-badge"

/** The week's line inside the day's card — absent when the week's quest is already finished. */
const val DASHBOARD_WEEKLY_TEST_TAG: String = "dashboard-weekly"

const val DASHBOARD_PROGRESS_TEST_TAG: String = "dashboard-progress"
const val DASHBOARD_COLLECTION_TEST_TAG: String = "dashboard-collection"
const val DASHBOARD_HELP_TEST_TAG: String = "dashboard-help"
const val DASHBOARD_LESSONS_TEST_TAG: String = "dashboard-lessons"
const val DASHBOARD_LOGOUT_TEST_TAG: String = "dashboard-logout"

/** The card at the top, present only when the server is holding something for this player. */
const val DASHBOARD_RESUME_TEST_TAG: String = "dashboard-resume"

/** The one remaining door an unconfirmed address is asked through from the lobby. */
const val DASHBOARD_CONFIRM_TEST_TAG: String = "dashboard-confirm"

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
 * What the collection is worth saying at the lobby: how much of the format is in hand.
 *
 * Counted where the catalogue is, not here — the lobby has no business filtering a card table by
 * format, and the collection screen's own count is derived the same way from the same two numbers.
 */
@Immutable
internal class LobbyCollection(val owned: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else owned.toFloat() / total
}

/**
 * The lobby: one thing to do, what resets tonight, and what has been earned so far.
 *
 * The grid is gone. It held Card Decks, Inventory and Play — which is the navigation bar drawn a
 * second time as cards — and it held them because the bar had four entries and nine destinations.
 * The bar has five now and every destination answers to one of them (see [Tab]), so the lobby is
 * free to stop being a menu: what is left is a **single** hero action chosen by urgency, the day's
 * and the week's quests in one card, a progress readout, and one quiet line for the course and the
 * rule book, which belong to no tab and are read once.
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
    collection: LobbyCollection,
    resume: LobbyResume?,
    onPlay: () -> Unit,
    // Null when there is nothing to confirm. It used to hang off the multiplayer card, which the
    // Play tab owns now — so it is its own line here rather than a badge on a card that left.
    onConfirmEmail: (() -> Unit)?,
    onStats: () -> Unit,
    onQuests: () -> Unit,
    onHelp: () -> Unit,
    onLessons: () -> Unit,
    lessonsBadge: String,
    onOptions: () -> Unit,
    onLogout: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    // Read, never written: `statuses` derives the day's draw when the save has not been credited
    // today, so the count is right on a character who has not played yet. See [QuestsScreen].
    val quests = remember(profile, at) { DailyQuestRepository().statuses(profile, at) }
    // And the week's, which the lobby could not see at all until now: the card read one repository
    // and the longest, best-paid objective in the game was invisible from the screen the player
    // opens on.
    val weekly = remember(profile, at) { WeeklyQuestRepository().statuses(profile, at) }

    ScreenScaffold(
        title = profile.username,
        // The root of a signed-in session has nothing above it. What used to be here was
        // `onLogout` behind a chevron, which is the one thing a back arrow must never mean.
        onBack = null,
        // No `CharacterActions`: the purse, the level and the boons are all in the progress card
        // below, and the bar was saying the level a second time fifteen pixels above the meter
        // that measures it.
        actions = {
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
            // Where the player is, before what they can do about it. The lobby opens on the
            // level, the purse and the collection because those are what the session before this
            // one *moved* — the hero below is the same one tap whether it is read first or not,
            // and putting the meter under it made the progress the reward for scrolling.
            SectionHeader(strings[StringKeys.LOBBY_PROGRESS])
            ProgressCard(profile = profile, collection = collection, onOpen = onStats)

            // One hero, never two. A prize on a deadline outranks a live match, a live match
            // outranks "go and play" — and whichever wins is the whole answer to "what now".
            if (resume != null) {
                SectionHeader(strings[StringKeys.LOBBY_RESUME], Modifier.padding(top = SpaceSm))
                ResumeCard(resume)
            } else {
                HomeCard(
                    label = strings[StringKeys.PLAY],
                    icon = TtoIcons.Play,
                    tag = DASHBOARD_PLAY_TEST_TAG,
                    modifier = Modifier.padding(top = SpaceSm),
                    accented = true,
                    onClick = onPlay,
                )
            }

            onConfirmEmail?.let { ConfirmCard(it) }

            SectionHeader(strings[StringKeys.LOBBY_TODAY], Modifier.padding(top = SpaceSm))
            QuestsCard(
                quests = quests,
                weekly = weekly,
                at = at,
                opponents = opponents,
                formatId = formatId,
                onOpen = onQuests,
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = SpaceMd),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            // The two screens that belong to no tab, on one line, at the bottom. They are read
            // once and never again, which is exactly the weight a line has and a card does not.
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                AsideLink(
                    label = strings[StringKeys.LESSONS],
                    badge = lessonsBadge,
                    tag = DASHBOARD_LESSONS_TEST_TAG,
                    modifier = Modifier.weight(1f),
                    onClick = onLessons,
                )
                AsideLink(
                    label = strings[StringKeys.HELP],
                    badge = null,
                    tag = DASHBOARD_HELP_TEST_TAG,
                    modifier = Modifier.weight(1f),
                    onClick = onHelp,
                )
            }
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
 * The one thing an unconfirmed address still costs, said where it can be acted on.
 *
 * It used to be a badge on the multiplayer card. That card is a tab of the Play root now, and a
 * player who never opens that tab would never learn why the door is shut.
 */
@Composable
private fun ConfirmCard(onConfirm: () -> Unit) {
    val strings = LocalStrings.current

    TtoCard(
        modifier = Modifier.testTag(DASHBOARD_CONFIRM_TEST_TAG).fillMaxWidth(),
        onClick = onConfirm,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Icon(
                imageVector = TtoIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconMd),
            )
            Text(
                text = strings[StringKeys.CONFIRM_NEEDED],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The week over the day, in one card.
 *
 * They are one card and not two because they answer the same question — what is worth doing before
 * this resets — and they are in that order because the week is the thing being worked towards while
 * the dailies come and go. The count in the corner stays the *day's*: it is what a glance wants,
 * and `QuestsUiTest` reads it.
 */
@Composable
private fun QuestsCard(
    quests: List<DailyQuestStatus>,
    weekly: List<DailyQuestStatus>,
    at: Long,
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
            if (weekly.isNotEmpty()) {
                Text(
                    text = strings.format(StringKeys.QUESTS_WEEK, questWeekOf(at)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(DASHBOARD_WEEKLY_TEST_TAG),
                )
                for (status in weekly) {
                    QuestLine(
                        status = status,
                        opponents = opponents,
                        formatId = formatId,
                        emphasis = true,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = SpaceSm),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

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
    emphasis: Boolean = false,
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
            color = when {
                emphasis -> LocalTtoColors.current.currency
                done -> MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Meter(
            fraction = if (done) 1f else status.progress.fraction,
            modifier = Modifier.width(MeterWidth),
            colour = when {
                emphasis -> LocalTtoColors.current.currency
                done -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}

/**
 * Collection, level and purse — the three numbers that move, and the way into the record.
 *
 * The collection count is here because it is the progression players actually track and it lived
 * nowhere: not on the lobby, not on the character sheet, only above the grid on the card list where
 * it doubles as a filter readout.
 */
@Composable
private fun ProgressCard(
    profile: GameSave,
    collection: LobbyCollection,
    onOpen: () -> Unit,
) {
    val strings = LocalStrings.current
    val floor = XpTable.thresholdFor(profile.level)
    val ceiling = XpTable.thresholdFor(profile.level + 1)
    // At the top of the table there is no next threshold, so the bar is full rather than dividing
    // by a zero span.
    val span = ceiling - floor
    val fraction = if (span <= 0L) 1f else ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)

    TtoCard(
        modifier = Modifier.testTag(DASHBOARD_PROGRESS_TEST_TAG).fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            AvatarBadge(profile = profile, tag = DASHBOARD_STATS_TEST_TAG)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(
                    text = "${strings[StringKeys.LEVEL]} ${profile.level}$DOT_SEPARATOR" +
                        "${profile.xp} ${strings[StringKeys.XP]}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Meter(fraction = fraction, colour = MaterialTheme.colorScheme.tertiary)

                Text(
                    text = "${strings[StringKeys.COLLECTION]}$DOT_SEPARATOR" +
                        "${collection.owned} / ${collection.total}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(DASHBOARD_COLLECTION_TEST_TAG),
                )
                Meter(
                    fraction = collection.fraction,
                    colour = MaterialTheme.colorScheme.primary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = TtoIcons.Chip,
                    contentDescription = strings[StringKeys.MGP],
                    tint = LocalTtoColors.current.currency,
                    modifier = Modifier.size(IconSm),
                )
                Text(
                    text = "${profile.mgp}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A destination with no tab and no card: a line of text with a count and a chevron. */
@Composable
private fun AsideLink(
    label: String,
    badge: String?,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .testTag(tag)
            .heightIn(min = MinTouchTarget)
            .ttoClickable(onClick = onClick)
            .padding(horizontal = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge != null) {
            Text(
                text = badge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("$tag-badge"),
            )
        }
        Icon(
            imageVector = TtoIcons.Forward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            modifier = Modifier.size(IconSm),
        )
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth().heightIn(min = CARD_MIN_HEIGHT).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            // Dimmed rather than a different colour: a disabled card is the same destination, and
            // `Multiplayer` on a local profile has to stay readable enough to be understood as
            // "not now" instead of as a rendering fault.
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
            // fitted the column the badge had left.
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
