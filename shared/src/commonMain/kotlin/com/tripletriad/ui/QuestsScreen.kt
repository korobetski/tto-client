package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.DailyQuestRepository
import com.tripletriad.data.DailyQuestStatus
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.DailyQuest
import com.tripletriad.model.GameSave
import com.tripletriad.model.Objective
import com.tripletriad.time.isoDate
import com.tripletriad.ui.theme.LocalTtoColors

const val QUESTS_LIST_TEST_TAG: String = "quests-list"
const val QUESTS_RESET_TEST_TAG: String = "quests-reset"
const val QUESTS_NONE_TEST_TAG: String = "quests-none"

/** `quest-q-win-3` — one row, by the quest's own id. */
fun questRowTestTag(id: String): String = "quest-$id"

/** `quest-progress-q-win-3` — the `1 / 3` counter, which is what a test reads. */
fun questProgressTestTag(id: String): String = "quest-progress-$id"

/**
 * The three quests of the day — the one screen in this app with no AS3 ancestor at all.
 *
 * Everything else here is a port of something; daily quests are new, and the only reason this
 * screen can be written the way it is is that [DailyQuestRepository.statuses] answers without
 * writing. A player who has not played today still sees what is on offer, because the day's draw
 * is *derived* from the character's creation date and the UTC day rather than assigned by a
 * request. See `DailyQuests` for why the draw is nonetheless pinned the moment one is credited.
 *
 * ### It reads like the achievements list on purpose
 *
 * A quest and an achievement are the same thing to a reader — a label, how far along, a reward —
 * and the two differ only in when they reset. `StatsScreen` earned its shape recently and this
 * takes it: icon, label, `n / m`, bar, and a **date instead of the counter** once the thing is
 * done. Inventing a second progress idiom would have been two vocabularies for one concept.
 *
 * ### Offline is not a special case
 *
 * Credit runs inside `MatchRewards.credit`, which is the same pure function a local profile and a
 * server-backed one both go through. So a character with no account has quests, they advance, and
 * they pay — with no branch anywhere in this file.
 *
 * @param at the instant the day is read from. `Clock.nowMillis()`; a parameter rather than a read
 *   so a test can sit on either side of midnight.
 * @param opponents used only to name the opponent a [Objective.BeatOpponent] quest asks for. Null
 *   before `npcs.json` has loaded, which the startup gate makes unreachable here — the label then
 *   falls back to the icon id rather than to nothing.
 */
@Composable
internal fun QuestsScreen(
    profile: GameSave,
    at: Long,
    opponents: NpcCatalog?,
    formatId: String,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val quests = remember(profile, at) { DailyQuestRepository().statuses(profile, at) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.QUESTS], onBack = onBack) {
        Text(
            text = strings.format(StringKeys.QUESTS_RESET, isoDate(at)),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag(
                QUESTS_RESET_TEST_TAG,
            ).padding(top = SpaceSm, bottom = 8.dp),
        )

        if (quests.isEmpty()) {
            // Reachable only from a save pinned to ids a later build removed from the catalogue —
            // `statuses` drops what it cannot resolve rather than rendering a blank row.
            EmptyNote(strings[StringKeys.NO_QUEST], QUESTS_NONE_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(QUESTS_LIST_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(quests, key = { it.quest.id }) { status ->
                    QuestRow(status = status, opponents = opponents, formatId = formatId)
                }
            }
        }
    }
}

/**
 * One quest.
 *
 * The completed form drops the counter for the reason `AchievementRow` does: `3 / 3` above a bar
 * pinned at full says nothing a player wants, and what a finished quest has to say is that it paid.
 */
@Composable
private fun QuestRow(
    status: DailyQuestStatus,
    opponents: NpcCatalog?,
    formatId: String,
) {
    val strings = LocalStrings.current
    val quest = status.quest

    Column(
        modifier = Modifier
            .testTag(questRowTestTag(quest.id))
            .fillMaxWidth()
            .rowSurface(selected = status.isCompleted)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            AchievementIcon(iconId = quest.iconId, description = "", size = 28.dp)
            Text(
                text = quest.label(strings, opponents, formatId),
                color = MaterialTheme.colorScheme.onSurface
                    .copy(alpha = if (status.isCompleted) 1f else 0.65f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The reward is shown whether or not it has been paid: before, it is the reason to
            // play; after, it is the receipt.
            Text(
                text = "${quest.reward.mgp} ${strings[StringKeys.MGP]}",
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }

        if (status.isCompleted) {
            Text(
                text = strings[StringKeys.QUEST_DONE],
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(questProgressTestTag(quest.id)),
            )
        } else {
            Text(
                text = "${status.progress.current} / ${status.progress.target}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(questProgressTestTag(quest.id)),
            )
            QuestMeter(fraction = status.progress.fraction)
        }
    }
}

/**
 * The label, with the one thing the objective names filled in.
 *
 * Two of the objectives carry a parameter — an opponent and a rule — and both resolve through keys
 * the bundles already hold: a rule constant *is* an i18n key (`RULE_SAME` is in all four bundles),
 * and an opponent's `nameKey` is how every other screen names one. So this adds no strings beyond
 * the six quest lines themselves.
 *
 * @param nameFor turns an opponent's icon id into a name. A function rather than a catalogue,
 *   because the two callers know the answer by different routes: this screen looks the opponent up,
 *   and the end-of-match panel has just played them. Falls back to the icon id, which is ugly and
 *   true — better than a blank where a name should be.
 */
internal fun DailyQuest.label(strings: Strings, nameFor: (String) -> String): String =
    when (val objective = objective) {
        is Objective.BeatOpponent -> strings.format(labelKey, nameFor(objective.iconId))
        is Objective.WinWithRule -> strings.format(labelKey, strings[objective.ruleKey])
        else -> strings[labelKey]
    }

/** [label] with the opponent resolved through the catalogue, which is what a list needs. */
private fun DailyQuest.label(
    strings: Strings,
    opponents: NpcCatalog?,
    formatId: String,
): String = label(strings) { iconId ->
    opponents?.byIcon(iconId, formatId)?.let { strings[it.nameKey] } ?: iconId
}

/** The bar `StatsScreen` draws, in the same colour. Four lines, not worth a shared file. */
@Composable
private fun QuestMeter(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(MeterHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.tertiary),
        )
    }
}

private val MeterHeight = 4.dp
