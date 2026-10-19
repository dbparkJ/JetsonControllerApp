package com.example.jetsoncontroller.ui.storage

import org.junit.Assert.*
import org.junit.Test

class StoragePreviewTest {
    @Test fun `UTF8 text includes Korean and an empty file`() {
        assertEquals("수집 로그\n정상", previewText("수집 로그\n정상".toByteArray(Charsets.UTF_8)))
        assertEquals("", previewText(byteArrayOf()))
    }

    @Test fun `binary data and malformed UTF8 are unsupported`() {
        assertNull(previewText(byteArrayOf(65, 0, 66)))
        assertNull(previewText(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test fun `text is supported through twelve MiB inclusive`() {
        assertEquals(MAX_TEXT_PREVIEW_BYTES, previewText(ByteArray(MAX_TEXT_PREVIEW_BYTES) { 65 })?.length)
        assertNull(previewText(ByteArray(MAX_TEXT_PREVIEW_BYTES + 1) { 65 }))
    }

    @Test fun `large images are sampled without changing small previews`() {
        assertEquals(1, calculatePreviewSampleSize(800, 600))
        assertEquals(4, calculatePreviewSampleSize(8000, 6000))
        assertEquals(8, calculatePreviewSampleSize(16000, 1))
    }
}
