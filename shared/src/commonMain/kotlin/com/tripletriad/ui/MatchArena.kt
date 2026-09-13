package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.MatchScore
import com.tripletriad.model.MatchView
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.model.PlayResult
import com.tripletriad.platform.rememberReducedMotion
import com.tripletriad.ui.theme.LocalTtoColors

/*
 * The match on a large landscape window — a desktop, a browser tab, a tablet on its side.
 *
 * The side panel was designed for the first window wide enough to spare it 200 dp, and on a
 * 1920x1080 screen it was still that: a narrow column of small type beside a board whose cards had
 * stopped growing at the size they were authored, with most of the window left empty. What a large
 * window is short of is not context but *table*, so past [ArenaMinWidth] the column goes and its
 * three contents move to where the eye already is — the opponent's name over the opponent's hand,
 * the rules under the score, the last moves in the header's free corner.
 */

/** How much furniture a match window can afford around the board. */
internal enum class MatchChrome { COMPACT, PANEL, ARENA }

/**
 * Landscape as well as large: a tall window that clears both sizes would stack the hands above and
 * below the board, and a seat header over each of them would cost the board twice what it costs
 * beside it.
 */
internal fun matchChrome(wide: Boolean, width: Dp, height: Dp): MatchChrome = when {
    !wide -> MatchChrome.COMPACT
    width >= ArenaMinWidth && height >= ArenaMinHeight && width >= height -> MatchChrome.ARENA
    height >= SidePanelMinHeight -> MatchChrome.PANEL
    else -> MatchChrome.COMPACT
}

/** Who sits behind each hand, for the arena's seat headers. */
internal data class MatchSeats(val face: OpponentFace, val opponentName: String)

/**
 * The line above a hand. [width] is the hand's own, so a long name elides at the edge of the cards
 * it belongs to rather than running over the board.
 */
@Composable
internal fun SeatHeader(own: Boolean, color: CardColor, seats: MatchSeats, width: Dp) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.size(width, SeatHeaderHeight),
        // Mirrored: the player's hand is on the right, and its label reads towards the board.
        horizontalArrangement = if (own) {
            Arrangement.spacedBy(SpaceSm, Alignment.End)
        } else {
            Arrangement.spacedBy(SpaceSm)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (own) {
            SeatName(strings[StringKeys.MATCH_YOUR_HAND], color, Modifier.weight(1f, fill = false))
            Box(
                modifier = Modifier.size(SeatMarkSize).clip(CircleShape).background(color.edge),
            )
        } else {
            OpponentPortrait(face = seats.face, name = seats.opponentName)
            SeatName(
                text = seats.opponentName,
                color = color,
                // The tag the header's and the panel's names carry: exactly one of the three is on
                // screen, so "where is the opponent named" keeps a single answer.
                modifier = Modifier.weight(1f, fill = false).testTag(MATCH_OPPONENT_TEST_TAG),
            )
        }
    }
}

@Composable
private fun SeatName(text: String, color: CardColor, modifier: Modifier) {
    Text(
        text = text,
        color = color.edge,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The arena's header: the way out on the left, the score over the turn in the middle, the last
 * moves on the right.
 *
 * Three boxes aligned inside one rather than a weighted row. The score has to be centred on the
 * *window*, where the board is, and a row centres it between whatever its neighbours happen to
 * measure — a long opponent name would push the score off the board's axis.
 */
@Composable
internal fun ArenaStatus(
    view: MatchView,
    selected: Card?,
    face: OpponentFace,
    opponentName: String,
    turnFraction: Float?,
    showOpponent: Boolean,
    outcomeTitle: String?,
    log: List<PlayResult>,
    onExit: () -> Unit,
) {
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MatchHeaderTopInset, start = SpaceSm, end = SpaceLg),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(ARENA_SIDE_FRACTION),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.testTag(MATCH_EXIT_TEST_TAG).size(ExitButtonSize),
            ) {
                Icon(
                    imageVector = TtoIcons.Back,
                    contentDescription = strings[StringKeys.BACK],
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                )
            }
            // Only where no seat header names them — the multiplayer board draws its hands in rows
            // of its own and has no seats.
            if (showOpponent) {
                OpponentPortrait(face = face, name = opponentName)
                SeatName(
                    text = opponentName,
                    color = view.opponent,
                    modifier = Modifier.weight(1f, fill = false).testTag(MATCH_OPPONENT_TEST_TAG),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(ARENA_CENTRE_FRACTION),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            ArenaScore(view = view, turnFraction = turnFraction)
            Box(
                modifier = Modifier.then(view.turnTag()?.let { Modifier.testTag(it) } ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                TurnLine(
                    view = view,
                    selected = selected,
                    opponentName = opponentName,
                    outcomeTitle = outcomeTitle,
                )
            }
        }

        ArenaLog(
            log = log,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(ARENA_SIDE_FRACTION),
        )
    }
}

@Composable
private fun ArenaScore(view: MatchView, turnFraction: Float?) {
    val score = view.score
    val pulse = rememberScorePulse(score)

    Box(contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                // One node that reads "5 — 5", as the compact score does, rather than two digits
                // and a gap: a screen reader announces a score, and the tests read one.
                .clearAndSetSemantics {
                    testTag = SCORE_TEST_TAG
                    text = AnnotatedString("${score.blue} — ${score.red}")
                }
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
            horizontalArrangement = Arrangement.spacedBy(SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreDigit(score.blue, CardColor.BLUE)
            // The ring is laid over this gap rather than placed in it: the row's cleared semantics
            // would take the timer's tags with them.
            Spacer(modifier = Modifier.size(TurnRingSize))
            ScoreDigit(score.red, CardColor.RED)
        }
        TurnRing(view = view, fraction = turnFraction)
    }
}

@Composable
private fun ScoreDigit(value: Int, color: CardColor) {
    Text(
        text = value.toString(),
        color = color.edge,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * The turn clock as a ring around the turn number — the bar the compact header draws, bent round.
 *
 * The number is the placement about to be made, so it stops at nine on a full board rather than
 * reading "10/9".
 */
@Composable
private fun TurnRing(view: MatchView, fraction: Float?) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = TIMER_TRACK_ALPHA)
    val fill = if (fraction != null && fraction <= TIMER_URGENT) {
        MaterialTheme.colorScheme.error
    } else {
        LocalTtoColors.current.transient
    }

    Box(modifier = Modifier.size(TurnRingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.testTag(TURN_TIMER_TEST_TAG).matchParentSize()) {
            ring(track, sweep = FULL_TURN_DEGREES)
        }
        if (fraction != null) {
            Canvas(modifier = Modifier.testTag(TURN_TIMER_FILL_TEST_TAG).matchParentSize()) {
                ring(fill, sweep = FULL_TURN_DEGREES * fraction)
            }
        }
        val turn = (view.placement + 1).coerceAtMost(PLACEMENTS_PER_MATCH)
        Text(
            text = "$turn/$PLACEMENTS_PER_MATCH",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun DrawScope.ring(color: Color, sweep: Float) {
    val stroke = TurnRingStroke.toPx()
    drawArc(
        color = color,
        // From twelve o'clock, emptying clockwise the way a clock face does.
        startAngle = -QUARTER_TURN_DEGREES,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/**
 * The last few moves, newest first and fading with age.
 *
 * A glance rather than a record: the panel's log scrolls back through the whole match, and a
 * corner of the header that grew with the match would end up over the board.
 */
@Composable
private fun ArenaLog(log: List<PlayResult>, modifier: Modifier) {
    val strings = LocalStrings.current

    Column(
        modifier = modifier.testTag(MATCH_LOG_TEST_TAG),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        log.takeLast(ARENA_LOG_LINES).asReversed().forEachIndexed { age, play ->
            MoveLogEntry(
                play = play,
                strings = strings,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.alpha(1f - age * ARENA_LOG_FADE),
            )
        }
    }
}

/**
 * The score's swell when it moves.
 *
 * ### It fires on the change, not on arrival
 *
 * `LaunchedEffect(score)` would also run on the first composition, so a board would open by
 * throbbing at a score nobody has moved. The remembered previous value is what tells a change from
 * a beginning, and it is why this is not simply keyed on the score.
 */
@Composable
internal fun rememberScorePulse(score: MatchScore): Animatable<Float, AnimationVector1D> {
    val pacing = LocalPacing.current
    val reduced = rememberReducedMotion()
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(score) }

    LaunchedEffect(score, reduced, pacing) {
        val moved = score != previous
        previous = score
        if (!moved || reduced) return@LaunchedEffect
        pulse.snapTo(SCORE_PULSE_PEAK)
        pulse.animateTo(1f, tween(pacing * SCORE_PULSE_MS))
    }
    return pulse
}

/**
 * Low enough that a browser tab on a 1366x768 laptop, toolbars and all, is an arena; high enough
 * that a 1024x768 window — every UI test's — stays on the panel.
 */
private val ArenaMinWidth = 1200.dp
private val ArenaMinHeight = 640.dp

internal val SeatHeaderHeight = 56.dp
internal val SeatGap = 8.dp
private val SeatMarkSize = 12.dp

private val TurnRingSize = 64.dp
private val TurnRingStroke = 5.dp

private const val ARENA_SIDE_FRACTION = 0.28f
private const val ARENA_CENTRE_FRACTION = 0.4f

private const val ARENA_LOG_LINES = 3
private const val ARENA_LOG_FADE = 0.25f

private const val FULL_TURN_DEGREES = 360f
private const val QUARTER_TURN_DEGREES = 90f
