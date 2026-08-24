package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadLibrarySession
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `only terminal upload records can be removed from history`() {
        assertFalse(isDeletableUploadJob(uploadJob(UploadJobState.QUEUED)))
        assertFalse(isDeletableUploadJob(uploadJob(UploadJobState.SCANNING)))
        assertFalse(isDeletableUploadJob(uploadJob(UploadJobState.UPLOADING)))
        assertTrue(isDeletableUploadJob(uploadJob(UploadJobState.COMPLETED)))
        assertTrue(isDeletableUploadJob(uploadJob(UploadJobState.FAILED)))
        assertTrue(isDeletableUploadJob(uploadJob(UploadJobState.CANCELLED)))
    }

    @Test
    fun `a stale full refresh cannot restore a locally deleted queue record`() {
        val deleted = uploadJob(UploadJobState.COMPLETED, id = "deleted")
        val retained = uploadJob(UploadJobState.FAILED, id = "retained")

        val visible = filterDeletedUploadJobs(
            jobs = listOf(deleted, retained),
            deletedJobIds = setOf(deleted.id)
        )

        assertEquals(listOf("retained"), visible.map { it.id })
    }

    @Test
    fun `active polling updates progress without dropping terminal history`() {
        val completed = uploadJob(UploadJobState.COMPLETED, id = "completed")
        val stale = uploadJob(
            UploadJobState.UPLOADING,
            id = "active",
            bytesTransferred = 10
        )
        val refreshed = stale.copy(bytesTransferred = 50)
        val newlyObserved = uploadJob(UploadJobState.QUEUED, id = "new")

        val merged = mergeActiveUploadJobs(
            history = listOf(stale, completed),
            activeJobs = listOf(newlyObserved, refreshed)
        )

        assertEquals(listOf("new", "active", "completed"), merged.map { it.id })
        assertEquals(50L, merged.first { it.id == "active" }.bytesTransferred)
        assertEquals(UploadJobState.COMPLETED, merged.last().state)
    }

    @Test
    fun `source summary must match the exact requested root and path`() {
        val summary = UploadSourceSummary(
            rootId = "data",
            relativePath = "capture/day-1",
            sourceName = "day-1",
            folderName = "day-1",
            sourceType = "DIRECTORY",
            bytesTotal = 1024,
            filesTotal = 1
        )

        assertTrue(summary.matchesUploadSource("data", "capture/day-1"))
        assertFalse(summary.matchesUploadSource("data", "capture/day-2"))
        assertFalse(summary.matchesUploadSource("other", "capture/day-1"))
        assertFalse(null.matchesUploadSource("data", "capture/day-1"))
    }

    @Test
    fun `completed mismatch starts a fresh reupload while failed job uses retry`() {
        val completed = uploadJob(UploadJobState.COMPLETED)
        val mismatch = UploadVerification(
            jobId = completed.id,
            state = "MISMATCH",
            matched = false,
            deletionAllowed = false
        )

        assertTrue(canStartFreshReupload(completed, mismatch))
        assertFalse(canStartFreshReupload(completed, mismatch.copy(matched = true)))
        assertFalse(
            canStartFreshReupload(
                uploadJob(UploadJobState.FAILED),
                mismatch
            )
        )
    }

    @Test
    fun `server library prefers explicit folder name`() {
        val session = UploadLibrarySession(
            sessionId = "session",
            sourceName = "legacy-source",
            totalBytes = 1,
            fileCount = 1,
            folderName = "capture-folder"
        )

        assertEquals("capture-folder", session.displayFolderName)
        assertEquals("legacy-source", session.copy(folderName = null).displayFolderName)
    }

    private fun uploadJob(
        state: UploadJobState,
        id: String = state.name,
        bytesTransferred: Long? = null
    ) = UploadJob(
        id = id,
        rootId = "data",
        relativePath = "capture",
        targetId = "server",
        state = state,
        bytesTotal = null,
        bytesTransferred = bytesTransferred,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = null
    )
}
