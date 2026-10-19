package com.example.jetsoncontroller.ui.model

import com.example.jetsoncontroller.ui.components.StatusTone

/** A visible state word paired with the tone used to render it. */
interface VocabularyEntry {
    val word: String
    val tone: StatusTone
}

enum class ConnectionState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    CONNECTED("연결됨", StatusTone.SUCCESS),
    DEFAULT_CONNECTION("기본 연결", StatusTone.WARNING),
    AVAILABLE("연결 가능", StatusTone.INFO),
    CHECKING("확인 중", StatusTone.PENDING),
    NO_RESPONSE("응답 없음", StatusTone.WARNING),
    OFFLINE("오프라인", StatusTone.UNKNOWN),
    AUTHENTICATION_REQUIRED("인증 필요", StatusTone.ERROR),
    PERMISSION_REQUIRED("권한 필요", StatusTone.WARNING)
}

enum class SensorState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    RECEIVING("수신 중", StatusTone.SUCCESS),
    WAITING("대기 중", StatusTone.INFO),
    DELAYED("지연", StatusTone.WARNING),
    DISCONNECTED("미연결", StatusTone.WARNING),
    NOT_CONFIGURED("미설정", StatusTone.UNKNOWN),
    UNKNOWN("미확인", StatusTone.UNKNOWN)
}

enum class PositionQuality(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    PRECISE("정밀 위치", StatusTone.SUCCESS),
    APPROXIMATE("대략 위치", StatusTone.WARNING),
    NONE("위치 없음", StatusTone.ERROR),
    UNKNOWN("위치 미확인", StatusTone.UNKNOWN)
}

enum class ZoneState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    CHECKING_LOCATION("위치 확인 중", StatusTone.PENDING),
    INSIDE("구역 안", StatusTone.SUCCESS),
    NEAR_BOUNDARY("경계 근처", StatusTone.INFO),
    OUTSIDE("구역 밖", StatusTone.WARNING),
    UNKNOWN("위치 미확인", StatusTone.UNKNOWN)
}

enum class TicketSegmentState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    TODO("할 일", StatusTone.UNKNOWN),
    IN_PROGRESS("진행 중", StatusTone.INFO),
    BLOCKED("막힘", StatusTone.WARNING),
    COMPLETED("완료", StatusTone.SUCCESS),
    UNKNOWN("상태 미확인", StatusTone.UNKNOWN)
}

enum class CollectionState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    START_CHECKING("시작 확인 중", StatusTone.PENDING),
    COLLECTING("수집 중", StatusTone.INFO),
    STOP_CHECKING("종료 확인 중", StatusTone.PENDING),
    STOPPED("종료", StatusTone.SUCCESS),
    ABNORMAL_END("비정상 종료", StatusTone.WARNING),
    START_FAILED("시작하지 못함", StatusTone.ERROR),
    UNKNOWN("상태 미확인", StatusTone.UNKNOWN)
}

enum class DeviceStorageState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    CHECKING("확인 중", StatusTone.PENDING),
    VERIFIED("저장 확인", StatusTone.SUCCESS),
    RECHECK("다시 확인", StatusTone.WARNING),
    UNKNOWN("미확인", StatusTone.UNKNOWN)
}

enum class TransferState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    WAITING("대기 중", StatusTone.UNKNOWN),
    CHECKING_FILES("파일 확인 중", StatusTone.PENDING),
    SENDING("보내는 중", StatusTone.INFO),
    VERIFYING_RECEIPT("수신 확인 중", StatusTone.PENDING),
    RECEIVED("수신 확인", StatusTone.SUCCESS),
    CONTENT_MISMATCH("내용 불일치", StatusTone.ERROR),
    FAILED("실패", StatusTone.ERROR),
    CANCELLED("취소됨", StatusTone.UNKNOWN)
}

enum class CompletionReportState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    NOT_REPORTED("보고 전", StatusTone.UNKNOWN),
    WAITING("보고 대기", StatusTone.PENDING),
    REPORTED("보고함", StatusTone.SUCCESS),
    ALREADY_CHANGED("먼저 바뀜", StatusTone.WARNING),
    FAILED("보고 실패", StatusTone.ERROR)
}

enum class TaskState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    WAITING("대기", StatusTone.UNKNOWN),
    COLLECTING("수집 중", StatusTone.INFO),
    START_REQUESTED("시작 요청 중", StatusTone.PENDING),
    STOP_REQUESTED("중지 요청 중", StatusTone.PENDING),
    ALIGNING_TIME("시간 맞추는 중", StatusTone.PENDING),
    RETRYING("재시도 중", StatusTone.WARNING),
    FAILED("실패", StatusTone.ERROR),
    UNKNOWN("미확인", StatusTone.UNKNOWN)
}

enum class CorrectionRelayState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    RELAYING("중계 중", StatusTone.SUCCESS),
    CONNECTING("연결하는 중", StatusTone.PENDING),
    OFF("꺼짐", StatusTone.UNKNOWN),
    ERROR("오류", StatusTone.ERROR)
}

enum class ServerConnectionState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    CONNECTED("연결됨", StatusTone.SUCCESS),
    LOGIN_REQUIRED("다시 로그인 필요", StatusTone.WARNING),
    FAILED("연결 실패", StatusTone.ERROR),
    UNCHECKED("확인 전", StatusTone.UNKNOWN)
}

enum class ServerJobState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    RECEIVED("수신 완료", StatusTone.SUCCESS),
    SENDING("보내는 중", StatusTone.INFO),
    FAILED("실패", StatusTone.ERROR),
    TRASH("휴지통", StatusTone.UNKNOWN),
    DELETING("삭제 처리 중", StatusTone.PENDING)
}

enum class TrashItemState(override val word: String, override val tone: StatusTone) : VocabularyEntry {
    RESTORABLE("복원 가능", StatusTone.UNKNOWN),
    RESTORING("복원 중", StatusTone.PENDING),
    DELETING("삭제 처리 중", StatusTone.PENDING),
    UNRESTORABLE("복원할 수 없음", StatusTone.WARNING)
}
