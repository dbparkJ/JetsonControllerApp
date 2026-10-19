package com.example.jetsoncontroller.ui.model

import com.example.jetsoncontroller.ui.components.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularyTest {
    @Test
    fun `connection words have the specified tones`() = assertVocabulary(
        ConnectionState.values().toList(),
        listOf(
            "연결됨" to StatusTone.SUCCESS,
            "기본 연결" to StatusTone.WARNING,
            "연결 가능" to StatusTone.INFO,
            "확인 중" to StatusTone.PENDING,
            "응답 없음" to StatusTone.WARNING,
            "오프라인" to StatusTone.UNKNOWN,
            "인증 필요" to StatusTone.ERROR,
            "권한 필요" to StatusTone.WARNING
        )
    )

    @Test
    fun `sensor position and zone words have the specified tones`() {
        assertVocabulary(
            SensorState.values().toList(),
            listOf(
                "수신 중" to StatusTone.SUCCESS,
                "대기 중" to StatusTone.INFO,
                "지연" to StatusTone.WARNING,
                "미연결" to StatusTone.WARNING,
                "미설정" to StatusTone.UNKNOWN,
                "미확인" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            PositionQuality.values().toList(),
            listOf(
                "정밀 위치" to StatusTone.SUCCESS,
                "대략 위치" to StatusTone.WARNING,
                "위치 없음" to StatusTone.ERROR,
                "위치 미확인" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            ZoneState.values().toList(),
            listOf(
                "위치 확인 중" to StatusTone.PENDING,
                "구역 안" to StatusTone.SUCCESS,
                "경계 근처" to StatusTone.INFO,
                "구역 밖" to StatusTone.WARNING,
                "위치 미확인" to StatusTone.UNKNOWN
            )
        )
    }

    @Test
    fun `ticket segment collection and device storage words have the specified tones`() {
        assertVocabulary(
            TicketSegmentState.values().toList(),
            listOf(
                "할 일" to StatusTone.UNKNOWN,
                "진행 중" to StatusTone.INFO,
                "막힘" to StatusTone.WARNING,
                "완료" to StatusTone.SUCCESS,
                "상태 미확인" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            CollectionState.values().toList(),
            listOf(
                "시작 확인 중" to StatusTone.PENDING,
                "수집 중" to StatusTone.INFO,
                "종료 확인 중" to StatusTone.PENDING,
                "종료" to StatusTone.SUCCESS,
                "비정상 종료" to StatusTone.WARNING,
                "시작하지 못함" to StatusTone.ERROR,
                "상태 미확인" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            DeviceStorageState.values().toList(),
            listOf(
                "확인 중" to StatusTone.PENDING,
                "저장 확인" to StatusTone.SUCCESS,
                "다시 확인" to StatusTone.WARNING,
                "미확인" to StatusTone.UNKNOWN
            )
        )
    }

    @Test
    fun `transfer and completion report words have the specified tones`() {
        assertVocabulary(
            TransferState.values().toList(),
            listOf(
                "대기 중" to StatusTone.UNKNOWN,
                "파일 확인 중" to StatusTone.PENDING,
                "보내는 중" to StatusTone.INFO,
                "수신 확인 중" to StatusTone.PENDING,
                "수신 확인" to StatusTone.SUCCESS,
                "내용 불일치" to StatusTone.ERROR,
                "실패" to StatusTone.ERROR,
                "취소됨" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            CompletionReportState.values().toList(),
            listOf(
                "보고 전" to StatusTone.UNKNOWN,
                "보고 대기" to StatusTone.PENDING,
                "보고함" to StatusTone.SUCCESS,
                "먼저 바뀜" to StatusTone.WARNING,
                "보고 실패" to StatusTone.ERROR
            )
        )
    }

    @Test
    fun `task relay and server words have the specified tones`() {
        assertVocabulary(
            TaskState.values().toList(),
            listOf(
                "대기" to StatusTone.UNKNOWN,
                "수집 중" to StatusTone.INFO,
                "시작 요청 중" to StatusTone.PENDING,
                "중지 요청 중" to StatusTone.PENDING,
                "시간 맞추는 중" to StatusTone.PENDING,
                "재시도 중" to StatusTone.WARNING,
                "실패" to StatusTone.ERROR,
                "미확인" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            CorrectionRelayState.values().toList(),
            listOf(
                "중계 중" to StatusTone.SUCCESS,
                "연결하는 중" to StatusTone.PENDING,
                "꺼짐" to StatusTone.UNKNOWN,
                "오류" to StatusTone.ERROR
            )
        )
        assertVocabulary(
            ServerConnectionState.values().toList(),
            listOf(
                "연결됨" to StatusTone.SUCCESS,
                "다시 로그인 필요" to StatusTone.WARNING,
                "연결 실패" to StatusTone.ERROR,
                "확인 전" to StatusTone.UNKNOWN
            )
        )
        assertVocabulary(
            ServerJobState.values().toList(),
            listOf(
                "수신 완료" to StatusTone.SUCCESS,
                "보내는 중" to StatusTone.INFO,
                "실패" to StatusTone.ERROR,
                "휴지통" to StatusTone.UNKNOWN,
                "삭제 처리 중" to StatusTone.PENDING
            )
        )
        assertVocabulary(
            TrashItemState.values().toList(),
            listOf(
                "복원 가능" to StatusTone.UNKNOWN,
                "복원 중" to StatusTone.PENDING,
                "삭제 처리 중" to StatusTone.PENDING,
                "복원할 수 없음" to StatusTone.WARNING
            )
        )
    }

    private fun assertVocabulary(actual: List<VocabularyEntry>, expected: List<Pair<String, StatusTone>>) {
        assertEquals(expected, actual.map { it.word to it.tone })
    }
}
