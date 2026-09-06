package com.tripletriad.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys

/**
 * The one search box in the app: a name typed, and a × once there is something to clear.
 *
 * Extracted from the collection's `CardSearchRow` when the auction room grew a search of its own.
 * Two hand-built `OutlinedTextField`s would have been two sets of colours, two placeholder
 * conventions and two answers to what the × does — and the collection's answers are the ones worth
 * keeping, since they were argued once already: the × appears only when it would do something, and
 * the field is a single line with a `Search` action rather than a `Done` one.
 *
 * The state lives in the caller. This holds none: the collection's query belongs to
 * [CardFilters], the room's to the board, and a field that remembered its own text would be a
 * third place the answer is stored.
 *
 * @param clearTag null for a field nothing tests the × of. The button is still drawn.
 */
@Composable
@Suppress("LongParameterList")
internal fun TtoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    clearTag: String? = null,
    placeholder: String? = null,
) {
    val strings = LocalStrings.current

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_QUERY)) },
        placeholder = { Text(placeholder ?: strings[StringKeys.SEARCH_CARDS]) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = TtoIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(IconSm),
            )
        },
        trailingIcon = {
            // Only once there is something to clear. A permanently lit × on an empty field is
            // a control that does nothing, next to the one place on this screen a tap is
            // expensive — the keyboard is open and the list is behind it.
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = clearTag?.let { Modifier.testTag(it) } ?: Modifier,
                ) {
                    Icon(
                        imageVector = TtoIcons.Back,
                        contentDescription = strings[StringKeys.CANCEL],
                        modifier = Modifier.size(IconSm),
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.testTag(tag),
    )
}

/** Longer than the longest card name in any of the four bundles, and short of a paste bomb. */
private const val MAX_QUERY = 40
