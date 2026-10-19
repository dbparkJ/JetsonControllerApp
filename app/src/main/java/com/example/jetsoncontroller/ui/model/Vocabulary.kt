package com.example.jetsoncontroller.ui.model

enum class Tone { INFO, SUCCESS, WARNING, ERROR, PENDING, UNKNOWN }

/** A visible state word paired with the tone used to render it. */
interface VocabularyEntry {
    val word: String
    val tone: Tone
}

enum class ConnectionState(override val word: String, override val tone: Tone) : VocabularyEntry {
    CONNECTED("연결됨", Tone.SUCCESS),
    DEFAULT_CONNECTION("기본 연결", Tone.WARNING),
    AVAILABLE("연결 가능", Tone.INFO),
    CHECKING("확인 중", Tone.PENDING),
    NO_RESPONSE("응답 없음", Tone.WARNING),
    OFFLINE("오프라인", Tone.UNKNOWN),
    AUTHENTICATION_REQUIRED("인증 필요", Tone.ERROR),
    PERMISSION_REQUIRED("권한 필요", Tone.WARNING)
}

enum class SensorState(override val word: String, override val tone: Tone) : VocabularyEntry {
    RECEIVING("수신 중", Tone.SUCCESS),
    WAITING("대기 중", Tone.INFO),
    DELAYED("지연", Tone.WARNING),
    DISCONNECTED("미연결", Tone.WARNING),
    NOT_CONFIGURED("미설정", Tone.UNKNOWN),
    UNKNOWN("미확인", Tone.UNKNOWN)
}

enum class PositionQuality(override val word: String, override val tone: Tone) : VocabularyEntry {
    PRECISE("정밀 위치", Tone.SUCCESS),
    APPROXIMATE("대략 위치", Tone.WARNING),
    NONE("위치 없음", Tone.ERROR),
    UNKNOWN("위치 미확인", Tone.UNKNOWN)
}

enum class ZoneState(override val word: String, override val tone: Tone) : VocabularyEntry {
    CHECKING_LOCATION("위치 확인 중", Tone.PENDING),
    INSIDE("구역 안", Tone.SUCCESS),
    NEAR_BOUNDARY("경계 근처", Tone.INFO),
    OUTSIDE("구역 밖", Tone.WARNING),
    UNKNOWN("위치 미확인", Tone.UNKNOWN)
}

enum class TicketSegmentState(override val word: String, override val tone: Tone) : VocabularyEntry {
    TODO("할 일", Tone.UNKNOWN),
    IN_PROGRESS("진행 중", Tone.INFO),
    BLOCKED("막힘", Tone.WARNING),
    COMPLETED("완료", Tone.SUCCESS),
    UNKNOWN("상태 미확인", Tone.UNKNOWN)
}

enum class CollectionState(override val word: String, override val tone: Tone) : VocabularyEntry {
    START_CHECKING("시작 확인 중", Tone.PENDING),
    COLLECTING("수집 중", Tone.INFO),
    STOP_CHECKING("종료 확인 중", Tone.PENDING),
    STOPPED("종료", Tone.SUCCESS),
    ABNORMAL_END("비정상 종료", Tone.WARNING),
    START_FAILED("시작하지 못함", Tone.ERROR),
    UNKNOWN("상태 미확인", Tone.UNKNOWN)
}

enum class DeviceStorageState(override val word: String, override val tone: Tone) : VocabularyEntry {
    CHECKING("확인 중", Tone.PENDING),
    VERIFIED("저장 확인", Tone.SUCCESS),
    RECHECK("다시 확인", Tone.WARNING),
    UNKNOWN("미확인", Tone.UNKNOWN)
}

enum class TransferState(override val word: String, override val tone: Tone) : VocabularyEntry {
    WAITING("대기 중", Tone.UNKNOWN),
    CHECKING_FILES("파일 확인 중", Tone.PENDING),
    SENDING("보내는 중", Tone.INFO),
    VERIFYING_RECEIPT("수신 확인 중", Tone.PENDING),
    RECEIVED("수신 확인", Tone.SUCCESS),
    CONTENT_MISMATCH("내용 불일치", Tone.ERROR),
    FAILED("실패", Tone.ERROR),
    CANCELLED("취소됨", Tone.UNKNOWN)
}

enum class CompletionReportState(override val word: String, override val tone: Tone) : VocabularyEntry {
    NOT_REPORTED("보고 전", Tone.UNKNOWN),
    WAITING("보고 대기", Tone.PENDING),
    REPORTED("보고함", Tone.SUCCESS),
    ALREADY_CHANGED("먼저 바뀜", Tone.WARNING),
    FAILED("보고 실패", Tone.ERROR)
}

enum class TaskState(override val word: String, override val tone: Tone) : VocabularyEntry {
    WAITING("대기", Tone.UNKNOWN),
    COLLECTING("수집 중", Tone.INFO),
    START_REQUESTED("시작 요청 중", Tone.PENDING),
    STOP_REQUESTED("중지 요청 중", Tone.PENDING),
    ALIGNING_TIME("시간 맞추는 중", Tone.PENDING),
    RETRYING("재시도 중", Tone.WARNING),
    FAILED("실패", Tone.ERROR),
    UNKNOWN("미확인", Tone.UNKNOWN)
}

enum class CorrectionRelayState(override val word: String, override val tone: Tone) : VocabularyEntry {
    RELAYING("중계 중", Tone.SUCCESS),
    CONNECTING("연결하는 중", Tone.PENDING),
    OFF("꺼짐", Tone.UNKNOWN),
    ERROR("오류", Tone.ERROR)
}

enum class ServerConnectionState(override val word: String, override val tone: Tone) : VocabularyEntry {
    CONNECTED("연결됨", Tone.SUCCESS),
    LOGIN_REQUIRED("다시 로그인 필요", Tone.WARNING),
    FAILED("연결 실패", Tone.ERROR),
    UNCHECKED("확인 전", Tone.UNKNOWN)
}

enum class ServerJobState(override val word: String, override val tone: Tone) : VocabularyEntry {
    RECEIVED("수신 완료", Tone.SUCCESS),
    SENDING("보내는 중", Tone.INFO),
    FAILED("실패", Tone.ERROR),
    TRASH("휴지통", Tone.UNKNOWN),
    DELETING("삭제 처리 중", Tone.PENDING)
}

enum class TrashItemState(override val word: String, override val tone: Tone) : VocabularyEntry {
    RESTORABLE("복원 가능", Tone.UNKNOWN),
    RESTORING("복원 중", Tone.PENDING),
    DELETING("삭제 처리 중", Tone.PENDING),
    UNRESTORABLE("복원할 수 없음", Tone.WARNING)
}
