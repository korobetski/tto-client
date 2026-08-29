package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.protocol.AccountCode
import kotlinx.coroutines.launch

const val CONFIRM_SCREEN_TEST_TAG: String = "confirm-screen"
const val CONFIRM_CODE_TEST_TAG: String = "confirm-code"
const val CONFIRM_SUBMIT_TEST_TAG: String = "confirm-submit"
const val CONFIRM_RESEND_TEST_TAG: String = "confirm-resend"
const val CONFIRM_LATER_TEST_TAG: String = "confirm-later"
const val CONFIRM_ERROR_TEST_TAG: String = "confirm-error"

/**
 * Typing back the six digits that were mailed.
 *
 * ### Why a code and not a link
 *
 * A link in a mail has to land somewhere, and this app is on four platforms with no web page
 * between them: it would mean serving an HTML page, a deep link on Android, a custom scheme on
 * iOS, and something else again on desktop — where a browser opening cannot reach the running
 * app at all. Six digits read off a screen and typed here work identically everywhere, and cost
 * the player one extra gesture.
 *
 * What they cost in security is bounded on the server, not here: five attempts, ten minutes, and
 * a rate limit on how often a new one can be asked for. See its `CodeStore`.
 *
 * ### Why *Later* is a real answer
 *
 * Nothing behind this screen is closed to an unconfirmed account except playing other people and
 * the auction house, and both are shut by level long before they are shut by this. A player who
 * has just registered wants to play, and standing between them and the game to collect a
 * confirmation they do not yet need would be asking for the wrong thing at the wrong moment. The
 * lobby says so again when it matters — see `DashboardScreen`, whose multiplayer card names this
 * as the reason the door is shut.
 */
@Composable
internal fun ConfirmEmailScreen(
    session: AccountSession,
    onConfirmed: () -> Unit,
    onLater: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    val note = rememberNoteHost(CONFIRM_ERROR_TEST_TAG)

    LaunchedEffect(session.failure) {
        session.failure?.let { note.show(it.message(strings)) }
    }

    ScreenScaffold(
        title = strings[StringKeys.CONFIRM_EMAIL],
        onBack = onLater,
        snackbar = note,
    ) {
        Column(
            modifier = Modifier.testTag(CONFIRM_SCREEN_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(ProgressHeight)) {
                if (session.isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(ProgressHeight),
                    )
                }
            }

            // The address is repeated back rather than merely referred to, because the commonest
            // reason a code does not arrive is a typo in it — and a player who cannot see what
            // they typed has no way to notice.
            Text(
                text = strings.format(
                    StringKeys.CONFIRM_EMAIL_BLURB,
                    session.player?.email.orEmpty(),
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            CredentialField(
                value = code,
                // Digits only, and never more than fit: the field cannot be made to hold something
                // the server would refuse, so a wrong code here is a wrong code rather than a typo
                // the player cannot see.
                onValueChange = { typed ->
                    code = typed.filter { it.isDigit() }.take(AccountCode.LENGTH)
                },
                label = strings[StringKeys.CODE],
                tag = CONFIRM_CODE_TEST_TAG,
                imeAction = ImeAction.Done,
                // There is no autofill category for a mailed code that any of these platforms
                // offers, and `NewPassword` would invite a password manager to save six digits as
                // a credential. `Username` is the least wrong of a bad set: inert everywhere.
                contentType = ContentType.Username,
            )

            WideButton(
                label = strings[StringKeys.CODE_SUBMIT],
                tag = CONFIRM_SUBMIT_TEST_TAG,
                enabled = AccountCode.looksValid(code) && !session.isBusy,
                onClick = {
                    scope.launch {
                        if (session.recovery.confirmEmail(code)) {
                            note.show(strings[StringKeys.EMAIL_CONFIRMED])
                            onConfirmed()
                        }
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = strings[StringKeys.CODE_RESEND],
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .testTag(CONFIRM_RESEND_TEST_TAG)
                        .clickable(enabled = !session.isBusy) {
                            scope.launch {
                                if (session.recovery.resendCode(strings.locale.tag)) {
                                    // The old code stops working the moment a new one is stored,
                                    // so the field is cleared with it: leaving six stale digits
                                    // under a message saying a new code is coming invites the
                                    // player to submit the wrong one and spend an attempt.
                                    code = ""
                                    note.show(strings[StringKeys.CODE_SENT])
                                }
                            }
                        }
                        .padding(8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = strings[StringKeys.CONFIRM_LATER],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .testTag(CONFIRM_LATER_TEST_TAG)
                        .clickable { onLater() }
                        .padding(8.dp),
                )
            }
        }
    }
}

private val ProgressHeight = 4.dp
