package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerStatus
import com.tripletriad.net.latency
import com.tripletriad.platform.rememberUrlOpener
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch

const val SERVERS_SCREEN_TEST_TAG: String = "servers-screen"
const val SERVERS_REFRESH_TEST_TAG: String = "servers-refresh"
const val UPDATE_NOTICE_TEST_TAG: String = "update-notice"
const val UPDATE_DOWNLOAD_TEST_TAG: String = "update-download"

fun serverRowTestTag(entry: ServerEntry): String = "server-${entry.id}"

@Composable
internal fun ServersScreen(
    connectivity: Connectivity,
    onSelect: suspend (ServerEntry) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Every server, on arrival. This is the one screen where the whole set is on display at once,
    // and a list of "unknown" that the player has to press refresh to populate would be a list that
    // makes them do the app's job.
    LaunchedEffect(connectivity) { connectivity.refreshAll() }

    val strings = LocalStrings.current

    ScreenScaffold(
        title = strings[StringKeys.SERVERS],
        onBack = onBack,
        // In the bar rather than under the list, where it used to be pushed off screen by a long
        // update notice — the same slot the store's Buy button took, and for the same reason.
        bottomBar = {
            WideButton(
                label = strings[
                    if (connectivity.isProbing) {
                        StringKeys.SERVERS_CHECKING
                    } else {
                        StringKeys.SERVERS_CHECK
                    },
                ],
                tag = SERVERS_REFRESH_TEST_TAG,
                enabled = !connectivity.isProbing,
                filled = false,
                onClick = { scope.launch { connectivity.refreshAll() } },
            )
        },
    ) {
        Column(
            modifier = Modifier.testTag(SERVERS_SCREEN_TEST_TAG).fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings[StringKeys.SERVERS_BLURB],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            connectivity.update?.let { UpdateNotice(it) }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(connectivity.servers, key = { it.id }) { entry ->
                    ServerRow(
                        entry = entry,
                        status = connectivity.statusOf(entry),
                        isSelected = entry.id == connectivity.selected.id,
                        onClick = { scope.launch { onSelect(entry) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    entry: ServerEntry,
    status: ServerStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(serverRowTestTag(entry))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(
                role = Role.RadioButton,
                // The server already in use is still announced as chosen — it is simply not
                // something to choose again.
                enabled = !isSelected,
                selected = isSelected,
                onClick = onClick,
            )
            .padding(horizontal = SpaceMd, vertical = SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        StatusDot(status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.baseUrl,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = status.describe(strings),
            color = status.tint(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun ServerIndicator(connectivity: Connectivity, onClick: () -> Unit) {
    val status = connectivity.status
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .testTag(TITLE_SERVER_TEST_TAG)
            .ttoClickable(onClick = onClick)
            .padding(horizontal = SpaceSm, vertical = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        StatusDot(status)
        Text(
            text = "${connectivity.selected.label}$DOT_SEPARATOR${status.describe(strings)}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun UpdateNotice(advice: UpdateAdvice) {
    val strings = LocalStrings.current
    val open = rememberUrlOpener()
    val download = advice.download
    val game = LocalTtoColors.current

    Column(
        modifier = Modifier
            .testTag(UPDATE_NOTICE_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = strings[
                if (advice.isRequired) StringKeys.UPDATE_REQUIRED else StringKeys.UPDATE_AVAILABLE,
            ],
            color = if (advice.isRequired) MaterialTheme.colorScheme.error else game.transient,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = strings.format(
                if (advice.isRequired) {
                    StringKeys.UPDATE_REQUIRED_BODY
                } else {
                    StringKeys.UPDATE_AVAILABLE_BODY
                },
                advice.target.toString(),
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
        )
        advice.notes?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (download != null) {
            WideButton(
                label = strings.format(StringKeys.UPDATE_GET, advice.target.toString()),
                tag = UPDATE_DOWNLOAD_TEST_TAG,
                onClick = { open(download) },
            )
        }
    }
}

@Composable
private fun StatusDot(status: ServerStatus) {
    Box(
        modifier = Modifier
            .size(DotSize)
            .background(status.tint(), CircleShape),
    )
}

private fun ServerStatus.describe(strings: Strings): String = when (this) {
    ServerStatus.Unknown -> strings[StringKeys.SERVER_UNKNOWN]
    ServerStatus.Checking -> strings[StringKeys.SERVER_CHECKING]
    // The latency is appended rather than interpolated into the phrase: it is absent for a probe
    // that did not time one, and a `{0}` with nothing in it would leave a gap mid-sentence.
    is ServerStatus.Online ->
        strings[StringKeys.SERVER_ONLINE] + latency?.let { "$DOT_SEPARATOR${it}ms" }.orEmpty()
    is ServerStatus.Degraded -> strings[StringKeys.SERVER_DEGRADED]
    is ServerStatus.Outdated -> strings[StringKeys.SERVER_OUTDATED]
    is ServerStatus.Unreachable -> strings[StringKeys.SERVER_UNREACHABLE]
    is ServerStatus.Unusable -> strings[StringKeys.SERVER_UNUSABLE]
}

@Composable
private fun ServerStatus.tint(): Color = when (this) {
    is ServerStatus.Online -> LocalTtoColors.current.positive
    is ServerStatus.Degraded, ServerStatus.Checking -> LocalTtoColors.current.transient
    is ServerStatus.Outdated -> LocalTtoColors.current.transient
    is ServerStatus.Unreachable, is ServerStatus.Unusable -> MaterialTheme.colorScheme.error
    ServerStatus.Unknown -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED)
}

private val DotSize = 8.dp
