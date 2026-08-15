package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.SaveSlot
import com.tripletriad.data.Starter
import com.tripletriad.data.StarterCatalog
import com.tripletriad.data.StarterPack
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import kotlinx.coroutines.launch

const val PROFILE_LIST_TEST_TAG: String = "profile-list"
const val PROFILE_NEW_TEST_TAG: String = "profile-new"
const val PROFILE_NAME_TEST_TAG: String = "profile-name"
const val PROFILE_CREATE_TEST_TAG: String = "profile-create"
const val PROFILE_EMPTY_TEST_TAG: String = "profile-empty"

/** The line that says a local profile is this device's, and not arbitrated. */
const val PROFILE_LOCAL_NOTE_TEST_TAG: String = "profile-local-note"
const val STARTER_CONFIRM_TEST_TAG: String = "starter-confirm"

/** `profile-row-<key>`, so a test can find a specific profile without knowing its position. */
fun profileRowTestTag(key: String): String = "profile-row-$key"

/** `profile-delete-<key>`. */
fun profileDeleteTestTag(key: String): String = "profile-delete-$key"

/** `starter-preview-<id>` — the box the chosen set opens with. */
fun starterPreviewTestTag(starterId: String): String = "starter-preview-$starterId"

/**
 * `starter-choice-ff14-beasts` — one tile per box on offer.
 *
 * Was `collection-ff14_`, keyed on the set a character was assigned to. `MODE` is gone: what a
 * player picks is the box they open, and it restricts nothing afterwards.
 */
fun starterChoiceTestTag(starterId: String): String = "starter-choice-$starterId"

/**
 * The profile list — the original's `LoadScreen`, which listed characters rather than files.
 *
 * Every row is a whole profile rather than a name: level, MGP and the win record are the three
 * things that tell a player which of two similarly-named characters is the one they meant, and the
 * profile is already fully decoded by the time the list exists ([SaveSlot]), so showing them costs
 * nothing.
 *
 * Deletion is **two taps and no dialog**: the row's × arms itself and the second tap does it. A
 * modal confirm was the original's answer (`STR_DELETE_SAVE_CONFIRMATION_MESSAGE`, which is why
 * that string is used as the armed label) but a dialog on a phone covers the list it is asking
 * about, and an armed control that disarms when you touch anything else is as recoverable and reads
 * faster.
 *
 * @param onDeleted what else a deleted profile takes with it, by key. Run **after** the profile is
 *   gone and never in its place: anything kept alongside a save is worth less than the save, and a
 *   failure to clean it up must not leave the profile itself half-deleted.
 */
@Composable
internal fun ProfileListScreen(
    session: ProfileSession,
    onSelected: (GameSave) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onDeleted: suspend (String) -> Unit = {},
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = strings[StringKeys.PROFILES], onBack = onBack) {
        // What a local profile is, said where the choice between the two is actually made.
        //
        // The anti-cheat work this game has had makes one promise — **on an account, the server is
        // the only writer of anything with value**. A `.sav` on this device is the other thing: a
        // file the player owns, which they can edit with a text editor, and which no amount of
        // server code protects. That is fine and deliberate: a local save is nobody's business.
        //
        // But a guarantee whose boundary is invisible is not a guarantee to the person relying on
        // it, and "my progress is safe" is exactly the sort of thing a player assumes. So the
        // boundary is named here rather than left to be inferred from the PvP button being absent.
        Text(
            text = strings[StringKeys.PROFILE_LOCAL_NOTE],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .testTag(PROFILE_LOCAL_NOTE_TEST_TAG)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        if (session.isLoaded && session.slots.isEmpty()) {
            Text(
                text = strings[StringKeys.NO_PROFILE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PROFILE_EMPTY_TEST_TAG).padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.testTag(PROFILE_LIST_TEST_TAG).weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(session.slots, key = { it.key }) { slot ->
                    ProfileRow(
                        slot = slot,
                        isArmed = armed == slot.key,
                        onSelect = {
                            armed = null
                            session.select(slot.save)
                            onSelected(slot.save)
                        },
                        onDelete = {
                            if (armed == slot.key) {
                                armed = null
                                scope.launch {
                                    session.delete(slot.key)
                                    onDeleted(slot.key)
                                }
                            } else {
                                armed = slot.key
                            }
                        },
                    )
                }
            }
        }

        WideButton(
            label = strings[StringKeys.NEW_PROFILE],
            tag = PROFILE_NEW_TEST_TAG,
            enabled = !session.isBusy,
            onClick = onNew,
        )
    }
}

@Composable
private fun ProfileRow(
    slot: SaveSlot,
    isArmed: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val save = slot.save

    Row(
        modifier = Modifier
            .testTag(profileRowTestTag(slot.key))
            .fillMaxWidth()
            .rowSurface(armed = isArmed)
            .ttoClickable(onClick = onSelect)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = save.username,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    "${strings[StringKeys.LEVEL]} ${save.level}",
                    "${save.mgp} ${strings[StringKeys.MGP]}",
                ).joinToString(DOT_SEPARATOR),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    "${save.stats.wins} ${strings[StringKeys.WINS]}",
                    "${save.stats.draws} ${strings[StringKeys.DRAWS]}",
                    "${save.stats.defeats} ${strings[StringKeys.DEFEATS]}",
                ).joinToString(DOT_SEPARATOR),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Two styles rather than two raw `sp` values: the armed state spells the word out and the
        // resting one is a single glyph, so they are different *kinds* of label and the scale
        // already names both. The sizes it used — 18 and 12 — predate the type scale.
        Text(
            text = if (isArmed) strings[StringKeys.DELETE] else "×",
            color = if (isArmed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
            },
            style = if (isArmed) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .testTag(profileDeleteTestTag(slot.key))
                .ttoClickable(onClick = onDelete)
                .padding(horizontal = SpaceSm, vertical = SpaceXs),
        )
    }
}

/**
 * Creating a profile: a name, and the box it opens with.
 *
 * This used to choose a **collection**, and that was the one irreversible decision in the game:
 * `Save.DATAS.MODE` decided which card table the profile's ids indexed, which opponents it could
 * meet and which rules they could impose. The original never offered the choice at all —
 * `setToDefaultValues()` hard-codes `'ff14_'` — so its second table shipped unreachable.
 *
 * `MODE` is gone. What is chosen here is a starter pack, and it restricts nothing: a player who
 * opens the FFXIV box buys FFVIII boosters the same afternoon and owns both. Which cards may be
 * *played* is the match's format to decide. Document 19 predicted exactly this — "it becomes a real
 * choice when `MODE` goes" — and this is that.
 */
@Composable
internal fun ProfileCreateScreen(
    session: ProfileSession,
    starters: StarterCatalog,
    onCreated: (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(GameSave.DEFAULT_USERNAME) }
    val offered = remember(starters) { starters.starters }
    var chosen by remember(offered) { mutableStateOf(offered.firstOrNull()) }

    ScreenScaffold(title = strings[StringKeys.NEW_PROFILE], onBack = onBack) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            label = { Text(strings[StringKeys.USERNAME]) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.testTag(PROFILE_NAME_TEST_TAG).fillMaxWidth(),
        )

        StarterChoiceRow(
            selected = chosen,
            offered = offered,
            onSelect = { chosen = it },
        )

        Box(modifier = Modifier.weight(1f))

        WideButton(
            label = strings[StringKeys.START],
            tag = PROFILE_CREATE_TEST_TAG,
            enabled = !session.isBusy,
            onClick = {
                scope.launch {
                    // The authored box, not `GameSave.new`'s five. Both creation paths go through
                    // the catalogue now; see [StarterPack.opened] for why that had to be one place.
                    session.create(name, chosen)
                    session.active?.let(onCreated)
                }
            },
        )
    }
}

/**
 * The collection, for a character the server made without asking — a freshly registered account.
 *
 * `POST /accounts` takes a name and a password and nothing else, so an account's character always
 * starts on `ff14_`. Shown once, immediately after registering, while the profile is still the
 * starter five cards and no match has been played: that is the only window in which the card ids
 * being replaced are certain to be the ones the server dealt a minute ago.
 *
 * ### Changing the collection is not a one-field edit
 *
 * It used to be, and the comment here used to say so: `copy(mode = …)` was harmless because "every
 * card id, deck and opponent it could invalidate is still the default one". That held while an id
 * was an index into whichever table `MODE` named. Ids are global now, so the same five numbers do
 * **not** follow the profile to the other set — they keep naming block 1, and a character that
 * chose FFVIII was left holding five FFXIV cards no screen would show it and no deck could field.
 * That is a registered account that cannot play at all, which is why the choice goes through
 * [StarterPack.startingIn] and not through `copy`.
 *
 * The choice is sent through [ProfileGate.persist] like any other profile change. Skipping it —
 * with Back — leaves `ff14_`, which is what the account already has, so there is nothing to
 * confirm and no way to end up without a collection.
 */
@Composable
internal fun StarterChoiceScreen(
    profile: GameSave,
    starters: StarterCatalog,
    onChosen: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    // The starter is the choice now, not a collection. `MODE` used to make picking a set the
    // decision — it gated the shop, the opponents and the campaign — so the starter followed from
    // it. With `MODE` gone the box a player opens is the whole of what they are choosing, which is
    // what document 19 said it should have been.
    val offered = remember(starters) { starters.starters }
    var chosen by remember(offered) { mutableStateOf(offered.firstOrNull()) }

    ScreenScaffold(title = strings[StringKeys.COLLECTION], onBack = onBack) {
        Text(
            text = profile.username,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        StarterChoiceRow(
            selected = chosen,
            offered = offered,
            onSelect = { chosen = it },
        )

        Box(modifier = Modifier.weight(1f))

        WideButton(
            label = strings[StringKeys.START],
            tag = STARTER_CONFIRM_TEST_TAG,
            onClick = {
                val starter = chosen ?: return@WideButton
                scope.launch { onChosen(StarterPack.opened(profile, starter)) }
            },
        )
    }
}

/** The starters on offer, shared by profile creation and the post-registration step. */
@Composable
private fun StarterChoiceRow(
    selected: Starter?,
    offered: List<Starter>,
    onSelect: (Starter) -> Unit,
) {
    val strings = LocalStrings.current

    Text(
        text = strings[StringKeys.COLLECTION],
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (choice in offered) {
            StarterChoice(
                starter = choice,
                isSelected = selected?.id == choice.id,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(choice) },
            )
        }
    }

    StarterPreview(starter = selected)
}

/**
 * What the chosen box actually contains: its name, and the five cards it opens with.
 *
 * ### Why the cards are drawn
 *
 * Because the choice is otherwise between two proper nouns. `FFXIV` and `FFVIII` say which game
 * the art comes from and nothing about what is being handed over, and this is the one
 * irreversible decision the game asks for — `MODE` cannot be changed after this screen. Document
 * 19 removes the irreversibility, not the choice; until it does, showing the hand is what makes
 * the choice informed.
 *
 * The **deck** and not all ten cards, as in the shop: it is the five the starter is about, it is
 * what the character will be holding in its first match, and ten thumbnails wrap on a phone.
 *
 * Absent rather than empty when no starter is authored for the set — a content bug
 * [StarterCatalog.violations] refuses, and one this screen should not invent a placeholder for.
 */
@Composable
private fun StarterPreview(starter: Starter?) {
    val strings = LocalStrings.current
    if (starter == null) return

    Column(
        modifier = Modifier
            .testTag(starterPreviewTestTag(starter.id))
            .fillMaxWidth()
            .padding(top = 12.dp)
            .rowSurface()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = strings[starter.nameKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // By id: this screen has no `CardCatalog`, and the thumbnail is the only thing being
            // drawn. A card whose art has not loaded is a plate rather than a crash, which is
            // [UiArt]'s contract everywhere else.
            for (id in starter.deck) {
                CardThumb(cardId = id)
            }
        }
    }
}

@Composable
private fun StarterChoice(
    starter: Starter,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier
            .testTag(starterChoiceTestTag(starter.id))
            .rowSurface(selected = isSelected)
            .ttoClickable(
                role = Role.RadioButton,
                selected = isSelected,
                onClick = onClick,
            )
            .padding(vertical = SpaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = strings[starter.nameKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** The longest a character name may be. Long enough for any real name, short enough to lay out. */
private const val MAX_NAME_LENGTH = 24
