package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val AUCTION_SCREEN_TEST_TAG: String = "auction-screen"

const val AUCTION_LOCK_TEST_TAG: String = "auction-lock"

/**
 * What the auction house will be, said before it is.
 *
 * A dimmed card in the lobby's grid was the other option and is the worse one: [HomeCard]'s own
 * note explains why a disabled entry has to stay readable enough to mean "not now" instead of
 * reading as a rendering fault — and even when it does, it teaches a player nothing. A page says
 * what is coming, and it is where the level requirement can be stated once rather than repeated
 * on every door the requirement shuts.
 */
@Composable
internal fun AuctionScreen(profile: GameSave, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val open = Unlocks.auction(profile)

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.AUCTION],
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .testTag(AUCTION_SCREEN_TEST_TAG)
                .fillMaxWidth()
                .padding(top = SpaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpaceLg),
        ) {
            Icon(
                imageVector = TtoIcons.Shop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = SUBDUED),
                modifier = Modifier.size(HeroIcon),
            )

            Text(
                text = strings[StringKeys.LOBBY_SOON],
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = strings[StringKeys.AUCTION_BLURB],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Only below the line, and stated rather than implied. A player at level 12 has no
            // reason to be told about a gate they cleared seven levels ago.
            if (!open) {
                Row(
                    modifier = Modifier
                        .testTag(AUCTION_LOCK_TEST_TAG)
                        .fillMaxWidth()
                        .rowSurface()
                        .padding(SpaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpaceMd),
                ) {
                    Icon(
                        imageVector = TtoIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                        modifier = Modifier.size(IconMd),
                    )
                    Text(
                        text = strings.format(
                            StringKeys.LOCKED_LEVEL,
                            Unlocks.AUCTION_LEVEL.toString(),
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val HeroIcon = 56.dp
