package com.example.jetsoncontroller.ui.pairing

import androidx.lifecycle.SavedStateHandle
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.PairingInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRecreationTest {
    private val info = PairingInfo(1, "00000000-0000-0000-0000-123456784de0", "ab".repeat(32))

    @Test
    fun `the registration sheet survives recreation before registration is tapped`() {
        val original = PairingRecreationState(PairingPhase.QR_SCANNED, info)
        val restored = recreate(original)
        assertEquals(original, restored)
        assertEquals(original, reconcileRestoredPairingState(restored, BlePairingState.Idle))
        assertFalse(shouldConnectPairingCandidate(restored.phase, false))
    }

    @Test
    fun `a theme recreation observes the existing authentication without reconnecting`() {
        val original = PairingRecreationState(PairingPhase.AUTHENTICATING, info, observeRepositoryState = true)
        val restored = reconcileRestoredPairingState(recreate(original), BlePairingState.Authenticating)
        assertEquals(original, restored)
        assertEquals(PairingPhase.AUTHENTICATING, resolvePairingPhase(restored.phase, true, BlePairingState.Authenticating))
        assertFalse(shouldConnectPairingCandidate(restored.phase, false))
    }

    @Test
    fun `process death during any pending step offers an explicit retry without a new command`() {
        PairingPhase.entries.filter { it.isPairingInProgress() }.forEach { phase ->
            val restored = reconcileRestoredPairingState(
                recreate(PairingRecreationState(phase, info, observeRepositoryState = true)), BlePairingState.Idle
            )
            assertEquals(PairingPhase.ERROR, restored.phase)
            assertEquals(info, restored.pairingInfo)
            assertFalse(restored.observeRepositoryState)
            assertFalse(shouldConnectPairingCandidate(restored.phase, false))
            assertEquals("장치 등록이 중단되었습니다. 다시 시도하세요.", restored.errorMessage)
        }
    }

    @Test
    fun `a cancelled attempt clears credentials and ignores late repository success`() {
        val handle = SavedStateHandle()
        savePairingRecreationState(handle, PairingRecreationState(PairingPhase.AUTHENTICATING, info, observeRepositoryState = true))
        savePairingRecreationState(handle, PairingRecreationState())
        val restored = restorePairingRecreationState(handle)
        assertNull(restored.pairingInfo)
        assertFalse(restored.observeRepositoryState)
        assertEquals(PairingPhase.IDLE, resolvePairingPhase(restored.phase, false, BlePairingState.Ready("old-device")))
        assertFalse(shouldConnectPairingCandidate(restored.phase, true))
    }

    @Test
    fun `restored errors keep the target and require a retry tap`() {
        val original = PairingRecreationState(PairingPhase.ERROR, info, "장치 등록이 중단되었습니다. 다시 시도하세요.")
        assertEquals(original, recreate(original))
        assertFalse(shouldConnectPairingCandidate(PairingPhase.SEARCHING, false))
        assertTrue(shouldConnectPairingCandidate(PairingPhase.SEARCHING, true))
    }

    private fun recreate(state: PairingRecreationState): PairingRecreationState {
        val handle = SavedStateHandle()
        savePairingRecreationState(handle, state)
        return restorePairingRecreationState(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
    }
}
