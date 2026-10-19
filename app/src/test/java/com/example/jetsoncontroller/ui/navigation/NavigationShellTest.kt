package com.example.jetsoncontroller.ui.navigation

import com.example.jetsoncontroller.ui.TabTap
import com.example.jetsoncontroller.ui.tabTap
import org.junit.Assert.*
import org.junit.Test

class NavigationShellTest {
    @Test
    fun `initial empty VM state cannot choose first run before the store restores`() {
        assertNull(initialWorkRoute(null))
        assertEquals(Routes.ONBOARDING, initialWorkRoute(0))
        assertEquals(Routes.TASKS, initialWorkRoute(1))
        assertEquals(Routes.TASKS, initialWorkRoute(3))
    }

    @Test
    fun `roots and browsing details retain navigation including both result routes`() {
        (primaryTabRoutes + listOf(
            Routes.TICKET_DETAIL, Routes.TASK_DETAIL, Routes.SURVEY_RUN, Routes.TASK_HISTORY,
            Routes.RUN_RESULT, Routes.HISTORY_RESULT, Routes.DEVICE_DETAIL, Routes.ALERTS,
            Routes.NETWORK_SETTINGS, Routes.SENSORS, Routes.STORAGE_ROUTE, Routes.DATA,
            Routes.LOCAL_TRASH, Routes.UPLOAD_CONFIRM, Routes.UPLOAD_PROGRESS, Routes.UPLOAD_QUEUE,
            Routes.SERVER_STORAGE, Routes.SERVER_PROXY_STORAGE, Routes.PIPELINES, Routes.PIPELINE_DETAIL,
            Routes.PIPELINE_LOGS, Routes.PIPELINE_PICKER, Routes.ADMIN_TOOLS, Routes.DIAGNOSTICS,
            Routes.ALERT_SETTINGS, Routes.WORK_BROWSE, Routes.DEVICE_BROWSE, Routes.TRANSFER_BROWSE,
            "ticket/row-1", "run_result/run-1?deviceId=device", "history_result/history-1?deviceId=device",
            "dev/pipeline/pipe/logs", "dev/upload-targets", "dev/server-profiles"
        )).forEach { assertTrue(it, primaryNavigationVisible(it)) }
    }

    @Test
    fun `focused committed and planned routes hide navigation`() {
        listOf(
            Routes.ONBOARDING, Routes.DEVICES_BLE, Routes.QR_SCANNER, Routes.PAIRING,
            Routes.ACTIVE_RUN, Routes.CAMERA_PREVIEW, Routes.GNSS_MAP,
            Routes.PIPELINE_EDITOR, Routes.PIPELINE_CONFIG, Routes.UPLOAD_SERVERS,
            "first_run", "scan", "run", "map?mode=run&run=one", "camera", "qr",
            "image_preview", "video_preview", "dev/terminal", "dev/upload-targets/edit",
            "dev/pipeline/new", "dev/pipeline/pipe/config", "dev/pipeline/pipe/policy",
            "dev/server-profiles/edit", "pipeline_config/pipe"
        ).forEach { assertFalse(it, primaryNavigationVisible(it)) }
        assertFalse(primaryNavigationVisible(null))
    }

    @Test
    fun `IME hides navigation without changing the route or its owner`() {
        assertFalse(primaryNavigationVisible(Routes.NETWORK_SETTINGS, imeVisible = true))
        assertTrue(primaryNavigationVisible(Routes.NETWORK_SETTINGS, imeVisible = false))
    }

    @Test
    fun `inline browser media previews are focused but text and receipts keep navigation`() {
        assertTrue(mediaPreviewFocused("image/png", "photo"))
        assertTrue(mediaPreviewFocused("video/mp4", "clip"))
        assertTrue(mediaPreviewFocused(null, "PHOTO.JPG"))
        assertTrue(mediaPreviewFocused(null, "clip.webm"))
        assertFalse(mediaPreviewFocused("text/plain", "log.txt"))
        assertFalse(mediaPreviewFocused(null, null))
        assertFalse(primaryNavigationVisible(Routes.SERVER_STORAGE, focusedMedia = true))
        assertTrue(primaryNavigationVisible(Routes.SERVER_STORAGE, focusedMedia = false))
    }

    @Test
    fun `re-tap pops the owning tab even for a cross-linked detail`() {
        assertEquals(TabTap.RETURN_TO_TAB_ROOT,
            tabTap(Routes.UPLOAD_PROGRESS, true, Routes.TASKS, currentTabRoute = Routes.TASKS))
        assertEquals(TabTap.RETURN_TO_TAB_ROOT,
            tabTap(Routes.DEVICE_DETAIL, true, Routes.SETTINGS, currentTabRoute = Routes.SETTINGS))
        assertEquals(TabTap.STAY, tabTap(Routes.TASKS, true, Routes.TASKS, Routes.TASKS))
    }

    @Test
    fun `another tab restores its stack even when its root is already on the back stack`() {
        assertEquals(TabTap.OPEN_TAB_ROOT,
            tabTap(Routes.ALERT_SETTINGS, true, Routes.TASKS, currentTabRoute = Routes.SETTINGS))
        assertEquals(TabTap.OPEN_TAB_ROOT,
            tabTap(Routes.TASK_HISTORY, false, Routes.TRANSFERS, currentTabRoute = Routes.TASKS))
        assertNotEquals(tabGraphRoute(Routes.TASKS), tabGraphRoute(Routes.DEVICES))
    }

    @Test
    fun `only non-work roots intercept back to work`() {
        listOf(Routes.DEVICES, Routes.TRANSFERS, Routes.SETTINGS).forEach {
            assertEquals(Routes.TASKS, rootBackDestination(it))
        }
        listOf(Routes.TASKS, Routes.RUN_RESULT, Routes.HISTORY_RESULT, Routes.ACTIVE_RUN,
            Routes.ONBOARDING, Routes.WORK_BROWSE, Routes.WIFI_DIRECT).forEach {
            assertNull(rootBackDestination(it))
        }
        assertEquals(listOf(Routes.TASKS, Routes.DEVICES, Routes.TRANSFERS, Routes.SETTINGS), primaryTabRoutes)
    }
}
