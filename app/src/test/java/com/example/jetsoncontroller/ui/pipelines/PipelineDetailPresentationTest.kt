package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class PipelineDetailPresentationTest {
    @Test fun genericListAndPickerFailuresExplainRecoveryInsteadOfRepeatingTheTitle() {
        val recovery = "장치 연결을 확인하고 다시 조회하세요."
        listOf(null, "", "작업 목록을 불러오지 못했습니다", "작업 목록을 불러오지 못했습니다.",
            "수집 작업을 불러오지 못했습니다. 다시 시도하세요.").forEach {
            assertEquals(recovery, pipelineFailureDetail(it, "작업 목록을 불러오지 못했습니다"))
        }
        assertEquals(recovery, pipelineFailureDetail("폴더 내용을 불러오지 못했습니다. 다시 시도하세요.", "폴더를 불러오지 못했습니다"))
        val screens = r8UiSource("pipelines/PipelineScreens.kt")
        assertTrue(screens.contains("pipelineFailureDetail(state.error, \"작업 목록을 불러오지 못했습니다\")"))
        assertTrue(screens.contains("pipelineFailureDetail(state.error, \"폴더를 불러오지 못했습니다\")"))
    }

    @Test fun specificFailureCausesRemainVisible() {
        val cause = "저장소 접근 권한이 없습니다. 관리자에게 확인하세요."
        assertEquals(cause, pipelineFailureDetail(cause, "폴더를 불러오지 못했습니다"))
        assertEquals("장치 응답 시간이 초과되었습니다.", pipelineFailureDetail(
            "작업 목록을 불러오지 못했습니다. 장치 응답 시간이 초과되었습니다.", "작업 목록을 불러오지 못했습니다"))
    }

    @Test fun taskListLoadingFailureAndEmptyAreExclusive() {
        assertEquals(PipelineDetailContent.LOADING, pipelineListContent(true, "failed", false))
        assertEquals(PipelineDetailContent.ERROR, pipelineListContent(false, "failed", false))
        assertEquals(PipelineDetailContent.EMPTY, pipelineListContent(false, null, false))
        assertEquals(PipelineDetailContent.CONTENT, pipelineListContent(true, "failed", true))
    }

    @Test fun disconnectedAndFailedLoadsNeverClaimEmptyContent() {
        assertEquals(PipelineDetailContent.OFFLINE, pipelineDetailContent(false, false, null, false))
        assertEquals(PipelineDetailContent.OFFLINE, pipelineDetailContent(false, true, "failed", false))
        assertEquals(PipelineDetailContent.ERROR, pipelineDetailContent(true, false, "failed", false))
        assertEquals(PipelineDetailContent.LOADING, pipelineDetailContent(true, true, null, false))
        assertEquals(PipelineDetailContent.EMPTY, pipelineDetailContent(true, false, null, false))
    }

    @Test fun cachedContentRemainsReadableWhileDisconnectedOrRefreshing() {
        assertEquals(PipelineDetailContent.CONTENT, pipelineDetailContent(false, false, null, true))
        assertEquals(PipelineDetailContent.CONTENT, pipelineDetailContent(true, true, "failed", true))
    }

    @Test fun archivedAndDisconnectedLogsAreNeverLabelledLive() {
        assertTrue(pipelineLogIsLive(true, true, true))
        assertFalse(pipelineLogIsLive(true, true, false))
        assertFalse(pipelineLogIsLive(false, true, true))
        assertFalse(pipelineLogIsLive(true, false, true))
    }

    @Test fun keepsReadableLabelsAndShowsUnknownKeysAsSecondaryTechnicalText() {
        assertEquals(ConfigFieldCaption("최소 여유 공간", "storage.min_free_bytes"),
            configFieldCaption("storage.min_free_bytes", "최소 여유 공간"))
        assertEquals(ConfigFieldCaption("설정 값", "camera.frame_rate"),
            configFieldCaption("camera.frame_rate", "camera > frame_rate"))
        assertEquals(ConfigFieldCaption("설정 값", "camera.frame_rate"),
            configFieldCaption("camera.frame_rate", ""))
    }
}
