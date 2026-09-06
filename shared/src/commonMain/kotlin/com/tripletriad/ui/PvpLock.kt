package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

/** Where the multiplayer screen says which level opens it. See [PvpLocked]. */
const val PVP_LOCK_TEST_TAG: String = "pvp-lock"

/**
 * The level refereed play opens at, stated on the multiplayer screen itself.
 *
 * ### Why it is stated rather than enforced
 *
 * A level is a number in a save the *server* also holds, and only its copy decides whether a table
 * may be opened or sat at — see `LocalUnlocks`. Hiding the lobby here would buy nothing against
 * anybody who matters and would cost the player the one thing the screen can honestly give them:
 * knowing why the server is about to say no, and what to do about it. So the room stays readable
 * and carries its own condition, the way the auction house does (`AUCTION_LOCK_TEST_TAG`).
 *
 * Drawn only below the line: a player who is past it has nothing to read here, and a banner that
 * congratulates them on being allowed in is a banner they scroll past forever.
 */
@Composable
internal fun PvpLocked(profile: GameSave) {
    val unlocks = LocalUnlocks.current
    if (unlocks.allowsMultiplayer(profile)) return
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(PVP_LOCK_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Icon(
            imageVector = TtoIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            modifier = Modifier.size(IconMd),
        )
        Text(
            text = strings.format(StringKeys.LOCKED_LEVEL, "${unlocks.multiplayer}"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
