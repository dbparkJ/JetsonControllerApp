package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState

enum class DashboardHealthLevel {
    HEALTHY,
    ATTENTION,
    UNKNOWN
}

enum class DashboardHealthIssue {
    STALE_STATUS,
    HIGH_TEMPERATURE,
    STORAGE_PRESSURE,
    FAILED_PIPELINE,
    FAILED_UPLOAD
}

data class DashboardHealth(
    val level: DashboardHealthLevel,
    val title: String,
    val detail: String,
    val issues: List<String>,
    val issueKinds: Set<DashboardHealthIssue> = emptySet()
)

internal fun assessDashboardHealth(
    status: JetsonStatus,
    freshness: StatusFreshness,
    pipelines: List<ManagedPipeline>,
    uploads: List<UploadJob>
): DashboardHealth {
    if (freshness == StatusFreshness.UNKNOWN) {
        return DashboardHealth(
            level = DashboardHealthLevel.UNKNOWN,
            title = "상태 확인 중",
            detail = "Jetson의 첫 상태 응답을 기다리고 있습니다.",
            issues = emptyList()
        )
    }

    val issueKinds = linkedSetOf<DashboardHealthIssue>()
    val issues = buildList {
        if (freshness == StatusFreshness.STALE) {
            issueKinds += DashboardHealthIssue.STALE_STATUS
            add("상태 정보가 오래되었습니다.")
        }
        if (status.temperatureC >= 80f) {
            issueKinds += DashboardHealthIssue.HIGH_TEMPERATURE
            add("장비 온도가 ${status.temperatureC.toInt()} C로 높습니다.")
        }
        if (status.storagePercent >= 90) {
            issueKinds += DashboardHealthIssue.STORAGE_PRESSURE
            add("저장 공간이 ${status.storagePercent}% 사용 중입니다.")
        }
        val failedPipelines = pipelines.count { it.state == PipelineState.FAILED }
        if (failedPipelines > 0) {
            issueKinds += DashboardHealthIssue.FAILED_PIPELINE
            add("실패한 작업이 ${failedPipelines}개 있습니다.")
        }
        val failedUploads = uploads.count { it.state == UploadJobState.FAILED }
        if (failedUploads > 0) {
            issueKinds += DashboardHealthIssue.FAILED_UPLOAD
            add("실패한 업로드가 ${failedUploads}개 있습니다.")
        }
    }

    return if (issues.isEmpty()) {
        DashboardHealth(
            level = DashboardHealthLevel.HEALTHY,
            title = "정상 작동 중",
            detail = "연결과 주요 시스템 상태가 안정적입니다.",
            issues = emptyList()
        )
    } else {
        DashboardHealth(
            level = DashboardHealthLevel.ATTENTION,
            title = "확인이 필요합니다",
            detail = issues.first(),
            issues = issues,
            issueKinds = issueKinds
        )
    }
}

internal fun dashboardHealthKey(health: DashboardHealth): String = buildString {
    append(health.level.name)
    append('|')
    append(health.issueKinds.sortedBy { it.name }.joinToString(",") { it.name })
}

internal fun dashboardHealthDismissalKeys(
    deviceId: String,
    health: DashboardHealth
): Set<String> = if (deviceId.isBlank() || health.level != DashboardHealthLevel.ATTENTION) {
    emptySet()
} else {
    health.issueKinds.mapTo(linkedSetOf()) { issue ->
        dashboardHealthDevicePrefix(deviceId) + issue.name
    }
}

internal fun dismissDashboardHealth(
    dismissals: Set<String>,
    deviceId: String,
    health: DashboardHealth
): Set<String> {
    val keys = dashboardHealthDismissalKeys(deviceId, health)
    return if (keys.isEmpty()) dismissals else dismissals + keys
}

internal fun reconcileDashboardHealthDismissals(
    dismissals: Set<String>,
    deviceId: String,
    health: DashboardHealth,
    online: Boolean
): Set<String> {
    if (!online || deviceId.isBlank() || health.level == DashboardHealthLevel.UNKNOWN) {
        return dismissals
    }
    val prefix = dashboardHealthDevicePrefix(deviceId)
    return when (health.level) {
        DashboardHealthLevel.HEALTHY -> dismissals.filterNot { it.startsWith(prefix) }.toSet()
        // Keep each issue kind independently dismissed until the device is
        // observed healthy. A newly added issue has a different key and remains
        // visible, while a temporary status/reconnect transition cannot resurrect
        // an alert that the user already acknowledged.
        DashboardHealthLevel.ATTENTION -> dismissals
        DashboardHealthLevel.UNKNOWN -> dismissals
    }
}

internal fun isDashboardHealthDismissed(
    dismissals: Set<String>,
    deviceId: String,
    health: DashboardHealth
): Boolean {
    val currentIssueKeys = dashboardHealthDismissalKeys(deviceId, health)
    return currentIssueKeys.isNotEmpty() && dismissals.containsAll(currentIssueKeys)
}

private fun dashboardHealthDevicePrefix(deviceId: String): String =
    "${deviceId.length}:$deviceId:"
