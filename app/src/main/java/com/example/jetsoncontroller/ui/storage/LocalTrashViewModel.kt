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
    val loading: Boolean = false,
    val restoringId: String? = null,
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

    fun refresh() {
        if (!_state.value.online || _state.value.restoringId != null) return
        val expected = generation
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.getTrash().onSuccess { response ->
                if (expected == generation) _state.value = _state.value.copy(
                    entries = response.entries.filter { it.state != "RESTORED" },
                    refreshedAt = response.refreshedAt,
                    loading = false
                )
            }.onFailure { error ->
                if (expected == generation) _state.value = _state.value.copy(
                    loading = false,
                    error = userFacingFailure(error, "장비 휴지통을 불러오지 못했습니다. 다시 시도하세요.").message
                )
            }
        }
    }

    fun restore(entry: TrashEntry) {
        if (!_state.value.online || _state.value.restoringId != null || !entry.restoreSupported || entry.state != "TRASHED") return
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
                    refresh()
                }
            }.onFailure { error ->
                if (expected == generation) {
                    _state.value = _state.value.copy(
                        restoringId = null,
                        error = if (error is JetsonCommandResultUnknownException) {
                            "복원 결과를 확인하지 못했습니다. 자동 재시도하지 않고 휴지통을 다시 조회합니다."
                        } else userFacingFailure(error, "항목을 복원하지 못했습니다. 다시 시도하세요.").message
                    )
                    refresh()
                }
            }
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
