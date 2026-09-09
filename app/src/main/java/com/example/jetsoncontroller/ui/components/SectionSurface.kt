package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

/** A short group within its existing lazy item; no extra click or state owner. */
@Composable
fun SectionSurface(color: Color, content: @Composable () -> Unit) {
    Surface(
        color = color, contentColor = LocalCobaltColors.current.ink,
        shape = MaterialTheme.shapes.medium, tonalElevation = 0.dp, shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}
