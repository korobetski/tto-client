package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import androidx.compose.foundation.Image as ComposeImage

const val MENU_PLAY_TEST_TAG: String = "menu-play"
const val MENU_PROFILES_TEST_TAG: String = "menu-profiles"
const val MENU_SERVERS_TEST_TAG: String = "menu-servers"
const val MENU_OPTIONS_TEST_TAG: String = "menu-options"
const val MENU_QUIT_TEST_TAG: String = "menu-quit"

const val MENU_PROFILE_TEST_TAG: String = "menu-profile"

const val MENU_SERVER_TEST_TAG: String = "menu-server"

const val MENU_RESUME_TEST_TAG: String = "menu-resume"

const val MENU_RESUME_STATE_TEST_TAG: String = "menu-resume-state"

const val MENU_RESUME_GO_TEST_TAG: String = "menu-resume-go"

const val MENU_RESUME_SWITCH_TEST_TAG: String = "menu-resume-switch"

internal enum class SessionState(val labelKey: String) {
    RESTORED(StringKeys.SESSION_RESTORED),

    CONNECTING(StringKeys.SESSION_CONNECTING),

    LAPSED(StringKeys.SESSION_LAPSED),
}

@Composable
@Suppress("LongParameterList")
internal fun MainMenuScreen(
    active: GameSave?,
    remembered: RememberedAccount?,
    connectivity: Connectivity?,
    onPlay: () -> Unit,
    onProfiles: () -> Unit,
    onServers: () -> Unit,
    onOptions: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    val logo by produceState<ImageBitmap?>(initialValue = null) { value = loadLogo() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.height(LogoHeight).widthIn(max = LogoMaxWidth),
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

        Text(
            text = active?.let {
                listOf(
                    it.username,
                    "${strings[StringKeys.LEVEL]} ${it.level}",
                ).joinToString(DOT_SEPARATOR)
            } ?: strings[StringKeys.NO_PROFILE],
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (active == null) 0.5f else 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(MENU_PROFILE_TEST_TAG).padding(top = SpaceMd),
        )

        // Probed once on arrival, and again whenever the menu is returned to. The releases page is
        // asked in the same breath and answers only once a launch — see `checkForRelease`.
        connectivity?.let {
            LaunchedEffect(it) {
                it.checkForRelease()
                it.refreshSelected()
            }
            ServerIndicator(it, onClick = onServers)
        }

        Column(
            modifier = Modifier.padding(top = SpaceLg).widthIn(max = MenuMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            remembered?.let { ResumeCard(it, active) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(MENU_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeCard(
                        label = strings[StringKeys.PLAY],
                        icon = TtoIcons.Play,
                        tag = MENU_PLAY_TEST_TAG,
                        accented = true,
                        onClick = onPlay,
                    )
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.PROFILE],
                        icon = TtoIcons.Person,
                        tag = MENU_PROFILES_TEST_TAG,
                        onClick = onProfiles,
                    )
                }
                // Only with a server. A build with none has nothing to list, and a card leading to
                // an empty screen is worse than no card.
                if (connectivity != null) {
                    item {
                        HomeCard(
                            label = strings[StringKeys.SERVERS],
                            icon = TtoIcons.Home,
                            tag = MENU_SERVERS_TEST_TAG,
                            onClick = onServers,
                        )
                    }
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.SETTINGS],
                        icon = TtoIcons.Options,
                        tag = MENU_OPTIONS_TEST_TAG,
                        onClick = onOptions,
                    )
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.QUIT],
                        icon = TtoIcons.Logout,
                        tag = MENU_QUIT_TEST_TAG,
                        onClick = onQuit,
                    )
                }
            }
        }
    }
}

@Immutable
internal class RememberedAccount(
    val username: String,
    val state: SessionState,
    val onGo: () -> Unit,
    val onSwitch: () -> Unit,
)

@Composable
private fun ResumeCard(account: RememberedAccount, active: GameSave?) {
    val strings = LocalStrings.current
    val lapsed = account.state == SessionState.LAPSED

    // `armed` is the destructive outline, and a lapsed session is what it is for here: the card
    // still offers Continue, but the thing behind it has expired and the border is the only part
    // of the card that says so before the player presses it.
    TtoCard(
        modifier = Modifier.testTag(MENU_RESUME_TEST_TAG).fillMaxWidth(),
        armed = lapsed,
    ) {
        Column(
            modifier = Modifier.padding(SpaceMd),
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                // The character's own avatar when there is one, and its plate when there is not:
                // the lapsed case has a name and no profile behind it yet.
                if (active != null) {
                    AvatarBadge(profile = active, size = ResumeAvatarSize)
                } else {
                    Box(modifier = Modifier.size(ResumeAvatarSize))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = strings[account.state.labelKey],
                        color = if (lapsed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(MENU_RESUME_STATE_TEST_TAG),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                Box(modifier = Modifier.weight(RESUME_GO_WEIGHT)) {
                    WideButton(
                        label = strings[
                            if (lapsed) StringKeys.SIGN_IN_AGAIN else StringKeys.CONTINUE,
                        ],
                        tag = MENU_RESUME_GO_TEST_TAG,
                        // Nothing to press while the round trip is in flight, and disabled rather
                        // than absent: a control that appears once the network answers is a
                        // control the player's thumb arrives at after it has moved.
                        enabled = account.state != SessionState.CONNECTING,
                        onClick = account.onGo,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    WideButton(
                        label = strings[StringKeys.SWITCH_ACCOUNT],
                        tag = MENU_RESUME_SWITCH_TEST_TAG,
                        filled = false,
                        onClick = account.onSwitch,
                    )
                }
            }
        }
    }
}

private const val MENU_COLUMNS = 2

private const val RESUME_GO_WEIGHT = 2f

private val LogoMaxWidth = 512.dp
private val LogoHeight = 128.dp

private val MenuMaxWidth = 380.dp
private val ResumeAvatarSize = 40.dp
