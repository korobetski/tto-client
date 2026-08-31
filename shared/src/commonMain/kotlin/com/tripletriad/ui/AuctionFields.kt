package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * A sum of MGP, typed.
 *
 * ### Why a field and not a slider
 *
 * A slider can express "somewhere around a thousand". A price cannot be somewhere around anything:
 * the seller has a number in mind, the bidder has a limit in mind, and a control that lands on
 * 1,013 because a thumb is 8 dp wide is a control that spends other people's money on rounding.
 * The PvP stake was a slider on the same objection; `PvpTableScreen.StakeField` is this control
 * with a ceiling drawn under it.
 *
 * ### Why the state is a string
 *
 * A half-typed number is not a number. Holding an `Int` means deciding what an empty field is, and
 * every answer to that ("0", "the minimum", "the last valid value") fights the player's backspace.
 * The string is what they typed; [digits] is what the button reads.
 */
@Composable
internal fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    supporting: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        // Filtered here rather than validated later: a price with a minus sign or a comma in it is
        // not a price a player meant to type, and refusing the keystroke is quieter than refusing
        // the form. The length cap is what stops a paste from turning into an overflow.
        onValueChange = { typed ->
            onValueChange(typed.filter { it.isDigit() }.take(MAX_DIGITS))
        },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        modifier = Modifier.testTag(tag).fillMaxWidth(),
    )
}

/** What the field is worth, or zero while it is empty. */
internal val String.digits: Int
    get() = toIntOrNull() ?: 0

/**
 * One line of the terms: what it is on the left, what it is on the right.
 *
 * The whole desk is a list of these, which is the point — a lot is a small table of numbers, and
 * every one of them is a number the player is entitled to before they commit.
 */
@Composable
internal fun TermRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Nine digits: past the ceiling of anything in the game, and short of an `Int` overflowing. */
private const val MAX_DIGITS = 9
