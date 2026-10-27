package com.example.jetsoncontroller.ui.network

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R8NetworkLayoutTest {
    private val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    private fun source(path: String) = File(ui, path).readText()

    @Test fun wifiUsesAnOwnedFooterAndKeepsTheImmutableConfirmationTarget() {
        val wifi = source("network/NetworkSettingsScreen.kt")
        assertTrue(wifi.contains("primaryAction = if (editing) GeoButtonAction(\"적용\", requestApply"))
        assertTrue(wifi.contains("enabled = unavailable == null"))
        assertTrue(wifi.contains("confirmation = state.applyTarget()"))
        assertTrue(wifi.contains("val valid = state.matches(target)"))
        assertTrue(wifi.contains("if (valid) onApply(target)"))
        assertTrue(wifi.contains("GeoInfoDialog(\"연결 상태\""))
        assertTrue(wifi.contains("GeoGroupedSection(\"현재 네트워크\", first = true, trailingColumn = GeoRowTrailing.Status)"))
    }

    @Test fun wifiNamesKeepUnicodeWhileLiteralPasswordsAndImeActionsRemainExplicit() {
        val wifi = source("network/NetworkSettingsScreen.kt")
        assertTrue(wifi.contains("inputKind = GeoInputKind.TEXT, imeAction = ImeAction.Next"))
        assertTrue(wifi.contains("KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next)"))
        assertTrue(wifi.contains("focus.moveFocus(FocusDirection.Down)"))
        assertEquals(2, Regex("inputKind = GeoInputKind.IDENTIFIER, imeAction = ImeAction.Done").findAll(wifi).count())
        assertTrue(wifi.contains("keyboardActions = finishInput"))
    }

    @Test fun directConnectionFooterUsesOnlyTheSelectedPeerAndAnExplicitDangerConfirmation() {
        val direct = source("wifi/WifiDirectScreen.kt")
        assertTrue(direct.contains("directSelectedPeer(state, permissionGranted, selectedAddress)"))
        assertTrue(direct.contains("pendingAddress = selectedPeer.deviceAddress"))
        assertTrue(direct.contains("primaryAction = primary, secondaryAction = secondary"))
        assertTrue(direct.contains("confirmLabel = \"직접 연결\", kind = GeoConfirmKind.DANGER"))
        assertTrue(direct.contains("if (canConnectDirectPeer(state, permissionGranted, pendingPeer.deviceAddress)) onConnectClick(pendingPeer)"))
    }
}
