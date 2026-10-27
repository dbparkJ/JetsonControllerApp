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

    @Test fun captureDestinationsStayInAChoiceSheetAndStatusCanScroll() {
        val camera = source("CameraPreviewScreen")
        assertTrue(camera.contains("kind = GeoSheetKind.CHOICE"))
        assertTrue(camera.contains("GeoCheckboxRow(\"장치에 저장\""))
        assertTrue(camera.contains("GeoCheckboxRow(\"휴대폰 갤러리에 저장\""))
        assertTrue(camera.contains("GeoSheetFooter(GeoOverlayAction(\"캡처\""))
        assertTrue(camera.contains("Modifier.weight(1f).verticalScroll(rememberScrollState())"))
        assertTrue(camera.contains("GeoMediaIconButton(GeoIcons.refresh, \"다시 시도\", onRefresh"))
    }

    @Test fun gnssKeepsTheSharedMapQualityAndFreshnessContract() {
        val gnss = source("GnssMapScreen")
        assertTrue(gnss.contains("GeoMapScreen(if (route.isEmpty()) GeoMapMode.LOCATION else GeoMapMode.ROUTE"))
        assertTrue(gnss.contains("trail = route, quality = quality, device = mapDevice(gnss)"))
        assertTrue(gnss.contains("deviceStale = !deviceOnline || !telemetryFresh"))
        assertTrue(gnss.contains("lastPositionAtMillis = gnss.lastSampleAtEpochMillis"))
    }
}
