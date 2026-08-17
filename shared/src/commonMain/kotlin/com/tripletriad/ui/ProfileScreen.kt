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

const val PROFILE_LOCAL_NOTE_TEST_TAG: String = "profile-local-note"
const val STARTER_CONFIRM_TEST_TAG: String = "starter-confirm"

fun profileRowTestTag(key: String): String = "profile-row-$key"

fun profileDeleteTestTag(key: String): String = "profile-delete-$key"

fun starterPreviewTestTag(starterId: String): String = "starter-preview-$starterId"

fun starterChoiceTestTag(starterId: String): String = "starter-choice-$starterId"

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

private const val MAX_NAME_LENGTH = 24
