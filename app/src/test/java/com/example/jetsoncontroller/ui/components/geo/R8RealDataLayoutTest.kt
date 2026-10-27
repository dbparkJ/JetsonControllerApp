package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

/** Source contracts cover real-data branches that a plain JVM cannot compose. */
class R8RealDataLayoutTest {
    @Test fun transferEventNoticeUsesTheSharedPropertyInsets() {
        val transfers = r8UiSource("transfers/TransfersHomeScreen.kt")
            .substringAfter("R7Section(\"서버\"")
        assertTrue(transfers.contains("state.deviceEventNotice?.let { notice ->"))
        assertTrue(transfers.contains("GeoFactRow(\"작업 알림\", notice.text)"))
        val facts = r8UiSource("components/geo/GeoRows.kt")
            .substringAfter("fun GeoFactRow(").substringBefore("private fun GeoNumericFactRow(")
        assertTrue(facts.contains("GeoPropertyRow(label, value, modifier, numeric = numeric)"))
        val property = r8UiSource("components/geo/GeoProperty.kt")
        val inset = ".padding(horizontal = if (LocalGeoGroupInset.current) 16.dp else 0.dp)"
        val list = property.substringBefore("fun GeoPropertyRow(")
        val row = property.substringAfter("fun GeoPropertyRow(")
        assertTrue(list.contains(inset))
        assertTrue(row.contains(inset))
        assertTrue(list.indexOf(inset) < list.indexOf("val sharedLabelWidth"))
        assertTrue(list.contains("LocalGeoGroupInset provides false"))
        assertEquals(2, Regex("padding\\(vertical = 12.dp\\)").findAll(row).count())
    }

    @Test fun alreadyInsetPropertyContainersDoNotAddAnotherSixteenDp() {
        val section = r8UiSource("storage/R7ScreenLayout.kt")
            .substringAfter("if (trailingColumn == null) {").substringBefore("} else content()")
        assertTrue(section.contains("LocalGeoGroupInset provides false"))
        assertTrue(section.contains("Column(Modifier.padding(16.dp), content = content)"))
        val device = r8UiSource("devices/DeviceDetailScreen.kt")
            .substringAfter("val statusSection:").substringBefore("val management:")
        assertTrue(device.contains("DeviceSurface {"))
        assertFalse(device.contains("contentPadding ="))
        val diagnostics = r8UiSource("diagnostics/ConnectionDiagnosticsScreen.kt")
            .substringAfter("GeoGroupedSection(\"기록 상태\"").substringBefore("GeoPropertyList(")
        assertTrue(diagnostics.contains("DeviceSurface {"))
        assertFalse(diagnostics.contains("contentPadding ="))
        val run = r8UiSource("field/ActiveRunScreen.kt")
            .substringAfter("GeoPropertyList(listOf(\"현재 위치\"").substringBefore("positionLabel?.let")
        assertFalse(run.contains("Modifier.padding"))
    }

    @Test fun fullScreenFlagCannotCenterAnOrdinaryStateColumn() {
        val state = r8UiSource("components/geo/GeoStateBlock.kt")
        val alignment = state.substringAfter("contentAlignment =").substringBefore("CompositionLocalProvider")
        assertTrue(alignment.contains("if (LocalGeoMediaStage.current)"))
        assertTrue(alignment.contains("Alignment.TopCenter else androidx.compose.ui.Alignment.TopStart"))
        assertFalse(alignment.contains("fullScreen"))
        assertTrue(state.contains("Modifier.widthIn(max = 560.dp).fillMaxWidth()"))
        assertTrue(state.contains(".geoGroupInset()"))
        assertTrue(state.contains("padding(top = if (fullScreen) 56.dp else 0.dp)"))
        assertTrue(r8UiSource("components/geo/GeoMedia.kt").contains("LocalGeoMediaStage provides true"))
        listOf("upload/UploadProgressScreen.kt", "storage/DirectServerScreen.kt").forEach { path ->
            // Both affected pages still use the shared state block with their existing gutters.
            val screen = r8UiSource(path)
            assertTrue(screen.contains("GeoStateBlock("))
            assertTrue(screen.contains("fullScreen = true"))
        }
    }

    @Test fun deviceWarningAndMetricsHaveAVisibleGapWithoutAnEmptyOnlineItem() {
        val device = r8UiSource("devices/DeviceDetailScreen.kt")
        val list = device.substringAfter("LazyColumn(").substringBefore("val sensors:")
        assertTrue(list.contains("verticalArrangement = Arrangement.spacedBy(16.dp)"))
        assertTrue(list.contains("if (!online || ongoingStrip != null) item {"))
        assertTrue(list.indexOf("GeoStatusStrip(") < list.indexOf("GeoMetricStrip("))
        assertTrue(list.contains("GeoMetricStrip(devicePhotoFigures("))
    }
}
