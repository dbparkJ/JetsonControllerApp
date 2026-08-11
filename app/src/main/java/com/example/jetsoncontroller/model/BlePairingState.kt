package com.example.jetsoncontroller.model

sealed interface BlePairingState {

    data object Idle :
        BlePairingState

    data object Connecting :
        BlePairingState

    data object DiscoveringServices :
        BlePairingState

    data object VerifyingIdentity :
        BlePairingState

    data object Authenticating :
        BlePairingState

    data object EnablingNotifications :
        BlePairingState

    data class Ready(
        val deviceName: String
    ) : BlePairingState

    data class Error(
        val userMessage: String,
        val technicalCode: String? = null
    ) : BlePairingState
}
