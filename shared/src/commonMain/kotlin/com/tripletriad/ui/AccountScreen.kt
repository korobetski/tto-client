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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.protocol.Credentials
import kotlinx.coroutines.launch

const val ACCOUNT_SCREEN_TEST_TAG: String = "account-screen"
const val ACCOUNT_NAME_TEST_TAG: String = "account-name"
const val ACCOUNT_PASSWORD_TEST_TAG: String = "account-password"
const val ACCOUNT_SUBMIT_TEST_TAG: String = "account-submit"
const val ACCOUNT_TOGGLE_TEST_TAG: String = "account-toggle"
const val ACCOUNT_ERROR_TEST_TAG: String = "account-error"

const val ACCOUNT_VERSION_TEST_TAG: String = "account-version"

const val ACCOUNT_BUSY_TEST_TAG: String = "account-busy"

@Composable
internal fun AccountScreen(
    session: AccountSession,
    update: UpdateAdvice?,
    // Which half the title screen asked for. The toggle below is still how a player changes
    // their mind; this is so a button that says *create an account* does not open a sign-in
    // form and leave them to notice.
    registering: Boolean = false,
    onSignedIn: (isNew: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    // Null wherever the platform has no autofill framework — desktop, and iOS for now.
    val autofill = LocalAutofillManager.current

    var isRegistering by remember(registering) { mutableStateOf(registering) }
    // Keyed on the remembered name so it survives recomposition but not a change of account: this
    // screen is reached again after a sign-out and after a server switch, and both set it to null.
    var username by remember(session.lastUsername) {
        mutableStateOf(session.lastUsername.orEmpty())
    }
    var password by remember { mutableStateOf("") }

    // Validated locally so the player learns their password is too short without a round trip. The
    // server checks the same rules and is the only check that counts — `Credentials.looksValid`.
    val credentials = Credentials(username, password)
    val canSubmit = credentials.looksValid() && !session.isBusy

    val title = strings[if (isRegistering) StringKeys.CREATE_ACCOUNT else StringKeys.SIGN_IN]
    val note = rememberNoteHost(ACCOUNT_ERROR_TEST_TAG)

    // The refusal, shown once and where a message belongs — over the form rather than wedged
    // between the password and the button, which pushed the button down as the player read it.
    // Keyed on the failure, so the same refusal twice is announced twice.
    LaunchedEffect(session.failure) {
        session.failure?.let { note.show(it.message(strings)) }
    }

    if (update?.isRequired == true) {
        ScreenScaffold(title = strings[StringKeys.UPDATE_NEEDED], onBack = onBack) {
            UpdateNotice(update)
        }
        return
    }

    ScreenScaffold(title = title, onBack = onBack, snackbar = note) {
        Column(
            modifier = Modifier.testTag(ACCOUNT_SCREEN_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            // A bar rather than a spinner on the button: `restore()` and a submit both go through
            // `isBusy`, and the first of those happens with nothing on screen to spin. Always laid
            // out, so the form does not shift down the moment a request starts.
            Box(modifier = Modifier.fillMaxWidth().height(ProgressHeight)) {
                if (session.isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .testTag(ACCOUNT_BUSY_TEST_TAG)
                            .fillMaxWidth()
                            .height(ProgressHeight),
                    )
                }
            }

            update?.let { UpdateNotice(it) }

            Text(
                text = strings[StringKeys.ACCOUNT_BLURB],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            CredentialField(
                value = username,
                onValueChange = { username = it.take(Credentials.USERNAME_LENGTH.last) },
                label = strings[StringKeys.USERNAME],
                tag = ACCOUNT_NAME_TEST_TAG,
                imeAction = ImeAction.Next,
                contentType = if (isRegistering) ContentType.NewUsername else ContentType.Username,
            )

            CredentialField(
                value = password,
                onValueChange = { password = it.take(Credentials.PASSWORD_LENGTH.last) },
                label = strings[StringKeys.PASSWORD],
                tag = ACCOUNT_PASSWORD_TEST_TAG,
                imeAction = ImeAction.Done,
                isPassword = true,
                contentType = if (isRegistering) ContentType.NewPassword else ContentType.Password,
            )

            WideButton(
                label = title,
                tag = ACCOUNT_SUBMIT_TEST_TAG,
                enabled = canSubmit,
                onClick = {
                    scope.launch {
                        if (isRegistering) {
                            session.register(username.trim(), password)
                        } else {
                            session.signIn(username.trim(), password)
                        }
                        // Only on success. `player` is the honest test for that: it is set by the
                        // same branch that stored the token, so it cannot disagree with whether
                        // there is a session to navigate into.
                        if (session.player != null) {
                            // What actually makes the password manager offer to save. Declaring
                            // the fields' `ContentType` is only half of it: without this the
                            // framework never learns the form was submitted, so it never prompts —
                            // and with nothing ever saved there is nothing to fill in next time.
                            //
                            // After the success check, deliberately. Committing on every press
                            // would offer to save a password the server has just rejected, which
                            // is how a manager ends up holding a wrong one for the right account.
                            autofill?.commit()
                            onSignedIn(isRegistering)
                        }
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = strings[
                        if (isRegistering) {
                            StringKeys.ACCOUNT_TO_SIGN_IN
                        } else {
                            StringKeys.ACCOUNT_TO_REGISTER
                        },
                    ],
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag(ACCOUNT_TOGGLE_TEST_TAG)
                        .clickable { isRegistering = !isRegistering }
                        .padding(8.dp),
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                VersionLine(ACCOUNT_VERSION_TEST_TAG)
            }
        }
    }
}

private val ProgressHeight = 4.dp
