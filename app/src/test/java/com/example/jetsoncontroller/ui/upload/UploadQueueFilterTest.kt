package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadQueueFilterTest {
    @Test
    fun `terminal history entries are excluded from the active queue`() {
        val jobs = UploadJobState.entries.map { state -> uploadJob(state) }

        assertEquals(
            listOf(
                UploadJobState.QUEUED,
                UploadJobState.SCANNING,
                UploadJobState.UPLOADING
            ),
            filterActiveUploadJobs(jobs).map { it.state }
        )
    }

    private fun uploadJob(state: UploadJobState) = UploadJob(
        id = state.name,
        rootId = "data",
        relativePath = "capture",
        targetId = "server",
        state = state,
        bytesTotal = null,
        bytesTransferred = null,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = null
    )
}
