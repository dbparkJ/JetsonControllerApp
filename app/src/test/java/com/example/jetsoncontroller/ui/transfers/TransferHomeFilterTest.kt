package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferHomeFilterTest {
    @Test fun `in flight filter includes queued records and receipts awaiting verification`() {
        val state = empty().copy(
            queued = listOf(TransferRowState("queued", "대기 자료", "대기 중", Tone.UNKNOWN)),
            awaitingVerification = listOf(TransferRowState("receipt", "보낸 자료", "수신 확인 중", Tone.PENDING)))
        assertEquals(2, transferHomeVisibleCount(state, 1))
        assertEquals(0, transferHomeVisibleCount(state, 4))
    }

    @Test fun `one hundred percent progress cannot enter verified receipts`() {
        val state = empty().copy(active = listOf(ActiveTransferState("active", "수집 자료", "서버", "보내는 중",
            Tone.INFO, 1f, "100 / 100 MB", null, null, true)))
        assertEquals(1, transferHomeVisibleCount(state, 1))
        assertEquals(0, transferHomeVisibleCount(state, 4))
    }

    @Test fun `failed and sendable categories remain independently visible`() {
        val state = empty().copy(
            failed = listOf(FailedTransferState("failed", "보낸 자료", "실패", "연결 끊김")),
            sendableRuns = listOf(StoredRunTransferState("run", "수집 자료", "보관 중")))
        assertEquals(1, transferHomeVisibleCount(state, 2))
        assertEquals(1, transferHomeVisibleCount(state, 3))
        assertEquals(2, transferHomeVisibleCount(state, 0))
    }

    private fun empty() = TransfersHomeUiState("", Tone.UNKNOWN, null,
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
}
