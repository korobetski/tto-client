package com.tripletriad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii — Material 3's five-step scale, all five filled.
 *
 * ### Why this changed
 *
 * Feathers rounds nothing itself: every rounded edge in the AS3 original is a nine-slice texture
 * out of the UI atlas, which this port does not import. So there was nothing to transcribe, and the
 * values that stood here — 4 / 6 / 8 dp, with `large` and `extraLarge` left at Material's defaults
 * — were the port's own, chosen to match "what the fourteen screens already drew". Three barely
 * distinguishable radii is not a scale; it is one radius with rounding error, and it is most of
 * what made the app look like a 2013 tablet game.
 *
 * These are Material's own steps. What they buy is a **hierarchy that can be read**: a chip is
 * visibly tighter than a row, a row than a card, a card than a sheet. And filling `large` and
 * `extraLarge` matters for the same reason filling every colour role does — `ModalBottomSheet`,
 * `AlertDialog` and the large button variants all reach for them, and a scale with two holes in it
 * is two components that will not match the app the day somebody uses one.
 *
 * | Step | Radius | What takes it |
 * |---|---|---|
 * | `extraSmall` | 4 dp | badges, the smallest markers |
 * | `small` | 8 dp | chips, list rows |
 * | `medium` | 12 dp | cards, grouped panels |
 * | `large` | 16 dp | buttons, the outcome panel, anything that is its own object |
 * | `extraLarge` | 28 dp | sheets and dialogs |
 */
internal val TtoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
