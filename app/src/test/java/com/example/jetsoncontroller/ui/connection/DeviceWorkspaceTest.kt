package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.ui.pipelines.PipelineDraft
import com.example.jetsoncontroller.ui.pipelines.PipelineUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceWorkspaceTest {
    @Test
    fun transientDisconnectRetainsDraftAndSwitchingDevicesRestoresOnlyTheirOwnDraft() {
        val workspace = DeviceWorkspace { PipelineUiState() }
        var state = workspace.select("jetson-a", PipelineUiState())
        state = state.copy(draft = PipelineDraft(label = "도로 수집"))
        state = workspace.select(null, state)
        assertEquals("도로 수집", state.draft.label)
        state = workspace.select("JETSON-A", state)
        assertEquals("도로 수집", state.draft.label)
        state = workspace.select("jetson-b", state)
        assertEquals("", state.draft.label)
        state = state.copy(draft = PipelineDraft(label = "실내 수집"))
        state = workspace.select("jetson-a", state)
        assertEquals("도로 수집", state.draft.label)
        state = workspace.select("jetson-b", state)
        assertEquals("실내 수집", state.draft.label)
    }
}
