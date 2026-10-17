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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
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

/**
 * Destinations of the bottom bar and the side rail. The four primary tabs are
 * DEVICES, TASKS, TRANSFERS and SETTINGS. The other values are legacy names that
 * screens written for the older shell still pass; [tab] maps each one onto a tab.
 */
enum class ControlSection {
    /** 장치: the device root. It is also the start destination. */
    DEVICES,
    /** 작업: collection tasks. */
    TASKS,
    /** 전송: uploads and server receipts. */
    TRANSFERS,
    /** 설정: app preferences. */
    SETTINGS,
    /** Legacy: the old home. Maps to [DEVICES]. */
    OVERVIEW,
    /** Legacy: the old collection tab. Maps to [TASKS]. */
    COLLECTION,
    /** Legacy: the old files tab. Maps to [TRANSFERS]. */
    DATA,
    /** Legacy: task destinations. Maps to [TASKS]. */
    PIPELINES,
    /** Legacy: sensor detail, reached from the device. Maps to [DEVICES]. */
    SENSORS;

    /** The primary tab that owns this destination. */
    fun tab(): ControlSection = when (this) {
        DEVICES, OVERVIEW, SENSORS -> DEVICES
        TASKS, COLLECTION, PIPELINES -> TASKS
        TRANSFERS, DATA -> TRANSFERS
        SETTINGS -> SETTINGS
    }
}

private data class NavEntry(
    val section: ControlSection,
    val icon: ImageVector,
    val label: String,
    val description: String
)

private val controlEntries = listOf(
    NavEntry(ControlSection.DEVICES, Icons.Default.Devices, "장치", "연결된 장치와 상태"),
    NavEntry(ControlSection.TASKS, Icons.Default.PlayCircle, "작업", "수집 작업 시작과 진행"),
    NavEntry(ControlSection.TRANSFERS, Icons.Default.Upload, "전송", "서버 전송과 수신 확인"),
    NavEntry(ControlSection.SETTINGS, Icons.Default.Settings, "설정", "앱과 서버 설정")
)

/** Set by the app shell when the same destinations are available in a side rail. */
val LocalControlNavigationRailVisible = compositionLocalOf { false }

/** Four tabs in the bottom bar, as in the approved device console shell. */
@Composable
fun ControlNavigationBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    if (LocalControlNavigationRailVisible.current) return
    val selectedTab = selected.tab()
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        controlEntries.forEach { entry ->
            NavigationBarItem(
                selected = selectedTab == entry.section,
                onClick = { onSelect(entry.section) },
                enabled = entry.section in enabledSections,
                icon = { Icon(entry.icon, contentDescription = entry.description) },
                label = { Text(entry.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** Optional rail for windows at least 840dp wide. It shows the same four tabs. */
@Composable
fun ControlNavigationRail(selected: ControlSection, onSelect: (ControlSection) -> Unit) {
    val c = LocalGeoColors.current
    val selectedTab = selected.tab()
    NavigationRail(
        // The tablet root scale turns the Material 80dp rail into the approved 88dp rail.
        modifier = Modifier.width(80.dp),
        containerColor = c.surface,
        header = {
            GeoLogo(Modifier.width(64.dp), backgroundColor = c.surface)
        }
    ) {
        controlEntries.forEach { entry ->
            NavigationRailItem(
                selected = selectedTab == entry.section,
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
