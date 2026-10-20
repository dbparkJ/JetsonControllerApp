package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.ui.navigation.Routes
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Operator homes preserved when the retired connection and BLE screens are removed. */
class InventoryReachabilityTest {
    private val sourceRoot = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    private fun source(path: String) = File(sourceRoot, path).readText()

    @Test fun `device root reaches detail with merged sensors Wi-Fi data and phone-local removal`() {
        val graph = source("navigation/DeviceGraph.kt")
        val root = source("devices/DevicesHomeScreen.kt")
        val detail = source("devices/DeviceDetailScreen.kt")
        assertTrue(root.contains("DeviceIdentity(current.deviceName, connection, onClick = onOpenDevice"))
        assertTrue(graph.contains("onOpenDevice = { navController.navigateSingle(Routes.DEVICE_DETAIL) }"))
        listOf("상태", "센서", "연결", "데이터", "관리", "이 휴대폰").forEach {
            assertTrue("Missing device-detail section: $it", detail.contains("GeoSection(\"$it\""))
        }
        val homes = mapOf(
            "4/5/79" to "DeviceSensorRows(", "6/117/118" to "ConnectMethodSheet(",
            "7/96" to "GeoConfirmDialog(", "120" to "onForget?.invoke()"
        )
        homes.forEach { (ids, marker) -> assertTrue("Unreachable inventory $ids", detail.contains(marker)) }
        assertTrue(detail.indexOf("GeoSection(\"이 휴대폰\"") < detail.indexOf("GeoButton(\"등록 해제\""))
        assertTrue(graph.contains("latest.deviceViewModel.forget(it)"))
        listOf("CAMERA_PREVIEW", "GNSS_MAP", "NETWORK_SETTINGS", "STORAGE", "LOCAL_TRASH").forEach {
            assertTrue("Missing device link: $it", graph.contains("navController.navigateSingle(Routes.$it)"))
        }
        val redirect = graph.substringAfter("composable(Routes.SENSORS)")
        assertTrue(redirect.contains("navigateSingle(Routes.DEVICE_DETAIL)"))
    }

    @Test fun `connection options camera states and Wi-Fi overlays have operator doors`() {
        val connect = source("devices/ConnectMethodSheet.kt")
        listOf("같은 네트워크에서 다시 찾기", "장치에 직접 연결", "블루투스로 기본 연결", "QR 코드로 다시 등록").forEach {
            assertTrue(connect.contains("DeviceConnectionChoice(\"$it\""))
        }
        val wifi = source("network/NetworkSettingsScreen.kt")
        listOf("현재 네트워크", "주변 네트워크", "숨겨진 네트워크 추가", "권한 허용").forEach { assertTrue(wifi.contains(it)) }
        assertTrue(wifi.contains("GeoInfoDialog(\"연결 상태\""))
        assertTrue(wifi.contains("onApply(target)"))
        val menuLabels = Regex("GeoMenuItem\\(\"([^\"]+)\"")
        assertEquals(listOf("연결 상태"), menuLabels.findAll(wifi).map { it.groupValues[1] }.toList())
        assertEquals(listOf("연결 끊기"), menuLabels.findAll(source("devices/DeviceDetailScreen.kt")).map { it.groupValues[1] }.toList())
        val camera = source("sensors/CameraPreviewScreen.kt")
        listOf("카메라 영상을 기다리고 있습니다", "영상을 받지 못했습니다", "실시간", "지연", "마지막 수신",
            "저장 위치", "캡처 저장 위치", "장치에 저장", "휴대폰 갤러리에 저장").forEach { assertTrue(camera.contains(it)) }
    }

    @Test fun `daily device screens use Geo controls and never read pairing credentials`() {
        val files = listOf("devices/DevicesHomeScreen.kt", "devices/DeviceDetailScreen.kt", "devices/ConnectMethodSheet.kt",
            "network/NetworkSettingsScreen.kt", "sensors/SensorScreen.kt", "sensors/CameraPreviewScreen.kt")
        val rawControls = Regex("import androidx\\.compose\\.material3\\.(Button|TopAppBar|Card|AlertDialog|ListItem)\\b")
        val secrets = Regex("pairingSecret|sessionKey|BarcodeScanner|QrDecoder")
        files.forEach { path ->
            val text = source(path)
            assertFalse(path, rawControls.containsMatchIn(text))
            assertFalse(path, secrets.containsMatchIn(text))
            assertFalse(path, text.contains("if (developerModeEnabled)"))
        }
    }

    @Test fun `retired connection and BLE features stay reachable through their replacement homes`() {
        val graph = source("navigation/DeviceGraph.kt")
        val root = source("devices/DevicesHomeScreen.kt")
        val detail = source("devices/DeviceDetailScreen.kt")
        val work = source("navigation/WorkGraph.kt")
        assertTrue(root.contains("discoveredDevices") && root.contains("onAddDevice"))
        assertTrue(graph.contains("repository.startLanDiscovery()"))
        assertTrue(graph.contains("navController.navigateSingle(Routes.ONBOARDING)"))
        assertTrue(graph.contains("navController.navigateSingle(Routes.SERVER_STORAGE)"))
        assertTrue(graph.contains("deviceViewModel.reconnect(device)"))
        assertTrue(graph.contains("onRequestBluetoothPermission = onRequestBluetoothPermission"))
        assertTrue(graph.contains("fun openConnectionMethods("))
        assertTrue(graph.contains("initialConnectionMethods = entry.savedStateHandle.get<Boolean>(\"connectionMethodsRequested\") == true"))
        assertTrue(detail.contains("mutableStateOf(initialConnectionMethods)"))
        assertTrue(work.contains("NextActionCommand.CONNECTION_HELP -> openConnectionMethods(navController)"))
        assertEquals(5, Regex("openConnectionMethods\\(navController\\)").findAll(work).count())
        // Phone-local removal keeps the immutable detail target, including an offline device.
        assertTrue(graph.contains("sameDeviceTarget(it.deviceId, targetId)"))
        assertTrue(graph.contains("latest.deviceViewModel.forget(it)"))
        val pairing = source("navigation/FocusedRoutes.kt")
        listOf("ONBOARDING", "QR_SCANNER", "PAIRING", "WIFI_DIRECT").forEach {
            assertTrue("Missing registration/direct-connection home: $it", pairing.contains("composable(Routes.$it)") ||
                pairing.contains("Routes.$it\n"))
        }
    }
}
