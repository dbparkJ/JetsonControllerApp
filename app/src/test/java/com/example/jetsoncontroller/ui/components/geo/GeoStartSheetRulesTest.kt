package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class GeoStartSheetRulesTest {
    private fun state(phase: GeoStartPhase = GeoStartPhase.PASSED) = GeoStartSheetState(
        "신월4거리 북측 보조 구간 2차 재수집", "수원시 도로대장", "GEO-X-현장지원차량-2", "구역 안", phase,
        listOf("연결", "장치 시간", "저장 공간", "카메라", "위치(GNSS)").map { GeoStartCheck(it, phase) }
    )

    @Test
    fun checkingIsBusyAndCannotStart() {
        val rules = startSheetRules(state(GeoStartPhase.CHECKING))
        assertTrue(rules.busy)
        assertFalse(rules.canStart)
        assertEquals("점검하는 중", rules.primaryLabel)
        assertFalse(rules.showRecheck)
    }

    @Test
    fun everyCheckMustPassBeforeStartIsEnabled() {
        val ready = state()
        assertTrue(startSheetRules(ready).canStart)
        assertFalse(startSheetRules(ready.copy(checks = emptyList())).canStart)
        ready.checks.indices.forEach { index ->
            val pending = ready.checks.mapIndexed { i, check -> if (i == index) check.copy(phase = GeoStartPhase.CHECKING) else check }
            assertFalse(startSheetRules(ready.copy(checks = pending)).canStart)
        }
    }

    @Test
    fun failedRowBlocksEvenWhenTheOverallFlagSaysPassed() {
        val ready = state()
        val failure = GeoStartCheck("저장 공간", GeoStartPhase.FAILED,
            reason = "저장 공간이 수집 설정의 최소값보다 적습니다 · 남은 공간 12 GB",
            remedy = GeoOverlayAction("장치 파일", {}))
        val failed = ready.copy(checks = ready.checks.map { if (it.label == "저장 공간") failure else it })
        val rules = startSheetRules(failed)
        assertFalse(rules.canStart)
        assertFalse(rules.busy)
        assertTrue(rules.showRecheck)
        assertEquals("수집 시작", rules.primaryLabel)
        assertEquals("점검을 통과해야 시작할 수 있습니다.", rules.blockingReason)
        assertEquals(ready.checks.map { it.label }, failed.checks.map { it.label })
    }

    @Test
    fun failedOverallStateIsNotOverriddenByOldPassedRows() {
        val rules = startSheetRules(state().copy(phase = GeoStartPhase.FAILED))
        assertFalse(rules.canStart)
        assertTrue(rules.showRecheck)
    }

    @Test
    fun changedTicketRequiresRecheckEvenAfterPassedPreflight() {
        val rules = startSheetRules(state().copy(ticketLinked = true, ticketChanged = true))
        assertFalse(rules.canStart)
        assertFalse(rules.busy)
        assertTrue(rules.showRecheck)
        assertEquals("GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요.", rules.blockingReason)
    }
}
