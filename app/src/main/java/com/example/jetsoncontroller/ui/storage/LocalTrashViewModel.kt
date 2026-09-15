package com.example.jetsoncontroller.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.TrashEntry
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class LocalTrashUiState(
    val deviceId: String? = null,
    val online: Boolean = false,
    val entries: List<TrashEntry> = emptyList(),
    val refreshedAt: String? = null,
    val emptySupported: Boolean = false,
    val hasLoaded: Boolean = false,
    val loading: Boolean = false,
    val restoringId: String? = null,
    val emptying: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class LocalTrashViewModel(private val repository: JetsonRepository) : ViewModel() {
    private val _state = MutableStateFlow(LocalTrashUiState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var job: Job? = null

    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { id, transport ->
                id to (transport is TransportState.Connected && transport.type != TransportType.BLE &&
                    transport.deviceId.equals(id, true))
            }.collectLatest { (id, online) ->
                generation++
                job?.cancel()
                _state.value = LocalTrashUiState(deviceId = id, online = online,
                    error = if (online) null else "장비에 연결한 뒤 휴지통을 확인하세요.")
                if (online) refresh()
            }
        }
    }

    fun refresh() = refresh(preserveError = false)

    private fun refresh(preserveError: Boolean) {
        if (!_state.value.online || _state.value.restoringId != null || _state.value.emptying) return
        val expected = generation
        job?.cancel()
        job = viewModelScope.launch {
            loadTrash(expected, preserveError)
        }
    }

    private suspend fun loadTrash(expected: Long, preserveError: Boolean) {
        val retainedError = _state.value.error.takeIf { preserveError }
        _state.value = _state.value.copy(loading = true, error = retainedError)
        repository.getTrash().onSuccess { response ->
            val entries = runCatching {
                response.entries.filter { it.state !in setOf("RESTORED", "PURGED") }
            }.getOrElse {
                if (expected == generation) _state.value = _state.value.copy(
                    loading = false,
                    error = retainedError ?: "장비 휴지통 응답을 확인하지 못했습니다. 다시 시도하세요."
                )
                return@onSuccess
            }
            if (expected == generation) _state.value = _state.value.copy(
                entries = entries,
                refreshedAt = response.refreshedAt,
                emptySupported = response.emptySupported,
                hasLoaded = true,
                loading = false,
                error = retainedError
            )
        }.onFailure { error ->
            if (expected == generation) _state.value = _state.value.copy(
                loading = false,
                error = retainedError ?: userFacingFailure(
                    error,
                    "장비 휴지통을 불러오지 못했습니다. 다시 시도하세요."
                ).message
            )
        }
    }

    fun restore(entry: TrashEntry) {
        if (!_state.value.online || _state.value.restoringId != null || _state.value.emptying ||
            !entry.restoreSupported || entry.state != "TRASHED") return
        val expected = generation
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(restoringId = entry.trashId, error = null, message = null)
            repository.restoreTrash(entry.trashId).onSuccess { restored ->
                if (expected == generation) {
                    _state.value = _state.value.copy(
                        entries = _state.value.entries.filterNot { it.trashId == restored.trashId && restored.state == "RESTORED" },
                        restoringId = null,
                        message = if (restored.state == "RESTORED") "${restored.name} 항목을 원래 위치로 복원했습니다."
                        else "복원 상태를 다시 확인해 주세요."
                    )
                    loadTrash(expected, preserveError = false)
                }
            }.onFailure { error ->
                if (expected == generation) {
                    _state.value = _state.value.copy(
                        restoringId = null,
                        error = if (error is JetsonCommandResultUnknownException) {
                            "복원 결과를 확인하지 못했습니다. 자동 재시도하지 않고 휴지통을 다시 조회합니다."
                        } else userFacingFailure(error, "항목을 복원하지 못했습니다. 다시 시도하세요.").message
                    )
                    loadTrash(expected, preserveError = true)
                }
            }
        }
    }

    fun emptyTrash(expectedDeviceId: String, trashIds: List<String>) {
        val current = _state.value
        val originalIds = trashIds.toList()
        val eligibleIds = current.entries.filter(::isPurgeEligible).map(TrashEntry::trashId).toSet()
        if (!current.online || !current.emptySupported || current.deviceId != expectedDeviceId ||
            current.loading || current.restoringId != null || current.emptying || originalIds.isEmpty() ||
            originalIds.distinct().size != originalIds.size || originalIds.any { it !in eligibleIds }) return
        val expected = generation
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(emptying = true, error = null, message = null)
            var completed = 0
            var failureMessage: String? = null
            for (chunk in originalIds.chunked(200)) {
                if (expected != generation || _state.value.deviceId != expectedDeviceId) return@launch
                val result = repository.emptyTrash(expectedDeviceId, chunk)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()!!
                    failureMessage = if (error is JetsonCommandResultUnknownException) {
                        "영구 삭제 결과를 확인하지 못했습니다. 같은 작업을 반복하지 않고 휴지통 상태를 다시 확인합니다."
                    } else userFacingFailure(
                        error,
                        "휴지통을 완전히 비우지 못했습니다. 현재 상태를 다시 확인하세요."
                    ).message
                    break
                }
                val response = result.getOrThrow()
                val validated = runCatching {
                    val responseIds = response.results.map { it.trashId }
                    check(responseIds.size == chunk.size && responseIds.distinct().size == responseIds.size &&
                        responseIds.toSet() == chunk.toSet() &&
                        response.results.all { it.state in setOf("PURGED", "PURGING", "FAILED") })
                    response.results
                }.getOrNull()
                val valid = validated != null
                if (!valid) {
                    failureMessage = "장비의 삭제 확인 응답이 요청과 일치하지 않습니다. 휴지통 상태를 다시 확인하세요."
                    break
                }
                val purged = validated!!.count { it.state == "PURGED" }
                completed += purged
                if (purged != chunk.size) {
                    val pending = validated.count { it.state == "PURGING" }
                    val failed = validated.count { it.state == "FAILED" }
                    failureMessage = buildString {
                        append("${originalIds.size}개 중 ${completed}개 삭제를 확인했습니다.")
                        if (pending > 0) append(" ${pending}개는 삭제 처리 중입니다.")
                        if (failed > 0) append(" ${failed}개는 삭제하지 못했습니다.")
                        append(" 휴지통 상태를 다시 확인하세요.")
                    }
                    break
                }
            }
            if (expected != generation || _state.value.deviceId != expectedDeviceId) return@launch
            _state.value = _state.value.copy(
                emptying = false,
                message = if (failureMessage == null) "휴지통의 ${originalIds.size}개 항목을 영구 삭제했습니다." else null,
                error = failureMessage
            )
            loadTrash(expected, preserveError = failureMessage != null)
        }
    }

    fun dismissMessage(message: String) {
        if (_state.value.message == message) _state.value = _state.value.copy(message = null)
    }

    class Factory(private val repository: JetsonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalTrashViewModel(repository) as T
    }
}

internal fun isPurgeEligible(entry: TrashEntry): Boolean =
    entry.purgeSupported && entry.state in setOf("TRASHED", "PURGING")
