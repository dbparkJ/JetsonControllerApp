package com.example.jetsoncontroller.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** Ignore soft break opportunities while still requiring the complete visible value. */
internal fun hasVisibleText(text: String): SemanticsMatcher = SemanticsMatcher("보이는 글자: $text") { node ->
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.replace("\u200B", "") == text }
}
