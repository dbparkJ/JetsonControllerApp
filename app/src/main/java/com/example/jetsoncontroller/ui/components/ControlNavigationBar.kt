package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

enum class ControlSection {
    OVERVIEW,
    DATA,
    PIPELINES,
    SENSORS,
    SETTINGS
}

private data class NavEntry(
    val section: ControlSection,
    val icon: ImageVector,
    val label: String,
    val description: String
)

/**
 * Four destinations named after the operator's job, not the system's architecture.
 *
 * "작업" became "조사": the field worker runs a survey, and the app's own data model is
 * organised as 프로젝트 → 구간 → 실행, so the tab that leads there should use the same word
 * the rest of the product and the operator already use.
 *
 * The bar keeps a top hairline so it stays distinguishable from a scrolling list on a
 * light background, where the previous borderless surface-on-surface bar disappeared.
 */
@Composable
fun ControlNavigationBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    val c = LocalGeoColors.current
    val items = listOf(
        NavEntry(ControlSection.OVERVIEW, Icons.Default.Home, "홈", "현장 홈 · 현재 상태와 다음 행동"),
        NavEntry(ControlSection.PIPELINES, Icons.Default.PlayCircle, "조사", "조사 준비 · 시작 전 점검 · 실행 이력"),
        NavEntry(ControlSection.DATA, Icons.Default.FolderOpen, "데이터", "장치 자료 · 서버 자료 · 전송"),
        NavEntry(ControlSection.SETTINGS, Icons.Default.Settings, "설정", "장치 등록 · 알림 · 관리자 도구")
    )
    Column {
        GeoRowDivider()
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            items.forEach { entry ->
                NavigationBarItem(
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = c.navSelected,
                        selectedIconColor = c.onNavSelected,
                        selectedTextColor = c.ink,
                        unselectedIconColor = c.muted,
                        unselectedTextColor = c.muted,
                        disabledIconColor = c.onDisabled,
                        disabledTextColor = c.onDisabled
                    ),
                    selected = selected == entry.section,
                    onClick = { onSelect(entry.section) },
                    enabled = entry.section in enabledSections,
                    icon = { Icon(entry.icon, contentDescription = entry.description) },
                    label = { Text(entry.label) }
                )
            }
        }
    }
}
