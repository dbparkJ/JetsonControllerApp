package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.GeoTechnicalValue
import com.example.jetsoncontroller.ui.model.Formats
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StorageTrashActionsTest {
    @Test fun `move confirmation cannot cross device root directory or replaced entry`() {
        val entry = RemoteFileEntry("capture", "runs/capture", RemoteEntryType.DIRECTORY, 10, "now")
        val state = DeviceStorageUiState(deviceId = "a", controlAvailable = true,
            currentRoot = RemoteRoot("data", "수집 자료", null), currentPath = "runs", entries = listOf(entry))
        val target = dataDeletionSnapshot(state, entry)!!
        assertTrue(target.matches(state))
        assertFalse(target.matches(state.copy(deviceId = "b")))
        assertFalse(target.matches(state.copy(currentRoot = RemoteRoot("other", "수집 자료", null))))
        assertFalse(target.matches(state.copy(currentPath = "other")))
        assertFalse(target.matches(state.copy(entries = listOf(entry.copy(modifiedAt = "new")))))
        assertFalse(target.matches(state.copy(isDeleting = true)))
        assertFalse(target.matches(state.copy(controlAvailable = false)))
    }

    @Test fun `trash restore requires both capability and restorable lifecycle`() {
        val entry = TrashEntry("trash", "STORAGE", "TRASHED", name = "capture", restoreSupported = true)
        assertTrue(localTrashCanRestore(entry))
        assertFalse(localTrashCanRestore(entry.copy(restoreSupported = false)))
        listOf("PURGING", "RESTORING", "RECOVERY_REQUIRED", "RESTORED").forEach {
            assertFalse(localTrashCanRestore(entry.copy(state = it)))
        }
        assertEquals("삭제 처리 중", localTrashStateLabel(entry.copy(state = "PURGING")))
        assertEquals("복원할 수 없음", localTrashStateLabel(entry.copy(restoreSupported = false)))
    }

    @Test fun `undo retains original storage identity after navigating to a different root`() {
        val state = DeviceStorageUiState(deviceId = "a", undoTrashId = "trash", undoRootId = "data",
            undoOriginalPath = "run/capture", currentRoot = RemoteRoot("other", "다른 위치", null), currentPath = "other/run")
        val target = deviceTrashUndoTarget(state)!!
        val entry = TrashEntry("trash", "STORAGE", "TRASHED", "data", "run/capture", "capture", restoreSupported = true)
        assertEquals("data", target.rootId)
        assertTrue(target.matches(entry))
        assertFalse(target.matches(entry.copy(relativePath = "different")))
        assertFalse(target.matches(entry.copy(rootId = "other")))
        assertFalse(target.matches(entry.copy(state = "PURGING")))
        assertNull(deviceTrashUndoTarget(state.copy(deviceId = null)))
    }

    @Test fun `purge confirmation retains only the original eligible ids`() {
        val entry = TrashEntry("first", "STORAGE", "TRASHED", name = "capture", purgeSupported = true)
        val state = LocalTrashUiState(deviceId = "a", online = true, emptySupported = true,
            entries = listOf(entry, entry.copy(trashId = "unsupported", purgeSupported = false),
                entry.copy(trashId = "restoring", state = "RESTORING")))
        val target = localTrashEmptySnapshot(state)!!
        assertEquals(listOf("first"), target.trashIds)
        assertEquals(listOf("first", "new"), localTrashEmptySnapshot(state.copy(entries = state.entries + entry.copy(trashId = "new")))!!.trashIds)
        assertEquals(listOf("first"), target.trashIds)
        assertNull(localTrashEmptySnapshot(state.copy(online = false)))
    }

    @Test fun `run logs and run folders read by the run's time and keep their names in technical info`() {
        val log = TrashEntry("log", "RUN_HISTORY", "TRASHED", name = "run-20260916T073608.169374Z-4190931992.log",
            trashedAt = "2026-10-20T05:02:00Z", restoreSupported = true)
        val started = Instant.parse("2026-09-16T07:36:08Z")
        assertEquals("${Formats.time(started)} 수집 기록", localTrashTitle(log))
        assertEquals("수집 기록 · ${Formats.time(Instant.parse("2026-10-20T05:02:00Z"))} 이동", localTrashMeta(log))
        val folder = log.copy(trashId = "folder", category = "UPLOADED_SOURCE", name = "run-20260916T073608.169374Z-4190931992")
        assertEquals("${Formats.time(started)} 수집", localTrashTitle(folder))
        assertEquals("업로드 원본 · 수집 자료", localTrashMeta(folder))
        val named = log.copy(trashId = "named", category = "STORAGE", name = "20260915_data")
        assertEquals("20260915_data", localTrashTitle(named))
        listOf(log, folder).forEach { assertFalse(localTrashTitle(it).contains("run-")) }
        assertEquals(listOf(GeoTechnicalValue(localTrashTitle(log), log.name), GeoTechnicalValue(localTrashTitle(folder), folder.name)),
            localTrashTechnicalNames(listOf(log, folder, named)))
    }

    @Test fun `run names without a readable start fall back to the category, never the run id`() {
        val log = TrashEntry("log", "RUN_HISTORY", "TRASHED", name = "run-20260916T073608-4190931992.log")
        assertEquals("수집 기록", localTrashTitle(log))
        assertEquals("수집 기록", localTrashMeta(log))
        assertEquals("장치 데이터", localTrashTitle(log.copy(category = "STORAGE", name = "run-20261399T073608.1Z-1")))
        assertEquals(listOf(GeoTechnicalValue("수집 기록", log.name)), localTrashTechnicalNames(listOf(log)))
    }

    @Test fun `object particles follow Korean final consonants and collection digits`() {
        assertEquals("영상을", storageObjectName("영상"))
        assertEquals("자료를", storageObjectName("자료"))
        assertEquals("20261019_1636을", storageObjectName("20261019_1636"))
        assertEquals("20260915_data를", storageObjectName("20260915_data"))
    }

    @Test fun cachedTrashFailureKeepsTheCauseAndExplainsTheVisibleResults() {
        val detail = localTrashFailureDetail("장치 연결이 끊겼습니다.", true)
        assertTrue(detail.startsWith("장치 연결이 끊겼습니다."))
        assertTrue(detail.contains("이전 조회 결과를 표시합니다."))
        val empty = localTrashFailureDetail("장치 휴지통을 불러오지 못했습니다", false)
        assertEquals("연결 상태를 확인한 뒤 다시 확인하세요.", empty)
        assertFalse(empty.contains("이전 조회"))
        assertEquals("장치 응답 시간이 초과되었습니다.",
            localTrashFailureDetail("장치 응답 시간이 초과되었습니다.", false))
    }
}
