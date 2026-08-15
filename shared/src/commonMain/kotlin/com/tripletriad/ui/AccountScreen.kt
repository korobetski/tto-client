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
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tripletriad.CLIENT_VERSION
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

/** The build number at the foot of the form. See [VersionLine]. */
const val ACCOUNT_VERSION_TEST_TAG: String = "account-version"

/** The bar under the title while a request is out — a restore on launch, or a submit. */
const val ACCOUNT_BUSY_TEST_TAG: String = "account-busy"

/**
 * Signing in, or creating an account — one screen with a switch, not two.
 *
 * ### Why one screen
 *
 * The two forms take the same two fields, validate them with the same rules ([Credentials]) and
 * differ in one word on one button. Splitting them would mean two layouts to keep in step and a
 * player who typed their name into the wrong one having to type it again.
 *
 * ### How not to type this every time
 *
 * Mostly by not reaching this screen: the session lasts thirty days and is restored on launch, so
 * the ordinary case is never seeing the form at all. When it *is* reached — first run, an expired
 * token, a new server — the name is filled in from [AccountSession.lastUsername] and the password
 * is left to the platform's password manager.
 *
 * That last part takes **two** things, and either one alone does nothing. The fields declare a
 * [ContentType] so the framework knows what they hold, *and* a successful submit calls
 * [androidx.compose.ui.autofill.AutofillManager.commit] so it learns the form was submitted and
 * offers to save. Without the commit nothing is ever saved, and so nothing is ever offered back —
 * which looks from the outside exactly like autofill not working at all.
 *
 * It also takes a working autofill service on the device, which is a system setting and not this
 * app's to set. A phone pointed at a service that is not installed offers nothing, here or in any
 * other app, and there is no way for this screen to tell.
 *
 * The app stores no password of its own, and that is not a gap. See `SessionStore` for what the
 * token already does that a stored password would only do worse.
 *
 * ### What is deliberately missing
 *
 * A "stay signed in" checkbox. The session lasts thirty days and is stored either way; offering the
 * choice would imply the alternative is more secure, and on a device the player owns it is not — it
 * is just a sign-in form more often. And password recovery, which needs a channel the server does
 * not have (`AccountRoutes`).
 *
 * ### Why a required update takes the whole screen
 *
 * Because the form below it cannot work. A server that will not serve this build will refuse the
 * sign-in too, and leaving the fields there would invite the player to type their password into
 * something guaranteed to fail and then read an error about it. A *suggested* update is the
 * opposite case — everything still works — so that one is a note above a form still usable.
 *
 * @param update this build's standing with the server, or null when there is nothing to say. See
 *   [UpdateAdvice].
 * @param onSignedIn where to go once the server has said yes. Not called for a failure — the screen
 *   stays and shows why. Its argument says whether the account was just *created*, which is the one
 *   moment [StarterChoiceScreen] can be offered: `POST /accounts` takes no collection, and this
 *   is the only place that knows the character behind it has never been played.
 */
@Composable
internal fun AccountScreen(
    session: AccountSession,
    update: UpdateAdvice?,
    onSignedIn: (isNew: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    // Null wherever the platform has no autofill framework — desktop, and iOS for now.
    val autofill = LocalAutofillManager.current

    var isRegistering by remember { mutableStateOf(false) }
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

            VersionLine()
        }
    }
}

/**
 * `v1.0.2`, at the foot of the sign-in screen.
 *
 * ### Why this screen and why at all
 *
 * Because it is the screen a player is on when something is wrong, and "which version are you
 * running" is the first question any answer depends on. Until now nothing in the app displayed it:
 * the number was in `gradle.properties`, in the APK's manifest and in the `X-TTO-Version` header,
 * which is three places a player cannot read. See `:shared:buildVersion` for how it gets here.
 *
 * ### It is the release number, not the protocol version
 *
 * [CLIENT_VERSION] and not `CURRENT_VERSION`. The two are deliberately different — a build numbered
 * 1.0.2 speaks protocol 1.0.0 — and the one worth showing is the one that identifies the *build*,
 * because that is what an update changes and what a bug report has to name. The protocol version is
 * the server's business and is on every request already.
 *
 * Faint and last, because it is reference and not instruction: it should be findable when looked
 * for and invisible when not.
 */
@Composable
private fun VersionLine() {
    Text(
        text = "v$CLIENT_VERSION",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .testTag(ACCOUNT_VERSION_TEST_TAG)
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )
}

/** Material's own indicator height, reserved whether or not anything is running. */
private val ProgressHeight = 4.dp
