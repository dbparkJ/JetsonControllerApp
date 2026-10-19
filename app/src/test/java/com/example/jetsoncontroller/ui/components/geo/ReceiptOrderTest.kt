package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptOrderTest {
    private val done = GeoReceiptItem("수신 확인", Tone.SUCCESS, done = true)

    @Test
    fun `the primary follows the first incomplete step in the four step chain`() {
        assertEquals(GeoReceiptStep.COLLECTION, GeoReceiptState().firstIncomplete())
        assertEquals(GeoReceiptStep.STORAGE, GeoReceiptState(collection = done).firstIncomplete())
        assertEquals(GeoReceiptStep.SERVER, GeoReceiptState(collection = done, storage = done).firstIncomplete())
        assertEquals(GeoReceiptStep.REPORT, GeoReceiptState(collection = done, storage = done, server = done).firstIncomplete())
        assertNull(GeoReceiptState(done, done, done, done).firstIncomplete())
    }

    @Test
    fun `a success tone alone does not assert completion`() {
        val state = GeoReceiptState(collection = GeoReceiptItem("종료", Tone.SUCCESS, done = false), storage = done)
        assertEquals(GeoReceiptStep.COLLECTION, state.firstIncomplete())
    }

    @Test
    fun `receipt labels stay in the specified order`() {
        assertEquals(listOf("수집 종료", "장치 저장", "서버 수신", "GEO& Work 보고"),
            GeoReceiptState().rows().map { it.first.title })
    }
}
