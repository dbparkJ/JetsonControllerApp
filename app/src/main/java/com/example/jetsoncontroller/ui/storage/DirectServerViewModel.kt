package com.example.jetsoncontroller.ui.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal enum class DirectServerSection { DATA, TRASH, PROFILES }

internal class DirectServerRequestGeneration {
    private var value = 0L

    fun next(): Long {
        value += 1
        return value
    }

    fun isCurrent(candidate: Long): Boolean = candidate == value
}

internal data class DirectServerPreview(
    val fileName: String,
    val mediaType: String?,
    val bytes: ByteArray
)

internal data class DirectServerUiState(
    val profiles: List<StoredServerProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val capabilities: ServerCapabilities? = null,
    val section: DirectServerSection = DirectServerSection.DATA,
    val jobs: List<ServerJob> = emptyList(),
    val nextOffset: Int? = null,
    val jobsSource: ServerJobsSource? = null,
    val refreshedAt: String? = null,
    val cachedAtEpochMillis: Long? = null,
    val selectedJob: ServerJob? = null,
    val currentPath: String = "",
    val files: List<ServerFileEntry> = emptyList(),
    val filesTruncated: Boolean = false,
    val preview: DirectServerPreview? = null,
    val receipt: ServerReceipt? = null,
    val trash: List<ServerTrashJob> = emptyList(),
    val undoSessionId: String? = null,
    val mutationMessage: String? = null,
    val isConnecting: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val errorActionLabel: String? = null
)

internal class DirectServerViewModel(context: Context) : ViewModel() {
    private val cache = RecentServerJobsCache(context.noBackupFilesDir.resolve("server-job-cache"))
    private val store = ServerProfileStore(context, cache)
    private val _uiState = MutableStateFlow(DirectServerUiState())
    val uiState = _uiState.asStateFlow()
    private var repository: DirectServerRepository? = null
    private var operation: Job? = null
    private val requestGeneration = DirectServerRequestGeneration()

    init {
        viewModelScope.launch {
            store.profiles.catch { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConnecting = false,
                    message = error.message ?: "저장된 서버 프로필을 불러오지 못했습니다.",
                    messageIsError = true,
                    errorActionLabel = "프로필 편집"
                )
            }.collectLatest { profiles ->
                val selected = _uiState.value.selectedProfileId
                    ?.takeIf { id -> profiles.any { it.profile.profileId == id } }
                    ?: profiles.firstOrNull()?.profile?.profileId
                _uiState.value = _uiState.value.copy(
                    profiles = profiles,
                    selectedProfileId = selected,
                    section = if (profiles.isEmpty()) DirectServerSection.PROFILES else _uiState.value.section
                )
            }
        }
    }

    fun selectSection(section: DirectServerSection) {
        operation?.cancel()
        requestGeneration.next()
        _uiState.value = _uiState.value.copy(
            section = section,
            isLoading = false,
            isConnecting = false,
            message = null
        )
        if (section == DirectServerSection.TRASH && repository != null) refreshTrash()
    }

    fun saveProfile(profile: ServerEndpointProfile, employeeToken: String) {
        operation?.cancel()
        val generation = requestGeneration.next()
        repository?.logout()
        repository = null
        _uiState.value = _uiState.value.forProfile(profile.profileId)
        launchOperation(connecting = true, generation = generation) { expected ->
            try {
                store.save(profile, employeeToken)
                if (requestGeneration.isCurrent(expected)) {
                    _uiState.value = _uiState.value.copy(
                        selectedProfileId = profile.profileId,
                        section = DirectServerSection.DATA
                    )
                    connectNow(profile.profileId, expected)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error, "입력 다시 확인", expected)
            }
        }
    }

    fun selectProfile(profileId: String) {
        operation?.cancel()
        requestGeneration.next()
        repository?.logout()
        repository = null
        _uiState.value = _uiState.value.forProfile(profileId)
    }

    fun connect() {
        val profileId = _uiState.value.selectedProfileId ?: run {
            _uiState.value = _uiState.value.copy(
                message = "서버 프로필을 먼저 추가하세요.",
                messageIsError = true,
                errorActionLabel = "프로필 추가"
            )
            return
        }
        launchOperation(connecting = true) { expected -> connectNow(profileId, expected) }
    }

    private suspend fun connectNow(profileId: String, generation: Long) {
        val connection = store.connection(profileId) ?: run {
            if (!requestGeneration.isCurrent(generation)) return
            _uiState.value = _uiState.value.copy(
                isConnecting = false,
                message = "저장된 인증 정보를 열지 못했습니다. 프로필을 다시 저장하세요.",
                messageIsError = true,
                errorActionLabel = "프로필 편집"
            )
            return
        }
        val next = DirectServerRepository(
            DirectServerApiFactory.create(connection.profile, connection.employeeToken),
            connection.profile,
            cache,
            connection.credentialRevision
        )
        next.connect().onSuccess { capabilities ->
            if (!requestGeneration.isCurrent(generation)) return@onSuccess
            repository = next
            _uiState.value = _uiState.value.copy(
                capabilities = capabilities,
                isConnecting = false,
                message = null,
                errorActionLabel = null
            )
            refreshJobsNow(generation)
        }.onFailure { error ->
            if (!requestGeneration.isCurrent(generation)) return@onFailure
            if (directServerAvailabilityFailure(error)) {
                // A transport/5xx outage may still use cache from this exact credential revision
                // and environment/employee/project scope.
                next.jobs().onSuccess { snapshot ->
                    if (!requestGeneration.isCurrent(generation)) return@onSuccess
                    repository = next
                    publishJobsSnapshot(snapshot, connectionWarning = error.message, generation = generation)
                }.onFailure { jobsError ->
                    if (!requestGeneration.isCurrent(generation)) return@onFailure
                    repository = null
                    showError(jobsError, directServerRecoveryAction(jobsError), generation)
                }
            } else {
                repository = null
                next.logout()
                showError(error, directServerRecoveryAction(error), generation)
            }
        }
    }

    fun refreshJobs() {
        if (repository == null) connect() else launchOperation { expected -> refreshJobsNow(expected) }
    }

    fun loadMoreJobs() {
        val offset = _uiState.value.nextOffset ?: return
        val source = repository ?: return
        launchOperation { generation ->
            source.jobs(offset = offset).onSuccess { snapshot ->
                if (!requestGeneration.isCurrent(generation)) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    jobs = (_uiState.value.jobs + snapshot.response.jobs).distinctBy { it.sessionId },
                    nextOffset = snapshot.response.nextOffset,
                    refreshedAt = snapshot.response.refreshedAt,
                    isLoading = false
                )
            }.onFailure { showError(it, directServerRecoveryAction(it), generation) }
        }
    }

    private suspend fun refreshJobsNow(generation: Long) {
        val source = repository ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, message = null)
        source.jobs().onSuccess { snapshot ->
            publishJobsSnapshot(snapshot, generation = generation)
        }.onFailure { showError(it, directServerRecoveryAction(it), generation) }
    }

    private fun publishJobsSnapshot(
        snapshot: ServerJobsSnapshot,
        connectionWarning: String? = null,
        generation: Long
    ) {
        if (!requestGeneration.isCurrent(generation)) return
        val refreshError = snapshot.refreshError ?: connectionWarning
        _uiState.value = _uiState.value.withJobsSnapshot(snapshot, refreshError)
    }

    fun openJob(job: ServerJob) {
        operation?.cancel()
        requestGeneration.next()
        _uiState.value = _uiState.value.copy(
            selectedJob = job,
            currentPath = "",
            files = emptyList(),
            preview = null,
            receipt = null,
            message = null
        )
        loadFiles("")
    }

    fun openDirectory(entry: ServerFileEntry) {
        if (entry.type.equals("directory", true)) loadFiles(entry.relativePath)
    }

    fun openFile(entry: ServerFileEntry) {
        val source = repository ?: return
        val job = _uiState.value.selectedJob ?: return
        launchOperation { generation ->
            source.preview(job.sessionId, entry.relativePath).onSuccess { body ->
                if (!requestGeneration.isCurrent(generation)) { body.close(); return@onSuccess }
                val preview = body.use {
                    val mediaType = it.contentType()?.toString()
                    val bytes = withContext(Dispatchers.IO) { it.bytes() }
                    DirectServerPreview(entry.name, mediaType, bytes)
                }
                if (requestGeneration.isCurrent(generation) && _uiState.value.selectedJob?.sessionId == job.sessionId) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        preview = preview
                    )
                }
            }.onFailure { showError(it, "파일 목록으로 돌아가기", generation) }
        }
    }

    private fun loadFiles(path: String) {
        val source = repository ?: return
        val job = _uiState.value.selectedJob ?: return
        launchOperation { generation ->
            source.files(job.sessionId, path).onSuccess { response ->
                if (!requestGeneration.isCurrent(generation) || _uiState.value.selectedJob?.sessionId != job.sessionId) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    currentPath = response.path,
                    files = response.entries,
                    filesTruncated = response.truncated,
                    preview = null,
                    isLoading = false,
                    message = null
                )
            }.onFailure { showError(it, "다시 불러오기", generation) }
        }
    }

    fun loadReceipt(job: ServerJob) {
        val source = repository ?: return
        launchOperation { generation ->
            source.receipt(job.sessionId).onSuccess { receipt ->
                if (!requestGeneration.isCurrent(generation) || _uiState.value.selectedJob?.sessionId != job.sessionId) return@onSuccess
                _uiState.value = _uiState.value.copy(receipt = receipt, isLoading = false)
            }.onFailure { showError(it, "수신 검증 다시 확인", generation) }
        }
    }

    fun moveToTrash(job: ServerJob) {
        val source = repository ?: return
        launchOperation { generation ->
            source.moveToTrash(job.sessionId).onSuccess { result ->
                if (!requestGeneration.isCurrent(generation)) return@onSuccess
                if (result.status == ServerMutationStatus.CONFIRMED) {
                    _uiState.value = _uiState.value.copy(
                        selectedJob = null,
                        files = emptyList(),
                        undoSessionId = job.sessionId,
                        mutationMessage = "서버 작업을 휴지통으로 이동했습니다.",
                        isLoading = false
                    )
                    refreshJobsNow(generation)
                } else {
                    showUnknownMutation(result.detail)
                }
            }.onFailure { showError(it, directServerRecoveryAction(it), generation) }
        }
    }

    fun undoTrash() {
        val sessionId = _uiState.value.undoSessionId ?: return
        restore(sessionId)
    }

    fun restore(sessionId: String) {
        val source = repository ?: return
        launchOperation { generation ->
            source.restore(sessionId).onSuccess { result ->
                if (!requestGeneration.isCurrent(generation)) return@onSuccess
                if (result.status == ServerMutationStatus.CONFIRMED) {
                    _uiState.value = _uiState.value.copy(
                        undoSessionId = null,
                        mutationMessage = "서버 작업을 복원했습니다.",
                        message = null,
                        messageIsError = false,
                        isLoading = false
                    )
                    refreshTrashNow(generation)
                    if (requestGeneration.isCurrent(generation)) refreshJobsNow(generation)
                } else {
                    showUnknownMutation(result.detail)
                }
            }.onFailure { showError(it, directServerRecoveryAction(it), generation) }
        }
    }

    fun refreshTrash() = launchOperation { generation -> refreshTrashNow(generation) }

    private suspend fun refreshTrashNow(generation: Long) {
        val source = repository ?: return
        source.trash().onSuccess { response ->
            if (!requestGeneration.isCurrent(generation)) return@onSuccess
            _uiState.value = _uiState.value.copy(
                trash = response.jobs,
                refreshedAt = response.refreshedAt,
                isLoading = false
            )
        }.onFailure { showError(it, "휴지통 다시 불러오기", generation) }
    }

    fun navigateBack(): Boolean {
        if (_uiState.value.receipt != null) {
            operation?.cancel()
            requestGeneration.next()
            _uiState.value = _uiState.value.copy(receipt = null)
            return true
        }
        if (_uiState.value.preview != null) {
            operation?.cancel()
            requestGeneration.next()
            _uiState.value = _uiState.value.copy(preview = null)
            return true
        }
        if (_uiState.value.currentPath.isNotEmpty()) {
            loadFiles(_uiState.value.currentPath.substringBeforeLast('/', ""))
            return true
        }
        if (_uiState.value.selectedJob != null) {
            operation?.cancel()
            requestGeneration.next()
            _uiState.value = _uiState.value.copy(selectedJob = null, files = emptyList())
            return true
        }
        return false
    }

    fun removeSelectedProfile() {
        val profileId = _uiState.value.selectedProfileId ?: return
        operation?.cancel()
        val generation = requestGeneration.next()
        launchOperation(generation = generation) { expected ->
            repository?.logout()
            repository = null
            store.remove(profileId)
            if (requestGeneration.isCurrent(expected)) {
                _uiState.value = DirectServerUiState(section = DirectServerSection.PROFILES)
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(
            message = null,
            errorActionLabel = null,
            mutationMessage = null,
            undoSessionId = null
        )
    }

    private fun launchOperation(
        connecting: Boolean = false,
        generation: Long? = null,
        block: suspend (Long) -> Unit
    ) {
        operation?.cancel()
        val expected = generation ?: requestGeneration.next()
        operation = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isConnecting = connecting,
                    isLoading = !connecting,
                    message = null,
                    errorActionLabel = null
                )
                block(expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error, directServerRecoveryAction(error), expected)
            }
        }
    }

    private fun showUnknownMutation(detail: String?) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            message = "서버 응답을 확인하지 못해 처리 결과를 알 수 없습니다. 같은 작업을 반복하지 말고 목록과 휴지통을 새로고침하세요." +
                detail?.let { " ($it)" }.orEmpty(),
            messageIsError = true,
            errorActionLabel = "상태 새로고침"
        )
    }

    private fun showError(error: Throwable, action: String, generation: Long) {
        if (!requestGeneration.isCurrent(generation)) return
        val clearSensitiveViews = directServerSecurityFailure(error)
        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            isLoading = false,
            capabilities = if (clearSensitiveViews) null else _uiState.value.capabilities,
            jobs = if (clearSensitiveViews) emptyList() else _uiState.value.jobs,
            nextOffset = if (clearSensitiveViews) null else _uiState.value.nextOffset,
            jobsSource = if (clearSensitiveViews) null else _uiState.value.jobsSource,
            refreshedAt = if (clearSensitiveViews) null else _uiState.value.refreshedAt,
            cachedAtEpochMillis = if (clearSensitiveViews) null else _uiState.value.cachedAtEpochMillis,
            selectedJob = if (clearSensitiveViews) null else _uiState.value.selectedJob,
            currentPath = if (clearSensitiveViews) "" else _uiState.value.currentPath,
            files = if (clearSensitiveViews) emptyList() else _uiState.value.files,
            preview = if (clearSensitiveViews) null else _uiState.value.preview,
            receipt = if (clearSensitiveViews) null else _uiState.value.receipt,
            trash = if (clearSensitiveViews) emptyList() else _uiState.value.trash,
            undoSessionId = if (clearSensitiveViews) null else _uiState.value.undoSessionId,
            mutationMessage = if (clearSensitiveViews) null else _uiState.value.mutationMessage,
            message = directServerErrorMessage(error),
            messageIsError = true,
            errorActionLabel = action
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DirectServerViewModel(context.applicationContext) as T
    }
}

internal fun DirectServerUiState.forProfile(profileId: String): DirectServerUiState =
    DirectServerUiState(
        profiles = profiles,
        selectedProfileId = profileId,
        section = section
    )

internal fun DirectServerUiState.withJobsSnapshot(
    snapshot: ServerJobsSnapshot,
    refreshError: String?
): DirectServerUiState = copy(
    jobs = snapshot.response.jobs,
    nextOffset = snapshot.response.nextOffset,
    jobsSource = snapshot.source,
    refreshedAt = snapshot.response.refreshedAt,
    cachedAtEpochMillis = snapshot.cachedAtEpochMillis,
    isConnecting = false,
    isLoading = false,
    message = refreshError?.let {
        "서버에 연결하지 못해 이전에 인증된 마지막 성공 결과를 표시합니다. $it"
    },
    messageIsError = refreshError != null,
    errorActionLabel = refreshError?.let { "다시 연결" }
)

internal fun directServerSecurityFailure(error: Throwable): Boolean =
    error is SSLException || error is CertificateException ||
        (error is ServerRequestException && error.statusCode in setOf(401, 403, 409)) ||
        listOf("environment", "project", "employee identity", "scope mismatch").any {
            error.message?.contains(it, ignoreCase = true) == true
        }

internal fun directServerAvailabilityFailure(error: Throwable): Boolean =
    (error is IOException && error !is SSLException) ||
        (error is ServerRequestException && error.statusCode in 500..599)

internal fun directServerRecoveryAction(error: Throwable): String = when {
    error is SSLException || error is CertificateException -> "서버 인증서 확인"
    error is ServerRequestException && error.statusCode in setOf(401, 403) -> "프로필 인증 확인"
    error.message?.contains("environment", ignoreCase = true) == true -> "서버 환경 확인"
    error.message?.contains("project", ignoreCase = true) == true -> "프로젝트 권한 확인"
    else -> "다시 연결"
}

internal fun directServerErrorMessage(error: Throwable): String = when {
    error is SSLException || error is CertificateException ->
        "서버 인증서를 신뢰할 수 없습니다. 운영 서버 주소와 인증서를 확인하세요."
    error is ServerRequestException && error.statusCode == 401 ->
        "직원 인증이 거절되었습니다. 저장된 토큰을 갱신하세요."
    error is ServerRequestException && error.statusCode == 403 ->
        "이 직원 계정에는 선택한 프로젝트 권한이 없습니다. 프로필의 직원·프로젝트를 확인하세요."
    error.message?.contains("environment", ignoreCase = true) == true ->
        "선택한 환경과 서버 응답 환경이 다릅니다. 서버 주소와 환경을 확인하세요."
    error.message?.contains("project", ignoreCase = true) == true ->
        "선택한 프로젝트와 서버 응답이 일치하지 않습니다. 프로젝트 프로필을 확인하세요."
    else -> error.message ?: "서버에 연결하지 못했습니다. 인터넷과 서버 주소를 확인하세요."
}
