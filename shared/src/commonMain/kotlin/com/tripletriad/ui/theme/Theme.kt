package com.tripletriad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun TripleTriadTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTtoColors provides TtoColors()) {
        MaterialTheme(
            colorScheme = TtoColorScheme,
            typography = appTypography(),
            shapes = TtoShapes,
            content = content,
        )
    }
}
