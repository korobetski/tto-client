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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

/** The back chevron of any [ScreenScaffold]. Only one is on screen at a time. */
const val SCREEN_BACK_TEST_TAG: String = "screen-back"

/** The [CharacterActions]. Their presence is what says a screen is behind the dashboard. */
const val CHARACTER_BAR_TEST_TAG: String = "character-bar"

/**
 * A full-width action button.
 *
 * The menu's stack and every screen's primary action are the same control, so it is one composable.
 * `softWrap = false` with ellipsis rather than a second line: `STR_BACKGROUND_VOLUME` in German is
 * `Hintergrundlautstärke`, and a stack whose rows change height by language is a stack that jumps
 * when the language does. A truncated label is a visible problem; a layout that shifts between
 * languages is a subtle one.
 *
 * ### Why this is two Material components and no longer one recoloured one
 *
 * It used to be a [Button] whose container was swapped between `primary` and `surfaceVariant` by
 * hand, which is the shape of a design system that has not been given the roles it needs: Material
 * already ships the quiet half of a pair as [FilledTonalButton], and hand-colouring one to imitate
 * it means the disabled state, the elevation and the ripple all have to be imitated too — and each
 * is a place the two can drift apart. With the scheme now complete, `filled = false` can simply
 * *be* the tonal button.
 *
 * @param filled false for the quiet half of a pair. Two filled buttons side by side ask the player
 *   to choose between two equally loud things; Material's own dialog pairs one filled action with
 *   one that is not.
 */
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

/** Material's height for a screen's primary action. */
private val ButtonHeight = 56.dp

/**
 * Clickable, at the size a finger needs, announcing what it is.
 *
 * ### The gap this closes
 *
 * There were twenty-two bare `Modifier.clickable` call sites in `ui/` and **not one `Role`,
 * `selected`, `toggleable` or `stateDescription` in the whole package**. A screen reader met every
 * list row in this app as an unlabelled node with no role and no state: it could not say that a row
 * was a button, and on the screens where one row is the current choice — a deck, a server, a locale
 * — it could not say which. That is not a rough edge, it is the app being unusable without sight.
 *
 * Fixing it at twenty-two call sites would have fixed it until the twenty-third. Fixing it here
 * fixes it for every row that is built this way, including the ones not written yet, which is the
 * same argument `WideButton` makes about its click sound and `rowSurface` about its border.
 *
 * ### Two things, because they are always wanted together
 *
 * - **The semantics.** [role] and, where the caller has one, [selected].
 * - **The focus ring.** The desktop build is driven by keyboard as well as by mouse, and had no
 *   visible focus anywhere — tabbing through a screen moved an invisible cursor. Drawn in
 *   `secondary`, which is the app's state colour, and only while focused.
 *
 * ### It does *not* grow the touch target, and that is a correction
 *
 * It called [androidx.compose.material3.minimumInteractiveComponentSize] for a while, on the
 * understanding — taken from `TouchTargetTest`'s own note — that "nothing enforces the 48 dp
 * minimum on `Modifier.clickable`". Measuring it says otherwise: the `×` in the profile list draws
 * 34 dp tall and reports **48 dp of touch bounds** with that call removed, and so does the help
 * screen's rule row at 38 dp. `clickable` already extends its own pointer bounds to the minimum.
 *
 * The old note was not wrong, it was about something else: `assertHeightIsAtLeast` reads *layout*
 * bounds, so what it measured was how tall a row **looks**, which is a real concern and a different
 * one. The call was doing nothing, and a line of code that documents itself as doing something it
 * does not is worse than no line at all.
 *
 * @param sound null for a control with a voice of its own — a board cell plays a card being placed,
 *   and a UI click underneath it is one sound too many.
 * @param selected null for a row that is merely tappable rather than one of a set of choices. The
 *   distinction is what a screen reader announces, so guessing it would be worse than omitting it.
 */
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
            if (focused) Modifier.border(FocusRingWidth, ring, ringShape) else Modifier,
        )
        .semantics { selected?.let { this.selected = it } }
}

/** Thick enough to see against a row's own one-dp border without being mistaken for it. */
private val FocusRingWidth = 2.dp

/**
 * A grouped panel: rounded, outlined, and holding a column of related things.
 *
 * ### Why this is one composable and was five
 *
 * "Rounded surface, `surfaceVariant` fill, one-dp outline" was written out by hand in five places —
 * `rowSurface` here, the settings group, the menu's resume card, the match's rule chip and its
 * outcome panel — and the five had already begun to disagree about their radius. Material ships the
 * pattern as [OutlinedCard]; what was being hand-rolled was a card with the parts that make it a
 * card left off.
 *
 * The fill is `surfaceContainerHigh` and not `surfaceVariant`, which is the correction the whole
 * palette rewrite turns on: `surfaceVariant` is Material's *de-emphasis* role, several tones
 * lighter than the surface, and dimmed text on it measures 3.77:1 — under AA. See `ContrastTest`.
 *
 * @param onClick present only for a card that is itself a destination. A card that does nothing
 *   should not report a role to a screen reader, so the clickable path is taken only when there is
 *   something to click.
 * @param selected null for a card that is not one of a set of choices — which is most of them. The
 *   three states are meaningfully different to a screen reader: *chosen*, *not chosen*, and *not
 *   the kind of thing that gets chosen*. A boolean could only say the first two.
 */
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

/**
 * The label above a group of settings, rules or statistics.
 *
 * Promoted out of `OptionsScreen`, which was the only screen that had one — the other dense screens
 * wrote a bare `Text` and each picked its own colour and case. What it gains on the way is
 * `semantics { heading() }`, which is how a screen reader offers "jump to next heading"; without it
 * the only way through a long settings column is to read every row of it.
 *
 * `tertiary` is the affirmative accent and deliberately not `primary`: a heading is not an action,
 * and a column of amber labels would compete with the button at the bottom of it.
 */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(bottom = SpaceXs).semantics { heading() },
    )
}

/**
 * One filter, format, rule or locale — the app's only chip.
 *
 * ### Why there were three of these
 *
 * Because nothing said there should be one. `CardListBody` drew a `Text` on a `rowSurface` and
 * called it a chip; `PvpScreen` and `PvpTableScreen` used Material's [FilterChip] with its
 * defaults; `OptionsScreen` used [FilterChip] with eight lines of hand-written colours. The three
 * looked like three different controls, and two of them were **visibly wrong** — with
 * `secondaryContainer` unfilled in the old scheme, a selected chip on the two PvP screens came out
 * in Material's baseline purple.
 *
 * Now there is one, its selected state is `secondaryContainer` — the state family, which is what
 * Material means that role for — and the chips on five screens are the same object.
 */
@Composable
internal fun TtoFilterChip(
    label: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val audio = LocalAudio.current

    FilterChip(
        selected = selected,
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leading,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.secondary,
        ),
        modifier = Modifier.testTag(tag),
    )
}

/**
 * An app bar, an optional snackbar, and a column for the screen's own content.
 *
 * Extracted because the profile, creation and opponent screens would otherwise repeat the same
 * header, the same max width and the same padding, and the first one to be edited alone is the one
 * that starts looking different.
 *
 * ### Why a real [TopAppBar] and not a two-`Text` row
 *
 * A row has no touch target worth the name — a 20 sp glyph with 4 dp of padding is 28 dp of
 * tappable width against Material's 48 — no elevation when content scrolls beneath it, and no slot
 * for anything but the title. The bar needs all three the moment a screen wants an action in the
 * corner, and the purse is that action on eleven screens.
 *
 * The bar is held to the content's own width and centred rather than spanning the window, so its
 * title sits over the column it belongs to. A full-bleed bar is the phone-shaped answer; on a
 * desktop window three times as wide it would strand the title a hand's width from its own list.
 *
 * @param actions the corner of the bar — the purse, on every screen behind the dashboard.
 * @param snackbar where transient confirmations land, or null on a screen that has none. Hoisted
 *   rather than created here because the message comes from the screen: only it knows what was
 *   bought, saved or refused.
 * @param bottomBar a screen's one committing action — Buy, Confirm — pinned below the content.
 *   Worth a slot of its own rather than being the last row of [content] because a snackbar is
 *   placed *above* the bottom bar and *over* the content: with the button in the column, the
 *   confirmation for a purchase lands on top of the button that makes the next one.
 */
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

/** `tab-<key>` — one per tab of a screen that has them, and no screen has two of a name. */
fun screenTabTestTag(key: String): String = "tab-$key"

/**
 * The two halves of a screen that holds two.
 *
 * Material's own [PrimaryTabRow], with the labels and the tags supplied by the caller. Two screens
 * use it — the collection and the store — and both hold **things that are the same kind of thing**:
 * cards you own and decks you build them into, what is for sale and what you bought. That is the
 * test for whether a tab row is the right control rather than a second destination; the record and
 * the rules are not tabs of each other and are not on one.
 *
 * @param tabs the label and the tag of each, in order.
 * @param selected which is showing, as an index into [tabs].
 */
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

/**
 * A value on a range — the volumes, and the wager on a PvP table.
 *
 * ### The third of these, and the same story as the chips
 *
 * There were two sliders and they looked like two different controls. `OptionsScreen` hand-wrote
 * three colours; `PvpTableScreen` took Material's defaults — and in Material 3 an inactive track
 * defaults to `secondaryContainer`, which in this palette is a **strong blue**. So a wager slider
 * sitting at zero drew a full-width bar of solid blue with the thumb at the far left, which reads
 * as *full* to anybody who does not stop to work out which end is which. A control whose empty
 * state looks like its full state is worse than no control.
 *
 * `tertiary` for the filled part, which is the reading this app gives that role everywhere: a
 * filled progress bar, an affordable price, a complete deck. `surfaceContainerHighest` behind it,
 * which is Material's own track role and is what the splash's progress bar was moved to for the
 * same reason.
 */
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

/**
 * A screen's transient confirmations, and the tag they are found by.
 *
 * A [SnackbarHostState] with a name attached, because the two always travel together: the host is
 * useless to a test that cannot find what it showed, and every screen that has one has exactly one.
 *
 * [show] replaces rather than queues. Material's default is a queue — a second `showSnackbar`
 * suspends until the first has run its four seconds — which is right for messages that must each be
 * read and wrong for these: buying three packs in a row should say what the *third* one was, not
 * make the player wait twelve seconds to be told.
 */
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

/**
 * The character's level, purse and active boosts, for the corner of the app bar.
 *
 * `display/UserBar.as`, which every dashboard screen put in its top-right corner — which is where
 * this is again, after a spell as a full-width band under the title. Two things of the original's
 * are still not here: the **jump menu** it opened on tap, which listed every dashboard screen
 * except the current one and existed because the original had no back button, and the character's
 * **name**, which is the app bar's own title on the one screen where it is the subject.
 *
 * The boon markers were the letters `MGP ×n` / `XP ×n`, because the original's two icons said only
 * that there was at least one and a boon is a **count of boosted matches** — see
 * [com.tripletriad.model.Boons.spending]. They are the plaque again now that the plaque is a glyph
 * rather than a 24x32 bitmap ([TtoIcons.MgpBoon]), and the count stays beside it: the objection was
 * never to the picture, it was to a picture *instead of* the number.
 */
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

/**
 * One boon: its plaque and how many matches it still covers, or nothing at all when it is spent.
 *
 * Nothing at all, rather than `×0`: a boon a player does not have is not a fact about their bar.
 *
 * @param label the boon's own name, which is the icon's description — a screen reader gets "MGP,
 *   2" and not "picture, 2".
 */
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

/** [ScreenScaffold] with the purse in its corner — every screen behind the dashboard. */
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

/**
 * A centred "there is nothing here" line.
 *
 * Its own composable because the tag is the assertion: `assertDoesNotExist` on a list is not the
 * same claim as "the screen says it is empty", and the four screens that can be empty should all
 * make the second one.
 */
@Composable
internal fun EmptyNote(text: String, tag: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(tag).padding(vertical = 24.dp),
    )
}

/**
 * A centred "we are still asking" spinner — the waiting counterpart of [EmptyNote].
 *
 * ### Why a list needs both
 *
 * Because an empty list and an unread list look identical, and rendering them the same way tells
 * the player something false. "Nobody is here" is an *answer*: somebody who reads it leaves. Half a
 * second later four tables arrive. `ProfileScreen` has always drawn this distinction for the local
 * profile list; this is the shape for the ones that come over the network.
 *
 * ### The same vertical padding as [EmptyNote], deliberately
 *
 * So the two states occupy the same space and the screen does not jump when one replaces the other.
 * A layout that shifts as an answer arrives is how a player ends up tapping the wrong row.
 *
 * Circular rather than the linear bar `AccountScreen` uses, and the difference is where they sit: a
 * bar belongs at the top of a form that is submitting, and a spinner belongs in the middle of a
 * space that is about to hold something.
 */
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

/** Big enough to read as a spinner, small enough not to read as the content. */
private val SpinnerSize = 28.dp
private val SpinnerStroke = 3.dp

/**
 * Where a fetched list has got to. **Three states, because a list has three.**
 *
 * The one it used to have was "the list I am holding", which conflates two answers and a question:
 * an empty list means *nothing is there*, *nothing has arrived yet*, or *nothing could be fetched*,
 * and only the first of those is something to tell a player. Showing the second as the first sends
 * them away half a second early; showing the third as either leaves them waiting on a server that
 * is not coming.
 */
enum class ListState {
    /** Asked, nothing back. [LoadingNote]. */
    LOADING,

    /** Answered. The list is now the truth, empty or not. */
    READY,

    /** Asked and refused, or not reached at all. [FailedNote]. */
    FAILED,
}

/**
 * A text button that sits inside a list row, at the size a finger actually needs.
 *
 * ### Why this exists rather than a bare `TextButton`
 *
 * Because a bare one is **40dp tall**, measured — `ButtonDefaults.MinHeight` — and Material's
 * minimum-touch-target enforcement does not lift it here. 40dp is below the 48dp both Material 3
 * and Android's own accessibility guidance ask for, and these are the buttons that matter most for
 * it: Join, Accept, Decline, Claim all sit in a crowded row beside *other* tap targets, which is
 * exactly where an undersized one gets mis-hit.
 *
 * It was a guess until it was measured. `TouchTargetTest` is what turned it into a number, and is
 * what will say so again if a future Material release changes the default underneath this.
 *
 * The **visual** size is untouched: `heightIn` sets a minimum on the layout, so the label and its
 * padding look exactly as they did and the tappable area grows to meet the hand.
 *
 * @param color the label's colour, or null for the button's own. Passed only by the destructive
 *   ones — `error` is the theme's word for "this went badly", and a row that ends an account
 *   looking like a row that opens a list is a row nobody reads twice.
 */
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

/**
 * One credential field, shared by the sign-in form and the delete-account confirmation.
 *
 * Extracted from `AccountScreen`, whose own note explains why it may not be copied: the two differ
 * in whether the characters are shown, and "a second copy of the eight-line `colors` block is how
 * the two forms would start looking different". A third copy would have been worse — the password
 * box that ends an account should not be able to drift from the one that opens a session.
 *
 * @param contentType what the platform's password manager should make of this field. Declaring it
 *   is what lets the OS offer to save the password and fill it back in — which is the *right* place
 *   for a password to be remembered, and the reason this app stores none of its own. Without the
 *   hint, autofill falls back to guessing from labels and mostly does not offer at all. Inert on
 *   desktop, where Compose has no autofill backend yet.
 */
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

/**
 * A centred "that did not work" line with something to press — the third of [ListState].
 *
 * ### Why it has a button and [EmptyNote] does not
 *
 * Because it is the only one of the three states the player can do something about. An empty lobby
 * is not a problem to solve, and a loading one solves itself; a failed read is a dead end unless
 * something offers a way out of it. A screen that reports a failure and offers nothing is a screen
 * the player has to leave and re-enter to retry — which they will do, so the only question is
 * whether the app looks like it knows.
 */
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

/**
 * The shared list-row surface: rounded, filled, outlined, and tappable.
 *
 * Six screens draw this same box. A modifier rather than a wrapper composable so a row keeps
 * control of its own layout — some are a `Row`, some a `Column`, and one is a grid cell. Where a
 * whole group is being drawn rather than one row, [TtoCard] is the same thing as a container.
 *
 * `@Composable` because it reads the theme, which is what a `Modifier` extension may do as long as
 * it is called from a composition — every call site here is inside one.
 *
 * ### The fill moved, and it is the point of the palette rewrite
 *
 * It was `surfaceVariant`, which in Material 3 is a **de-emphasis** role several tones lighter than
 * the surface — not the thing a row sits on. That is `surfaceContainerHigh`, and the difference is
 * measurable rather than nominal: `FAINT` text on `surfaceVariant` is 3.77:1, under WCAG AA, and on
 * `surfaceContainerHigh` it is 5.04. Nearly every row in this app carries a dimmed secondary line.
 * See `ContrastTest`, which measures both.
 *
 * The tone it lands on — `#2D2926` — is within a step of the `#2E2A26` the rows were already drawn
 * in, so the screens keep their appearance and gain a role that explains it.
 *
 * @param armed draws the destructive-confirmation outline instead of the ordinary one.
 * @param selected draws the state outline and tints the fill, for a row that is the current choice
 *   rather than merely tappable. **Marking it visually is half the job** — pass the same flag to
 *   [ttoClickable] so a screen reader is told too.
 */
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
