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

/** The side panel a window wide enough gets. Absent below the threshold. */
const val MATCH_SIDE_TEST_TAG: String = "match-side"

/** The move log inside it. Present with the panel, empty until the first placement. */
const val MATCH_LOG_TEST_TAG: String = "match-log"

/** The named drops on the outcome panel. Absent when a match dropped nothing. */
const val MATCH_REWARDS_TEST_TAG: String = "match-rewards"

/** `match-reward-<slug>` — one per dropped item, so a test can name the one it means. */
fun matchRewardTestTag(item: Item): String = "match-reward-${itemSlug(item)}"

/**
 * The board, and — on a window wide enough — a column beside it.
 *
 * ### Why the match needed this and the collection did not
 *
 * [matchLayout] has always adapted: it is handed measured bounds and derives one scale everything
 * fits inside, so a desktop window already produced a *bigger* board. What it could not produce was
 * a different arrangement, and past a certain width bigger stops being better — a 3×3 grid of cards
 * drawn at their authored size leaves two thirds of a desktop window as background, while the
 * things a player actually wants beside a board (who they are playing, what the rules do, what just
 * happened) are crammed into a 20 dp strip above it or missing entirely.
 *
 * So this is the same move [CardListBody] makes at the same 600 dp threshold, and for the same
 * reason: the extra width becomes a second pane rather than a wider first one.
 *
 * @param side the panel. A slot rather than the panel itself, so this composable is about the
 *   arrangement and knows nothing about a match.
 * @param content the board and its chrome, told **whether the panel was actually drawn**. It has to
 *   be told rather than left to work it out from the width: the decision now depends on the height
 *   too, and only this composable measures that. What the caller does with it is decide the two
 *   things that would otherwise be drawn twice or not at all — the opponent's face and the rules
 *   strip, both of which live in the panel when there is one.
 */
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

/**
 * The height below which [MatchSidePanel] has nowhere to put itself.
 *
 * The panel's own content is a portrait, a rules strip and a move log — about 300 dp before the log
 * has a single line in it. 560 leaves the log somewhere to grow and is comfortably above every
 * phone in landscape and comfortably below every tablet and desktop window.
 */
private val SidePanelMinHeight = 560.dp

/**
 * Who is being played, how it stands, what the rules do, and what has happened so far.
 *
 * Everything here exists somewhere on a phone too — except the log, which exists nowhere. That is
 * the honest description of this panel: three things that were compressed, and one that could not
 * be shown at all without taking space the board needed more.
 *
 * The rules are the same strip the phone gets, tap and all, rather than a second one permanently
 * open. Two behaviours for one control would be two things to keep true, and the sentences are
 * still one tap away — what the panel buys them is somewhere to expand into that is not the board.
 */
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

/**
 * The rule strip where it belongs for this width: above the board, or nowhere.
 *
 * Nowhere, on a wide window, because [MatchSidePanel] already draws one — two strips naming the
 * same rules would be two places to read the same thing and one of them to keep in step.
 */
@Composable
internal fun BoardRules(rules: GameRules, wide: Boolean) {
    if (!wide) RulesStrip(rules)
}

/**
 * Every placement's own result, accumulated as the match runs.
 *
 * Read off [MatchState.lastPlay] rather than from `MatchScreen`'s `moves` list, and that is the
 * whole reason a log is affordable at all: `moves` records what *the screen* played — the
 * opponent's turns go through `MatchAi` and never touch it — whereas every state the match passes
 * through carries the play that produced it, whichever side made it.
 *
 * @param key the match. A rematch starts empty; sudden death continues, which is right — it is the
 *   same match by every other measure.
 */
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

/**
 * What each placement did, newest last.
 *
 * A capture count and not a list of the cards taken: the board is right there, and the thing a
 * player cannot reconstruct by looking at it is *when* something was taken and by how much.
 */
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

/** `A1` … `C3`, row-major over a 3×3 board — the same order as `Board.cells`. */
private fun cellName(position: Int): String =
    "${'A' + position / BOARD_COLUMNS}${position % BOARD_COLUMNS + 1}"

/**
 * The rules in force, named.
 *
 * `RulesDigest.as` did the same job on the board, and it matters more than it looks: Reverse or
 * Fallen Ace silently changes which card beats which, and a player who has not been told is playing
 * a different game from the one they think. The keys are the AS3 rule constants, which are also
 * their own i18n keys — so this is `activeRuleKeys()` looked up, with no mapping table in between.
 *
 * ### One chip each, rather than one line of dot-separated text
 *
 * The line was legible with two rules and a wall with six. Each rule is a separate fact and now
 * looks like one, and they wrap instead of eliding — a rule silently truncated is the one case this
 * strip exists to prevent.
 *
 * ### And the strip opens
 *
 * The bundles carry a `_HELP` sentence for every rule (`RULE_FALLEN_ACE_HELP`: a rank 1 side can
 * flip an A) and nothing in the port has ever shown one during a match. They cannot go under the
 * chips — a one-line ellipsis of "A rank 1 side can flip an A. When Reverse is in play…" tells the
 * player less than the name did — so tapping the strip opens the full sentences and tapping it
 * again closes them. Closed is the default, and closed costs exactly what the old line cost.
 *
 * `FlowRow` is stable in `foundation`; nothing here needs an opt-in.
 *
 * @param roulette whether to say the rules are not final yet. A table can be offered with a draw
 *   pending — the server adds one to three rules as the match opens — and a strip showing only what
 *   the host ticked would be describing a match nobody is going to play.
 * @param tag the test tag, or null for none. Null is what a **list** of strips passes: the lobby
 *   draws one per table, and repeating [MATCH_RULES_TEST_TAG] down a column would make the tag name
 *   several nodes at once, which is a broken assertion rather than a helpful one.
 */
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
                        text = "${strings[key]} — ${strings[help]}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag(ruleHelpTestTag(key)),
                    )
                }
            }
        }
    }
}

/**
 * One rule, named.
 *
 * A plain `Surface` rather than an `AssistChip`: a chip is a control with a ripple and a 32 dp
 * minimum height, and six of those above a board would cost the phone layout more than the rules
 * are worth. The tap that opens the descriptions belongs to the whole strip, not to one rule.
 */
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

/**
 * What the match paid, over the board it was won on.
 *
 * Stands in for `RematchPanel.as`, which the original opened over the finished board with the same
 * contents: the result, the MGP, the XP, any dropped items and any achievement just earned. Two
 * actions, as it had: play the same opponent again, or leave.
 *
 * ### A dimmed panel and not an `AlertDialog`
 *
 * A dialog was the obvious move and it is the wrong one here. `AlertDialog` opens in a popup above
 * everything in this composition, and three things are deliberately drawn *after* this panel and
 * over it: [MatchBannerOverlay], [LessonBubbles] and [OutcomeBubble] — the last of which the AS3
 * puts on screen at the same time as the panel on purpose (`endGame` adds the `TalkAnim`, then
 * schedules `rematch` behind `intervalDuration`). A dialog would put the opponent's parting line
 * behind a scrim.
 *
 * So the panel keeps its place in the tree and takes what a dialog was wanted for: the theme's own
 * `scrim` behind it instead of a live board, a `Surface` at dialog elevation instead of a
 * hand-mixed `0xFF11141C`, and the leave action as the quiet one of the two.
 */
@Composable
internal fun OutcomePanel(
    reward: MatchReward,
    opponentName: String,
    cards: Map<Int, Card>,
    next: ScriptExit?,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current

    // The board is finished and nothing on it is actionable; dimming it says so, and stops a
    // full board of bright card art from competing with the two controls that now matter.
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        OutcomeCard(reward, opponentName, cards, next, onDone, strings)
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
                text = when (reward.result) {
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

            // Always shown, and always positive: every result pays here — see `MatchRewards`.
            Text(
                text = buildList {
                    add("+${reward.mgp} ${strings[StringKeys.MGP]}")
                    if (reward.xp > 0) add("+${reward.xp} ${strings[StringKeys.XP]}")
                }.joinToString(DOT_SEPARATOR),
                // The affirmative pair rather than a green literal of this file's own — see
                // `TtoColors.positive`, which is where `ServersScreen`'s went too.
                color = LocalTtoColors.current.positive,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(MATCH_PAYOUT_TEST_TAG),
            )

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

/**
 * One dropped item: what it looks like, what it is called, and how many.
 *
 * The count is appended only when there is more than one, for the reason the collection's copy
 * badge gives: `x1` on every row is noise, and a row without a count is unambiguous.
 */
@Composable
private fun RewardRow(item: Item, cards: Map<Int, Card>, strings: Strings) {
    val name = itemName(strings, item, cards)

    Row(
        modifier = Modifier.testTag(matchRewardTestTag(item)),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIcon(iconId = itemIconId(item), description = name, size = IconSm)
        Text(
            text = if (item.stack > 1) "$name x${item.stack}" else name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Material's own dialog elevation, since the panel stands in for one.
 *
 * Shared with `PvpResult`, which stands in for the same dialog at the end of a PvP match and has to
 * sit at the same height for the two endings to read as one thing.
 */
internal val OutcomeElevation = 6.dp

/** `RULE_COMBO` is the rule's *name*; this marks one having happened, and needs no translation. */
private const val COMBO_MARK = "✦ combo"

/** Wide enough for a portrait beside a name and for a rule sentence to wrap sanely. */
private val SidePanelWidth = 200.dp

/** The board is three across, and `cellName` needs to say which column. */
private const val BOARD_COLUMNS = 3
