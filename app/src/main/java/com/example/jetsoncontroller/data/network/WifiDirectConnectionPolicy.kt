package com.example.jetsoncontroller.data.network

/** Owns the target and callback lifetime, including the asynchronous Android cleanup. */
internal class WifiDirectConnectionSession {
    var generation: Long = 0
        private set
    var peerAddress: String? = null
        private set
    var phase: Phase = Phase.IDLE
        private set

    enum class Phase { IDLE, CONNECTING, CONNECTED, CLEANING_UP }

    fun begin(peerAddress: String): Long? {
        if (phase != Phase.IDLE) return null
        this.peerAddress = peerAddress
        phase = Phase.CONNECTING
        return ++generation
    }

    fun isCurrent(callbackGeneration: Long): Boolean = generation == callbackGeneration

    fun isConnecting(callbackGeneration: Long): Boolean =
        isCurrent(callbackGeneration) && phase == Phase.CONNECTING

    fun acceptGroup(
        callbackGeneration: Long,
        ownerAddress: String?,
        clientAddresses: Collection<String>
    ): Boolean {
        if (!isCurrent(callbackGeneration) ||
            phase !in setOf(Phase.CONNECTING, Phase.CONNECTED) ||
            !wifiDirectGroupBelongsToPeer(peerAddress, ownerAddress, clientAddresses)
        ) return false
        if (phase == Phase.CONNECTING) generation += 1
        phase = Phase.CONNECTED
        return true
    }

    fun beginCleanup(): Long? {
        if (phase == Phase.IDLE || phase == Phase.CLEANING_UP) return null
        phase = Phase.CLEANING_UP
        return ++generation
    }

    fun isCleaningUp(callbackGeneration: Long): Boolean =
        isCurrent(callbackGeneration) && phase == Phase.CLEANING_UP

    fun finishCleanup(callbackGeneration: Long): Boolean {
        if (!isCleaningUp(callbackGeneration)) return false
        reset()
        return true
    }

    fun reset() {
        generation += 1
        phase = Phase.IDLE
        peerAddress = null
    }
}

internal fun wifiDirectGroupBelongsToPeer(
    peerAddress: String?,
    ownerAddress: String?,
    clientAddresses: Collection<String>
): Boolean = !peerAddress.isNullOrBlank() &&
    (peerAddress.equals(ownerAddress, ignoreCase = true) ||
        clientAddresses.any { peerAddress.equals(it, ignoreCase = true) })
