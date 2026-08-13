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

data class DashboardHealth(
    val level: DashboardHealthLevel,
    val title: String,
    val detail: String,
    val issues: List<String>
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

    val issues = buildList {
        if (freshness == StatusFreshness.STALE) {
            add("상태 정보가 오래되었습니다.")
        }
        if (status.temperatureC >= 80f) {
            add("장비 온도가 ${status.temperatureC.toInt()} C로 높습니다.")
        }
        if (status.storagePercent >= 90) {
            add("저장 공간이 ${status.storagePercent}% 사용 중입니다.")
        }
        val failedPipelines = pipelines.count { it.state == PipelineState.FAILED }
        if (failedPipelines > 0) {
            add("실패한 작업이 ${failedPipelines}개 있습니다.")
        }
        val failedUploads = uploads.count { it.state == UploadJobState.FAILED }
        if (failedUploads > 0) {
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
            issues = issues
        )
    }
}
