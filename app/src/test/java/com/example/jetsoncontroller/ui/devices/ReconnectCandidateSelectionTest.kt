package com.example.jetsoncontroller.ui.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectCandidateSelectionTest {

    @Test
    fun `exact stored name wins while scan is running`() {
        val generic = Candidate(
            name = "MMS 장비 (7F3A)",
            advertisesService = true
        )
        val exact = Candidate(
            name = "MMS-D137",
            advertisesService = true
        )

        val decision = chooseReconnectCandidate(
            candidates = listOf(generic, exact),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(
            decision is ReconnectCandidateDecision.Connect
        )
        assertSame(
            exact,
            (decision as
                ReconnectCandidateDecision.Connect<Candidate>)
                .candidate
        )
    }

    @Test
    fun `legacy device ID alias is an immediate exact reconnect match`() {
        val legacy = Candidate(
            name = "MMS-9D137",
            advertisesService = true
        )

        val decision = chooseReconnectCandidate(
            candidates = listOf(legacy),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(
            decision is ReconnectCandidateDecision.Connect
        )
        assertSame(
            legacy,
            (decision as
                ReconnectCandidateDecision.Connect<Candidate>)
                .candidate
        )
    }

    @Test
    fun `canonical reconnect name wins over legacy alias`() {
        val legacy = Candidate(
            name = "MMS-9D137",
            advertisesService = true
        )
        val canonical = Candidate(
            name = "MMS-D137",
            advertisesService = true
        )

        val decision = chooseReconnectCandidate(
            candidates = listOf(legacy, canonical),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(
            decision is ReconnectCandidateDecision.Connect
        )
        assertSame(
            canonical,
            (decision as
                ReconnectCandidateDecision.Connect<Candidate>)
                .candidate
        )
    }

    @Test
    fun `multiple legacy reconnect aliases remain ambiguous`() {
        val decision = chooseReconnectCandidate(
            candidates =
                listOf(
                    Candidate(
                        name = "MMS-9D137",
                        advertisesService = true
                    ),
                    Candidate(
                        name = "mms-9d137",
                        advertisesService = true
                    )
                ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertEquals(
            ReconnectCandidateDecision.Ambiguous,
            decision
        )
    }

    @Test
    fun `single UUID candidate waits for scan completion`() {
        val decision = chooseReconnectCandidate(
            candidates =
                listOf(
                    Candidate(
                        name = "MMS 장비 (7F3A)",
                        advertisesService = true
                    )
                ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = true,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertEquals(
            ReconnectCandidateDecision.ContinueScanning,
            decision
        )
    }

    @Test
    fun `single UUID candidate is accepted after scan completion`() {
        val candidate = Candidate(
            name = "MMS 장비 (7F3A)",
            advertisesService = true
        )

        val decision = chooseReconnectCandidate(
            candidates = listOf(candidate),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertTrue(
            decision is ReconnectCandidateDecision.Connect
        )
        assertSame(
            candidate,
            (decision as
                ReconnectCandidateDecision.Connect<Candidate>)
                .candidate
        )
    }

    @Test
    fun `multiple UUID candidates are never selected`() {
        val decision = chooseReconnectCandidate(
            candidates =
                listOf(
                    Candidate(
                        name = "MMS 장비 (7F3A)",
                        advertisesService = true
                    ),
                    Candidate(
                        name = "MMS 장비 (19B2)",
                        advertisesService = true
                    )
                ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertEquals(
            ReconnectCandidateDecision.Ambiguous,
            decision
        )
    }

    @Test
    fun `multiple exact names are not selected by list order`() {
        val decision = chooseReconnectCandidate(
            candidates =
                listOf(
                    Candidate(
                        name = "MMS-D137",
                        advertisesService = true
                    ),
                    Candidate(
                        name = "mms-d137",
                        advertisesService = true
                    )
                ),
            expectedBleName = "MMS-D137",
            legacyExpectedBleName = "MMS-9D137",
            isScanning = false,
            nameOf = { it.name },
            advertisesJetsonService = { it.advertisesService }
        )

        assertEquals(
            ReconnectCandidateDecision.Ambiguous,
            decision
        )
    }

    private data class Candidate(
        val name: String,
        val advertisesService: Boolean
    )
}
