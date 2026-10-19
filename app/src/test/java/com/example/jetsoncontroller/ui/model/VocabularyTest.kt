package com.example.jetsoncontroller.ui.model

import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularyTest {
    @Test
    fun `connection words have the specified tones`() = assertVocabulary(
        ConnectionState.values().toList(),
        listOf(
            "연결됨" to Tone.SUCCESS,
            "기본 연결" to Tone.WARNING,
            "연결 가능" to Tone.INFO,
            "확인 중" to Tone.PENDING,
            "응답 없음" to Tone.WARNING,
            "오프라인" to Tone.UNKNOWN,
            "인증 필요" to Tone.ERROR,
            "권한 필요" to Tone.WARNING
        )
    )

    @Test
    fun `sensor position and zone words have the specified tones`() {
        assertVocabulary(
            SensorState.values().toList(),
            listOf(
                "수신 중" to Tone.SUCCESS,
                "대기 중" to Tone.INFO,
                "지연" to Tone.WARNING,
                "미연결" to Tone.WARNING,
                "미설정" to Tone.UNKNOWN,
                "미확인" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            PositionQuality.values().toList(),
            listOf(
                "정밀 위치" to Tone.SUCCESS,
                "대략 위치" to Tone.WARNING,
                "위치 없음" to Tone.ERROR,
                "위치 미확인" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            ZoneState.values().toList(),
            listOf(
                "위치 확인 중" to Tone.PENDING,
                "구역 안" to Tone.SUCCESS,
                "경계 근처" to Tone.INFO,
                "구역 밖" to Tone.WARNING,
                "위치 미확인" to Tone.UNKNOWN
            )
        )
    }

    @Test
    fun `ticket segment collection and device storage words have the specified tones`() {
        assertVocabulary(
            TicketSegmentState.values().toList(),
            listOf(
                "할 일" to Tone.UNKNOWN,
                "진행 중" to Tone.INFO,
                "막힘" to Tone.WARNING,
                "완료" to Tone.SUCCESS,
                "상태 미확인" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            CollectionState.values().toList(),
            listOf(
                "시작 확인 중" to Tone.PENDING,
                "수집 중" to Tone.INFO,
                "종료 확인 중" to Tone.PENDING,
                "종료" to Tone.SUCCESS,
                "비정상 종료" to Tone.WARNING,
                "시작하지 못함" to Tone.ERROR,
                "상태 미확인" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            DeviceStorageState.values().toList(),
            listOf(
                "확인 중" to Tone.PENDING,
                "저장 확인" to Tone.SUCCESS,
                "다시 확인" to Tone.WARNING,
                "미확인" to Tone.UNKNOWN
            )
        )
    }

    @Test
    fun `transfer and completion report words have the specified tones`() {
        assertVocabulary(
            TransferState.values().toList(),
            listOf(
                "대기 중" to Tone.UNKNOWN,
                "파일 확인 중" to Tone.PENDING,
                "보내는 중" to Tone.INFO,
                "수신 확인 중" to Tone.PENDING,
                "수신 확인" to Tone.SUCCESS,
                "내용 불일치" to Tone.ERROR,
                "실패" to Tone.ERROR,
                "취소됨" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            CompletionReportState.values().toList(),
            listOf(
                "보고 전" to Tone.UNKNOWN,
                "보고 대기" to Tone.PENDING,
                "보고함" to Tone.SUCCESS,
                "먼저 바뀜" to Tone.WARNING,
                "보고 실패" to Tone.ERROR
            )
        )
    }

    @Test
    fun `task relay and server words have the specified tones`() {
        assertVocabulary(
            TaskState.values().toList(),
            listOf(
                "대기" to Tone.UNKNOWN,
                "수집 중" to Tone.INFO,
                "시작 요청 중" to Tone.PENDING,
                "중지 요청 중" to Tone.PENDING,
                "시간 맞추는 중" to Tone.PENDING,
                "재시도 중" to Tone.WARNING,
                "실패" to Tone.ERROR,
                "미확인" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            CorrectionRelayState.values().toList(),
            listOf(
                "중계 중" to Tone.SUCCESS,
                "연결하는 중" to Tone.PENDING,
                "꺼짐" to Tone.UNKNOWN,
                "오류" to Tone.ERROR
            )
        )
        assertVocabulary(
            ServerConnectionState.values().toList(),
            listOf(
                "연결됨" to Tone.SUCCESS,
                "다시 로그인 필요" to Tone.WARNING,
                "연결 실패" to Tone.ERROR,
                "확인 전" to Tone.UNKNOWN
            )
        )
        assertVocabulary(
            ServerJobState.values().toList(),
            listOf(
                "수신 완료" to Tone.SUCCESS,
                "보내는 중" to Tone.INFO,
                "실패" to Tone.ERROR,
                "휴지통" to Tone.UNKNOWN,
                "삭제 처리 중" to Tone.PENDING
            )
        )
        assertVocabulary(
            TrashItemState.values().toList(),
            listOf(
                "복원 가능" to Tone.UNKNOWN,
                "복원 중" to Tone.PENDING,
                "삭제 처리 중" to Tone.PENDING,
                "복원할 수 없음" to Tone.WARNING
            )
        )
    }

    private fun assertVocabulary(actual: List<VocabularyEntry>, expected: List<Pair<String, Tone>>) {
        assertEquals(expected, actual.map { it.word to it.tone })
    }
}
