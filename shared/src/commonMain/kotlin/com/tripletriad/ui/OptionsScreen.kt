package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.settings.UserSettings
import kotlinx.coroutines.launch

const val OPTIONS_BACKGROUND_VOLUME_TEST_TAG: String = "options-background-volume"
const val OPTIONS_NOISE_VOLUME_TEST_TAG: String = "options-noise-volume"

const val OPTIONS_SHEET_TEST_TAG: String = "options-sheet"
const val OPTIONS_CLOSE_TEST_TAG: String = "options-close"

const val OPTIONS_ACCOUNT_GROUP_TEST_TAG: String = "options-account-group"
const val OPTIONS_DELETE_ACCOUNT_TEST_TAG: String = "options-delete-account"
const val OPTIONS_DELETE_PASSWORD_TEST_TAG: String = "options-delete-password"
const val OPTIONS_DELETE_CONFIRM_TEST_TAG: String = "options-delete-confirm"
const val OPTIONS_DELETE_NOTE_TEST_TAG: String = "options-delete-note"

fun optionsLanguageTestTag(locale: AppLocale): String = "options-language-${locale.tag}"

/**
 * The settings, as a sheet over whatever asked for them.
 *
 * A screen was the obvious shape and it was the wrong one. Settings are reachable from the title
 * screen *and* from the lobby, and `Screen.up` is a pure function of the destination — so one of
 * the two callers would always have got the other one's way back, which for the lobby means being
 * dropped out of the session. A sheet has no `up` to be wrong about.
 *
 * It has to stay reachable from the title screen in particular: the language picker is in here,
 * and putting it behind "create an account" means a player who does not read English has to cross
 * an English form to find out how to stop it being in English.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsSheet(
    settings: SettingsHolder,
    account: AccountSession?,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(OPTIONS_SHEET_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpaceLg, vertical = SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceLg),
        ) {
            Text(
                text = strings[StringKeys.SETTINGS],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            OptionsBody(settings = settings, account = account, onDeleted = onDeleted)

            // A sheet is normally left by its handle or by tapping past it, and both still work.
            // This is here for the desktop, where there is no back gesture and dragging a handle
            // with a mouse is a worse way to say "done" than a button is.
            WideButton(
                label = strings[StringKeys.BACK],
                tag = OPTIONS_CLOSE_TEST_TAG,
                filled = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun OptionsBody(
    settings: SettingsHolder,
    account: AccountSession? = null,
    onDeleted: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val current = settings.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpaceLg),
    ) {
        SettingsGroup(strings[StringKeys.GENERAL_SETTINGS]) {
            Label(strings[StringKeys.LANGUAGE])
            LanguageChoice(current) { locale ->
                settings.update { it.copy(language = locale.tag) }
            }
        }

        SettingsGroup(strings[StringKeys.AUDIO_SETTINGS]) {
            Text(
                text = strings[StringKeys.AUDIO_PENDING],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
            )
            VolumeRow(
                label = strings[StringKeys.BACKGROUND_VOLUME],
                value = current.backgroundVolume,
                tag = OPTIONS_BACKGROUND_VOLUME_TEST_TAG,
            ) { volume -> settings.update { it.copy(backgroundVolume = volume) } }
            VolumeRow(
                label = strings[StringKeys.NOISE_VOLUME],
                value = current.noiseVolume,
                tag = OPTIONS_NOISE_VOLUME_TEST_TAG,
            ) { volume -> settings.update { it.copy(noiseVolume = volume) } }
        }

        // Signed in only, and last. A destructive control at the top of a list is a control the
        // thumb reaches on the way to something else.
        if (account?.player != null) {
            SettingsGroup(
                heading = strings[StringKeys.ACCOUNT_SETTINGS],
                modifier = Modifier.testTag(OPTIONS_ACCOUNT_GROUP_TEST_TAG),
            ) {
                account.save?.username?.let { Label(it) }
                DeleteAccountRow(account = account, onDeleted = onDeleted)
            }
        }
    }
}

@Composable
private fun DeleteAccountRow(account: AccountSession, onDeleted: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    // Inline rather than a snackbar, and that is the sheet's doing. A `ModalBottomSheet` draws in
    // its own `Popup`, over the scaffold and therefore over the snackbar host — the refusal was
    // being shown underneath the thing it was about. See `StoreScreen`, which hit the same wall
    // and answered it by closing the sheet first; that answer is not available here, because the
    // message is about the field the player is still standing in.
    var refusal by remember { mutableStateOf<String?>(null) }

    RowButton(
        label = strings[StringKeys.DELETE_ACCOUNT],
        tag = OPTIONS_DELETE_ACCOUNT_TEST_TAG,
        // `error`, which is the theme's own colour for "this went badly" — the same one the board's
        // penalty badge and the shop's refusals use. A destructive control that looks like every
        // other row is a destructive control nobody reads twice.
        color = MaterialTheme.colorScheme.error,
        onClick = {
            open = !open
            password = ""
            refusal = null
        },
    )

    if (!open) return

    Text(
        text = strings[StringKeys.DELETE_ACCOUNT_BODY],
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
        style = MaterialTheme.typography.labelMedium,
    )

    CredentialField(
        value = password,
        onValueChange = { password = it },
        label = strings[StringKeys.PASSWORD],
        tag = OPTIONS_DELETE_PASSWORD_TEST_TAG,
        imeAction = ImeAction.Done,
        isPassword = true,
        // `Password` and not `NewPassword`: this is the existing one being re-typed, so a manager
        // should offer to fill it in and must not offer to save it as a new credential.
        contentType = ContentType.Password,
    )

    refusal?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(OPTIONS_DELETE_NOTE_TEST_TAG),
        )
    }

    WideButton(
        label = strings[StringKeys.DELETE_ACCOUNT_CONFIRM],
        tag = OPTIONS_DELETE_CONFIRM_TEST_TAG,
        enabled = password.isNotEmpty() && !account.isBusy,
        onClick = {
            scope.launch {
                if (account.deleteAccount(password)) {
                    // Straight out, with no confirmation message. The player did not ask to read
                    // one, and the destination says it plainly: the sign-in form is what "there is
                    // no account any more" looks like.
                    onDeleted()
                } else {
                    // Almost always "that is not your password". The failure carries which, and
                    // says it in the player's language.
                    refusal = account.failure?.message(strings)
                        ?: strings[StringKeys.ERROR_BAD_CREDENTIALS]
                    password = ""
                }
            }
        },
    )
}

@Composable
private fun SettingsGroup(
    heading: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(heading)
        TtoCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(SpaceLg),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
                content = content,
            )
        }
    }
}

@Composable
private fun LanguageChoice(settings: UserSettings, onPick: (AppLocale) -> Unit) {
    val selected = settings.locale
    Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
        for (locale in AppLocale.entries) {
            TtoFilterChip(
                label = locale.displayName,
                tag = optionsLanguageTestTag(locale),
                selected = locale == selected,
                onClick = { onPick(locale) },
            )
        }
    }
}

@Composable
private fun VolumeRow(
    label: String,
    value: Float,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label(label)
            // Percent rather than the raw 0..1: `SoundTransform.volume`'s scale is an
            // implementation detail and 0.6 means nothing on a slider.
            Text(
                text = "${(value * PERCENT).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        TtoSlider(value = value, onValueChange = onChange, tag = tag)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val PERCENT = 100
