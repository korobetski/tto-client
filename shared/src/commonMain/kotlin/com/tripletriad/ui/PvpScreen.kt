package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.PvpChallenge
import kotlinx.coroutines.launch

const val PVP_FIND_TEST_TAG: String = "pvp-find"
const val PVP_WAITING_TEST_TAG: String = "pvp-waiting"
const val PVP_NAME_TEST_TAG: String = "pvp-name"
const val PVP_CHALLENGE_TEST_TAG: String = "pvp-challenge"
const val PVP_NO_CHALLENGE_TEST_TAG: String = "pvp-no-challenge"
const val PVP_LIST_TEST_TAG: String = "pvp-challenges"

/** `pvp-invite-<id>` — one invitation row. */
fun challengeRowTestTag(id: String): String = "pvp-invite-$id"

/** `pvp-accept-<id>` — the button that turns an invitation into a match. */
fun challengeAcceptTestTag(id: String): String = "pvp-accept-$id"

/**
 * Finding somebody to play — the original's `PVPScreen`, which never worked.
 *
 * ### There is no original to be faithful to
 *
 * `PVPScreen.as` is 363 lines around a socket protocol where **27 of its 29 handlers are dead
 * code**; the user list it drew is assigned and only `trace`d, and the call that would have
 * refreshed it is commented out. So this screen is designed rather than ported, and its two ways
 * in are the two questions a player has: *I want to play now*, and *I want to play with them*.
 *
 * ### Why the queue is a button and not a list of who is online
 *
 * A lobby listing everybody available is what the AS3 sketched, and it is the wrong shape for a
 * game with few players connected at once: an empty list says "nobody is here" and ends the
 * session, where a queue says "waiting" and pairs the moment somebody else taps the same button.
 * Naming a friend covers the other half, and needs no presence at all.
 *
 * ### It polls only while it is on screen
 *
 * The [LaunchedEffect] below is the whole subscription. Leaving the screen cancels it, which is
 * what stops a request a second running behind the shop. See [PvpSession].
 */
@Composable
internal fun PvpScreen(
    profile: GameSave,
    session: PvpSession,
    onMatch: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }

    // Two things are being waited for and one loop covers both: an opponent from the queue, and an
    // invitation from somebody else. Neither has a notification to arrive on.
    LaunchedEffect(session) {
        session.refreshChallenges()
        session.watch { session.match != null }
        session.refreshChallenges()
    }

    // The instant a match exists — however it arrived — the board takes over. Written as an effect
    // rather than checked in the loop above so that a match resumed at launch lands here too.
    LaunchedEffect(session.match) {
        if (session.match != null) onMatch()
    }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.MULTIPLAYER],
        onBack = onBack,
    ) {
        QuickMatch(session = session, scope = scope)

        Text(
            text = strings[StringKeys.PVP_CHALLENGE],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(strings[StringKeys.USERNAME]) },
                modifier = Modifier.weight(1f).testTag(PVP_NAME_TEST_TAG),
            )
            TextButton(
                // Trimmed here as well as on the server, for the reason `Credentials.looksValid`
                // gives: a round trip to be told about a trailing space is a round trip wasted.
                enabled = name.isNotBlank() && !session.isBusy,
                onClick = {
                    scope.launch {
                        session.challenge(name.trim())
                        name = ""
                    }
                },
                modifier = Modifier.testTag(PVP_CHALLENGE_TEST_TAG),
            ) {
                Text(strings[StringKeys.PVP_INVITE])
            }
        }

        if (session.challenges.isEmpty()) {
            EmptyNote(strings[StringKeys.PVP_NO_CHALLENGE], PVP_NO_CHALLENGE_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(PVP_LIST_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(session.challenges, key = { it.id }) { challenge ->
                    ChallengeRow(
                        challenge = challenge,
                        mine = challenge.fromName.equals(profile.username, ignoreCase = true),
                        onAccept = { scope.launch { session.accept(challenge.id) } },
                        onDrop = { scope.launch { session.dropChallenge(challenge.id) } },
                    )
                }
            }
        }
    }
}

/** The queue: one button that says what it is doing. */
@Composable
private fun QuickMatch(session: PvpSession, scope: kotlinx.coroutines.CoroutineScope) {
    val strings = LocalStrings.current

    if (session.isQueued) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(PVP_WAITING_TEST_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = strings[StringKeys.PVP_WAITING],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = { scope.launch { session.leaveQueue() } }) {
                Text(strings[StringKeys.CANCEL])
            }
        }
    } else {
        WideButton(
            label = strings[StringKeys.PVP_FIND],
            tag = PVP_FIND_TEST_TAG,
            enabled = !session.isBusy,
            onClick = { scope.launch { session.findMatch() } },
        )
    }
}

/**
 * One invitation.
 *
 * Both directions are listed, and they are not the same row: an invitation this player *sent* has
 * nothing to accept, only to withdraw. Showing an Accept on it would be offering them a match
 * against themselves.
 */
@Composable
private fun ChallengeRow(
    challenge: PvpChallenge,
    mine: Boolean,
    onAccept: () -> Unit,
    onDrop: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(challengeRowTestTag(challenge.id))
            .fillMaxWidth()
            .rowSurface(selected = !mine)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (mine) {
                strings.format(StringKeys.PVP_SENT_TO, challenge.toName)
            } else {
                strings.format(StringKeys.PVP_FROM, challenge.fromName)
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!mine) {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag(challengeAcceptTestTag(challenge.id)),
            ) {
                Text(strings[StringKeys.PVP_ACCEPT])
            }
        }
        TextButton(onClick = onDrop) {
            Text(strings[if (mine) StringKeys.CANCEL else StringKeys.PVP_DECLINE])
        }
    }
}
