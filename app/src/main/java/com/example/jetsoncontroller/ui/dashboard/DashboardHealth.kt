package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import java.security.MessageDigest

enum class DashboardHealthLevel {
    HEALTHY,
    ATTENTION,
    UNKNOWN
}

enum class DashboardHealthIssue {
    STALE_STATUS,
    UNAVAILABLE_METRICS,
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
    val issueKinds: Set<DashboardHealthIssue> = emptySet(),
    val issueKeys: Set<String> = issueKinds.mapTo(linkedSetOf()) { it.name }
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
    val issueKeys = linkedSetOf<String>()
    val issues = buildList {
        if (freshness == StatusFreshness.STALE) {
            issueKinds += DashboardHealthIssue.STALE_STATUS
            issueKeys += DashboardHealthIssue.STALE_STATUS.name
            add("상태 정보가 오래되었습니다.")
        }
        if (status.metricValidity.values.any { it.validity != "valid" }) {
            issueKinds += DashboardHealthIssue.UNAVAILABLE_METRICS
            status.metricValidity.filterValues { it.validity != "valid" }.forEach { (name, metric) ->
                issueKeys += healthIssueKey(DashboardHealthIssue.UNAVAILABLE_METRICS, name, metric.validity)
            }
            add("일부 장비 지표를 확인하지 못했거나 이전 측정값입니다.")
        }
        if (status.metricIsValid("temperatureC") && status.temperatureC >= 80f) {
            issueKinds += DashboardHealthIssue.HIGH_TEMPERATURE
            issueKeys += DashboardHealthIssue.HIGH_TEMPERATURE.name
            add("장비 온도가 ${status.temperatureC.toInt()} C로 높습니다.")
        }
        if (status.metricIsValid("storagePercent") && status.storagePercent >= 90) {
            issueKinds += DashboardHealthIssue.STORAGE_PRESSURE
            issueKeys += DashboardHealthIssue.STORAGE_PRESSURE.name
            add("저장 공간이 ${status.storagePercent}% 사용 중입니다.")
        }
        val failedPipelines = pipelines.filter { it.state == PipelineState.FAILED }
        if (failedPipelines.isNotEmpty()) {
            issueKinds += DashboardHealthIssue.FAILED_PIPELINE
            failedPipelines.forEach { pipeline ->
                issueKeys += healthIssueKey(
                    DashboardHealthIssue.FAILED_PIPELINE,
                    pipeline.id, pipeline.result, pipeline.lastExitCode.toString(), pipeline.sourceRevision
                )
            }
            add("실패한 작업이 ${failedPipelines.size}개 있습니다.")
        }
        val failedUploads = uploads.filter { it.state == UploadJobState.FAILED }
        if (failedUploads.isNotEmpty()) {
            issueKinds += DashboardHealthIssue.FAILED_UPLOAD
            failedUploads.forEach { upload ->
                issueKeys += healthIssueKey(DashboardHealthIssue.FAILED_UPLOAD, upload.id, upload.errorMessage.orEmpty())
            }
            add("실패한 업로드가 ${failedUploads.size}개 있습니다.")
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
            issueKinds = issueKinds,
            issueKeys = issueKeys
        )
    }
}

internal fun dashboardHealthKey(health: DashboardHealth): String = buildString {
    append(health.level.name)
    append('|')
    append(health.issueKeys.sorted().joinToString(","))
}

internal fun dashboardHealthDismissalKeys(
    deviceId: String,
    health: DashboardHealth
): Set<String> = if (deviceId.isBlank() || health.level != DashboardHealthLevel.ATTENTION) {
    emptySet()
} else {
    health.issueKeys.mapTo(linkedSetOf()) { issueKey ->
        dashboardHealthDevicePrefix(deviceId) + issueKey
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

private fun healthIssueKey(kind: DashboardHealthIssue, vararg identity: String): String {
    // Stable content identity distinguishes new failures without storing error text in preferences.
    val content = identity.joinToString("") { "${it.length}:$it" }
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return kind.name + ":" + digest.joinToString("") { "%02x".format(it) }
}
