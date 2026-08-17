package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.settings.UserSettings
import kotlinx.coroutines.launch

const val OPTIONS_BACKGROUND_VOLUME_TEST_TAG: String = "options-background-volume"
const val OPTIONS_NOISE_VOLUME_TEST_TAG: String = "options-noise-volume"

const val OPTIONS_ACCOUNT_GROUP_TEST_TAG: String = "options-account-group"
const val OPTIONS_DELETE_ACCOUNT_TEST_TAG: String = "options-delete-account"
const val OPTIONS_DELETE_PASSWORD_TEST_TAG: String = "options-delete-password"
const val OPTIONS_DELETE_CONFIRM_TEST_TAG: String = "options-delete-confirm"
const val OPTIONS_DELETE_NOTE_TEST_TAG: String = "options-delete-note"

fun optionsLanguageTestTag(locale: AppLocale): String = "options-language-${locale.tag}"

@Composable
internal fun OptionsScreen(
    settings: SettingsHolder,
    onBack: () -> Unit,
    account: AccountSession? = null,
    onDeleted: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val current = settings.value
    val note = rememberNoteHost(OPTIONS_DELETE_NOTE_TEST_TAG)

    ScreenScaffold(title = strings[StringKeys.SETTINGS], onBack = onBack, snackbar = note) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                    DeleteAccountRow(account = account, note = note, onDeleted = onDeleted)
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountRow(account: AccountSession, note: NoteHost, onDeleted: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

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

    WideButton(
        label = strings[StringKeys.DELETE_ACCOUNT_CONFIRM],
        tag = OPTIONS_DELETE_CONFIRM_TEST_TAG,
        enabled = password.isNotEmpty() && !account.isBusy,
        onClick = {
            scope.launch {
                if (account.deleteAccount(password)) {
                    // Straight out, with no confirmation message, and that is a correction rather
                    // than an omission. The first version showed one first — but `NoteHost.show`
                    // **suspends until the snackbar goes away**, so it did not put a message on the
                    // way out, it held the player on a settings screen for four seconds after their
                    // account had ceased to exist. A snackbar cannot survive the screen that hosts
                    // it, so the choice was to leave late or to leave now.
                    //
                    // Leaving now is also what Logout does, and the destination says it plainly:
                    // the sign-in form is what "there is no account any more" looks like.
                    onDeleted()
                } else {
                    // Almost always "that is not your password". The failure carries which, and
                    // says it in the player's language.
                    note.show(
                        account.failure?.message(strings)
                            ?: strings[StringKeys.ERROR_BAD_CREDENTIALS],
                    )
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
