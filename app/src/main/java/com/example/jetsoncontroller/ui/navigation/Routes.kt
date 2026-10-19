package com.example.jetsoncontroller.ui.navigation

internal object Routes {

    const val CONNECTION_HUB =
        "connection_hub"

    const val ONBOARDING =
        "onboarding"

    const val DEVICES_BLE =
        "devices_ble"

    const val QR_SCANNER =
        "qr_scanner"

    const val PAIRING =
        "pairing"

    const val WIFI_DIRECT =
        "wifi_direct"

    /** 장치 root. Route names remain compatible with the current screens. */
    const val DEVICES =
        "devices"

    const val DEVICE_DETAIL =
        "device_detail"

    /** 작업 root: pinned field collection, the running card, recent receipts, other tasks. */
    const val TASKS = "tasks"

    /** 전송 root: uploads in progress, queued, failed and confirmed by the server. */
    const val TRANSFERS = "transfers"

    /** Read-only task detail for normal operators. */
    const val TASK_DETAIL = "task_detail/{pipelineId}"
    const val TICKET_DETAIL = "ticket_detail/{rowId}"

    const val NETWORK_SETTINGS =
        "network_settings"

    const val STORAGE = "storage"

    const val SERVER_STORAGE = "server_storage"
    const val SERVER_PROXY_STORAGE = "server_proxy_storage"

    const val STORAGE_ROUTE =
        "storage?rootId={rootId}&path={path}"

    const val UPLOAD_CONFIRM =
        "upload_confirm/{rootId}?path={path}&runId={runId}"

    const val UPLOAD_PROGRESS =
        "upload_progress"

    const val UPLOAD_QUEUE =
        "upload_queue"

    const val UPLOAD_SERVERS =
        "upload_servers"

    const val PIPELINES =
        "pipelines"
    const val TASK_HISTORY = "task_history"

    const val PIPELINE_EDITOR =
        "pipeline_editor"

    const val PIPELINE_PICKER =
        "pipeline_picker"

    const val PIPELINE_LOGS =
        "pipeline_logs/{pipelineId}"

    const val PIPELINE_CONFIG =
        "pipeline_config/{pipelineId}"

    const val SENSORS = "sensors"

    const val CAMERA_PREVIEW = "camera_preview"

    const val GNSS_MAP = "gnss_map"

    const val SETTINGS = "settings"
    const val ADMIN_TOOLS = "admin_tools"
    const val ALERT_SETTINGS = "alert_settings"
    const val DATA = "data_hub"
    const val LOCAL_TRASH = "local_trash"
    const val PIPELINE_DETAIL = "pipeline_detail/{pipelineId}"
    const val SURVEY_RUN = "survey_run/{pipelineId}"

    // 업무 단계형 IA에서 새로 생긴 두 목적지.
    // 이전 구조에는 '진행 중인 수집' 과 '종료 후 결과 요약' 에 해당하는 라우트가 없어서,
    // 시작을 누른 직원이 갈 곳이 작업 목록밖에 없었습니다.
    const val ACTIVE_RUN = "active_run"
    const val RUN_RESULT = "run_result/{runId}?deviceId={deviceId}&pipelineId={pipelineId}&historyId={historyId}"
    const val HISTORY_RESULT = "history_result/{historyId}?deviceId={deviceId}"

    const val DIAGNOSTICS = "connection_diagnostics"

    const val ALERTS = "alerts"
    const val WORK_BROWSE = "work_overview"
    const val DEVICE_BROWSE = "device_overview"
    const val TRANSFER_BROWSE = "transfer_overview"
}

internal val primaryTabRoutes = listOf(Routes.TASKS, Routes.DEVICES, Routes.TRANSFERS, Routes.SETTINGS)

internal fun tabGraphRoute(root: String): String = "tab/$root"

internal fun initialWorkRoute(registeredDeviceCount: Int?): String? = registeredDeviceCount?.let {
    if (it == 0) Routes.ONBOARDING else Routes.TASKS
}

/** Both committed names and the focused routes reserved by §1.2 use this policy. */
internal fun focusedRoute(route: String?): Boolean {
    val path = route?.substringBefore('?') ?: return false
    return path in setOf(
        Routes.ONBOARDING, Routes.DEVICES_BLE, Routes.QR_SCANNER, Routes.PAIRING,
        Routes.ACTIVE_RUN, Routes.CAMERA_PREVIEW, Routes.GNSS_MAP,
        Routes.PIPELINE_EDITOR, Routes.PIPELINE_CONFIG.substringBefore('/'),
        Routes.UPLOAD_SERVERS, "first_run", "scan", "run", "map", "camera", "qr",
        "image_preview", "video_preview", "dev/terminal", "dev/upload-targets/edit",
        "dev/pipeline/new", "dev/server-profiles/edit"
    ) || path.startsWith("pipeline_config/") ||
        Regex("dev/pipeline/[^/]+/(config|policy)").matches(path)
}

internal fun primaryNavigationVisible(route: String?, imeVisible: Boolean = false, focusedMedia: Boolean = false): Boolean =
    route != null && !focusedRoute(route) && !imeVisible && !focusedMedia

internal fun mediaPreviewFocused(mediaType: String?, fileName: String?): Boolean =
    mediaType?.startsWith("image/") == true || mediaType?.startsWith("video/") == true ||
        fileName?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "mp4", "webm", "mov")

internal fun currentTabRoot(navController: androidx.navigation.NavHostController): String =
    generateSequence(navController.currentDestination) { it.parent }
        .mapNotNull { it.route }
        .firstOrNull { it.startsWith("tab/") }
        ?.removePrefix("tab/") ?: Routes.TASKS

internal fun currentTab(navController: androidx.navigation.NavHostController): com.example.jetsoncontroller.ui.components.ControlSection =
    when (currentTabRoot(navController)) {
        Routes.DEVICES -> com.example.jetsoncontroller.ui.components.ControlSection.DEVICES
        Routes.TRANSFERS -> com.example.jetsoncontroller.ui.components.ControlSection.TRANSFERS
        Routes.SETTINGS -> com.example.jetsoncontroller.ui.components.ControlSection.SETTINGS
        else -> com.example.jetsoncontroller.ui.components.ControlSection.TASKS
    }

internal fun rootBackDestination(route: String?): String? =
    Routes.TASKS.takeIf { route in primaryTabRoutes && route != Routes.TASKS }

