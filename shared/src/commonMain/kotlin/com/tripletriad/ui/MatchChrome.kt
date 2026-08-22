package com.tripletriad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.MatchReward
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.Item
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.Npc
import com.tripletriad.model.PlayResult
import com.tripletriad.ui.theme.LocalTtoColors

/*
 * What surrounds the board: the rules in force, and what the match paid.
 *
 * Split out of `MatchScreen` when it reached the twenty functions detekt allows in one file, and
 * along the seam that was already there — nothing here reads a [com.tripletriad.model.MatchState]
 * or can affect one. `MatchScreen` runs the match; this draws around it.
 */

const val MATCH_SIDE_TEST_TAG: String = "match-side"

const val MATCH_LOG_TEST_TAG: String = "match-log"

const val MATCH_REWARDS_TEST_TAG: String = "match-rewards"

fun matchRewardTestTag(item: Item): String = "match-reward-${itemSlug(item)}"

@Composable
internal fun MatchFrame(
    wide: Boolean,
    side: @Composable () -> Unit,
    content: @Composable ColumnScope.(panelShown: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // **Width is not enough, and a phone held sideways is why.** A 890x411 window clears the
        // 600 dp threshold comfortably, so it used to get the panel — and the panel is a *column*:
        // a portrait, the rules, and a move log that grows downwards. At 411 dp tall it could show
        // the portrait, one chip and the word "Matches" over an empty space, while charging the
        // board 200 dp of width it badly needed. The board came out squeezed so that a panel could
        // display nothing.
        //
        // So the panel asks for height as well, and a phone in landscape now gets the whole width
        // for the board — which is what it wanted the width for.
        val roomForPanel = wide && maxHeight >= SidePanelMinHeight

        if (!roomForPanel) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content(false) }
            return@BoxWithConstraints
        }

        // The panel goes on the **left**. It is context — who is being played, what the rules do,
        // what has happened — and context belongs where reading starts; the board is what the eye
        // should land on afterwards and stay on. On the right it was the last thing before the
        // edge of the screen and the first thing a right-handed thumb covered.
        Row(modifier = Modifier.fillMaxSize()) {
            side()
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content(true) }
        }
    }
}

private val SidePanelMinHeight = 560.dp

@Composable
internal fun MatchSidePanel(
    npc: Npc,
    opponentName: String,
    rules: GameRules,
    log: List<PlayResult>,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(MATCH_SIDE_TEST_TAG)
            .width(SidePanelWidth)
            .fillMaxHeight()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpcPortrait(npc = npc, name = opponentName)
            Text(
                text = opponentName,
                color = CardColor.RED.edge,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // The same tag the banner's name carries: exactly one of the two is ever on
                // screen, so "where is the opponent named" has one answer at either width.
                modifier = Modifier.testTag(MATCH_OPPONENT_TEST_TAG),
            )
        }

        RulesStrip(rules)

        Text(
            text = strings[StringKeys.MATCHES],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
        )
        MoveLog(log, strings)
    }
}

@Composable
internal fun BoardRules(rules: GameRules, wide: Boolean) {
    if (!wide) RulesStrip(rules)
}

@Composable
internal fun rememberMoveLog(key: Any, state: MatchState): List<PlayResult> {
    val log = remember(key) { mutableStateListOf<PlayResult>() }
    LaunchedEffect(key, state.placement) {
        val play = state.lastPlay ?: return@LaunchedEffect
        // Identity, not equality: two placements can produce equal results — the same card cannot
        // be played twice, but a regrouped sudden-death hand can.
        if (log.lastOrNull() !== play) log += play
    }
    return log
}

/** The same log for a refereed match, where the placements arrive as views rather than states. */
@Composable
internal fun rememberViewMoveLog(key: Any, view: MatchView): List<PlayResult> {
    val log = remember(key) { mutableStateListOf<PlayResult>() }
    LaunchedEffect(key, view.placement) {
        val play = view.lastPlay ?: return@LaunchedEffect
        // Identity, not equality — see [rememberMoveLog]. It matters more here: a refereed view is
        // rebuilt from the wire on every response, so two equal results are routine.
        if (log.lastOrNull() !== play) log += play
    }
    return log
}

@Composable
private fun ColumnScope.MoveLog(log: List<PlayResult>, strings: Strings) {
    val game = LocalTtoColors.current

    Column(
        modifier = Modifier
            .testTag(MATCH_LOG_TEST_TAG)
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (play in log) {
            Text(
                text = buildString {
                    append(strings[play.card.nameKey])
                    append(" → ")
                    // Named by the cell the player can point at rather than by an index: `A1` is
                    // the row and column, which is how the board reads.
                    append(cellName(play.position))
                    if (play.captures.isNotEmpty()) append("  +${play.captures.size}")
                },
                color = if (play.player == CardColor.BLUE) {
                    CardColor.BLUE.edge
                } else {
                    CardColor.RED.edge
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A combo is marked rather than counted: `wave >= 1` means the capture cascaded,
            // which is the thing a player wants to know happened and cannot see afterwards.
            if (play.captures.any { it.wave >= 1 }) {
                Text(
                    text = COMBO_MARK,
                    color = game.selectionRing,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun cellName(position: Int): String =
    "${'A' + position / BOARD_COLUMNS}${position % BOARD_COLUMNS + 1}"

@Composable
internal fun RulesStrip(
    rules: GameRules,
    roulette: Boolean = false,
    tag: String? = MATCH_RULES_TEST_TAG,
) {
    val keys = rules.activeRuleKeys()
    if (keys.isEmpty() && !roulette) return
    val strings = LocalStrings.current
    var open by remember(keys) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(horizontal = SpaceSm, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (key in keys) {
                RuleChip(name = strings[key])
            }
            // Last, and named as the pending draw it is: the rules before it are settled, and this
            // says more are coming. `GameRules.roulette` is deliberately not read — that flag means
            // a draw has *happened*, and on a table it has not.
            if (roulette) {
                RuleChip(name = strings[StringKeys.PVP_ROULETTE])
            }
        }
        if (open) {
            for (key in keys) {
                val help = "${key}_HELP"
                // Absent rather than a key on screen where a bundle has no sentence — the four
                // locales do not describe the same set. `CardListScreen` makes the same test.
                if (strings.has(help)) {
                    Text(
                        text = markup("${strings[key]} — ${strings[help]}"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag(ruleHelpTestTag(key)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleChip(name: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = name,
            color = LocalTtoColors.current.selectionRing,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = SpaceSm, vertical = 2.dp),
        )
    }
}

@Composable
@Suppress("LongParameterList")
internal fun OutcomePanel(
    reward: MatchReward,
    opponentName: String,
    cards: Map<Int, Card>,
    next: ScriptExit?,
    onDone: () -> Unit,
    title: String? = null,
) {
    val strings = LocalStrings.current

    // The board is finished and nothing on it is actionable; dimming it says so, and stops a
    // full board of bright card art from competing with the two controls that now matter.
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        OutcomeCard(reward, opponentName, cards, next, onDone, strings, title)
    }
}

@Composable
@Suppress("LongParameterList")
private fun OutcomeCard(
    reward: MatchReward,
    opponentName: String,
    cards: Map<Int, Card>,
    next: ScriptExit?,
    onDone: () -> Unit,
    strings: Strings,
    title: String?,
) {
    // `surfaceContainerHigh` and `extraLarge`, which are what Material dresses a dialog in — and
    // this panel stands in for one deliberately, as the note above `OutcomePanel` explains. It was
    // `surface` at `medium`, so the thing that lands over the board was the same tone as the
    // board's own background with a slightly rounder corner.
    Surface(
        modifier = Modifier.testTag(MATCH_RESULT_TEST_TAG).widthIn(max = ContentMaxWidth)
            .padding(SpaceLg),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = OutcomeElevation,
        shadowElevation = OutcomeElevation,
        border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Text(
                // A lesson names itself here instead — see [MatchScript.outcomeTitle]. Everything
                // below is unchanged by it: the tutor is still named, the score is still on the
                // board behind, and the closing bubble still says what the rule did.
                text = title?.let { strings[it] } ?: when (reward.result) {
                    MatchResult.WIN -> strings[StringKeys.YOU_WIN]
                    MatchResult.LOSE -> strings[StringKeys.YOU_LOSE]
                    MatchResult.DRAW -> strings[StringKeys.DRAW]
                },
                color = MaterialTheme.colorScheme.onSurface,
                // The one place in the app that announces an outcome, so it takes the scale's own
                // headline rather than a `20.sp` that belonged to no ladder.
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = opponentName,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // **Shown when something was actually paid**, which used to be unconditional: every
            // *counted* result pays — win, draw and defeat alike, see `MatchRewards`, and no
            // opponent in `npcs.json` pays zero for any of the three — so this reads exactly as it
            // did for every match that goes on the record.
            //
            // A lesson does not (`MatchScript.counted`). Left unconditional, the tutorial ended on
            // `+0 MGP` in the affirmative colour: a line announcing a reward, in the place a reward
            // is announced, for a match deliberately paying none.
            val payout = buildList {
                if (reward.mgp > 0) add("+${reward.mgp} ${strings[StringKeys.MGP]}")
                if (reward.xp > 0) add("+${reward.xp} ${strings[StringKeys.XP]}")
            }
            if (payout.isNotEmpty()) {
                Text(
                    text = payout.joinToString(DOT_SEPARATOR),
                    // The affirmative pair rather than a green literal of this file's own — see
                    // `TtoColors.positive`, which is where `ServersScreen`'s went too.
                    color = LocalTtoColors.current.positive,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(MATCH_PAYOUT_TEST_TAG),
                )
            }

            // **Named, not counted.** This said `Rewards: 1` — a line that tells the player
            // something happened and refuses to say what, about the only part of a match whose
            // contents are not already visible somewhere else. The MGP is in the payout above and
            // the cards flipped on the board in front of them; a dropped item exists nowhere but
            // here until they go and look in the bag for it.
            //
            // `itemName` is the shop's own naming, so a Bronze Pack is called the same thing where
            // it is won as where it is sold, in every language, including the card items whose
            // name is the card's.
            if (reward.items.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(MATCH_REWARDS_TEST_TAG),
                    verticalArrangement = Arrangement.spacedBy(SpaceXs),
                ) {
                    Text(
                        text = strings[StringKeys.REWARDS],
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    for (item in reward.items) {
                        RewardRow(item = item, cards = cards, strings = strings)
                    }
                }
            }
            for (achievement in reward.achievements) {
                Text(
                    text = strings[StringKeys.ACHIEVEMENT_EARNED] + " — " +
                        strings[achievement.labelKey],
                    color = LocalTtoColors.current.selectionRing,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Announced here or nowhere: a quest that finished mid-match and said nothing would be
            // a reward the player only discovers by going looking for it. The MGP is already in
            // the payout line above — `MatchRewards` credits quests into the same total — so this
            // says what was finished, not what it paid.
            for (quest in reward.quests) {
                Text(
                    // `BeatOpponent` is the only quest that names anybody, and by construction the
                    // opponent it names is the one just played. So no catalogue lookup here.
                    text = strings[StringKeys.QUEST_DONE] + " — " +
                        quest.label(strings) { opponentName },
                    color = LocalTtoColors.current.selectionRing,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(questRowTestTag(quest.id)),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                // Leaving first and quiet, playing again second and filled — a dialog's
                // `dismissButton` / `confirmButton` order, and the one the player wants most often
                // is the one under the thumb. Absent, not disabled, when a script says there is
                // nowhere to go: that is how `CCGroupRematchPanel` ends a ladder — no button.
                Box(modifier = Modifier.weight(1f)) {
                    WideButton(
                        label = strings[StringKeys.BACK],
                        tag = MATCH_DONE_TEST_TAG,
                        filled = next == null,
                        onClick = onDone,
                    )
                }
                next?.let {
                    Box(modifier = Modifier.weight(1f)) {
                        WideButton(strings[it.labelKey], NEW_MATCH_TEST_TAG, onClick = it.onLeave)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(item: Item, cards: Map<Int, Card>, strings: Strings) {
    val name = itemName(strings, item, cards)

    Row(
        modifier = Modifier.testTag(matchRewardTestTag(item)),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemGlyph(item = item, description = name, size = IconSm)
        Text(
            text = if (item.stack > 1) "$name x${item.stack}" else name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal val OutcomeElevation = 6.dp

private const val COMBO_MARK = "✦ combo"

private val SidePanelWidth = 200.dp

private const val BOARD_COLUMNS = 3
