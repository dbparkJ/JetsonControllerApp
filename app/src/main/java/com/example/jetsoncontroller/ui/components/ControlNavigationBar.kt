package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoBreakpoint
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

enum class ControlSection {
    OVERVIEW,
    DATA,
    SETTINGS,
    /** Legacy task destinations now belong to Home. */
    PIPELINES,
    /** Sensor detail is reached from Home or Settings. */
    SENSORS
}

private data class NavEntry(
    val section: ControlSection,
    val icon: ImageVector,
    val label: String,
    val description: String
)

private val controlEntries = listOf(
    NavEntry(ControlSection.OVERVIEW, Icons.Default.Home, "홈", "현장 홈"),
    NavEntry(ControlSection.DATA, Icons.Default.FolderOpen, "파일", "장치와 서버 파일"),
    NavEntry(ControlSection.SETTINGS, Icons.Default.Settings, "설정", "앱과 장치 설정")
)

/** Set by the app shell when the same destinations are available in a side rail. */
val LocalControlNavigationRailVisible = compositionLocalOf { false }

private fun normalized(section: ControlSection): ControlSection = when (section) {
    ControlSection.PIPELINES -> ControlSection.OVERVIEW
    ControlSection.SENSORS -> ControlSection.SETTINGS
    else -> section
}

/** Three short destinations from the approved Figma shell. */
@Composable
fun ControlNavigationBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    if (LocalControlNavigationRailVisible.current) return
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        controlEntries.forEach { entry ->
            val isSelected = normalized(selected) == entry.section
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(entry.section) },
                enabled = entry.section in enabledSections,
                icon = { Icon(entry.icon, contentDescription = entry.description) },
                label = { Text(entry.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** Optional rail for windows at least 840dp wide. */
@Composable
fun ControlNavigationRail(selected: ControlSection, onSelect: (ControlSection) -> Unit) {
    val c = LocalGeoColors.current
    NavigationRail(
        modifier = Modifier.width(88.dp),
        containerColor = c.surface,
        header = {
            GeoLogo(Modifier.width(64.dp), backgroundColor = c.surface)
        }
    ) {
        controlEntries.forEach { entry ->
            NavigationRailItem(
                selected = normalized(selected) == entry.section,
                onClick = { onSelect(entry.section) },
                icon = { Icon(entry.icon, contentDescription = entry.description) },
                label = { Text(entry.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** Centers content on large windows while preserving the 20dp phone gutter. */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = 760.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = GeoSpace.gutter),
            content = content
        )
    }
}

/** Uses the 800dp two-column workspace only when both columns remain readable. */
@Composable
fun AdaptiveColumns(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val twoPane = maxWidth >= GeoBreakpoint.medium && LocalDensity.current.fontScale <= 1.3f
        if (twoPane) {
            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                Column(Modifier.weight(1f), content = first)
                Column(Modifier.weight(1f), content = second)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
                first()
                second()
            }
        }
    }
}
