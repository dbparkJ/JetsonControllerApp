package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test

class ServerResultsTest {
    @Test fun `job paging merges by session and preserves confirmed undo`() {
        val job = job("one")
        val state = DirectServerUiState(jobs = listOf(job), nextOffset = 30, undoSessionId = "trash",
            mutationMessage = "서버 휴지통으로 옮겼습니다.")
        val snapshot = ServerJobsSnapshot(ServerJobsResponse("production", "employee", "project",
            listOf(job, job("two")), 60, "2026-10-20T00:00:00Z"), ServerJobsSource.NETWORK, 1)
        val page = state.withJobsPage(snapshot)
        assertEquals(listOf("one", "two"), page.jobs.map { it.sessionId })
        assertEquals(60, page.nextOffset)
        assertEquals("trash", page.undoSessionId)
        assertEquals(state.mutationMessage, page.mutationMessage)
        assertNull(page.withJobsPage(snapshot.copy(response = snapshot.response.copy(nextOffset = null))).nextOffset)
    }

    @Test fun `server move binds profile job version and role`() {
        val job = job("one")
        val state = DirectServerUiState(selectedProfileId = "profile", jobs = listOf(job),
            capabilities = ServerCapabilities(1, "production", ServerEmployee("employee", "작업자", "OPERATOR"), emptyList(), 1024, "now"))
        val target = serverTrashTarget(state, job)!!
        assertTrue(target.matches(state))
        assertFalse(target.matches(state.copy(selectedProfileId = "other")))
        assertFalse(target.matches(state.copy(jobs = listOf(job.copy(updatedAt = "changed")))))
        assertFalse(target.matches(state.copy(isLoading = true)))
        assertFalse(target.matches(state.copy(isMutating = true)))
        assertFalse(target.matches(state.copy(mutationResultUnknown = true)))
        assertNull(serverTrashTarget(state.copy(capabilities = null), job))
        assertNull(serverTrashTarget(state.copy(jobs = listOf(job.copy(state = "UPLOADING"))), job))
    }

    @Test fun `server trash retains count and unsupported pending restore states`() {
        val job = ServerTrashJob("one", "client", "capture", 100, 1, "TRASHED", "now", restoreSupported = true)
        assertEquals("전체 128개 중 100개를 표시합니다.", serverTrashCount(100, 128))
        assertEquals("전체 3개 중 3개를 표시합니다.", serverTrashCount(3, 3))
        assertTrue(serverTrashCanRestore(job))
        assertFalse(serverTrashCanRestore(job.copy(restoreSupported = false)))
        assertFalse(serverTrashCanRestore(job.copy(state = "PURGING")))
        assertEquals("복원할 수 없음", serverTrashStatus(job.copy(restoreSupported = false)))
        assertEquals("삭제 처리 중", serverTrashStatus(job.copy(state = "PURGING")))
    }

    @Test fun `unknown server states use Korean words without exposing the code`() {
        assertEquals("상태 미확인", serverJobState("SERVER_INTERNAL_STATE").word)
        assertEquals("보내는 중", serverJobState("UPLOADING").word)
        assertEquals(Tone.PENDING, serverJobState("PURGING").tone)
    }

    private fun job(id: String) = ServerJob(id, "client-$id", "project", "GEO-X-1", "capture", "COMPLETED", null,
        100, 100, 1, "created", "updated", "completed", ServerPathSummary(emptyList(), false, 0, 0))
}
