package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import kotlinx.coroutines.launch

const val AVATAR_GRID_TEST_TAG: String = "avatar-grid"

fun avatarChoiceTestTag(avatarId: String): String = "avatar-choice-$avatarId"

@Composable
internal fun AvatarScreen(
    profile: GameSave,
    onChoose: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    CharacterScaffold(profile = profile, title = strings[StringKeys.AVATAR], onBack = onBack) {
        LazyVerticalGrid(
            // Adaptive rather than a fixed count: 27 tiles are four columns on a phone and eight in
            // a desktop window, and the alternative is a grid that either crowds or wastes half of
            // whichever it was not designed for.
            columns = GridCells.Adaptive(TileSize + TileGap),
            modifier = Modifier
                .testTag(AVATAR_GRID_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            items(AVATAR_NAMES, key = { it }) { avatarId ->
                AvatarTile(
                    avatarId = avatarId,
                    isSelected = avatarId == profile.avatarId,
                    onClick = { scope.launch { onChoose(profile.copy(avatarId = avatarId)) } },
                )
            }
        }
    }
}

@Composable
private fun AvatarTile(avatarId: String, isSelected: Boolean, onClick: () -> Unit) {
    val image = rememberAvatar(LocalUiArt.current, avatarId)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .testTag(avatarChoiceTestTag(avatarId))
            .size(TileSize)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .border(
                width = if (isSelected) SelectedRing else 1.dp,
                color = if (isSelected) colors.primary else colors.primary.copy(alpha = RING),
                shape = CircleShape,
            )
            // The picker is a set of mutually exclusive portraits, and the ring is the only
            // thing that says which one is worn — so the state has to be announced too.
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = avatarId,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Not a monogram, unlike `AvatarBadge`: every tile here would show the same letter,
            // since they all belong to the same character.
            Text(
                text = "?",
                color = colors.onSurfaceVariant.copy(alpha = MUTED),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private val TileSize = 64.dp
private val TileGap = 10.dp
private val SelectedRing = 3.dp
private const val RING = 0.4f
