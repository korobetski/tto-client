package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.ui.theme.LocalTtoColors

/** The picture under a rule's paragraph, where there is one. */
fun ruleDiagramTestTag(ruleKey: String): String = "rule-diagram-$ruleKey"

/**
 * One placement: a card already down, a card put beside it, and whether it turned.
 *
 * The sides are written as they are printed on a card — `A` is ten — because that is what the
 * paragraph beside the picture calls them, and a diagram that said `10` where the sentence says
 * `A` would be a third spelling of the same rule.
 */
internal data class RuleFrame(val defender: String, val attacker: String, val captured: Boolean)

/** Whose square this is, which is the whole of what a tile's colour says. */
internal enum class TileOwner {
    /** The card being put down. */
    PLAYED,

    /** A card already on the board. */
    HELD,

    /** The edge of the board, which Same Wall reads as a side of its own. */
    WALL,

    /** Nothing here — drawn so the squares that do hold something keep their places. */
    EMPTY,
}

/**
 * One square of a rule's little board, written the way a card face is: a number per side.
 *
 * Only the sides that take part are given. A card whose left side is what met its neighbour shows
 * that number and nothing else, because the other three are not what the rule is about and four
 * numbers on every square would bury the two that are.
 */
internal data class RuleTile(
    val owner: TileOwner = TileOwner.EMPTY,
    val top: String = "",
    val right: String = "",
    val bottom: String = "",
    val left: String = "",
    /** Drawn as turned: this is a card the placement took. */
    val flips: Boolean = false,
    /** A number the rule adds to the picture — a sum, a bonus, the step of a chain. */
    val badge: String = "",
)

/** One card in a hand: a number, a back, or somebody else's card among yours. */
internal data class HandFace(
    val side: String = "",
    /** Face down — what the rules of sight are about. */
    val hidden: Boolean = false,
    /** The one the rule points at: the card that must be played, or the one that is not yours. */
    val marked: Boolean = false,
    /** Drawn in the other player's colour, for the rule that trades a card across. */
    val foreign: Boolean = false,
)

/**
 * What a rule's picture is made of. Three shapes, because rules come in three shapes.
 *
 * ### Why not one shape
 *
 * A pair of cards says *this side met that side, and here is what happened* — the whole of Reverse
 * and the whole of Fallen Ace, both rules whose prose is famously read backwards. It says nothing
 * about Same, which is two sides at once, or about Order, which is about a hand and not about a
 * capture at all. Drawing those as pairs would illustrate rules they do not have — which is why
 * they had no picture before rather than a wrong one.
 *
 * So: [Placements] for the pair, [Grid] for the rules that need squares beside each other, and
 * [Hand] for the rules about what is in your hand and which of it you may play.
 *
 * ### Two of the seventeen still have none
 *
 * Sudden Death re-deals a drawn match from the board, which is two boards and an arrow between
 * them; Roulette picks the *rules*, so its picture would be a picture of a list. Both are a
 * sentence's work and no picture's, and an empty diagram is better than a misleading one.
 */
internal sealed interface RuleArt {
    /** Two placements, one under the other: what happens, and what does not. */
    data class Placements(val frames: List<RuleFrame>) : RuleArt

    /** Squares laid out in [columns], read the way a board is. */
    data class Grid(val columns: Int, val tiles: List<RuleTile>) : RuleArt

    /** A hand of five, as the rules of sight and of order see it. */
    data class Hand(val cards: List<HandFace>) : RuleArt
}

/**
 * The picture each rule gets.
 *
 * The numbers are chosen so that the rule is the only thing that could explain the outcome: Same
 * uses two *different* pairs of equal sides, so the picture cannot be read as "5 beats 5"; Plus
 * uses sums that match on numbers that do not.
 */
internal val RULE_DIAGRAMS: Map<String, RuleArt> = mapOf(
    // The lower number wins, so the 1 takes the 9 — and the same 9 played against a 1 takes
    // nothing. Both frames are needed: the first alone reads as "1 beats everything".
    "RULE_REVERSE" to RuleArt.Placements(
        listOf(
            RuleFrame(defender = "9", attacker = "1", captured = true),
            RuleFrame(defender = "1", attacker = "9", captured = false),
        ),
    ),
    // The ace falls to a 1 and to nothing else. The second frame is the half the sentence loses:
    // the very same 1, against a 9, is still a 1.
    "RULE_FALLEN_ACE" to RuleArt.Placements(
        listOf(
            RuleFrame(defender = "A", attacker = "1", captured = true),
            RuleFrame(defender = "9", attacker = "1", captured = false),
        ),
    ),
    // Two neighbours at once, and neither capture would have happened on its own: 5 does not beat
    // 5. That is the whole of the rule and the reason it needs squares rather than a pair.
    "RULE_SAME" to RuleArt.Grid(
        columns = 2,
        tiles = listOf(
            RuleTile(owner = TileOwner.HELD, bottom = "5", flips = true),
            RuleTile(),
            RuleTile(owner = TileOwner.PLAYED, top = "5", right = "3"),
            RuleTile(owner = TileOwner.HELD, left = "3", flips = true),
        ),
    ),
    // The wall on the left counts as an A, so the played card's A matches it — and that is one
    // half of a Same, the other being the A above. The wall itself turns nothing.
    "RULE_SAME_WALL" to RuleArt.Grid(
        columns = 2,
        tiles = listOf(
            RuleTile(),
            RuleTile(owner = TileOwner.HELD, bottom = "A", flips = true),
            RuleTile(owner = TileOwner.WALL, right = "A"),
            RuleTile(owner = TileOwner.PLAYED, top = "A", left = "A"),
        ),
    ),
    // Both sums are 8 on four numbers that beat nothing: 6 loses to 2 nowhere, and that is the
    // point — the badge is the sum, and the sums are what matched.
    "RULE_PLUS" to RuleArt.Grid(
        columns = 2,
        tiles = listOf(
            RuleTile(owner = TileOwner.HELD, bottom = "2", flips = true, badge = "8"),
            RuleTile(),
            RuleTile(owner = TileOwner.PLAYED, top = "6", right = "3"),
            RuleTile(owner = TileOwner.HELD, left = "5", flips = true, badge = "8"),
        ),
    ),
    // The chain, which is the only thing Combo is: the card taken first takes the next one with
    // its **own** sides, and the badges are the order it happens in.
    "RULE_COMBO" to RuleArt.Grid(
        columns = 3,
        tiles = listOf(
            RuleTile(owner = TileOwner.PLAYED, right = "7"),
            RuleTile(owner = TileOwner.HELD, left = "3", right = "6", flips = true, badge = "1"),
            RuleTile(owner = TileOwner.HELD, left = "2", flips = true, badge = "2"),
        ),
    ),
    // A card of the same element as the tile it lands on is worth one more on every side, and a
    // card of any other element one less. Two squares, because the rule is the difference.
    "RULE_ELEMENTAL" to RuleArt.Grid(
        columns = 2,
        tiles = listOf(
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "+1"),
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "-1"),
        ),
    ),
    // What rises is every card of the type already on the board, so the bonus grows with the
    // count: the third one of its kind is worth two more than the first.
    "RULE_ASCENSION" to RuleArt.Grid(
        columns = 3,
        tiles = listOf(
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "+1"),
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "+2"),
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "+3"),
        ),
    ),
    // And the same count, downwards. Drawn as its own picture rather than as a note under
    // Ascension's, because the two rules are read one at a time.
    "RULE_DESCENSION" to RuleArt.Grid(
        columns = 3,
        tiles = listOf(
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "-1"),
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "-2"),
            RuleTile(owner = TileOwner.PLAYED, top = "5", badge = "-3"),
        ),
    ),
    // Every card of the other hand, face up.
    "RULE_ALL_OPEN" to RuleArt.Hand(
        listOf(
            HandFace(side = "5"),
            HandFace(side = "A"),
            HandFace(side = "3"),
            HandFace(side = "7"),
            HandFace(side = "2"),
        ),
    ),
    // Three of the five, and the two that are not are the whole difference from All Open.
    "RULE_THREE_OPEN" to RuleArt.Hand(
        listOf(
            HandFace(side = "5"),
            HandFace(side = "A"),
            HandFace(side = "3"),
            HandFace(hidden = true),
            HandFace(hidden = true),
        ),
    ),
    // The hand itself is dealt for you: five cards you did not choose.
    "RULE_RANDOM" to RuleArt.Hand(
        listOf(
            HandFace(hidden = true),
            HandFace(hidden = true),
            HandFace(hidden = true),
            HandFace(hidden = true),
            HandFace(hidden = true),
        ),
    ),
    // The order is the hand's own, left to right, and the marked card is the only one that may
    // be played this turn.
    "RULE_ORDER" to RuleArt.Hand(
        listOf(
            HandFace(side = "5", marked = true),
            HandFace(side = "A"),
            HandFace(side = "3"),
            HandFace(side = "7"),
            HandFace(side = "2"),
        ),
    ),
    // The same picture with the mark somewhere nobody chose, which is the difference between
    // Order and Chaos.
    "RULE_CHAOS" to RuleArt.Hand(
        listOf(
            HandFace(side = "5"),
            HandFace(side = "A"),
            HandFace(side = "3", marked = true),
            HandFace(side = "7"),
            HandFace(side = "2"),
        ),
    ),
    // One card of theirs in your hand, for the length of the match.
    "RULE_SWAP" to RuleArt.Hand(
        listOf(
            HandFace(side = "5"),
            HandFace(side = "A"),
            HandFace(side = "3", foreign = true, marked = true),
            HandFace(side = "7"),
            HandFace(side = "2"),
        ),
    ),
)

/**
 * A rule's picture.
 *
 * ### What a screen reader is told
 *
 * The pair diagram reads each frame as a sentence — "a 1 captures an A" — because that sentence
 * *is* the rule and the paragraph above states the other half. A board or a hand cannot be read
 * out that way without turning into a list of numbers in an order that says nothing, so those two
 * announce the rule they illustrate and leave the explaining to the paragraph they sit under,
 * which is right above them and says it in words.
 */
@Composable
internal fun RuleDiagram(ruleKey: String, art: RuleArt) {
    val strings = LocalStrings.current

    // On a ground of its own. The open rule's panel is drawn in the primary container colour, and
    // a blue card on a blue panel is a rectangle with a number in it.
    Column(
        modifier = Modifier
            .testTag(ruleDiagramTestTag(ruleKey))
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = SUBDUED))
            .padding(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (art) {
            is RuleArt.Placements -> for (frame in art.frames) {
                FrameRow(frame = frame, strings = strings)
            }

            is RuleArt.Grid -> TileGrid(art = art, label = strings[ruleKey])
            is RuleArt.Hand -> HandRow(art = art, label = strings[ruleKey])
        }
    }
}

@Composable
private fun FrameRow(frame: RuleFrame, strings: Strings) {
    val colors = LocalTtoColors.current
    val outcome = strings[if (frame.captured) StringKeys.HELP_CAPTURES else StringKeys.HELP_FAILS]

    Row(
        // One sentence for the whole frame. Read out card by card it is five fragments —
        // "9", "holds", "captures", "1" — in an order that says nothing.
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${frame.attacker} $outcome ${frame.defender}"
        },
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameCard(
            side = frame.defender,
            ink = colors.cardRed,
            caption = strings[StringKeys.HELP_HOLDS],
        )
        Text(
            text = if (frame.captured) CAPTURE_ARROW else NO_CAPTURE,
            color = if (frame.captured) colors.positive else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        FrameCard(side = frame.attacker, ink = colors.cardBlue, caption = outcome)
    }
}

@Composable
private fun FrameCard(side: String, ink: Color, caption: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(FaceWidth)
                .height(FaceHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(ink.copy(alpha = FACE_FILL))
                .border(HairlineWidth, ink, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = side,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** The squares, in rows of [RuleArt.Grid.columns]. */
@Composable
private fun TileGrid(art: RuleArt.Grid, label: String) {
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        for (row in art.tiles.chunked(art.columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                for (tile in row) BoardTile(tile)
            }
        }
    }
}

@Composable
private fun BoardTile(tile: RuleTile) {
    val colors = LocalTtoColors.current
    // An empty square keeps its place and draws nothing at all: `Color.Transparent.copy(alpha)`
    // is translucent *black*, which is a visible fifth square saying a card is there.
    if (tile.owner == TileOwner.EMPTY) {
        Box(modifier = Modifier.width(TileSize).height(TileSize))
        return
    }
    val ink = when (tile.owner) {
        TileOwner.PLAYED -> colors.cardBlue
        TileOwner.HELD -> colors.cardRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .width(TileSize)
            .height(TileSize)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                ink.copy(alpha = if (tile.owner == TileOwner.WALL) WALL_FILL else TILE_FILL),
            )
            .border(
                if (tile.flips) TurnRing else HairlineWidth,
                if (tile.flips) colors.cardBlue else ink,
                MaterialTheme.shapes.extraSmall,
            ),
    ) {
        Side(tile.top, Alignment.TopCenter)
        Side(tile.right, Alignment.CenterEnd)
        Side(tile.bottom, Alignment.BottomCenter)
        Side(tile.left, Alignment.CenterStart)
        // In a pill, and not as a fifth number. A bare digit in the middle of a card face reads
        // as another side; the sum, the bonus and the step of a chain are none of them a side.
        if (tile.badge.isNotEmpty()) {
            Text(
                text = tile.badge,
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(colors.positive)
                    .padding(horizontal = SpaceXs, vertical = 1.dp),
            )
        }
    }
}

/** One number on one edge of a square, absent where the rule does not use that edge. */
@Composable
private fun BoxScope.Side(side: String, where: Alignment) {
    if (side.isEmpty()) return
    Text(
        text = side,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.align(where).padding(horizontal = 2.dp),
    )
}

/** Five cards, some of them face down and one of them sometimes pointed at. */
@Composable
private fun HandRow(art: RuleArt.Hand, label: String) {
    val colors = LocalTtoColors.current

    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        for (card in art.cards) {
            val ink = if (card.foreign) colors.cardRed else colors.cardBlue
            Box(
                modifier = Modifier
                    .width(HandCardWidth)
                    .height(FaceHeight)
                    .alpha(if (card.hidden) HIDDEN_ALPHA else 1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(ink.copy(alpha = FACE_FILL))
                    // The mark is the whole of Order, Chaos and Swap: which card the rule points
                    // at. A slightly thicker edge in the card's own colour was not a mark.
                    .border(
                        if (card.marked) TurnRing else HairlineWidth,
                        if (card.marked) colors.positive else ink,
                        MaterialTheme.shapes.extraSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (card.hidden) FACE_DOWN else card.side,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val CAPTURE_ARROW = "←"
private const val NO_CAPTURE = "✕"

/** What a card face down shows, which is nothing about the card. */
private const val FACE_DOWN = "?"

/** Faint enough that the number reads on it, strong enough to say whose card it is. */
private const val FACE_FILL = 0.25f

/** Squares carry four numbers where a frame carries one, so they are filled more lightly. */
private const val TILE_FILL = 0.35f

private const val WALL_FILL = 0.2f

/** A face-down card is present but says nothing, and is drawn that way. */
private const val HIDDEN_ALPHA = 0.45f

private val FaceWidth = 32.dp
private val FaceHeight = 40.dp
private val HandCardWidth = 28.dp
private val TileSize = 52.dp
private val TileGap = 4.dp

/** Thick enough to read as a ring around a square rather than as its edge. */
private val TurnRing = 2.dp
