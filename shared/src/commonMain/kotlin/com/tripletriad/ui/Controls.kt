package com.tripletriad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.LocalTtoColors

const val SCREEN_BACK_TEST_TAG: String = "screen-back"

const val CHARACTER_BAR_TEST_TAG: String = "character-bar"

@Composable
internal fun WideButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    // `TouchLabel.as:31` played this on any tap on a control, so it belongs to the control and not
    // to each caller — otherwise the next screen added is the one that forgets it.
    val audio = LocalAudio.current
    val press = {
        audio.play(Sound.UI_CLICK)
        onClick()
    }
    // Material's own height for a button that is the point of its screen, and `large` rather than
    // `extraSmall`: a 4 dp radius on a 56 dp bar reads as a rectangle, which is what made the
    // stack of them on the main menu look like a list of table rows rather than a set of choices.
    val shell = Modifier.fillMaxWidth().height(ButtonHeight).testTag(tag)
    val shape = MaterialTheme.shapes.large
    val content: @Composable RowScope.() -> Unit = {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (filled) {
        Button(
            onClick = press,
            enabled = enabled,
            modifier = shell,
            shape = shape,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = press,
            enabled = enabled,
            modifier = shell,
            shape = shape,
            content = content,
        )
    }
}

private val ButtonHeight = 56.dp

@Composable
internal fun Modifier.ttoClickable(
    role: Role = Role.Button,
    enabled: Boolean = true,
    selected: Boolean? = null,
    shape: Shape? = null,
    sound: Sound? = Sound.UI_CLICK,
    onClick: () -> Unit,
): Modifier {
    val audio = LocalAudio.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    // Clicking focuses on desktop, and the focus outlives the click — so a ring drawn on
    // focus alone stays behind after a card is deselected, saying "selected" when nothing
    // is. The ring is for keyboard travel, so it is shown when the keyboard is what moved
    // focus. This is the rule Material's own components follow.
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val ring = MaterialTheme.colorScheme.secondary
    val ringShape = shape ?: MaterialTheme.shapes.small

    return this
        .clickable(
            interactionSource = interactions,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = {
                sound?.let { audio.play(it) }
                onClick()
            },
        )
        .then(
            if (focused && keyboard) {
                Modifier.border(FocusRingWidth, ring, ringShape)
            } else {
                Modifier
            },
        )
        .semantics { selected?.let { this.selected = it } }
}

private val FocusRingWidth = 2.dp

@Composable
internal fun TtoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    armed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val game = LocalTtoColors.current
    val shape = MaterialTheme.shapes.medium
    val isChosen = selected == true
    val clickable = onClick?.let { action ->
        Modifier.ttoClickable(selected = selected, shape = shape, onClick = action)
    } ?: Modifier

    OutlinedCard(
        modifier = modifier.then(clickable),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isChosen) {
                game.selectedFill
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = BorderStroke(
            width = HairlineWidth,
            color = when {
                armed -> MaterialTheme.colorScheme.error
                isChosen -> game.selectedOutline
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        content = content,
    )
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(bottom = SpaceXs).semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: NoteHost? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalStrings.current
    val navigation = LocalNavigation.current
    val isWide = LocalWideLayout.current
    // The navigation, if it is going down the left edge — null when it is along the bottom or when
    // there is none. One value rather than a boolean beside a nullable, so that "the rail is up"
    // and "there is something to put in it" cannot disagree: the rail *replaces* the bar, and two
    // sets of the same four entries is the one thing an adaptive layout must not do.
    val rail = navigation.takeIf { isWide }
    val columnWidth = if (wide && isWide) WideContentMaxWidth else ContentMaxWidth

    Row(modifier = Modifier.fillMaxSize()) {
        rail?.let { SideNavigation(it) }

        Scaffold(
            // The app draws its own backdrop under everything — see `App` — and a Scaffold that
            // painted `surface` over it would put a lighter rectangle behind every screen.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TopAppBar(
                        modifier = Modifier.widthIn(max = columnWidth),
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag(SCREEN_BACK_TEST_TAG),
                            ) {
                                Icon(
                                    imageVector = TtoIcons.Back,
                                    contentDescription = strings[StringKeys.BACK],
                                )
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor =
                            MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    bottomBar?.let { bar ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = columnWidth)
                                    .fillMaxWidth(),
                            ) {
                                bar()
                            }
                        }
                    }
                    // Below the screen's own action, because it leaves the screen and the action
                    // does not: the thing nearest the thumb should act on what is above it. Absent
                    // outside a character's shell, and on a wide window where the rail has it.
                    if (rail == null) navigation?.let { BottomNavigation(it) }
                }
            },
            snackbarHost = {
                snackbar?.let { host ->
                    SnackbarHost(hostState = host.state) { data ->
                        // Tagged rather than found by text: a confirmation names the thing it is
                        // about — a card, a pack — so its wording is data, and a test asserting on
                        // it would be asserting on the catalogue.
                        //
                        // The colours are named rather than defaulted **because the default is
                        // right**: `inverseSurface` is what a snackbar is meant to draw on, and for
                        // as long as the scheme left that role unfilled every confirmation in this
                        // game appeared as a light lavender box on a dark screen. Saying them here
                        // is what makes that visible at the one call site it was ever wrong at.
                        Snackbar(
                            snackbarData = data,
                            modifier = Modifier.testTag(host.tag),
                            shape = MaterialTheme.shapes.small,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            actionColor = MaterialTheme.colorScheme.inversePrimary,
                        )
                    }
                }
            },
        ) { inset ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inset),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = columnWidth)
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    content = content,
                )
            }
        }
    }
}

fun screenTabTestTag(key: String): String = "tab-$key"

@Composable
internal fun ScreenTabs(
    tabs: List<Pair<String, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val audio = LocalAudio.current

    // The gap belongs to the tab row and not to each screen behind it. A `PrimaryTabRow` puts its
    // indicator flush against its own bottom edge, so content that starts immediately underneath
    // has the underline running through its first line — which is what the collection's
    // "Owned · 10 / 263" was doing once the type scale grew.
    Column(modifier = modifier.fillMaxWidth()) {
        PrimaryTabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            tabs.forEachIndexed { index, (label, tag) ->
                Tab(
                    selected = index == selected,
                    onClick = {
                        audio.play(Sound.UI_CLICK)
                        onSelect(index)
                    },
                    modifier = Modifier.testTag(tag),
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor =
                    MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                )
            }
        }
        Spacer(modifier = Modifier.height(SpaceMd))
    }
}

@Composable
internal fun TtoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.testTag(tag),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.tertiary,
            activeTrackColor = MaterialTheme.colorScheme.tertiary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

@Stable
internal class NoteHost(val tag: String) {
    val state: SnackbarHostState = SnackbarHostState()

    suspend fun show(message: String) {
        state.currentSnackbarData?.dismiss()
        state.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }
}

@Composable
internal fun rememberNoteHost(tag: String): NoteHost = remember(tag) { NoteHost(tag) }

@Composable
internal fun CharacterActions(save: GameSave) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.testTag(CHARACTER_BAR_TEST_TAG).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoonMarker(TtoIcons.MgpBoon, strings[StringKeys.MGP], save.boons.mgp)
        BoonMarker(TtoIcons.XpBoon, strings[StringKeys.XP], save.boons.xp)
        Text(
            text = "${strings[StringKeys.LEVEL]} ${save.level}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
        // The purse as the game's own coin rather than the letters `MGP`: it is the one number on
        // this bar the player is tracking. Drawn rather than `icons/PGS.png`, which was 29 px of
        // token scaled to 16 dp beside vector text — see [TtoIcons.Chip].
        Icon(
            imageVector = TtoIcons.Chip,
            contentDescription = strings[StringKeys.MGP],
            tint = LocalTtoColors.current.currency,
            modifier = Modifier.size(IconSm),
        )
        Text(
            text = "${save.mgp}",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun BoonMarker(icon: ImageVector, label: String, matches: Int) {
    if (matches <= 0) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = LocalTtoColors.current.transient,
            modifier = Modifier.size(IconSm),
        )
        Text(
            text = "×$matches",
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun CharacterScaffold(
    profile: GameSave,
    title: String,
    onBack: () -> Unit,
    snackbar: NoteHost? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = { CharacterActions(profile) },
        snackbar = snackbar,
        bottomBar = bottomBar,
        wide = wide,
        content = content,
    )
}

@Composable
internal fun EmptyNote(text: String, tag: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(tag).padding(vertical = 24.dp),
    )
}

@Composable
internal fun LoadingNote(tag: String) {
    val strings = LocalStrings.current
    val label = strings[StringKeys.LOADING]

    Box(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            // Announced as one thing rather than left as an unlabelled shape: a spinner with no
            // description is a screen reader saying nothing at all while the screen is busy.
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize),
            strokeWidth = SpinnerStroke,
        )
    }
}

private val SpinnerSize = 28.dp
private val SpinnerStroke = 3.dp

enum class ListState {
    LOADING,

    READY,

    FAILED,
}

@Composable
internal fun RowButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = color?.let { ButtonDefaults.textButtonColors(contentColor = it) }
            ?: ButtonDefaults.textButtonColors(),
        modifier = Modifier.testTag(tag).heightIn(min = MinTouchTarget),
    ) {
        Text(label)
    }
}

@Composable
internal fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    imeAction: ImeAction,
    contentType: ContentType,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        // The password is masked as it is typed, and this is the only place in the app that
        // renders one at all. It is never logged, never stored, and never put in a `toString`.
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.contentType = contentType }
            .fillMaxWidth(),
    )
}

@Composable
internal fun FailedNote(text: String, tag: String, onRetry: () -> Unit) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(tag).fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
            style = MaterialTheme.typography.bodyMedium,
        )
        RowButton(label = strings[StringKeys.RETRY], tag = "$tag-retry", onClick = onRetry)
    }
}

@Composable
internal fun Modifier.rowSurface(
    armed: Boolean = false,
    selected: Boolean = false,
): Modifier {
    val game = LocalTtoColors.current
    val shape = MaterialTheme.shapes.small
    return clip(shape)
        .background(
            if (selected) {
                game.selectedFill
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        )
        .border(
            width = HairlineWidth,
            color = when {
                armed -> MaterialTheme.colorScheme.error
                selected -> game.selectedOutline
                else -> MaterialTheme.colorScheme.outlineVariant
            },
            shape = shape,
        )
}

/*
 * The width limits, the `·` separator and the four alphas moved to `Dimens.kt`, which is where the
 * rest of the spacing and sizing tokens now live. They are in the same package, so no call site
 * changed — see that file for why they are not in `ui/theme/`.
 */
