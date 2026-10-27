package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.ui.components.geo.shell.geoDeviceDetailColumns
import com.example.jetsoncontroller.ui.components.geo.shell.geoPaneWidths
import com.example.jetsoncontroller.ui.components.geo.shell.geoWindow
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R8DeviceLayoutTest {
    private val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
        File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
    private fun source(path: String) = File(ui, path).readText().replace("\r\n", "\n")

    @Test fun exhibitionTabletKeepsTheReferencePaneWidthsAndReflowsLargerText() {
        val normal = geoWindow(1280f, 800f, 1f)
        val panes = requireNotNull(geoPaneWidths(normal))
        assertEquals(400f, panes.listDp, 0f)
        assertEquals(728f, panes.detailDp, 0f)
        assertEquals(2, geoDeviceDetailColumns(panes.detailDp, 1f, normal.landscapeLayout))
        val enlarged = geoWindow(1280f, 800f, 1.3f)
        val enlargedPanes = requireNotNull(geoPaneWidths(enlarged))
        assertTrue(enlargedPanes.listDp >= 320f * 1.3f)
        assertTrue(enlargedPanes.detailDp >= 400f * 1.3f)
        assertEquals(1, geoDeviceDetailColumns(enlargedPanes.detailDp, 1.3f, enlarged.landscapeLayout))
        listOf(1.5f, 2f).forEach { assertNull(geoPaneWidths(geoWindow(1280f, 800f, it))) }
        listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
            assertNull(geoPaneWidths(geoWindow(412f, 882f, scale)))
            assertNull(geoPaneWidths(geoWindow(800f, 1280f, scale)))
        }
    }

    @Test fun deviceListSelectionAndIndependentReconnectKeepSeparateTargets() {
        val root = source("devices/DevicesHomeScreen.kt")
        assertTrue(root.contains("GeoLargeTitleScaffold(\"장치\""))
        assertTrue(root.contains("GeoPaneListRow(device.deviceName"))
        assertTrue(root.contains("selected = sameDeviceTarget(device.deviceId, highlightedDeviceId)"))
        assertTrue(root.contains("GeoActionRow(current.deviceName, \"다시 연결\", onOpenDevice, onReconnect"))
        assertTrue(root.contains("if (LocalGeoPane.current) 0.dp else 24.dp"))
        assertFalse(root.contains("GeoDevicePhotoCard("))
    }

    @Test fun detailPreservesFourMetricsCopyAndDangerConfirmations() {
        val detail = source("devices/DeviceDetailScreen.kt")
        assertTrue(detail.contains("if (!detailRoot) GeoDetailToolbar(onBack)"))
        assertTrue(detail.contains("GeoMetricStrip(devicePhotoFigures("))
        assertTrue(detail.contains("DeviceIdentityRow(deviceId)"))
        val identity = source("devices/DeviceScreenComponents.kt").substringAfter("internal fun DeviceIdentityRow")
            .substringBefore("/** Rows own their insets")
        assertTrue(identity.contains("GeoIconTile(GeoIcons.tabDevice)"))
        assertTrue(identity.contains("Text(\"장치 ID\", style = type.titleSm"))
        assertTrue(identity.contains("SelectionContainer"))
        assertTrue(identity.contains("clipboard.setText(AnnotatedString(deviceId))"))
        assertTrue(identity.contains("if (deviceId.isNotBlank()) GeoIconButton"))
        assertTrue(detail.contains("kind = GeoConfirmKind.DANGER"))
        assertTrue(detail.contains("if (enabled) when { forget -> onForget?.invoke(); reboot -> onReboot(); else -> onShutdown() }"))
        val graph = source("navigation/DeviceGraph.kt")
        assertTrue(graph.contains("if (targetId.isBlank())"))
        assertTrue(graph.contains("DeviceDetailPlaceholder(deviceState.isLoading"))
        assertTrue(detail.contains("if (loading) GeoSkeleton("))
        assertTrue(detail.contains("else GeoStateBlock("))
    }

    @Test fun detailStatusAndMetricsUseTheSharedSlotsWithoutDuplicateInsets() {
        val detail = source("devices/DeviceDetailScreen.kt")
        assertTrue(detail.contains("subtitleStatus = GeoStatusSpec(connection.label, connection.tone)"))
        assertFalse(detail.contains("GeoBadge(connection.label"))
        assertTrue(detail.contains("DeviceSurface {\n                        GeoMetricStrip"))
    }

    @Test fun emptyInventoryHasAFixedQrActionAndLoadingMatchesTheList() {
        val root = source("devices/DevicesHomeScreen.kt")
        assertTrue(root.contains("val empty = !loadingDevices && registeredDevices.isEmpty()"))
        assertTrue(root.contains("primaryAction = if (empty) GeoButtonAction(\"QR 코드 스캔\", onAddDevice)"))
        assertTrue(root.contains("secondaryAction = if (empty) GeoButtonAction(\"장치 없이 서버 파일 보기\", onServerFiles, GeoButtonStyle.Tertiary)"))
        val loading = root.substringAfter("internal fun DevicesHomeLoading()")
        assertTrue(loading.contains("GeoGroupedSection(\"장치 목록\", first = true"))
        assertTrue(loading.contains("kind = GeoSkeletonKind.Rows"))
        assertFalse(loading.contains("GeoSkeletonKind.DeviceCard"))
    }

    @Test fun ownedPagesDoNotReintroducePrivateBarsRawClicksOrFixedTypography() {
        val packages = listOf("devices", "sensors", "pairing", "onboarding", "connection", "diagnostics", "network", "wifi")
        val forbidden = Regex("\\bPairingTopBar\\b|\\.clickable\\(|\\bfontSize\\s*=|\\blineHeight\\s*=|TextOverflow\\.Ellipsis")
        packages.flatMap { File(ui, it).walkTopDown().filter { file -> file.extension == "kt" }.toList() }
            .forEach { file -> assertFalse(file.name, forbidden.containsMatchIn(file.readText())) }
        listOf("devices/DevicesHomeScreen.kt", "devices/DeviceDetailScreen.kt", "sensors/SensorScreen.kt").forEach {
            assertFalse(it, source(it).contains("DeviceTitleBar("))
        }
    }
}
