package com.tripletriad.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberUrlOpener(): (String) -> Unit
