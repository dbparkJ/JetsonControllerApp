package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.data.server.ServerRequestException
import com.example.jetsoncontroller.data.server.ServerJobsSource
import com.example.jetsoncontroller.data.server.ServerJob
import com.example.jetsoncontroller.data.server.ServerPathSummary
import com.example.jetsoncontroller.data.server.ServerJobsSnapshot
import com.example.jetsoncontroller.data.server.ServerJobsResponse
import com.example.jetsoncontroller.data.server.ServerTrashJob
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.model.TrashEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class DirectServerPresentationTest {
    @Test
    fun operatorMessageHidesEndpointAndCredentialInstructions() {
        val raw = "저장된 토큰이 https://uploads.example.com 에서 거절되었습니다."

        val message = directServerOperatorMessage(raw)

        assertTrue(message.contains("관리자"))
        assertFalse(message.contains("토큰"))
        assertFalse(message.contains("https://"))
        assertFalse(directServerOperatorMessage("Connection refused at 10.0.0.8/api/jobs")
            .contains("10.0.0.8"))
    }

    @Test fun `auth errors point to saved employee credentials`() {
        val error = ServerRequestException(401)
        assertEquals("프로필 인증 확인", directServerRecoveryAction(error))
        assertTrue(directServerErrorMessage(error).contains("토큰"))
    }

    @Test fun `project mismatch never appears as generic offline`() {
        val error = IllegalStateException("Server project mismatch")
        assertEquals("프로젝트 권한 확인", directServerRecoveryAction(error))
        assertTrue(directServerErrorMessage(error).contains("프로젝트"))
    }

    @Test fun `only availability failures may attempt offline cache fallback`() {
        assertTrue(directServerAvailabilityFailure(IOException("offline")))
        assertTrue(directServerAvailabilityFailure(ServerRequestException(503)))
        assertTrue(!directServerAvailabilityFailure(ServerRequestException(403)))
        assertTrue(!directServerAvailabilityFailure(SSLHandshakeException("bad cert")))
        assertTrue(directServerSecurityFailure(SSLHandshakeException("bad cert")))
    }

    @Test fun `large image preview is decoded with a bounded sample`() {
        assertEquals(1, calculatePreviewSampleSize(1024, 2048))
        assertEquals(4, calculatePreviewSampleSize(8000, 6000))
    }

    @Test fun `profile switch clears cached views and old undo target`() {
        val old = DirectServerUiState(
            selectedProfileId = "production",
            jobs = listOf(ServerJob(
                "session", "client", "project", "device", "capture", "COMPLETED", null,
                1, 1, 1, "created", "updated", "completed",
                ServerPathSummary(emptyList(), false, 0, 0)
            )),
            jobsSource = ServerJobsSource.CACHE,
            refreshedAt = "old",
            currentPath = "camera",
            undoSessionId = "session"
        )

        val switched = old.forProfile("test")

        assertEquals("test", switched.selectedProfileId)
        assertTrue(switched.jobs.isEmpty())
        assertEquals(null, switched.jobsSource)
        assertEquals(null, switched.refreshedAt)
        assertEquals("", switched.currentPath)
        assertEquals(null, switched.undoSessionId)
    }

    @Test fun `jobs refresh preserves a confirmed trash undo target`() {
        val state = DirectServerUiState(
            undoSessionId = "session",
            mutationMessage = "서버 작업을 휴지통으로 이동했습니다."
        )
        val snapshot = ServerJobsSnapshot(
            response = ServerJobsResponse(
                serverEnvironment = "production",
                employeeId = "employee",
                projectId = "project",
                jobs = emptyList(),
                nextOffset = null,
                refreshedAt = "now"
            ),
            source = ServerJobsSource.NETWORK,
            cachedAtEpochMillis = 1
        )

        val refreshed = state.withJobsSnapshot(snapshot, refreshError = null)

        assertEquals("session", refreshed.undoSessionId)
        assertTrue(refreshed.mutationMessage.orEmpty().contains("휴지통"))
    }

    @Test fun `only receiver operator roles enable trash mutations`() {
        assertTrue(directServerRoleCanMutate("OPERATOR"))
        assertTrue(directServerRoleCanMutate("admin"))
        assertTrue(!directServerRoleCanMutate("VIEWER"))
        assertTrue(!directServerRoleCanMutate(null))
    }

    @Test fun `late request cannot publish after a newer scoped request starts`() {
        val generation = DirectServerRequestGeneration()
        val oldProfileRequest = generation.next()
        val newProfileRequest = generation.next()

        assertTrue(!generation.isCurrent(oldProfileRequest))
        assertTrue(generation.isCurrent(newProfileRequest))
    }

    @Test fun `local empty snapshot is capability and device scoped`() {
        val eligible = TrashEntry(
            trashId = "trash-a", category = "STORAGE", state = "TRASHED", name = "capture",
            purgeSupported = true
        )
        val legacy = LocalTrashUiState(
            deviceId = "device-a", online = true, entries = listOf(eligible), emptySupported = false
        )
        assertEquals(null, localTrashEmptySnapshot(legacy))

        val snapshot = localTrashEmptySnapshot(legacy.copy(emptySupported = true))!!
        val afterArrival = legacy.copy(
            emptySupported = true,
            entries = listOf(eligible, eligible.copy(trashId = "trash-new"))
        )
        assertEquals("device-a", snapshot.deviceId)
        assertEquals(listOf("trash-a"), snapshot.trashIds)
        assertEquals(listOf("trash-a", "trash-new"), localTrashEmptySnapshot(afterArrival)!!.trashIds)
    }

    @Test fun `server empty snapshot contains only visible eligible entries and original profile`() {
        val entries = (1..205).map { index ->
            ServerTrashJob(
                sessionId = "session-$index", clientJobId = "client-$index", sourceName = "capture-$index",
                totalBytes = 1, fileCount = 1, state = if (index == 205) "PURGING" else "TRASHED",
                trashedAt = "now", purgeSupported = index != 204, restoreSupported = index < 204
            )
        }
        val snapshot = serverTrashEmptySnapshot(
            DirectServerUiState(
                selectedProfileId = "profile-a", trash = entries, trashEmptySupported = true,
                trashTotal = 250, trashNextOffset = 205
            )
        )!!

        assertEquals("profile-a", snapshot.profileId)
        assertEquals(200, snapshot.sessionIds.size)
        assertFalse(snapshot.sessionIds.contains("session-204"))
    }

    @Test fun `data deletion snapshot keeps the original device root path and entry`() {
        val entry = RemoteFileEntry("capture", "runs/capture", RemoteEntryType.DIRECTORY, null, null)
        val snapshot = dataDeletionSnapshot(
            DeviceStorageUiState(
                deviceId = "device-a", currentRoot = RemoteRoot("collections", "수집", null),
                currentPath = "runs", entries = listOf(entry)
            ),
            entry
        )!!

        assertEquals("device-a", snapshot.deviceId)
        assertEquals("collections", snapshot.rootId)
        assertEquals("runs", snapshot.parentPath)
        assertEquals("runs/capture", snapshot.entry.relativePath)
    }
}
