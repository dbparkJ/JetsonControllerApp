package com.example.jetsoncontroller.ui.pipelines

import org.junit.Assert.*
import org.junit.Test

class PipelineDetailPresentationTest {
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
