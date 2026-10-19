package com.example.jetsoncontroller.ui.dev

internal enum class DevDestination(val route: String, val parent: String?, val inventory: Set<Int> = emptySet()) {
    HUB("dev", null, setOf(92)),
    TERMINAL("dev/terminal", "dev", setOf(93, 94)),
    FAN("dev/fan", "dev", setOf(95)),
    NETWORK("dev/network", "dev"),
    SENSOR_TEST("dev/sensor-test", "dev"),
    PIPELINES("dev/pipelines", "dev"),
    PIPELINE("dev/pipeline/{pipelineId}", "dev/pipelines", setOf(99, 108, 110)),
    NEW_PIPELINE("dev/pipeline/new", "dev", setOf(100)),
    PICKER("dev/picker", "dev/pipeline/new", setOf(101, 103)),
    LOGS("dev/pipeline/{pipelineId}/logs", "dev/pipeline/{pipelineId}", setOf(102, 104, 106)),
    CONFIG("dev/pipeline/{pipelineId}/config", "dev/pipeline/{pipelineId}", setOf(105, 107, 109)),
    POLICY("dev/pipeline/{pipelineId}/policy", "dev/pipeline/{pipelineId}"),
    UPLOAD_TARGETS("dev/upload-targets", "dev", setOf(88, 89)),
    UPLOAD_EDITOR("dev/upload-targets/edit?targetId={targetId}", "dev/upload-targets", setOf(90, 91)),
    PROFILES("dev/server-profiles", "dev"),
    PROFILE_EDITOR("dev/server-profiles/edit?profileId={profileId}", "dev/server-profiles", setOf(47)),
    SERVER_TRASH("dev/server-trash", "dev", setOf(46)),
    SERVER_PROXY("dev/server-proxy", "dev", setOf(51, 52, 53, 54)),
    PROXY_PREVIEW("image_preview?devProxy=true", "dev/server-proxy"),
    DIAGNOSTICS("dev/diagnostics", "dev", setOf(97)),
    RELAY("dev/relay", "dev/pipeline/{pipelineId}")
}

internal data class DevDoor(val label: String, val destination: DevDestination)

internal val devHubSections = listOf(
    "장치" to listOf(DevDoor("원격 터미널", DevDestination.TERMINAL), DevDoor("팬 제어", DevDestination.FAN),
        DevDoor("네트워크 주소", DevDestination.NETWORK), DevDoor("카메라·GPS 테스트", DevDestination.SENSOR_TEST)),
    "수집 작업" to listOf(DevDoor("작업 관리", DevDestination.PIPELINES), DevDoor("작업 등록", DevDestination.NEW_PIPELINE)),
    "서버·전송" to listOf(DevDoor("업로드 서버", DevDestination.UPLOAD_TARGETS), DevDoor("서버 프로필", DevDestination.PROFILES),
        DevDoor("서버 휴지통 비우기", DevDestination.SERVER_TRASH), DevDoor("장치 경유 서버 조회", DevDestination.SERVER_PROXY)),
    "진단" to listOf(DevDoor("연결 진단 기록", DevDestination.DIAGNOSTICS))
)

internal fun devObject(value: String): String {
    val last = value.lastOrNull() ?: return value
    val finalConsonant = if (last in '\uAC00'..'\uD7A3') (last.code - 0xAC00) % 28 != 0
        else last in "013678"
    return value + if (finalConsonant) "을" else "를"
}
