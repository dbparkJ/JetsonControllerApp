package com.example.jetsoncontroller.data.diagnostics

/** No payloads, exception messages, network addresses or device identifiers cross this boundary. */
internal object DiagnosticPrivacy {
    val events = setOf(
        "app_started", "app_foreground", "app_background", "power_state", "user_marker",
        "transport_state", "connection_intent", "recovery_state", "api_request", "api_response",
        "api_failure", "api_auth", "p2p_connection", "p2p_group", "p2p_channel", "p2p_cleanup",
        "network_state", "status_refresh", "incident_started", "incident_finished",
        "api_call_started", "api_call_ended", "api_authenticated", "api_request_headers",
        "api_connection", "api_endpoint", "api_hello_clock", "api_result_unknown", "rtk_state", "rtk_heartbeat"
    )
    private val numbers = setOf(
        "generation", "retryCount", "httpStatus", "durationMs", "lastAuthenticatedResponseAgeMs",
        "measurementAgeMs", "failureCount", "attemptId", "sessionId", "serverTimeMs", "offsetMs",
        "offsetUncertaintyMs", "responseBytes", "networkCount", "failureThreshold", "nextRetryMs",
        "endpointGeneration", "authRevision", "requestSequence", "localPort", "remotePort",
        "offsetEstimateMs", "uncertaintyMs", "responseAgeMs", "bytesFromCaster", "rtcmBytes"
    )
    private val booleans = setOf(
        "groupPresent", "peerPresent", "networkPresent", "authenticated", "cleanupConfirmed",
        "success", "reused", "foreground", "interactive", "idle", "powerSave", "charging",
        "validated", "metered", "vpn", "wifi", "cellular", "ethernet", "intended", "stale",
        "sensorAvailable", "sensorFresh", "ntripConnected", "active", "preparing", "errorPresent"
    )
    private val enums = setOf(
        "oldState", "newState", "desiredTransport", "actualTransport", "state", "reasonCode",
        "exceptionClass", "route", "requestKind", "method", "outcome", "group", "peer", "networkTransports"
    )
    // String callers must use a reviewed finite vocabulary. Enum callers supply compile-time constants.
    private val tokens = setOf(
        "UNKNOWN", "NONE", "LAN", "BLE", "WIFI_DIRECT", "CELLULAR", "WIFI", "VPN", "ETHERNET",
        "CONNECTED", "CONNECTING", "DISCONNECTED", "DISCONNECTING", "ERROR", "FAILED", "READY",
        "IDLE", "STOPPED", "STARTED", "RETRYING", "EXHAUSTED", "CANCELLED", "SUCCESS", "FAILURE",
        "Connected", "Connecting", "Disconnected", "Error", "Idle", "Unavailable",
        "PRESENT", "ABSENT", "AUTHENTICATED", "UNAUTHENTICATED", "AUTH_FAILED", "TIMEOUT",
        "IO", "TLS", "HTTP", "CANCELLED", "OTHER", "GET", "POST", "PUT", "DELETE", "PATCH",
        "HEAD", "OPTIONS", "STATUS", "HELLO", "COMMAND", "READ", "WRITE", "DEFAULT", "BOUND", "DIRECT",
        "CAPABILITIES", "PIPELINE", "UPLOAD", "STORAGE", "RTK", "SYSTEM", "PREVIEW",
        "AUTH_EXPIRED", "RESPONSE_SIGNATURE", "UNSIGNED_RESPONSE", "AUTHENTICATION", "CANCELED",
        "APPLIED", "DISCARDED", "REQUESTED", "AUTO", "API_THRESHOLD", "TRUST_FAILURE", "BUDGET_EXHAUSTED",
        "USER", "LINK_LOST", "API_FAILURE", "CHANNEL_LOST", "PERMISSION", "NETWORK_LOST",
        "SocketTimeoutException", "IOException", "ConnectException", "UnknownHostException",
        "SSLException", "SSLHandshakeException", "SSLPeerUnverifiedException", "CancellationException"
    )
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun fields(input: Map<String, Any?>): Map<String, Any> = buildMap {
        input.entries.take(48).forEach { (key, value) ->
            when {
                key in numbers && (value is Byte || value is Short || value is Int || value is Long || value is Float || value is Double) &&
                    (value as Number).toDouble().isFinite() -> put(key, value)
                key in booleans && value is Boolean -> put(key, value)
                key in enums && value is Enum<*> -> put(key, value.name)
                key in enums && value is String && value in tokens -> put(key, value)
                key in setOf("requestId", "clientId", "connectionId") && value is String && uuid.matches(value) -> put(key, value)
                key in setOf("requestRef", "deviceRef", "endpointRef", "localAddressRef", "remoteAddressRef") &&
                    value is String && value.matches(Regex("[0-9a-f]{16}")) -> put(key, value)
            }
        }
    }
}
