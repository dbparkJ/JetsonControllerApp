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
        assertTrue(qr.contains("CameraPermissionControls(onBack, onRequestCameraPermission, openManual"))
        assertTrue(qr.contains("GeoMediaIconButton(if (torchEnabled) GeoIcons.torchOff else GeoIcons.torch"))
        assertTrue(qr.contains("kind = GeoSheetKind.FORM"))
        assertTrue(qr.contains("inputKind = GeoInputKind.IDENTIFIER"))
        assertTrue(qr.contains("KeyboardActions(onDone = { if (manualValue.isNotBlank()) submitManual() })"))
        assertTrue(qr.contains("openerFocusRequester = manualOpener"))
    }

    @Test fun deniedCameraPermissionKeepsBothActionsInTheConstrainedFooter() {
        val permission = source("QrScannerScreen").substringAfter("private fun BoxScope.CameraPermissionControls")
            .substringBefore("private fun CameraScannerContent")
        val footer = permission.substringAfter("footer = {").substringBefore("}) {")
        assertTrue(footer.contains("Modifier.widthIn(max = 560.dp).fillMaxWidth()"))
        assertTrue(footer.contains("GeoButton(\"코드 직접 입력\", onManual, manualModifier.fillMaxWidth()"))
        assertTrue(footer.contains("style = GeoButtonStyle.Tertiary"))
        assertTrue(footer.contains("GeoButton(\"권한 허용\", { requested = true; onRequestPermission() }"))
        assertTrue(footer.contains("GeoButton(\"설정에서 허용\""))
        assertTrue(permission.contains("GeoStateKind.PERMISSION"))
        assertFalse(footer.contains("GeoStateBlock("))
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

    @Test fun registrationErrorWithCredentialsRetriesTheSameTarget() {
        val calls = mutableListOf<String>()
        val recovery = pairingErrorRecoveryAction(true, { calls += "retry" }, { calls += "scan" })
        assertEquals("다시 시도", recovery.label)
        assertTrue(recovery.enabled)
        recovery.onClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test fun registrationErrorWithoutCredentialsOffersAnEnabledScannerAction() {
        val calls = mutableListOf<String>()
        val recovery = pairingErrorRecoveryAction(false, { calls += "retry" }, { calls += "scan" })
        assertEquals("다시 스캔", recovery.label)
        assertTrue(recovery.enabled)
        assertNull(recovery.disabledReason)
        recovery.onClick()
        assertEquals(listOf("scan"), calls)
    }

    @Test fun registrationErrorHasOneCauseAndNoBodyRecoveryButton() {
        val pairing = source("PairingScreen")
        assertEquals(1, Regex("pairingFailureText\\(").findAll(pairing).count())
        val errorBody = pairing.substringAfter("Column(Modifier.semantics")
            .substringAfter("PairingPhase.ERROR -> {").substringBefore("else -> {")
        assertTrue(errorBody.contains("GeoStateBlock("))
        assertFalse(errorBody.contains("GeoButton("))
        assertTrue(pairing.contains("pairingErrorRecoveryAction(state.pairingInfo != null, onRetry, onCancel)"))
    }
}
