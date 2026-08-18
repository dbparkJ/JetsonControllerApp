package com.example.jetsoncontroller.ui.pairing

import com.example.jetsoncontroller.model.BlePairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSelectionTest {

    @Test
    fun `stale repository Ready does not replace a newly scanned QR phase`() {
        val phase = resolvePairingPhase(
            requestedPhase = PairingPhase.QR_SCANNED,
            observeRepositoryState = false,
            repositoryState = BlePairingState.Ready("old-device")
        )

        assertEquals(PairingPhase.QR_SCANNED, phase)
    }

    @Test
    fun `stale repository Error does not replace a new QR session`() {
        val repositoryState = BlePairingState.Error("old failure")
        val phase = resolvePairingPhase(
            requestedPhase = PairingPhase.IDLE,
            observeRepositoryState = false,
            repositoryState = repositoryState
        )
        val error = resolvePairingError(
            localError = null,
            observeRepositoryState = false,
            repositoryState = repositoryState
        )

        assertEquals(PairingPhase.IDLE, phase)
        assertNull(error)
    }

    @Test
    fun `active attempt still follows repository progress`() {
        val phase = resolvePairingPhase(
            requestedPhase = PairingPhase.SEARCHING,
            observeRepositoryState = true,
            repositoryState = BlePairingState.VerifyingIdentity
        )

        assertEquals(PairingPhase.VERIFYING_IDENTITY, phase)
    }

    @Test
    fun `exact BLE name wins over earlier generic service candidate`() {
        val generic = Candidate("Other Jetson", advertisesService = true)
        val exact = Candidate("MMS-D137", advertisesService = true)

        val decision = choosePairingCandidate(
            candidates = listOf(generic, exact),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Connect)
        assertSame(
            exact,
            (decision as PairingCandidateDecision.Connect<Candidate>).candidate
        )
    }

    @Test
    fun `legacy five-character BLE name connects immediately while scanning`() {
        val generic = Candidate("Other Jetson", advertisesService = true)
        val legacy = Candidate("MMS-9D137", advertisesService = true)

        val decision = choosePairingCandidate(
            candidates = listOf(generic, legacy),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Connect)
        assertSame(
            legacy,
            (decision as PairingCandidateDecision.Connect<Candidate>).candidate
        )
    }

    @Test
    fun `canonical BLE name wins when canonical and legacy candidates are visible`() {
        val legacy = Candidate("MMS-9D137", advertisesService = true)
        val canonical = Candidate("MMS-D137", advertisesService = true)

        val decision = choosePairingCandidate(
            candidates = listOf(legacy, canonical),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Connect)
        assertSame(
            canonical,
            (decision as PairingCandidateDecision.Connect<Candidate>).candidate
        )
    }

    @Test
    fun `multiple legacy BLE names are not selected by list order`() {
        val decision = choosePairingCandidate(
            candidates = listOf(
                Candidate("MMS-9D137", advertisesService = true),
                Candidate("mms-9d137", advertisesService = true)
            ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Fail)
    }

    @Test
    fun `UUID-only candidate waits until name discovery has finished`() {
        val generic = Candidate("Unknown", advertisesService = true)

        val decision = choosePairingCandidate(
            candidates = listOf(generic),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertEquals(PairingCandidateDecision.ContinueScanning, decision)
    }

    @Test
    fun `single UUID-only candidate is accepted after scan finishes`() {
        val generic = Candidate("Unknown", advertisesService = true)

        val decision = choosePairingCandidate(
            candidates = listOf(generic),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Connect)
        assertSame(
            generic,
            (decision as PairingCandidateDecision.Connect<Candidate>).candidate
        )
    }

    @Test
    fun `multiple UUID-only candidates are rejected instead of choosing by RSSI`() {
        val decision = choosePairingCandidate(
            candidates = listOf(
                Candidate("Jetson A", advertisesService = true),
                Candidate("Jetson B", advertisesService = true)
            ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(decision is PairingCandidateDecision.Fail)
    }

    private data class Candidate(
        val name: String,
        val advertisesService: Boolean
    )
}
