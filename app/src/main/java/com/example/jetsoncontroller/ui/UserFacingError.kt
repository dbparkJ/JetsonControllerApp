package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.data.network.JetsonAuthenticationRecoveryException
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.network.JetsonEndpointUnavailableException
import com.example.jetsoncontroller.data.network.JetsonResponseSignatureException
import com.example.jetsoncontroller.data.network.JetsonSessionExpiredException
import com.example.jetsoncontroller.data.network.JetsonUnsignedServerErrorException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Operator-safe copy plus detail that may only be rendered in Developer Mode. */
data class UserFacingFailure(
    val message: String,
    val technicalDetail: String
)

fun userFacingFailure(
    error: Throwable,
    fallback: String = "요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요."
): UserFacingFailure {
    val causes = generateSequence(error as Throwable?) { it.cause }.take(8).toList()
    val message = when {
        causes.any { it is JetsonCommandResultUnknownException } ->
            "요청 결과를 아직 확인하지 못했습니다. 상태 확인을 눌러 다시 확인하세요."
        causes.any { it is JetsonSessionExpiredException } ->
            "장비 인증이 만료되었습니다. 장비에 다시 연결해 주세요."
        causes.any { it is JetsonEndpointUnavailableException } ->
            "이 기능을 현재 장비에서 사용할 수 없습니다. 관리자에게 장비 소프트웨어 확인을 요청하세요."
        causes.any {
            it is JetsonResponseSignatureException ||
                it is JetsonUnsignedServerErrorException ||
                it is JetsonAuthenticationRecoveryException ||
                it is SSLException
        } -> "장비 응답을 안전하게 확인하지 못했습니다. 연결을 끊고 다시 연결해 주세요."
        causes.any { it is SocketTimeoutException } ->
            "장비 응답이 늦습니다. 연결 상태를 확인하고 다시 시도하세요."
        causes.any { it is ConnectException || it is UnknownHostException || it is IOException } ->
            "장비에 연결할 수 없습니다. 같은 네트워크인지 확인하고 다시 시도하세요."
        else -> fallback
    }
    val detail = causes.joinToString("\n") { cause ->
        val name = cause::class.java.simpleName.ifBlank { cause::class.java.name }
        cause.message?.takeIf(String::isNotBlank)?.let { "$name: $it" } ?: name
    }
    return UserFacingFailure(message, detail)
}
