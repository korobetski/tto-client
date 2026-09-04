package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.MatchTally
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.MatchResult
import com.tripletriad.model.OpponentKind
import com.tripletriad.time.isoDate
import kotlin.math.roundToInt

const val HISTORY_LIST_TEST_TAG: String = "history-list"
const val HISTORY_EMPTY_TEST_TAG: String = "history-empty"
const val HISTORY_TALLY_TEST_TAG: String = "history-tally"
const val HISTORY_FORM_TEST_TAG: String = "history-form"

fun historyRowTestTag(id: String): String = "history-$id"

/**
 * Every match this character has finished, newest first.
 *
 * ### What it says that the profile screen cannot
 *
 * `Stats` holds three running totals, and three numbers cannot answer the question a player
 * actually has after a bad evening: *what happened*. A run of four defeats to one opponent, a
 * format that never goes well, a night that paid nothing — all of it is in the record and none of
 * it is in the counters.
 *
 * ### Recorded, not reconstructed
 *
 * The rows are what the **server settled**, written when it settled them — see [MatchJournal].
 * Nothing here recomputes a result from a board, and nothing asks the network: the profile screen
 * is reachable offline and a history that empties when the connection does is a history nobody
 * would open twice.
 *
 * ### What is deliberately absent
 *
 * A duration, because nothing on the wire carries when a match was dealt — see
 * `PveMatchView.asRecord`. And no MGP-per-day curve: the rows carry what each match paid, but a
 * daily total is a different screen's question and a chart of a dozen points is decoration.
 */
@Composable
internal fun HistoryScreen(
    profile: GameSave,
    records: List<MatchRecord>,
    isLoading: Boolean,
    opponents: NpcCatalog?,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val tally = remember(records) { MatchTally.of(records) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.HISTORY], onBack = onBack) {
        if (isLoading) {
            LoadingNote(HISTORY_EMPTY_TEST_TAG)
            return@CharacterScaffold
        }
        if (records.isEmpty()) {
            EmptyNote(strings[StringKeys.NO_HISTORY], HISTORY_EMPTY_TEST_TAG)
            return@CharacterScaffold
        }

        HistorySummary(tally)
        FormStrip(records)

        LazyColumn(
            modifier = Modifier
                .testTag(HISTORY_LIST_TEST_TAG)
                .fillMaxWidth()
                .weight(1f)
                .padding(top = SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            items(records, key = { it.id }) { record ->
                HistoryRow(record = record, opponents = opponents)
            }
        }
    }
}

/**
 * The tally over what is **on this device**, which is not the profile's own count.
 *
 * Said in the same breath as the number, because the two disagree on any character older than the
 * history — 2000 rows is the cap, and a profile that predates this screen has none at all. A
 * summary that quietly reported a different total from the profile screen's would be the more
 * confusing of the two options.
 */
@Composable
private fun HistorySummary(tally: MatchTally) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(HISTORY_TALLY_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.format(StringKeys.HISTORY_KEPT, "${tally.played}"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(tally.winRate * PERCENT).roundToInt()}%",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
        Meter(fraction = tally.winRate)
        Text(
            text = "${tally.wins} ${strings[StringKeys.WINS]}$DOT_SEPARATOR" +
                "${tally.losses} ${strings[StringKeys.DEFEATS]}$DOT_SEPARATOR" +
                "${tally.draws} ${strings[StringKeys.DRAWS]}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The last [FORM_LENGTH] results as pips, oldest at the left.
 *
 * A strip and not a curve. A rolling win rate over a few dozen matches is mostly the noise of the
 * window it is averaged over, and it hides the thing a player is actually looking for — whether the
 * last four went badly. Three colours carry that in one glance and survive being 12 dp tall, which
 * is the size a summary can afford above a list.
 *
 * Colour is not the only channel: the row is ordered and a screen reader reads the whole strip's
 * description, which names the run in words.
 */
@Composable
private fun FormStrip(records: List<MatchRecord>) {
    val strings = LocalStrings.current
    // Newest first in the list, so the strip is reversed to read left-to-right as time does.
    val form = remember(records) { records.take(FORM_LENGTH).reversed() }
    if (form.size < FORM_MINIMUM) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = strings[StringKeys.HISTORY_FORM],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
        )
        // Named as one run rather than as twenty coloured boxes, because that is what it is: a
        // reader that announced "graphic, graphic, graphic" twenty times would be reading the
        // implementation. Colour is the only channel the pips themselves have, so the sentence is
        // not a courtesy — it is the strip.
        val spoken = remember(form) { form.map { it.result } }
        Row(
            modifier = Modifier
                .testTag(HISTORY_FORM_TEST_TAG)
                .fillMaxWidth()
                .semantics {
                    contentDescription = spoken.joinToString(DOT_SEPARATOR) { it.word(strings) }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (record in form) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(FormPipHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(record.result.tone()),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(record: MatchRecord, opponents: NpcCatalog?) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(historyRowTestTag(record.id))
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A pip in front of the name, so the result reads before the sentence does — the same
            // three colours the strip above uses, which is what makes the strip legible at all.
            Box(
                modifier = Modifier
                    .size(ResultPipSize)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(record.result.tone()),
            )
            Text(
                text = record.opponentLabel(strings, opponents),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${record.ownScore} — ${record.opponentScore}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = record.facts(strings),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // The rules the match was played under, which is half of why a defeat happened. Elided
        // rather than wrapped: a Roulette board can name six of them and the row is not the place
        // to read all six — the point is to recognise the one that mattered.
        val ruleNames = remember(record.rules) { record.rules.activeRuleKeys() }
        if (ruleNames.isNotEmpty()) {
            Text(
                text = ruleNames.joinToString(DOT_SEPARATOR) { strings[it] },
                color = MaterialTheme.colorScheme.secondary.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The opponent as this locale writes them.
 *
 * A row stores an opponent's `iconID`, never their translated name — see `PveMatchView.asRecord`.
 * Resolving it here rather than at the point of writing is what makes a history written in English
 * read in French, and it is why an opponent a later build removes degrades to their id rather than
 * to a blank.
 *
 * A person's row is the exception and carries the name it was played against, because there is no
 * catalogue to look one up in.
 */
private fun MatchRecord.opponentLabel(strings: Strings, opponents: NpcCatalog?): String {
    val stored = opponentName.orEmpty()
    if (opponentKind == OpponentKind.PVP) return stored
    return opponents?.byIcon(stored, formatId)?.let { strings[it.nameKey] } ?: stored
}

/** The day, what it paid, and — for a person — that it was one. */
private fun MatchRecord.facts(strings: Strings): String = buildList {
    add(isoDate(timestamp))
    if (opponentKind == OpponentKind.PVP) add(strings[StringKeys.MULTIPLAYER])
    // Signed, because a wagered match can take MGP as well as pay it — see `PvpMatchView.asRecord`,
    // which folds the stake in for exactly this reason.
    if (mgpDelta != 0) add("${mgpDelta.signed()} ${strings[StringKeys.MGP]}")
    if (xpGained != 0L) add("+$xpGained ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)

private fun Int.signed(): String = if (this > 0) "+$this" else "$this"

/**
 * A result in the words the board itself used.
 *
 * `STR_YOU_WIN` and not `STR_WINS`: the second is a column heading on the profile screen and reads
 * as a count, and a history is a list of things that happened rather than a tally of them.
 */
private fun MatchResult.word(strings: Strings): String = when (this) {
    MatchResult.WIN -> strings[StringKeys.YOU_WIN]
    MatchResult.LOSE -> strings[StringKeys.YOU_LOSE]
    MatchResult.DRAW -> strings[StringKeys.DRAW]
}

/**
 * The colour a result is drawn in, here and in the strip.
 *
 * The theme's own three: what a win already looks like on the result panel, what an error looks
 * like everywhere, and the outline for a draw — which is neither and should not borrow either.
 */
@Composable
private fun MatchResult.tone(): Color = when (this) {
    MatchResult.WIN -> MaterialTheme.colorScheme.tertiary
    MatchResult.LOSE -> MaterialTheme.colorScheme.error
    MatchResult.DRAW -> MaterialTheme.colorScheme.outline
}

/** How many results the strip shows — a fortnight of play at a handful of matches a night. */
private const val FORM_LENGTH = 20

/** Below this a strip is three pips and a lie about there being a trend. */
private const val FORM_MINIMUM = 3

private const val PERCENT = 100

private val FormPipHeight = 12.dp
private val ResultPipSize = 10.dp
