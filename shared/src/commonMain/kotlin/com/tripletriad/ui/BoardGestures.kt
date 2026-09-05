package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.tripletriad.model.MatchView
import com.tripletriad.platform.rememberReducedMotion

/*
 * How the board reacts, as opposed to what it draws.
 *
 * Three small things — where a pointer is resting, when the grid is knocked, and what counts as
 * worth knocking about — that `MatchBoard` used to hold and that pushed it past the number of
 * functions detekt allows in one file. The split is the honest one: nothing here paints a card.
 */

/** A placement that takes this many is worth a knock — see [Modifier.jolt]. */
internal const val JOLT_CAPTURES: Int = 3

/** Four half-swings and a return: enough to read as a knock, short of a wobble. */
private const val JOLT_SWINGS = 4

private const val JOLT_TRAVEL = 6f

private const val JOLT_STEP_MS = 45

/**
 * The placement a chain of [JOLT_CAPTURES] or more landed on, or null.
 *
 * On the view rather than in the two play areas, because both of them ask it and the answer is a
 * fact about the board. Reading `lastPlay` and the placement together is what makes it an *event*:
 * see [Modifier.jolt] for why the count alone is not a key.
 */
internal fun MatchView.joltAt(): Int? =
    placement.takeIf { (lastPlay?.captures?.size ?: 0) >= JOLT_CAPTURES }

/**
 * A short sideways knock when a placement takes three cards or more.
 *
 * ### Why the threshold, and why it is on the count rather than on the wave
 *
 * Every capture already flips, staggers and sounds; a board that shook for all of them would be a
 * board that shook. Three is where a placement stops being a trade and starts being a turn nobody
 * expected — a Same or a Plus into a combo — and it is counted in *cards taken* rather than in
 * chain depth because that is what the player is looking at. A four-card Same with no chain at all
 * is the loudest thing that happens on this board and has a wave depth of zero.
 *
 * ### Keyed on the placement, not on the count
 *
 * Two consecutive placements can take three cards each, and a key of `3` would not change between
 * them — so the second would pass in silence. The placement index is what makes each one its own
 * event. Null is "nothing to knock about", which is every ordinary turn.
 *
 * Silent under `rememberReducedMotion`: a screen that shakes is the first thing a motion setting is
 * about, and the flips and the sound carry the same fact.
 */
@Composable
internal fun Modifier.jolt(at: Int?): Modifier {
    val reduced = rememberReducedMotion()
    val pacing = LocalPacing.current
    val offset = remember { Animatable(0f) }

    LaunchedEffect(at, reduced, pacing) {
        if (at == null || reduced) return@LaunchedEffect
        repeat(JOLT_SWINGS) { swing ->
            val to = if (swing % 2 == 0) JOLT_TRAVEL else -JOLT_TRAVEL
            offset.animateTo(to, tween(pacing * JOLT_STEP_MS, easing = LinearEasing))
        }
        offset.animateTo(0f, tween(pacing * JOLT_STEP_MS, easing = LinearEasing))
    }

    return this.graphicsLayer { translationX = offset.value }
}

/**
 * Reports a pointer resting on this cell, and only a pointer.
 *
 * `Enter` and `Exit` are hover events: a mouse or a stylus produces them and a finger does not, so
 * this is inert on a phone rather than needing to be excluded there. Keyed on the two stable things
 * it closes over — the state object and the cell's index — because a lambda pair would be new on
 * every recomposition and would restart the loop with every drag frame.
 */
internal fun Modifier.cellHover(drag: BoardDragState, position: Int): Modifier =
    pointerInput(drag, position) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> drag.enter(position)
                    PointerEventType.Exit -> drag.leave(position)
                    else -> Unit
                }
            }
        }
    }
