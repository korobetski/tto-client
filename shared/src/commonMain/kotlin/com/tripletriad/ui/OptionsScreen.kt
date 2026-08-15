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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.settings.UserSettings
import kotlinx.coroutines.launch

const val OPTIONS_BACKGROUND_VOLUME_TEST_TAG: String = "options-background-volume"
const val OPTIONS_NOISE_VOLUME_TEST_TAG: String = "options-noise-volume"

/** The account group and the two steps of deleting one. See [DeleteAccountRow]. */
const val OPTIONS_ACCOUNT_GROUP_TEST_TAG: String = "options-account-group"
const val OPTIONS_DELETE_ACCOUNT_TEST_TAG: String = "options-delete-account"
const val OPTIONS_DELETE_PASSWORD_TEST_TAG: String = "options-delete-password"
const val OPTIONS_DELETE_CONFIRM_TEST_TAG: String = "options-delete-confirm"
const val OPTIONS_DELETE_NOTE_TEST_TAG: String = "options-delete-note"

/** `options-language-fr_FR` and so on, so a test can name the chip it means. */
fun optionsLanguageTestTag(locale: AppLocale): String = "options-language-${locale.tag}"

/**
 * The three settings `UserSettings.json` actually holds: language and the two volumes.
 *
 * Grouped under the AS3's own headings — `STR_GENERAL_SETTINGS` and `STR_AUDIO_SETTINGS`, which
 * `SettingsScreen.as` uses for the same split — so the four bundles already carry every label on
 * this screen except **Back** and the audio caveat.
 *
 * ### Changes apply and persist immediately
 *
 * There is no Save button, and `STR_SETTINGS_SAVED` (which exists in all four bundles) is not
 * used. `SettingsScreen.as` had one because Feathers gave it a form; on a phone, a settings pane
 * you can leave with the system Back gesture must not be able to lose what you just did. Picking a
 * language redraws this screen in it, which *is* the confirmation — a toast saying "saved" would
 * be telling the user something the screen already showed them.
 *
 * ### The caveat under the volumes is now only half true
 *
 * `APP_AUDIO_PENDING` — "saved, but nothing plays yet" — was written when nothing did. The Android
 * host installs a real `AndroidAudioPlayer` and the match has had music and effects since Phase 1;
 * the **desktop host still installs `SilentAudioPlayer`**, so the line is right there and wrong on
 * a phone. Left in place rather than deleted because which of the two to fix is a product call, not
 * a wording one.
 *
 * ### On the shell, like everything else
 *
 * [ScreenScaffold] provides the title and the back control, so this screen's back sits where every
 * other screen's does. The groups are cards rather than headings over bare rows — a settings pane
 * is a list of *groups*, and a group whose edge the eye cannot find is a heading pretending to be
 * one.
 *
 * ### Why deleting an account is on the settings screen and not next to Logout
 *
 * Logout is on the dashboard, where it is one tap from a player who meant to go home. That is right
 * for a sign-out, which costs a sign-in to undo, and wrong for a deletion, which costs everything
 * and cannot be undone. Settings is a screen somebody navigates to on purpose, which is the first
 * of the two gates; the second is typing the password.
 *
 * @param account the signed-in session, or null in local-profile mode. Null hides the account group
 *   entirely rather than disabling it: there is no account to act on, and a greyed-out **Delete
 *   account** invites the question of whose.
 * @param onDeleted called once the server has confirmed the account is gone, so the shell can leave
 *   a screen whose subject no longer exists. Not called for a refusal — the screen stays and says
 *   why, exactly as the sign-in form does.
 */
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

/**
 * **Delete account**, and behind it the only gate that means anything: the password.
 *
 * ### Why this is not the `armed` two-tap the rest of the app uses
 *
 * `ProfileScreen` and `InventoryBody` both arm a button and let the second tap do the thing, and
 * that is right for what they delete — a local save file the player can make again, an item they
 * can buy again. Two taps in the same place is a *rhythm*, though, and rhythm is exactly what a
 * mis-tap has. It is the wrong gate for the one action in this app that nothing can undo.
 *
 * So the second step is not another tap: it is typing the password. That cannot be arrived at by
 * momentum, it is the same thing the **server** insists on — `AccountRoutes` will refuse without it
 * — and it is what makes "somebody picked up an unlocked phone" a different event from "the owner
 * asked to be forgotten".
 *
 * ### What the first tap does, and does not
 *
 * It reveals the paragraph and the field. Nothing is sent, nothing is armed, and tapping again
 * closes it and **clears the typed password**, so backing out leaves nothing behind for the next
 * person holding the phone.
 *
 * The confirm button stays disabled until something is typed. That is not validation — the server
 * decides whether the password is right — it is refusing to send a request that could only be
 * refused, on an endpoint deliberately rate-limited as a place where passwords get guessed.
 */
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

/**
 * A heading and the card under it.
 *
 * The heading stays outside the card: Material puts a group's label above its container, and a
 * label inside one reads as the first row of it.
 */
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

/**
 * One chip per locale, labelled in the language it selects.
 *
 * `AppLocale.displayName` is the endonym — `Deutsch`, not `German` — so the list is readable to
 * someone who has landed in a language they cannot read and is looking for their own.
 */
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
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.tertiary,
                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
        )
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
