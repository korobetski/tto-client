package com.tripletriad.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.platform.rememberReducedMotion
import androidx.compose.foundation.Image as ComposeImage

const val TITLE_SCREEN_TEST_TAG: String = "title-screen"

/** The whole-screen tap target. Absent wherever a tap has nowhere to go. */
const val TITLE_CONTINUE_TEST_TAG: String = "title-continue"

const val TITLE_PROMPT_TEST_TAG: String = "title-prompt"

const val TITLE_NAME_TEST_TAG: String = "title-name"

const val TITLE_VERSION_TEST_TAG: String = "title-version"

const val TITLE_SWITCH_TEST_TAG: String = "title-switch"

const val TITLE_OPTIONS_TEST_TAG: String = "title-options"

const val TITLE_QUIT_TEST_TAG: String = "title-quit"

const val TITLE_SERVER_TEST_TAG: String = "title-server"

fun titleChoiceTestTag(key: String): String = "title-choice-$key"

/**
 * What the device's stored session turned out to be worth.
 *
 * Three answers rather than a boolean, because "we are asking" and "it expired" lead the player to
 * different places and only one of them is an error.
 */
internal enum class SessionState(val labelKey: String) {
    RESTORED(StringKeys.SESSION_RESTORED),

    CONNECTING(StringKeys.SESSION_CONNECTING),

    LAPSED(StringKeys.SESSION_LAPSED),
}

/**
 * What the title screen offers, once the session has been asked about.
 *
 * Decided by `App`, which is the only thing that knows whether there is a server, a stored token
 * and a character behind it — the screen renders the answer rather than deriving it, so the two
 * states a tap cannot answer are data here rather than a second copy of that reasoning.
 *
 * @property promptKey the line under the name. Always present: an empty title screen gives a
 *   player nothing to act on, and "no character yet" is as much an answer as "tap to continue".
 * @property onContinue what one tap anywhere does. Null while there is nowhere for it to go.
 * @property isWaiting whether the stored session is still being asked about, in which case a tap
 *   is **held** rather than dropped — see [TitleScreen].
 * @property choices the buttons, for the two states a tap cannot answer: a device nobody has
 *   signed in on, and one with no character on it at all.
 */
@Immutable
internal class TitleEntry(
    val promptKey: String,
    val alarming: Boolean = false,
    val onContinue: (() -> Unit)? = null,
    val isWaiting: Boolean = false,
    val choices: List<TitleChoice> = emptyList(),
)

@Immutable
internal class TitleChoice(
    val labelKey: String,
    val key: String,
    val filled: Boolean = true,
    val onPick: () -> Unit,
)

/**
 * The first screen, and the whole of it is the button.
 *
 * It replaced a grid of five cards. Play, Character, Servers, Options and Quit were five ways of
 * asking the same question — *whose game is this and shall we start it* — and four of them only
 * existed because the fifth could not answer on its own. It can: the stored session says who is
 * playing, so the screen says the name and waits to be touched.
 *
 * The four that left did not vanish. Options and Quit are in the corner, small, because a player
 * looks for them once a month; Servers is the status dot, which is the only part of it anyone
 * reads; and Character is what the avatar in the middle *is*.
 */
@Composable
@Suppress("LongParameterList")
internal fun TitleScreen(
    profile: GameSave?,
    entry: TitleEntry,
    back: ImageBitmap?,
    connectivity: Connectivity?,
    onServers: () -> Unit,
    onSwitchAccount: (() -> Unit)?,
    onOptions: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    val audio = LocalAudio.current
    val logo by produceState<ImageBitmap?>(initialValue = null) { value = loadLogo() }
    val interactions = remember { MutableInteractionSource() }

    // A tap taken while the stored session is still being asked about, kept rather than dropped.
    // `AccountSession.restore` is a round trip, and a player who taps a screen that is not ready
    // yet taps again — at which point the second tap lands on whatever the first one opened.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(entry.isWaiting) {
        if (!entry.isWaiting && armed) {
            armed = false
            entry.onContinue?.invoke()
        }
    }

    // Probed on arrival, and again whenever the screen is returned to. The releases page is asked
    // in the same breath and answers only once a launch — see `checkForRelease`.
    connectivity?.let {
        LaunchedEffect(it) {
            it.checkForRelease()
            it.refreshSelected()
        }
    }

    val go = entry.onContinue
    val press: (() -> Unit)? = when {
        go != null -> {
            {
                audio.play(Sound.UI_CLICK)
                go()
            }
        }

        entry.isWaiting -> {
            { armed = true }
        }

        else -> null
    }

    Box(modifier = Modifier.testTag(TITLE_SCREEN_TEST_TAG).fillMaxSize()) {
        TitleBackdrop(back)

        // The tap target, and it is a *sibling* of the content rather than its parent. Wrapping
        // the screen in it was the obvious shape and it was the wrong one: `clickable` merges its
        // descendants, so the name, the prompt, the version and the footer all collapsed into one
        // enormous button that a screen reader read out in a single breath. Underneath instead —
        // nothing in the column below is a pointer target, so a tap anywhere but on the footer's
        // own controls falls through to here, and everything above keeps its own semantics.
        press?.let { go ->
            Box(
                modifier = Modifier
                    .testTag(TITLE_CONTINUE_TEST_TAG)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactions,
                        // No ripple. The target is the whole window, and a ripple across it
                        // reads as the screen breaking rather than as a control being pressed.
                        indication = null,
                        role = Role.Button,
                        onClickLabel = strings[entry.promptKey],
                        onClick = go,
                    ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.padding(top = SpaceXl).height(LogoHeight)
                    .widthIn(max = LogoMaxWidth),
                contentAlignment = Alignment.Center,
            ) {
                logo?.let {
                    ComposeImage(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                profile?.let {
                    Identity(it)
                    Spacer(modifier = Modifier.height(SpaceXl))
                }

                Prompt(text = strings[entry.promptKey], alarming = entry.alarming)

                if (entry.choices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SpaceXl))
                    Column(
                        modifier = Modifier.widthIn(max = ChoiceMaxWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SpaceSm),
                    ) {
                        for (choice in entry.choices) {
                            WideButton(
                                label = strings[choice.labelKey],
                                tag = titleChoiceTestTag(choice.key),
                                filled = choice.filled,
                                onClick = choice.onPick,
                            )
                        }
                    }
                }
            }

            TitleFooter(
                connectivity = connectivity,
                onServers = onServers,
                onSwitchAccount = onSwitchAccount,
                onOptions = onOptions,
                onQuit = onQuit,
            )
        }
    }
}

@Composable
private fun Identity(profile: GameSave) {
    val strings = LocalStrings.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        AvatarBadge(profile = profile, size = TitleAvatarSize)
        Text(
            text = profile.username,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TITLE_NAME_TEST_TAG),
        )
        Text(
            text = listOf(
                "${strings[StringKeys.LEVEL]} ${profile.level}",
                "${profile.cards.size} ${strings[StringKeys.CARDS]}",
            ).joinToString(DOT_SEPARATOR),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The line that says what a tap will do, breathing so it reads as an invitation.
 *
 * Between [PROMPT_LOW] and [PROMPT_HIGH] rather than to zero: a prompt that disappears entirely is
 * a prompt a player can be looking straight at and not see.
 */
@Composable
private fun Prompt(text: String, alarming: Boolean) {
    val reduced = rememberReducedMotion()
    val breath = rememberInfiniteTransition(label = "title-prompt")
    val alpha = if (reduced) {
        PROMPT_HIGH
    } else {
        breath.animateFloat(
            initialValue = PROMPT_LOW,
            targetValue = PROMPT_HIGH,
            animationSpec = infiniteRepeatable(
                animation = tween(BREATH_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "title-prompt-alpha",
        ).value
    }

    Text(
        text = text,
        color = if (alarming) {
            MaterialTheme.colorScheme.error.copy(alpha = alpha)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        },
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(TITLE_PROMPT_TEST_TAG),
    )
}

/**
 * Three card backs, adrift.
 *
 * They are the game's own `back.png` rather than an ornament invented for this screen, which is
 * why they can be this faint and still read as something: a player who has seen one match knows
 * that shape. The parallax is three different periods against each other — one shared period would
 * read as a single sheet sliding, which is the opposite of depth.
 *
 * Silent under `rememberReducedMotion`, and drawn at rest rather than removed: the composition is
 * the same either way, and a backdrop that vanishes when a system setting changes is a backdrop
 * that will be reported as a bug.
 */
@Composable
private fun TitleBackdrop(back: ImageBitmap?) {
    if (back == null) return
    val reduced = rememberReducedMotion()
    val drift = rememberInfiniteTransition(label = "title-backs")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        for ((index, spec) in TITLE_BACKS.withIndex()) {
            val phase = if (reduced) {
                REST_PHASE
            } else {
                drift.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(spec.periodMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "title-back-$index",
                ).value
            }

            ComposeImage(
                bitmap = back,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(spec.size)
                    .offset(
                        x = width * spec.x - spec.size / 2,
                        y = height * spec.y - spec.size / 2,
                    )
                    .graphicsLayer {
                        val swing = phase - REST_PHASE
                        translationY = swing * 2f * spec.travel.toPx()
                        translationX = -swing * spec.travel.toPx()
                        rotationZ = spec.angle + swing * SWING_DEGREES
                        alpha = spec.alpha
                    }
                    // Faded from the top down, so a back that drifts up behind the wordmark does
                    // not compete with it. `DstIn` over a gradient is one layer; masking by moving
                    // the cards away from the type would have made the drift the type's shape.
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black),
                                endY = size.height * FADE_STOP,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
            )
        }
    }
}

@Composable
private fun TitleFooter(
    connectivity: Connectivity?,
    onServers: () -> Unit,
    onSwitchAccount: (() -> Unit)?,
    onOptions: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            connectivity?.let { ServerIndicator(it, onClick = onServers) }

            Spacer(modifier = Modifier.weight(1f))

            onSwitchAccount?.let { switch ->
                Text(
                    text = strings[StringKeys.SWITCH_ACCOUNT],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .testTag(TITLE_SWITCH_TEST_TAG)
                        .ttoClickable(onClick = switch)
                        .padding(horizontal = SpaceSm, vertical = SpaceMd),
                )
            }

            FooterIcon(
                icon = TtoIcons.Options,
                description = strings[StringKeys.SETTINGS],
                tag = TITLE_OPTIONS_TEST_TAG,
                onClick = onOptions,
            )

            // Kept where the old menu's Quit card was — the title screen is the root, and it is
            // where a desktop player closes the game. `onQuit` is the host's: `::exitApplication`
            // on desktop, the activity's own finish on Android.
            FooterIcon(
                icon = TtoIcons.Logout,
                description = strings[StringKeys.QUIT],
                tag = TITLE_QUIT_TEST_TAG,
                onClick = onQuit,
            )
        }

        VersionLine(TITLE_VERSION_TEST_TAG)
    }
}

@Composable
private fun FooterIcon(
    icon: ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    // The click belongs to the control, not to the caller — `WideButton` makes the same argument,
    // and these two are the only controls on this screen that are not the screen itself.
    val audio = LocalAudio.current

    IconButton(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        modifier = Modifier.testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            modifier = Modifier.size(IconMd),
        )
    }
}

// A data class rather than a plain one, which is what detekt's `ignoreDataClasses` is for: this is
// a value holder and its seven fields are the description, not a parameter list somebody let grow.
private data class BackSpec(
    val x: Float,
    val y: Float,
    val size: Dp,
    val angle: Float,
    val alpha: Float,
    val periodMillis: Int,
    val travel: Dp,
)

/*
 * Placed by fraction of the window rather than in dp, so the three keep their arrangement from a
 * phone to a desktop window. The periods are deliberately coprime-ish — 17, 21 and 25 seconds — so
 * the three take about half an hour to line up again.
 */
private val TITLE_BACKS = listOf(
    BackSpec(0.10f, 0.30f, 96.dp, -12f, 0.26f, 17_000, 18.dp),
    BackSpec(0.90f, 0.56f, 124.dp, 9f, 0.20f, 21_000, 22.dp),
    BackSpec(0.24f, 0.82f, 76.dp, 5f, 0.14f, 25_000, 14.dp),
)

private const val REST_PHASE = 0.5f

private const val SWING_DEGREES = 5f

private const val FADE_STOP = 0.55f

private const val PROMPT_LOW = 0.32f

private const val PROMPT_HIGH = 0.85f

private const val BREATH_MILLIS = 2600

private val LogoMaxWidth = 512.dp
private val LogoHeight = 96.dp

private val TitleAvatarSize = 88.dp

private val ChoiceMaxWidth = 320.dp
