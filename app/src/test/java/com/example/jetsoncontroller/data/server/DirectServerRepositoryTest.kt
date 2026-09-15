package com.example.jetsoncontroller.data.server

import com.example.jetsoncontroller.data.storage.CachedServerJobs
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import com.example.jetsoncontroller.data.storage.ServerJobsCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException

class DirectServerRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    private val profile = ServerEndpointProfile(
        profileId = "prod-main",
        displayName = "Production",
        environment = ServerEnvironment.PRODUCTION,
        baseUrl = "https://Uploads.Example.com",
        employeeId = "employee.one",
        projectId = "road-alpha"
    ).validated()

    @Test fun `profile requires https root and normalizes host`() {
        assertEquals("https://uploads.example.com/", profile.baseUrl)
        val rejected = runCatching { profile.copy(baseUrl = "http://uploads.example.com").validated() }
        assertTrue(rejected.isFailure)
        assertTrue(
            runCatching {
                profile.copy(baseUrl = "https://uploads.example.com/prefix").validated()
            }.isFailure
        )
    }

    @Test fun `recent cache isolates scope and credential revision`() {
        val cache = RecentServerJobsCache(temporary.newFolder("cache"))
        val key = ServerJobsCacheKey.from(profile)
        val entry = CachedServerJobs(key, "revision-1", 42L, jobsResponse())
        cache.write(entry)
        assertEquals(entry, cache.read(key, "revision-1"))
        assertNull(cache.read(key, "revision-2"))
        assertNull(
            cache.read(
                ServerJobsCacheKey.from(profile.copy(projectId = "road-beta")),
                "revision-1"
            )
        )
    }

    @Test fun `availability fallback is explicitly stale and auth failure clears cache`() = runTest {
        val cache = RecentServerJobsCache(temporary.newFolder("repository-cache"))
        val api = FakeApi { Response.success(jobsResponse()) }
        val repository = DirectServerRepository(
            api,
            profile,
            cache,
            credentialRevision = "revision-1",
            nowEpochMillis = { 1234L }
        )
        val current = repository.jobs().getOrThrow()
        assertTrue(current.isCurrent)
        assertEquals(ServerJobsSource.NETWORK, current.source)

        api.jobsCall = { throw IOException("offline") }
        val stale = repository.jobs().getOrThrow()
        assertFalse(stale.isCurrent)
        assertEquals(ServerJobsSource.CACHE, stale.source)
        assertEquals("2026-09-14T00:00:00Z", stale.response.refreshedAt)

        api.jobsCall = { throw ServerRequestException(403) }
        assertTrue(repository.jobs().isFailure)
        assertNull(cache.read(ServerJobsCacheKey.from(profile), "revision-1"))
    }

    @Test fun `cancellation is never converted to cache fallback`() = runTest {
        val cache = RecentServerJobsCache(temporary.newFolder("cancel-cache"))
        cache.write(
            CachedServerJobs(
                ServerJobsCacheKey.from(profile), "revision-1", 10L, jobsResponse()
            )
        )
        val repository = DirectServerRepository(
            FakeApi { throw CancellationException("switched profile") },
            profile,
            cache,
            "revision-1"
        )
        var cancelled = false
        try {
            repository.jobs()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun `empty trash validates the exact requested scope and receipt`() = runTest {
        val api = FakeApi({ Response.success(jobsResponse()) }).apply {
            emptyTrashCall = { projectId, request ->
                assertEquals("road-alpha", projectId)
                assertEquals(listOf("session-1", "session-2"), request.sessionIds)
                Response.success(
                    ServerEmptyTrashResponse(
                        serverEnvironment = "production",
                        projectId = "road-alpha",
                        accessProjectId = "road-alpha",
                        results = request.sessionIds.map { ServerEmptyTrashItemResult(it, "PURGED") },
                        refreshedAt = "now"
                    )
                )
            }
        }
        val repository = DirectServerRepository(
            api, profile, RecentServerJobsCache(temporary.newFolder("empty-cache")), "revision-1"
        )

        val result = repository.emptyTrash(listOf("session-1", "session-2")).getOrThrow()

        assertEquals(ServerMutationStatus.CONFIRMED, result.status)
        assertEquals(2, result.response!!.results.size)
    }

    @Test fun `empty trash rejects missing results and reports availability as unknown`() = runTest {
        val api = FakeApi({ Response.success(jobsResponse()) })
        val repository = DirectServerRepository(
            api, profile, RecentServerJobsCache(temporary.newFolder("invalid-empty-cache")), "revision-1"
        )
        api.emptyTrashCall = { _, _ ->
            Response.success(
                ServerEmptyTrashResponse(
                    "production", "road-alpha", "road-alpha",
                    listOf(ServerEmptyTrashItemResult("wrong", "PURGED")), "now"
                )
            )
        }
        assertTrue(repository.emptyTrash(listOf("session-1")).isFailure)

        api.emptyTrashCall = { _, _ -> throw IOException("response lost") }
        assertEquals(
            ServerMutationStatus.UNKNOWN,
            repository.emptyTrash(listOf("session-1")).getOrThrow().status
        )
    }

    private fun jobsResponse() = ServerJobsResponse(
        serverEnvironment = "production",
        employeeId = "employee.one",
        projectId = "road-alpha",
        jobs = listOf(
            ServerJob(
                sessionId = "session-1",
                clientJobId = "a".repeat(32),
                projectId = "road-alpha",
                deviceId = "00000000-0000-0000-0000-000000000001",
                sourceName = "capture",
                state = "COMPLETED",
                failureCode = null,
                totalBytes = 1,
                receivedBytes = 1,
                fileCount = 1,
                createdAt = "2026-09-14T00:00:00Z",
                updatedAt = "2026-09-14T00:00:00Z",
                completedAt = "2026-09-14T00:00:00Z",
                pathSummary = ServerPathSummary(listOf("camera"), false, 1, 0)
            )
        ),
        nextOffset = null,
        refreshedAt = "2026-09-14T00:00:00Z"
    )

    private class FakeApi(var jobsCall: () -> Response<ServerJobsResponse>) : DirectServerApi {
        var emptyTrashCall: suspend (String, ServerEmptyTrashRequest) -> Response<ServerEmptyTrashResponse> =
            { _, _ -> error("unused") }
        override suspend fun jobs(projectId: String, limit: Int, offset: Int) = jobsCall()
        override suspend fun capabilities(): Response<ServerCapabilities> = error("unused")
        override suspend fun files(
            sessionId: String, projectId: String, path: String
        ): Response<ServerFilesResponse> = error("unused")
        override suspend fun preview(
            sessionId: String, projectId: String, path: String
        ): Response<ResponseBody> = error("unused")
        override suspend fun receipt(
            sessionId: String, projectId: String
        ): Response<ServerReceipt> = error("unused")
        override suspend fun trash(projectId: String, limit: Int, offset: Int): Response<ServerTrashResponse> = error("unused")
        override suspend fun emptyTrash(
            projectId: String,
            request: ServerEmptyTrashRequest
        ): Response<ServerEmptyTrashResponse> = emptyTrashCall(projectId, request)
        override suspend fun moveToTrash(
            sessionId: String, projectId: String
        ): Response<ServerLifecycleResponse> = error("unused")
        override suspend fun restore(
            sessionId: String, projectId: String
        ): Response<ServerLifecycleResponse> = error("unused")
    }
}
