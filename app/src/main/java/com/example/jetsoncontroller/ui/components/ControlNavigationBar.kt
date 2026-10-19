package com.example.jetsoncontroller.ui.components

import com.example.jetsoncontroller.ui.components.geo.shell.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoSpace

/**
 * Destinations of the bottom bar and the side rail. The four primary tabs are
 * TASKS, DEVICES, TRANSFERS and SETTINGS. The other values are legacy names that
 * screens written for the older shell still pass; [tab] maps each one onto a tab.
 */
enum class ControlSection {
    /** 장치: the device root. */
    DEVICES,
    /** 작업: collection tasks and the start destination. */
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

/** Suppresses a legacy screen's bar when the app shell owns navigation chrome. */
val LocalControlNavigationRailVisible = compositionLocalOf { false }

@Composable
fun ControlNavigationBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    if (!LocalControlNavigationRailVisible.current) {
        GeoBottomBar(selected, onSelect, enabledSections = enabledSections)
    }
}

@Composable
fun ControlNavigationRail(selected: ControlSection, onSelect: (ControlSection) -> Unit) {
    GeoRail(selected, onSelect)
}

/** Centers a 720dp column with a gutter derived from the current pane. */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = 720.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = LocalGeoWindow.current.gutterDp.dp),
            content = content
        )
    }
}

/** Applies the measured minimum widths to the available content pane. */
@Composable
fun AdaptiveColumns(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val parent = LocalGeoWindow.current
        val scale = LocalDensity.current.fontScale
        val twoPane = parent.widthClass == GeoWidthClass.EXPANDED && !parent.compactHeight && scale < 1.5f &&
            maxWidth.value >= (320f + 400f) * kotlin.math.max(1f, scale) + 1f + 2f * paneGutter(maxWidth.value)
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
