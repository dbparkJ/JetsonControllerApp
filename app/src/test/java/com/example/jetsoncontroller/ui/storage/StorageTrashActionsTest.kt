package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.*
import org.junit.Assert.*
import org.junit.Test

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

    @Test fun `object particles follow Korean final consonants and collection digits`() {
        assertEquals("영상을", storageObjectName("영상"))
        assertEquals("자료를", storageObjectName("자료"))
        assertEquals("20261019_1636을", storageObjectName("20261019_1636"))
        assertEquals("20260915_data를", storageObjectName("20260915_data"))
    }
}
