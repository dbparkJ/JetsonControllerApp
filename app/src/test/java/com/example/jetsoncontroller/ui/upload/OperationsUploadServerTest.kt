package com.example.jetsoncontroller.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsUploadServerTest {
    @Test
    fun productionReceiverUsesPublicHttpsEndpoint() {
        assertEquals("operations", OperationsUploadServer.TARGET_ID)
        assertTrue(OperationsUploadServer.BASE_URL.startsWith("https://"))
        assertTrue(isValidHttpsUrl(OperationsUploadServer.BASE_URL))
    }
}
