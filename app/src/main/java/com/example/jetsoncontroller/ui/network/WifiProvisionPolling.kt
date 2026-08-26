package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.model.WifiProvisionPhase
import com.example.jetsoncontroller.model.WifiProvisionStatus
import com.example.jetsoncontroller.model.phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal const val WIFI_PROVISION_POLL_MAX_ATTEMPTS = 100
internal const val WIFI_PROVISION_POLL_INTERVAL_MILLIS = 1_000L
private const val WIFI_PROVISION_POLL_MAX_CONSECUTIVE_ERRORS = 3

internal suspend fun awaitWifiProvisionCompletion(
    expectedSsid: String,
    maxAttempts: Int = WIFI_PROVISION_POLL_MAX_ATTEMPTS,
    maxConsecutiveErrors: Int = WIFI_PROVISION_POLL_MAX_CONSECUTIVE_ERRORS,
    pollIntervalMillis: Long = WIFI_PROVISION_POLL_INTERVAL_MILLIS,
    fetchStatus: suspend () -> Result<WifiProvisionStatus>,
    pause: suspend (Long) -> Unit = { delay(it) }
): Result<WifiProvisionStatus> {
    require(maxAttempts > 0)
    require(maxConsecutiveErrors > 0)

    var consecutiveErrors = 0
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        val result = fetchStatus()
        val status = result.getOrNull()
        if (status != null) {
            val statusMatchesRequest = status.ssid == null || status.ssid == expectedSsid
            if (!statusMatchesRequest) {
                consecutiveErrors += 1
                lastError = IllegalStateException(
                    "다른 Wi-Fi 요청의 상태가 반환되었습니다: ${status.ssid}"
                )
            } else {
                when (status.phase()) {
                    WifiProvisionPhase.CONNECTED,
                    WifiProvisionPhase.FAILED -> return Result.success(status)
                    WifiProvisionPhase.CONNECTING,
                    WifiProvisionPhase.IDLE -> {
                        consecutiveErrors = 0
                        lastError = null
                    }
                    WifiProvisionPhase.UNKNOWN -> return Result.failure(
                        IllegalStateException(
                            "Jetson이 알 수 없는 Wi-Fi 상태를 반환했습니다: ${status.state}"
                        )
                    )
                }
            }
        } else {
            val error = result.exceptionOrNull()
                ?: IllegalStateException("Wi-Fi 연결 상태를 확인하지 못했습니다.")
            if (error is CancellationException) {
                throw error
            }
            consecutiveErrors += 1
            lastError = error
        }

        if (consecutiveErrors >= maxConsecutiveErrors) {
            return Result.failure(
                IllegalStateException(
                    "Wi-Fi 요청은 접수됐지만 최종 결과를 확인하지 못했습니다: " +
                        (lastError?.message ?: "상태 조회 실패"),
                    lastError
                )
            )
        }
        if (attempt + 1 < maxAttempts) {
            pause(pollIntervalMillis)
        }
    }

    return Result.failure(
        IllegalStateException("Wi-Fi 연결 결과 확인 시간이 초과되었습니다.")
    )
}

internal fun wifiProvisionConnectedMessage(
    status: WifiProvisionStatus,
    fallbackSsid: String
): String = "${status.ssid ?: fallbackSsid} Wi-Fi 연결에 성공했습니다."

internal fun wifiProvisionFailedMessage(
    status: WifiProvisionStatus,
    fallbackSsid: String
): String {
    val backendMessage = status.message?.trim()
    val reason = when {
        backendMessage == "Wi-Fi authentication failed; check the password" ->
            "Wi-Fi 비밀번호가 맞는지 확인해 주세요."
        backendMessage == "Wi-Fi network was not found after scanning" ->
            "Jetson에서 이 Wi-Fi를 찾지 못했습니다. 공유기 신호와 SSID를 확인해 주세요."
        backendMessage == "Wi-Fi radio coordination is unavailable" ||
            backendMessage == "Wi-Fi Direct discovery could not be paused" ->
            "Jetson의 무선 모드 전환에 실패했습니다. 잠시 후 다시 시도해 주세요."
        backendMessage?.startsWith("NetworkManager rejected the connection") == true ->
            "비밀번호나 네트워크 설정을 확인해 주세요."
        backendMessage == "Wi-Fi connection timed out" ->
            "연결 시간이 초과되었습니다. 신호와 비밀번호를 확인해 주세요."
        backendMessage == "NetworkManager command is unavailable" ||
            backendMessage == "NetworkManager is unavailable" ->
            "Jetson의 네트워크 관리 서비스를 사용할 수 없습니다."
        backendMessage.isNullOrEmpty() -> "Jetson이 네트워크 연결에 실패했습니다."
        else -> backendMessage
    }
    return "${status.ssid ?: fallbackSsid} Wi-Fi 연결 실패: $reason"
}
