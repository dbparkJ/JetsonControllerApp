package com.example.jetsoncontroller.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.server.DirectServerApiFactory
import com.example.jetsoncontroller.data.server.DirectServerRepository
import com.example.jetsoncontroller.data.server.ServerEndpointProfile
import com.example.jetsoncontroller.data.server.ServerProfileStore
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ServerConnectionUiState(
    val status: ServerConnectionStatus = ServerConnectionStatus.UNKNOWN,
    val lastCheckedAtEpochMillis: Long? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

internal class ServerConnectionViewModel(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val cache = RecentServerJobsCache(
        context.applicationContext.noBackupFilesDir.resolve("server-job-cache")
    )
    private val profiles = ServerProfileStore(context.applicationContext, cache)
    private val mutableUiState = MutableStateFlow(ServerConnectionUiState())
    val uiState = mutableUiState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L

    fun check(profile: ServerEndpointProfile) {
        runCheck(profile.profileId, tokenToSave = null)
    }

    fun relogin(profile: ServerEndpointProfile, employeeToken: String) {
        if (employeeToken.isBlank()) return
        runCheck(profile.profileId, tokenToSave = profile to employeeToken)
    }

    private fun runCheck(
        profileId: String,
        tokenToSave: Pair<ServerEndpointProfile, String>?
    ) {
        val profileToSave = tokenToSave?.first
        var pendingToken = tokenToSave?.second
        operation?.cancel()
        val request = ++generation
        mutableUiState.value = mutableUiState.value.copy(
            status = ServerConnectionStatus.CHECKING,
            isBusy = true,
            errorMessage = null
        )
        operation = viewModelScope.launch {
            try {
                val connection = withContext(Dispatchers.IO) {
                    if (profileToSave != null && pendingToken != null) {
                        val token = requireNotNull(pendingToken)
                        pendingToken = null
                        profiles.save(profileToSave, token)
                    }
                    profiles.connection(profileId)
                } ?: run {
                    publishFailure(
                        request,
                        ServerConnectionFailure.AUTHENTICATION,
                        clock()
                    )
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    DirectServerRepository(
                        api = DirectServerApiFactory.create(connection.profile, connection.employeeToken),
                        profile = connection.profile,
                        cache = cache,
                        credentialRevision = connection.credentialRevision
                    ).connect()
                }
                result.fold(
                    onSuccess = {
                        if (request == generation) {
                            mutableUiState.value = ServerConnectionUiState(
                                status = ServerConnectionStatus.CONNECTED,
                                lastCheckedAtEpochMillis = clock()
                            )
                        }
                    },
                    onFailure = { error ->
                        publishFailure(request, classifyServerConnectionFailure(error), clock())
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                publishFailure(request, classifyServerConnectionFailure(error), clock())
            }
        }
    }

    private fun publishFailure(
        request: Long,
        failure: ServerConnectionFailure,
        checkedAt: Long
    ) {
        if (request != generation) return
        mutableUiState.value = ServerConnectionUiState(
            status = failure.status(),
            lastCheckedAtEpochMillis = checkedAt,
            errorMessage = failure.userMessage()
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServerConnectionViewModel(context.applicationContext) as T
    }
}
