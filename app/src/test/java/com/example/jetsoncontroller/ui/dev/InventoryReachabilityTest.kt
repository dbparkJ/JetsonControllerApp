package com.example.jetsoncontroller.ui.dev

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** 7-E homes only: unlock (#87) and device power (#96) belong to 7-D and 7-A. */
class InventoryReachabilityTest {
    @Test fun everyDeveloperInventoryHomeIsReachableFromTheHub() {
        val pages = DevDestination.entries.associateBy { it.route }
        assertEquals(DevDestination.entries.size, pages.size)
        val reachable = mutableSetOf(DevDestination.HUB.route)
        repeat(pages.size) { pages.values.filter { it.parent in reachable }.forEach { reachable += it.route } }
        assertEquals(pages.keys, reachable)
        val owned = (88..110).toSet() - setOf(96, 98)
        assertTrue(pages.values.flatMap { it.inventory }.toSet().containsAll(owned))
        assertFalse(pages.values.any { 98 in it.inventory })
        assertFalse(pages.keys.any { it.contains("dashboard") || it.contains("admin_tools") || it.contains("developer_menu") })
    }

    @Test fun hubOffersEveryToolFromTheDrawingInOrder() {
        assertEquals(listOf("장치", "수집 작업", "서버·전송", "진단"), devHubSections.map { it.first })
        assertEquals(listOf("원격 터미널", "팬 제어", "네트워크 주소", "카메라·GPS 테스트", "작업 관리", "작업 등록",
            "업로드 서버", "서버 프로필", "서버 휴지통 비우기", "장치 경유 서버 조회", "연결 진단 기록"),
            devHubSections.flatMap { it.second }.map { it.label })
        assertTrue(devHubSections.flatMap { it.second }.all { it.destination.parent == DevDestination.HUB.route })
        assertEquals(DevDestination.PIPELINE.route, DevDestination.CONFIG.parent)
        assertEquals(DevDestination.PIPELINE.route, DevDestination.POLICY.parent)
    }

    @Test fun retiredAdminAndDashboardFunctionsHaveOperatorAndDeveloperHomes() {
        val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
            File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
        fun source(path: String) = File(ui, path).readText()
        val detail = source("devices/DeviceDetailScreen.kt")
        assertTrue(detail.contains("DevicePowerButtons("))
        assertTrue(detail.contains("DeviceSensorRows("))
        val graph = source("navigation/DevGraph.kt")
        listOf("FAN", "NETWORK", "SENSOR_TEST", "DIAGNOSTICS").forEach {
            assertTrue("Missing former admin tool: $it", graph.contains("DevDestination.$it.route"))
        }
        assertTrue(source("dev/DevFanScreen.kt").contains("GeoSliderRow("))
        assertTrue(source("dev/DevDeviceTools.kt").contains("onCamera"))
        assertTrue(source("tasks/TasksHomeScreen.kt").contains("GeoFocusBlock("))
        assertTrue(source("devices/DevicesHomeScreen.kt").contains("GeoFigureGrid("))
    }
}
