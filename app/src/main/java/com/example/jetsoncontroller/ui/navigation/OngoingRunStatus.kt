package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.ui.navigateSingle
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStrip
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStripState
import com.example.jetsoncontroller.ui.field.ongoingRunStripState
import com.example.jetsoncontroller.ui.field.runTarget

@Composable
internal fun OngoingRunStatus(
    state: AppNavigationState,
    deviceId: String? = state.selectedDeviceId,
    deviceName: String = state.selectedDeviceName,
    candidates: List<GeoStatusStripState> = emptyList()
) {
    val survey = state.surveyRunState
    GeoStatusStrip(candidates + listOfNotNull(ongoingRunStripState(
        deviceId, deviceName, survey.activeRun,
        onOpenRun = { target ->
            val current = state.surveyRunViewModel.uiState.value
            if (state.latestSelectedDeviceId.equals(target.deviceId, true) &&
                current.activeRun?.let(::runTarget) == target) {
                state.navController.navigateSingle(Routes.ACTIVE_RUN)
            }
        },
        awaitingStart = survey.pendingStart != null || survey.unconfirmedRunId != null || survey.restoringLocalState
    )))
}
