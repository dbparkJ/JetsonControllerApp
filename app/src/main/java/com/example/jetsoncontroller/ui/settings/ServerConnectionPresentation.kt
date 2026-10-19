package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.data.server.ServerEnvironment
import com.example.jetsoncontroller.data.server.ServerRequestException
import java.security.cert.CertificateException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

internal enum class ServerConnectionStatus {
    UNKNOWN,
    CHECKING,
    CONNECTED,
    LOGIN_REQUIRED,
    FAILED
}

internal enum class ServerConnectionFailure {
    CERTIFICATE,
    AUTHENTICATION,
    PROJECT_PERMISSION,
    UNAVAILABLE
}

internal fun classifyServerConnectionFailure(error: Throwable): ServerConnectionFailure {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    return when {
        causes.any { it is SSLException || it is CertificateException } ->
            ServerConnectionFailure.CERTIFICATE
        causes.filterIsInstance<ServerRequestException>().any { it.statusCode == 401 } ->
            ServerConnectionFailure.AUTHENTICATION
        causes.filterIsInstance<ServerRequestException>().any { it.statusCode == 403 } ||
            causes.any { it.message?.contains("project", ignoreCase = true) == true } ->
            ServerConnectionFailure.PROJECT_PERMISSION
        else -> ServerConnectionFailure.UNAVAILABLE
    }
}

internal fun ServerConnectionFailure.userMessage(): String = when (this) {
    ServerConnectionFailure.CERTIFICATE ->
        "서버 인증서를 확인할 수 없습니다. 관리자에게 문의해 주세요."
    ServerConnectionFailure.AUTHENTICATION ->
        "직원 인증이 거절되었습니다. 다시 로그인하세요."
    ServerConnectionFailure.PROJECT_PERMISSION ->
        "이 계정에는 이 프로젝트 권한이 없습니다. 관리자에게 문의해 주세요."
    ServerConnectionFailure.UNAVAILABLE ->
        "서버에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 시도하세요."
}

internal fun ServerConnectionFailure.status(): ServerConnectionStatus =
    if (this == ServerConnectionFailure.AUTHENTICATION) ServerConnectionStatus.LOGIN_REQUIRED
    else ServerConnectionStatus.FAILED

internal fun serverEnvironmentLabel(environment: ServerEnvironment): String = when (environment) {
    ServerEnvironment.DEVELOPMENT -> "개발"
    ServerEnvironment.TEST -> "시험"
    ServerEnvironment.PRODUCTION -> "운영"
}

internal fun formatServerCheckTime(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMillis))
