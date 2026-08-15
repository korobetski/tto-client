package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch

const val PVP_CLAIM_TEST_TAG: String = "pvp-claim"
const val PVP_CLAIM_CONFIRM_TEST_TAG: String = "pvp-claim-confirm"
const val PVP_CLAIM_EMPTY_TEST_TAG: String = "pvp-claim-empty"

/** Shown while the prizes have not come back yet — see [PVP_CLAIM_EMPTY_TEST_TAG] for the other. */
const val PVP_CLAIM_LOADING_TEST_TAG: String = "pvp-claim-loading"
const val PVP_CLAIM_FAILED_TEST_TAG: String = "pvp-claim-failed"
const val PVP_CLAIM_PROMPT_TEST_TAG: String = "pvp-claim-prompt"

/** `pvp-prize-<cardId>` — one of the loser's cards, offered as a prize. */
fun prizeTestTag(cardId: Int): String = "pvp-prize-$cardId"

/**
 * Choosing what you won.
 *
 * ### The screen the wager was always missing
 *
 * A card stake used to be two ids agreed before the match, which meant the interesting half of the
 * wager — *which* card — was decided before a card had been played. Under One and Diff the winner
 * names it afterwards, out of the five the loser actually brought, and that decision needs
 * somewhere to happen.
 *
 * ### Why the whole card is drawn and not a thumbnail
 *
 * This is the one screen in the game where a choice is irreversible and the difference between the
 * options is arithmetic. A 44dp thumbnail renders the four powers at a size nobody reads — the
 * deck editor works around it with `CardStatsLine` underneath — so here the card is drawn at full
 * size, faces and all, because "which of these is better" is the only question being asked.
 *
 * ### A deadline that picks for you
 *
 * The server settles an unclaimed match once [PvpMatchView] says the deadline has passed, taking
 * the strongest card of the five. That is deliberately generous and still not what most players
 * want, so the countdown is on screen: a choice quietly made on your behalf is worse than a choice
 * you were rushed into.
 */
@Composable
internal fun PvpClaimScreen(
    session: PvpSession,
    cards: Map<Int, Card>,
    now: Long,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val claim = session.claims.firstOrNull()
    var picked by remember(claim?.matchId) { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(session) { session.refreshClaims() }

    // Nothing owed is the ordinary state of a player who has collected everything, and it is also
    // where the screen lands after the last claim is settled. **But not before the list has been
    // read**: this screen is reached by tapping a banner that says a prize is waiting, so telling
    // the player there is nothing while the request is still out contradicts what sent them here.
    val outcome = claim?.outcome
    if (claim == null || outcome == null || outcome.picksOwed == 0) {
        when (session.claimsState) {
            ListState.LOADING -> LoadingNote(PVP_CLAIM_LOADING_TEST_TAG)
            ListState.READY ->
                EmptyNote(strings[StringKeys.PVP_CLAIM_NONE], PVP_CLAIM_EMPTY_TEST_TAG)

            ListState.FAILED -> FailedNote(
                text = strings[StringKeys.ERROR_OFFLINE],
                tag = PVP_CLAIM_FAILED_TEST_TAG,
                onRetry = { scope.launch { session.refreshClaims() } },
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag(PVP_CLAIM_TEST_TAG).padding(SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings[StringKeys.PVP_CLAIM_TITLE],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = claimPrompt(outcome.picksOwed, picked.size, outcome.claimDeadline, now, strings),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(PVP_CLAIM_PROMPT_TEST_TAG),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for (id in outcome.pickFrom) {
                // A card the catalogue does not know is skipped rather than drawn as a hole — the
                // same refusal `PvpMatchView.toMatchView` makes, for the same reason.
                val card = cards[id] ?: continue
                Prize(
                    card = card,
                    selected = id in picked,
                    // Once enough are picked the rest stop responding, because the server counts
                    // them: a sixth tap that silently replaced a choice would be worse feedback
                    // than one that does nothing.
                    enabled = id in picked || picked.size < outcome.picksOwed,
                    onTap = { picked = if (id in picked) picked - id else picked + id },
                )
            }
        }

        WideButton(
            label = strings[StringKeys.PVP_CLAIM_CONFIRM],
            tag = PVP_CLAIM_CONFIRM_TEST_TAG,
            enabled = picked.size == outcome.picksOwed && !session.isBusy,
            onClick = {
                scope.launch {
                    session.claim(claim.matchId, picked.toList())
                    onDone()
                }
            },
        )
    }
}

/** One of the loser's cards, tappable, ringed when chosen. */
@Composable
private fun Prize(card: Card, selected: Boolean, enabled: Boolean, onTap: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .testTag(prizeTestTag(card.id))
                // Several cards can be owed at once under Diff, so picking is a set of
                // independent toggles rather than one choice — `Checkbox`, not `RadioButton`.
                .ttoClickable(
                    role = Role.Checkbox,
                    selected = selected,
                    enabled = enabled,
                    onClick = onTap,
                ),
        ) {
            CardFace(card = card)
            if (selected) {
                Spacer(
                    modifier = Modifier
                        .size(CardSpriteWidth, CardSpriteHeight)
                        .border(
                            SelectionRingWidth,
                            LocalTtoColors.current.selectionRing,
                            TileShape,
                        ),
                )
            }
        }
        CardStatsLine(card = card)
    }
}

/**
 * What is left to choose, and how long there is to choose it.
 *
 * The countdown is stated in whole seconds and stops at zero rather than going negative, for the
 * reason `PvpMatchScreen.turnLine` gives: a clock reading "-4s" says the game is broken, where a
 * clock at zero says the server is about to act.
 */
private fun claimPrompt(
    owed: Int,
    chosen: Int,
    deadline: Long?,
    now: Long,
    strings: com.tripletriad.i18n.Strings,
): String {
    val line = strings.format(StringKeys.PVP_CLAIM_PROMPT, "${owed - chosen}")
    val left = deadline?.minus(now)?.coerceAtLeast(0L) ?: return line
    return "$line $DOT_SEPARATOR ${left / MILLIS_PER_SECOND}s"
}

private const val MILLIS_PER_SECOND = 1_000L
