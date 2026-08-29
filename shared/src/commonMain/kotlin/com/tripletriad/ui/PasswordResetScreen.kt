package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.tripletriad.protocol.Credentials
import kotlinx.coroutines.launch

const val RESET_SCREEN_TEST_TAG: String = "reset-screen"
const val RESET_NAME_TEST_TAG: String = "reset-name"
const val RESET_SEND_TEST_TAG: String = "reset-send"
const val RESET_CODE_TEST_TAG: String = "reset-code"
const val RESET_PASSWORD_TEST_TAG: String = "reset-password"
const val RESET_SUBMIT_TEST_TAG: String = "reset-submit"
const val RESET_ERROR_TEST_TAG: String = "reset-error"

/**
 * Getting back into an account whose password is gone.
 *
 * ### Why both halves are on one screen
 *
 * Because the second half cannot be reached any other way, and splitting them would mean a
 * destination whose only entrance is the one before it — the player would still be typing the same
 * three things in the same order, with a transition in the middle for no reason. The code and the
 * new password appear once the request has been made, which is also the only moment they are
 * useful.
 *
 * ### What the screen may not say
 *
 * Whether the account exists. The server answers 202 for a name it has never heard of, deliberately
 * — otherwise this form is a way of asking which usernames are registered, which is the leak the
 * sign-in form carefully does not have. So the message after *send* is conditional in its wording
 * and this screen keeps it that way: **if** that account exists, a code is on its way.
 *
 * ### And what it costs on success
 *
 * Every session on the account, including this device's. That is the point rather than a side
 * effect: somebody resetting a password usually believes it has leaked, and leaving the other
 * party's thirty-day token alive would answer the wrong half of the problem.
 */
@Composable
internal fun PasswordResetScreen(session: AccountSession, onDone: () -> Unit, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var username by remember(session.lastUsername) {
        mutableStateOf(session.lastUsername.orEmpty())
    }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSent by remember { mutableStateOf(false) }
    val note = rememberNoteHost(RESET_ERROR_TEST_TAG)

    LaunchedEffect(session.failure) {
        session.failure?.let { note.show(it.message(strings)) }
    }

    ScreenScaffold(
        title = strings[StringKeys.RESET_PASSWORD],
        onBack = onBack,
        snackbar = note,
    ) {
        Column(
            modifier = Modifier.testTag(RESET_SCREEN_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(ProgressHeight)) {
                if (session.isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(ProgressHeight),
                    )
                }
            }

            Text(
                text = strings[if (isSent) StringKeys.RESET_SENT else StringKeys.RESET_BLURB],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            CredentialField(
                value = username,
                onValueChange = { username = it.take(Credentials.USERNAME_LENGTH.last) },
                label = strings[StringKeys.USERNAME],
                tag = RESET_NAME_TEST_TAG,
                imeAction = ImeAction.Next,
                contentType = ContentType.Username,
            )

            WideButton(
                label = strings[StringKeys.RESET_SEND],
                tag = RESET_SEND_TEST_TAG,
                enabled = username.trim().length >= Credentials.USERNAME_LENGTH.first &&
                    !session.isBusy,
                // Not filled once the fields below are showing: from that point the button that
                // finishes the errand is the one at the bottom, and two identical-looking primary
                // buttons would leave the player to guess which.
                filled = !isSent,
                onClick = {
                    scope.launch {
                        if (session.recovery.requestReset(username, strings.locale.tag)) {
                            isSent = true
                            note.show(strings[StringKeys.RESET_SENT])
                        }
                    }
                },
            )

            // Only after a request. Showing them from the start would invite somebody to type a
            // code they have not been sent, and the refusal would look like the code was wrong
            // rather than like the request was never made.
            if (isSent) {
                CredentialField(
                    value = code,
                    onValueChange = { typed ->
                        code = typed.filter { it.isDigit() }.take(AccountCode.LENGTH)
                    },
                    label = strings[StringKeys.CODE],
                    tag = RESET_CODE_TEST_TAG,
                    imeAction = ImeAction.Next,
                    // See `ConfirmEmailScreen`: no platform offers a category for a mailed code,
                    // and the password ones would ask a manager to save six digits as a secret.
                    contentType = ContentType.Username,
                )

                CredentialField(
                    value = password,
                    onValueChange = { password = it.take(Credentials.PASSWORD_LENGTH.last) },
                    label = strings[StringKeys.NEW_PASSWORD],
                    tag = RESET_PASSWORD_TEST_TAG,
                    imeAction = ImeAction.Done,
                    isPassword = true,
                    contentType = ContentType.NewPassword,
                )

                WideButton(
                    label = strings[StringKeys.RESET_SUBMIT],
                    tag = RESET_SUBMIT_TEST_TAG,
                    enabled = AccountCode.looksValid(code) &&
                        password.length >= Credentials.PASSWORD_LENGTH.first &&
                        !session.isBusy,
                    onClick = {
                        scope.launch {
                            if (session.recovery.reset(username, code, password)) {
                                note.show(strings[StringKeys.RESET_DONE])
                                onDone()
                            }
                        }
                    },
                )
            }
        }
    }
}

private val ProgressHeight = 4.dp
