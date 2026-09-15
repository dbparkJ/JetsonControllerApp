package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.data.server.ServerJob
import com.example.jetsoncontroller.data.server.ServerReceipt
import com.example.jetsoncontroller.data.server.ServerTrashResponse
import com.example.jetsoncontroller.model.TrashEntriesResponse
import com.example.jetsoncontroller.model.EmptyTrashResponse
import com.example.jetsoncontroller.model.UploadJob
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P0StorageContractTest {
    private val gson = Gson()

    @Test fun `receiver job and receipt keep access project separate from survey context`() {
        val job = gson.fromJson("""{"sessionId":"s","clientJobId":"j","projectId":"access-a",
          "accessProjectId":"access-a","deviceId":"device-a","sourceName":"source","state":"COMPLETED",
          "failureCode":null,"totalBytes":10,"receivedBytes":10,"fileCount":1,"createdAt":"c","updatedAt":"u",
          "completedAt":"done","surveyContext":$CONTEXT,"pathSummary":{"rootEntries":[],
          "rootEntriesTruncated":false,"imageCount":1,"videoCount":0}}""", ServerJob::class.java)
        val receipt = gson.fromJson("""{"serverEnvironment":"production","projectId":"access-a",
          "accessProjectId":"access-a","sessionId":"s","clientJobId":"j","sourceName":"source",
          "folderName":"folder","state":"COMPLETED","totalBytes":10,"fileCount":1,"contentSha256":"hash",
          "completedAt":"done","matched":true,"verifiedAt":"verified","refreshedAt":"refreshed",
          "surveyContext":$CONTEXT}""", ServerReceipt::class.java)

        assertEquals("access-a", job.accessProjectId)
        assertEquals("survey-a", job.surveyContext!!.surveyProjectId)
        assertEquals("access-a", receipt.accessProjectId)
        assertEquals("section-a", receipt.surveyContext!!.surveySectionId)
    }

    @Test fun `local trash and uploaded source recovery fields decode`() {
        val trash = gson.fromJson("""{"entries":[{"trashId":"trash-a","category":"RUN_HISTORY",
          "state":"TRASHED","rootId":"pipeline-logs","relativePath":"capture/run.log","name":"run.log",
          "entryType":"BUNDLE","trashedAt":"time","restoredAt":null,"updatedAt":"time","lastError":null,
          "metadata":{"pipelineId":"capture"},"audit":[{"event":"TRASH_CONFIRMED"}],
          "restoreSupported":true,"purgeSupported":true}],"refreshedAt":"now","emptySupported":true}""", TrashEntriesResponse::class.java)
        val upload = gson.fromJson("""{"id":"job","rootId":"collections","relativePath":"output",
          "targetId":"server","state":"COMPLETED","sourceDeleted":false,"deletionEligible":false,
          "sourceTrashId":"trash-source","sourceTrashedAt":"time","sourceRecoverable":true,"sourceDeletionState":"TRASHED",
          "sourceIdentity":"identity-sha256","context":$CONTEXT}""", UploadJob::class.java)

        assertTrue(trash.entries.single().restoreSupported)
        assertTrue(trash.entries.single().purgeSupported)
        assertTrue(trash.emptySupported)
        assertEquals("RUN_HISTORY", trash.entries.single().category)
        assertFalse(upload.sourceDeleted)
        assertTrue(upload.sourceRecoverable)
        assertEquals("TRASHED", upload.sourceDeletionState)
        assertEquals("identity-sha256", upload.sourceIdentity)
        assertEquals("run-a", upload.context!!.runId)
    }

    @Test fun `local empty trash receipt decodes per item states`() {
        val response = gson.fromJson(
            """{"results":[{"trashId":"a","state":"PURGED"},{"trashId":"b","state":"FAILED","error":"busy"}],"refreshedAt":"now"}""",
            EmptyTrashResponse::class.java
        )

        assertEquals(listOf("PURGED", "FAILED"), response.results.map { it.state })
        assertEquals("busy", response.results.last().error)
    }

    @Test fun `legacy server trash keeps restore compatible and empty unavailable`() {
        val response = gson.fromJson(
            """{"serverEnvironment":"production","projectId":"project","jobs":[{"sessionId":"s","clientJobId":"j","sourceName":"capture","totalBytes":1,"fileCount":1,"state":"TRASHED","trashedAt":"now"}],"refreshedAt":"now"}""",
            ServerTrashResponse::class.java
        )

        assertEquals(null, response.jobs.single().restoreSupported)
        assertEquals(null, response.jobs.single().purgeSupported)
        assertFalse(response.emptySupported)
        assertEquals(0, response.total)
    }

    private companion object {
        const val CONTEXT = """{"schemaVersion":1,"surveyProjectId":"survey-a",
          "surveySectionId":"section-a","runId":"run-a","deviceId":"device-a","pipelineId":"capture",
          "sourceRevision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "configSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "outputId":"output-a","createdAt":"time"}"""
    }
}
