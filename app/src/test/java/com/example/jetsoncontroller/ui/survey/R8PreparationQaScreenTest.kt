package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.model.PipelineRunPolicy
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8PreparationQaScreenTest {
    private fun ready() = SurveyRunUiState(deviceId = "device", pipelineId = "capture", online = true,
        runStateConfirmed = true,
        selectedProject = SurveyProject("project", "프로젝트", 1, "created", "updated"),
        selectedSection = SurveySection("section", "project", "구간", 1, "created", "updated"),
        policy = PipelineRunPolicy(pipelineId = "capture", revision = "policy", outputRootId = "data", updatedAt = "updated"))

    @Test fun preparationActionOpensChecksAndPreservesThePreflightGate() {
        var checks = 0
        val action = preparationPrimaryAction(ready(), true, null, { checks++ }, { fail() }, { fail() })
        assertTrue(action.enabled)
        assertEquals("시작 전 점검", action.label)
        action.onClick()
        assertEquals(1, checks)
        listOf(ready().copy(online = false), ready().copy(controlAvailable = false),
            ready().copy(runStateConfirmed = false), ready().copy(operation = "preflight"),
            ready().copy(selectedSection = null), ready().copy(policy = null)).forEach { state ->
            assertFalse(preparationPrimaryAction(state, true, null, {}, {}, {}).enabled)
        }
        val changed = preparationPrimaryAction(ready(), false, "구간이 바뀌었습니다.", {}, {}, {})
        assertFalse(changed.enabled)
        assertEquals("구간이 바뀌었습니다.", changed.disabledReason)
    }

    @Test fun anUnconfirmedStartKeepsItsExistingRefreshAction() {
        var refreshes = 0
        val state = ready().copy(unconfirmedRunId = "existing-run")
        val action = preparationPrimaryAction(state, true, null, { fail() }, { fail() }, { refreshes++ })
        assertEquals("상태 다시 확인", action.label)
        assertTrue(action.enabled)
        action.onClick()
        assertEquals(1, refreshes)
        assertFalse(preparationPrimaryAction(state.copy(online = false), true, null, {}, {}, {}).enabled)
    }

    @Test fun footerExplainsTheCheckActionAndCurrentLoadState() {
        assertTrue(preparationActionReason(ready()).endsWith(" · 시작 전 점검을 진행하세요."))
        assertFalse(preparationActionReason(ready()).contains("시작할 수 있습니다"))
        val loading = ready().copy(isLoading = true, operation = "refresh", runStateConfirmed = false)
        assertEquals("수집 구간을 불러오고 있습니다.", preparationActionReason(loading))
        assertEquals(com.example.jetsoncontroller.ui.model.Tone.PENDING, preparationActionTone(loading))
        val failed = ready().copy(error = "조회 실패", runStateConfirmed = false)
        assertEquals("수집 구간을 불러오지 못했습니다. 다시 확인하세요.", preparationActionReason(failed))
        assertEquals(com.example.jetsoncontroller.ui.model.Tone.WARNING, preparationActionTone(failed))
        val loadingAction = preparationPrimaryAction(loading, true, null, {}, {}, {})
        assertFalse(loadingAction.enabled)
        assertEquals(preparationActionReason(loading), loadingAction.disabledReason)
        // Presentation never opens the start gate after a failed lookup.
        assertFalse(preparationPrimaryAction(failed, true, null, {}, {}, {}).enabled)
    }

    @Test fun preparationUsesSharedStatusFooterAndOneProjectLabel() {
        val screen = r8UiSource("survey/SurveyRunScreen.kt")
        assertTrue(screen.contains("subtitleStatus = GeoStatusSpec(taskState.word, taskState.tone)"))
        assertFalse(screen.contains("GeoBadge(taskState.word"))
        assertFalse(screen.contains("GeoReadinessLine("))
        assertFalse(screen.contains("GeoSection(\"프로젝트\")"))
        assertTrue(screen.contains("GeoPickerField(\"프로젝트\""))
        assertTrue(screen.substringAfter("GeoFixedActionBar(primary").substringBefore("internal fun preparationPrimaryAction")
            .contains("preparationActionReason(state).takeIf { primary.enabled }"))
        assertTrue(screen.contains("takeUnless { !primary.enabled && it == primary.disabledReason }"))
    }

    @Test fun sectionStatesAndRadioRowsInheritGroupInsetsAndFirstLineAlignment() {
        val screen = r8UiSource("survey/SurveyRunScreen.kt")
        val sections = screen.substringAfter("GeoGroupedSection(\"구간\")").substringBefore("if (newSection)")
        assertTrue(sections.contains("GeoSkeleton(\"수집 구간을 불러오는 중\""))
        assertTrue(sections.contains("GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY"))
        assertTrue(sections.contains("GeoRadioRow(section.label"))
        assertFalse(sections.contains("padding(horizontal = 16.dp)"))
        assertTrue(r8UiSource("survey/TaskChoiceSheet.kt").contains("divider = index < state.pipelines.lastIndex"))
    }
}
