package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * The rules a pair of cards can state, and only those.
 *
 * ### Why two rules and not seventeen
 *
 * A picture of two cards can say exactly one thing: *this side met that side, and here is what
 * happened*. That is the whole of Reverse and the whole of Fallen Ace, and both are rules whose
 * prose is famously read backwards — "a 1 beats an A" is remembered without the half that says a 1
 * still loses to a 9.
 *
 * It is not the whole of any other rule here. Same and Plus are about **two sides at once** and
 * cannot be drawn without a board; Combo is about what the first capture then does; Same Wall
 * needs the edge of the board in the frame; the three element rules are about a tile under a card
 * rather than about either card. Each of those wants a three-cell board, which is a different
 * component from this one — and drawing them a two-card picture anyway would illustrate a rule
 * they do not have.
 *
 * So a rule has a diagram when a pair says it, and a paragraph alone otherwise.
 */
internal val RULE_DIAGRAMS: Map<String, List<RuleFrame>> = mapOf(
    // The lower number wins, so the 1 takes the 9 — and the same 9 played against a 1 takes
    // nothing. Both frames are needed: the first alone reads as "1 beats everything".
    "RULE_REVERSE" to listOf(
        RuleFrame(defender = "9", attacker = "1", captured = true),
        RuleFrame(defender = "1", attacker = "9", captured = false),
    ),
    // The ace falls to a 1 and to nothing else. The second frame is the half the sentence loses:
    // the very same 1, against a 9, is still a 1.
    "RULE_FALLEN_ACE" to listOf(
        RuleFrame(defender = "A", attacker = "1", captured = true),
        RuleFrame(defender = "9", attacker = "1", captured = false),
    ),
)

/**
 * A rule in two placements.
 *
 * Read right to left, the way the arrow points: the card on the right is the one being put down,
 * and the card on the left is the one already there. That is the order the sentences above are
 * written in — "a 1 captures an A", not "an A falls to a 1".
 */
@Composable
internal fun RuleDiagram(ruleKey: String, frames: List<RuleFrame>) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(ruleDiagramTestTag(ruleKey)).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for (frame in frames) {
            FrameRow(frame = frame, strings = strings)
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
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
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

private const val CAPTURE_ARROW = "←"
private const val NO_CAPTURE = "✕"

/** Faint enough that the number reads on it, strong enough to say whose card it is. */
private const val FACE_FILL = 0.25f

private val FaceWidth = 32.dp
private val FaceHeight = 40.dp
