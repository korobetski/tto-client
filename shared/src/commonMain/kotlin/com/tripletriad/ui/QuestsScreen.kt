package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

fun questRowTestTag(id: String): String = "quest-$id"

fun questProgressTestTag(id: String): String = "quest-progress-$id"

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
            Meter(fraction = status.progress.fraction)
        }
    }
}

internal fun DailyQuest.label(strings: Strings, nameFor: (String) -> String): String =
    when (val objective = objective) {
        is Objective.BeatOpponent -> strings.format(labelKey, nameFor(objective.iconId))
        is Objective.WinWithRule -> strings.format(labelKey, strings[objective.ruleKey])
        else -> strings[labelKey]
    }

internal fun DailyQuest.label(
    strings: Strings,
    opponents: NpcCatalog?,
    formatId: String,
): String = label(strings) { iconId ->
    opponents?.byIcon(iconId, formatId)?.let { strings[it.nameKey] } ?: iconId
}
