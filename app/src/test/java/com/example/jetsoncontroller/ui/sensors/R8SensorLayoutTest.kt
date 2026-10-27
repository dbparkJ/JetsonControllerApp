package com.example.jetsoncontroller.ui.sensors

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R8SensorLayoutTest {
    private val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    private fun source(name: String) = File(ui, "sensors/$name.kt").readText()

    @Test fun captureUsesTheSharedFrameAndRechecksFreshnessAtThePinnedTarget() {
        val camera = source("CameraPreviewScreen")
        assertTrue(camera.contains("GeoImmersiveMediaFrame("))
        assertTrue(camera.contains("captureAction = captureNow"))
        assertTrue(camera.contains("captureEnabled = canCapture"))
        assertTrue(camera.contains("SystemClock.elapsedRealtime()"))
        assertTrue(camera.contains("state.targetStillCurrent() && cameraCaptureEnabled(currentStage"))
        assertTrue(camera.contains("onCapture(saveDevice, saveMobile)"))
        assertFalse(camera.contains("GeoFloatingMediaNavigation"))
        assertFalse(camera.contains("CameraLandscapeContent"))
        assertFalse(camera.contains("GeoCaptureButton("))
        assertTrue(camera.contains("ContentScale.Fit"))
    }

    @Test fun captureDestinationsStayInAChoiceSheetAndMediaUsesDedicatedSlots() {
        val camera = source("CameraPreviewScreen")
        assertTrue(camera.contains("kind = GeoSheetKind.CHOICE"))
        assertTrue(camera.contains("GeoCheckboxRow(\"장치에 저장\""))
        assertTrue(camera.contains("GeoCheckboxRow(\"휴대폰 갤러리에 저장\""))
        assertTrue(camera.contains("GeoSheetFooter(GeoOverlayAction(\"캡처\""))
        assertTrue(camera.contains("info = GeoMediaInfoSpec("))
        assertTrue(camera.contains("caption = if (compactInfo) null"))
        assertTrue(camera.contains("if (compactInfo) {"))
        assertTrue(camera.contains("cameraPreviewErrorExplanation(state.error)"))
        assertTrue(camera.contains("onRetry = if (stage == CameraStageState.ERROR) onRefresh else null"))
        assertTrue(camera.contains("retryEnabled = !state.isRefreshing"))
        assertTrue(camera.contains("loadingText = if (stage == CameraStageState.WAITING)"))
        assertFalse(camera.contains("GeoMediaIconButton(GeoIcons.refresh"))
        assertFalse(camera.contains("GeoSkeleton("))
    }

    @Test fun offlineSensorsKeepTheirStatusColumnWithoutEnablingNavigation() {
        val sensors = source("SensorScreen")
        assertTrue(sensors.contains("GeoRowList(GeoRowTrailing.Status)"))
        assertTrue(sensors.contains("canOpen = current && control"))
        assertTrue(sensors.contains("canOpen = current, unavailableReason"))
        assertTrue(sensors.contains("if (!canOpen) disabled()"))
        assertTrue(sensors.contains("navigable = canOpen"))
        assertTrue(sensors.contains("meta = if (canOpen) meta else unavailableReason"))
        assertFalse(sensors.contains("listOf(camera.badge"))
        assertFalse(sensors.contains("listOf(location.word"))
    }

    @Test fun gnssKeepsTheSharedMapQualityAndFreshnessContract() {
        val gnss = source("GnssMapScreen")
        assertTrue(gnss.contains("GeoMapScreen(if (route.isEmpty()) GeoMapMode.LOCATION else GeoMapMode.ROUTE"))
        assertTrue(gnss.contains("trail = route, quality = quality, device = mapDevice(gnss)"))
        assertTrue(gnss.contains("deviceStale = !deviceOnline || !telemetryFresh"))
        assertTrue(gnss.contains("lastPositionAtMillis = gnss.lastSampleAtEpochMillis"))
    }
}
