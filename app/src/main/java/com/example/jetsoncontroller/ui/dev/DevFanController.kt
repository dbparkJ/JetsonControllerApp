package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.FanStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class DevFanSnapshot(val status: FanStatus? = null, val loading: Boolean = false, val error: String? = null)

/** Device-scoped fan state lives with the developer tool; dashboard callers are adapters. */
internal class DevFanController(private val repository: JetsonRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(DevFanSnapshot())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var target = repository.selectedDeviceId.value to repository.transportState.value
    init {
        scope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { id, transport -> id to transport }
                .distinctUntilChanged().collect { synchronizeTarget() }
        }
    }
    private fun synchronizeTarget() {
        val current = repository.selectedDeviceId.value to repository.transportState.value
        if (current != target) {
            target = current
            generation++
            job?.cancel()
            mutable.value = DevFanSnapshot()
        }
    }
    private fun available(): Boolean {
        val transport = repository.transportState.value as? TransportState.Connected ?: return false
        return transport.type != TransportType.BLE && transport.deviceId.equals(repository.selectedDeviceId.value, true)
    }
    fun refresh() = request(null, null)
    fun auto() {
        synchronizeTarget()
        if (mutable.value.status?.autoAvailable == true) request("AUTO", null)
    }
    fun manual(percent: Int) {
        synchronizeTarget()
        val fan = mutable.value.status?.takeIf { it.available } ?: return
        request("MANUAL", fanManualPercent(percent, fan.minimumManualPercent))
    }
    private fun request(mode: String?, percent: Int?) {
        synchronizeTarget()
        if (!available() || mutable.value.loading) return
        val expected = generation
        val device = repository.selectedDeviceId.value
        val transport = repository.transportState.value
        mutable.value = mutable.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                if (!available() || device != repository.selectedDeviceId.value || transport != repository.transportState.value) return@launch
                val result = if (mode == null) repository.getFanStatus() else repository.setFan(mode, percent)
                if (expected != generation || device != repository.selectedDeviceId.value || transport != repository.transportState.value) return@launch
                mutable.value = result.fold({ DevFanSnapshot(status = it) }, {
                    mutable.value.copy(loading = false, error = if (mode == null) "팬 상태를 확인하지 못했습니다." else "팬을 제어하지 못했습니다.")
                })
            } finally {
                if (expected == generation) mutable.value = mutable.value.copy(loading = false)
            }
        }
    }
}
