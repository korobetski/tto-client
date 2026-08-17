package com.tripletriad.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

internal val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

internal val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

internal val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal const val DURATION_SHORT = 200

internal const val DURATION_MEDIUM = 300

internal const val DURATION_LONG = 500
