package com.example.jetsoncontroller.ui.pairing

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R8PairingLayoutTest {
    private val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    private fun source(name: String) = File(ui, "pairing/$name.kt").readText()

    @Test fun scannerKeepsSafeMediaControlsAndLiteralManualEntryWithImeSubmission() {
        val qr = source("QrScannerScreen")
        assertTrue(qr.contains("GeoImmersiveMediaFrame(controls ="))
        assertTrue(qr.contains("Box(Modifier.weight(1f).fillMaxWidth(), content = center)"))
        assertTrue(qr.contains("CameraPermissionContent(onRequestCameraPermission)"))
        assertTrue(qr.contains("GeoMediaIconButton(if (torchEnabled) GeoIcons.torchOff else GeoIcons.torch"))
        assertTrue(qr.contains("kind = GeoSheetKind.FORM"))
        assertTrue(qr.contains("inputKind = GeoInputKind.IDENTIFIER"))
        assertTrue(qr.contains("KeyboardActions(onDone = { if (manualValue.isNotBlank()) submitManual() })"))
        assertTrue(qr.contains("openerFocusRequester = manualOpener"))
    }

    @Test fun registrationChecksHaveAStatusColumnAndExistingFooterCallbacks() {
        val pairing = source("PairingScreen")
        assertTrue(pairing.contains("kind = GeoSheetKind.FORM"))
        assertTrue(pairing.contains("GeoGroupedSection(\"등록 점검\", trailingColumn = GeoRowTrailing.Status)"))
        assertTrue(pairing.contains("GeoOverlayAction(\"등록\", onStartPairing"))
        assertTrue(pairing.contains("GeoOverlayAction(\"다시 시도\", onRetry"))
        assertTrue(pairing.contains("GeoOverlayAction(\"취소\", onCancel)"))
        assertTrue(pairing.contains("maskedPairingCode(state.pairingInfo)"))
    }
}
