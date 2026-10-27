package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.components.geo.shell.geoHeaderTitleTrailingStacks
import org.junit.Assert.*
import org.junit.Test

class R8FinalIntegrationRulesTest {
    @Test fun photoWrappersUseTheSharedAnatomyAndKeepSelectionBoundaries() {
        val pane = r8UiSource("components/geo/GeoPaneListRow.kt")
        val action = r8UiSource("components/geo/GeoRows.kt").substringAfter("fun GeoActionRow(")
        listOf(pane, action).forEach { assertTrue(it.contains("leadingIsPhoto: Boolean = false")) }
        assertTrue(pane.contains("leadingIsPhoto = leadingIsPhoto"))
        assertTrue(action.contains("geoRowAnatomy(twoLine = true, photo = leadingIsPhoto)"))
        assertTrue(action.contains("contentDivider(tone, anatomy.dividerXDp.dp)"))
        assertTrue(action.contains("anatomy.textXDp - anatomy.leadingXDp - anatomy.leadingSizeDp"))
        assertEquals(GeoRowAnatomy(72, 16, 40, 68, 68), geoRowAnatomy(true, true))
        assertEquals(PaneListRowCorners(false, false), paneListRowCorners(1, 3))
        assertEquals(PaneListRowCorners(true, true), paneListRowCorners(0, 1))
        assertFalse(paneListRowDivider(0, 3, true, false))
        assertFalse(paneListRowDivider(0, 3, false, true))
        assertTrue(paneListRowDivider(0, 3, false, false))
    }

    @Test fun titlePhotoStacksAtLargeTextOrMeasuredOverflowAndNeverEntersTheCompactBar() {
        assertFalse(geoHeaderTitleTrailingStacks(312f, 1f, 180f, 80f))
        assertFalse(geoHeaderTitleTrailingStacks(272f, 1.29f, 180f, 80f))
        assertTrue(geoHeaderTitleTrailingStacks(271f, 1f, 180f, 80f))
        listOf(1.3f, 1.5f, 2f).forEach { assertTrue(geoHeaderTitleTrailingStacks(720f, it, 180f, 80f)) }
        val source = r8UiSource("components/geo/shell/GeoLargeTitleScaffold.kt")
        assertEquals(2, Regex("titleTrailingContent: \\(@Composable \\(\\) -> Unit\\)\\? = null").findAll(source).count())
        assertTrue(source.contains("titleTrailingContent = titleTrailingContent"))
        val compact = source.substringAfter("subcompose(\"compact\")").substringBefore("}.single().measure(loose)")
        assertFalse(compact.contains("titleTrailingContent"))
        assertFalse(compact.contains("GeoLargeTitleBlock"))
        assertTrue(r8UiSource("devices/DeviceDetailScreen.kt").contains("titleTrailingContent = { DevicePhoto(80.dp) }"))
    }

    @Test fun overlayButtonsAndBothFooterActionsKeepTheirDisabledReasons() {
        val source = r8UiSource("components/geo/GeoSheets.kt")
        assertTrue(source.contains("disabledReason = action.disabledReason"))
        assertTrue(source.contains("disabledReason = it.disabledReason"))
        assertTrue(source.contains("disabledReason = primary.disabledReason"))
        assertTrue(r8UiSource("sensors/CameraPreviewScreen.kt").contains("disabledReason = if (!saveDevice && !saveMobile)"))
        assertTrue(r8UiSource("pairing/PairingScreen.kt").contains("disabledReason = \"QR 코드로 다시 등록하세요.\""))
        assertTrue(r8UiSource("pairing/QrScannerScreen.kt").contains("disabledReason = \"등록 코드를 입력하세요.\""))
    }

    @Test fun immersiveCameraKeepsCircularControlsAndShellNavigationAtTheLowerLeft() {
        val camera = r8UiSource("sensors/CameraPreviewScreen.kt")
        assertTrue(camera.contains("GeoImmersiveMediaFrame("))
        assertTrue(camera.contains("captureEnabled = canCapture"))
        val media = r8UiSource("components/geo/GeoMedia.kt")
        assertTrue(media.contains("modifier.size(48.dp).clip(GeoShape.circle)"))
        assertTrue(media.contains("modifier.size(80.dp).clip(GeoShape.circle)"))
        val shell = r8UiSource("components/geo/shell/GeoAppShell.kt")
        assertTrue(shell.contains("if (floatingNavigation) GeoFloatingMediaNavigation(selected, onSelect,"))
        assertTrue(shell.contains("Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(24.dp)"))
    }

    @Test fun pipelineErrorsKeepCopyableTechnicalDetailsBehindDeveloperMode() {
        val source = r8UiSource("pipelines/PipelineScreens.kt")
        assertTrue(source.contains("if (developerModeEnabled) state.technicalError?.let { GeoCopyableValue(it, label = \"오류 상세\") }"))
    }
}
