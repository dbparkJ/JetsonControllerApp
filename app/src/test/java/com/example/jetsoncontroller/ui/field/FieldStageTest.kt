package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.components.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 단계 판정은 순서가 곧 안전 정책이므로, 가장 비싼 오판부터 고정해 둡니다.
 *
 * 실기에서 재현하기 어려운 분기(응답 유실, 연결 단절 중 수집 지속)일수록 여기서 막아야
 * 합니다. 이 테스트는 장치 없이 JVM에서 돌아갑니다.
 */
class FieldStageTest {

    private val connectedIdle = FieldStageInput(
        connected = true,
        runStateConfirmed = true,
        registeredDeviceCount = 2
    )

    // ---- 순서 규칙 ---------------------------------------------------------

    @Test
    fun `결과 미확인 시작 요청은 다른 무엇보다 먼저 나온다`() {
        // 조사도 골랐고 점검도 통과했지만, 결과를 못 받은 요청이 있으면
        // 사용자를 '시작' 으로 보내면 안 된다. 같은 구간을 두 번 기록하게 된다.
        val input = connectedIdle.copy(
            surveySelected = true,
            preflightReady = true,
            pendingStartRequestId = "req_0941"
        )
        assertEquals(FieldStage.START_PENDING, judgeFieldStage(input))
        assertNotEquals(FieldStage.READY_TO_START, judgeFieldStage(input))
    }

    @Test
    fun `상태를 모르는 실행은 미연결보다 먼저 나온다`() {
        // 수집 중에 연결이 끊긴 경우. '장치에 연결하세요' 가 아니라
        // '상태를 확인하지 못했습니다' 가 맞다.
        val input = FieldStageInput(
            connected = false,
            unconfirmedRunId = "run_0941",
            registeredDeviceCount = 2
        )
        assertEquals(FieldStage.RUN_UNCONFIRMED, judgeFieldStage(input))
    }

    @Test
    fun `연결은 됐지만 실행 목록을 아직 못 받았으면 미확인이다`() {
        val input = connectedIdle.copy(runStateConfirmed = false, surveySelected = true)
        assertEquals(FieldStage.RUN_UNCONFIRMED, judgeFieldStage(input))
    }

    // ---- 평상시 흐름 -------------------------------------------------------

    @Test
    fun `아무것도 없으면 연결부터`() {
        assertEquals(
            FieldStage.NOT_CONNECTED,
            judgeFieldStage(FieldStageInput(registeredDeviceCount = 2))
        )
    }

    @Test
    fun `연결 후 조사 선택 점검 시작 순서로 진행한다`() {
        assertEquals(FieldStage.NEEDS_SURVEY, judgeFieldStage(connectedIdle))
        assertEquals(
            FieldStage.NEEDS_PREFLIGHT,
            judgeFieldStage(connectedIdle.copy(surveySelected = true))
        )
        assertEquals(
            FieldStage.READY_TO_START,
            judgeFieldStage(connectedIdle.copy(surveySelected = true, preflightReady = true))
        )
    }

    @Test
    fun `장치가 실행 중이라고 보고하면 수집 중이다`() {
        val input = connectedIdle.copy(surveySelected = true, activeRunId = "run_0941")
        assertEquals(FieldStage.COLLECTING, judgeFieldStage(input))
    }

    @Test
    fun `종료된 실행의 저장 결과가 미확인이면 결과 확인 단계다`() {
        val input = connectedIdle.copy(surveySelected = true, runAwaitingResultId = "run_0941")
        assertEquals(FieldStage.NEEDS_RESULT, judgeFieldStage(input))
    }

    // ---- 톤 규칙 -----------------------------------------------------------

    @Test
    fun `미확인은 성공으로도 실패로도 표시하지 않는다`() {
        val plan = fieldStagePlan(FieldStageInput(connected = true, unconfirmedRunId = "run_0941"))
        assertEquals(StatusTone.UNKNOWN, plan.tone)
        assertNotEquals(StatusTone.SUCCESS, plan.tone)
        assertNotEquals(StatusTone.ERROR, plan.tone)
    }

    @Test
    fun `결과 대기는 처리 중이지 성공이 아니다`() {
        val plan = fieldStagePlan(
            FieldStageInput(connected = true, runStateConfirmed = true, pendingStartRequestId = "req_1")
        )
        assertEquals(StatusTone.PENDING, plan.tone)
    }

    @Test
    fun `종료 후 결과 확인 단계도 성공 배지를 달지 않는다`() {
        val plan = fieldStagePlan(
            FieldStageInput(
                connected = true, runStateConfirmed = true,
                surveySelected = true, runAwaitingResultId = "run_0941"
            )
        )
        assertEquals(StatusTone.PENDING, plan.tone)
    }

    // ---- 문구 규칙 ---------------------------------------------------------

    @Test
    fun `모든 단계가 짧은 사용자 행동을 갖는다`() {
        val samples = listOf(
            FieldStageInput(registeredDeviceCount = 0),
            FieldStageInput(registeredDeviceCount = 3),
            connectedIdle,
            connectedIdle.copy(surveySelected = true),
            connectedIdle.copy(surveySelected = true, preflightReady = true),
            connectedIdle.copy(surveySelected = true, activeRunId = "r", elapsedLabel = "00:42:18"),
            connectedIdle.copy(surveySelected = true, runAwaitingResultId = "r"),
            connectedIdle.copy(pendingStartRequestId = "req"),
            FieldStageInput(connected = true, unconfirmedRunId = "r")
        )
        for (input in samples) {
            val plan = fieldStagePlan(input)
            assertTrue("actionLabel 비어 있음: ${plan.stage}", plan.actionLabel.isNotBlank())
            assertTrue("title 비어 있음: ${plan.stage}", plan.title.isNotBlank())
            assertFalse("내부 단계 번호 노출: ${plan.stage}", plan.eyebrow.contains("단계"))
            assertFalse("확인 전 전송 상태 단정: ${plan.stage}", plan.detail.contains("전송은 시작하지"))
        }
    }

    @Test
    fun `등록 장치가 없으면 연결이 아니라 등록을 안내한다`() {
        val plan = fieldStagePlan(FieldStageInput(registeredDeviceCount = 0))
        assertEquals(FieldDestination.CONNECT, plan.destination)
        assertTrue(plan.actionLabel.contains("등록"))
    }

    @Test
    fun `연결이 끊긴 미확인 상태는 재연결로 보낸다`() {
        val plan = fieldStagePlan(FieldStageInput(connected = false, unconfirmedRunId = "r"))
        assertEquals(FieldDestination.CONNECT, plan.destination)
    }

    @Test
    fun `연결된 미확인 상태는 실행 조회로 보낸다`() {
        val plan = fieldStagePlan(FieldStageInput(connected = true, unconfirmedRunId = "r"))
        assertEquals(FieldDestination.ACTIVE_RUN, plan.destination)
    }
}
