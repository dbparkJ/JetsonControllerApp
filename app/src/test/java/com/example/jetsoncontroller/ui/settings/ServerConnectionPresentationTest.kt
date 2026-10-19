package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.data.server.ServerEnvironment
import com.example.jetsoncontroller.data.server.ServerRequestException
import java.time.Instant
import java.time.ZoneId
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConnectionPresentationTest {
    @Test
    fun failuresMapToOperatorMessagesWithoutExposingTheExceptionText() {
        assertEquals(
            "서버 인증서를 확인할 수 없습니다. 관리자에게 문의해 주세요.",
            classifyServerConnectionFailure(SSLException("private host detail")).userMessage()
        )
        assertEquals(
            "직원 인증이 거절되었습니다. 다시 로그인하세요.",
            classifyServerConnectionFailure(ServerRequestException(401)).userMessage()
        )
        assertEquals(
            "이 계정에는 이 프로젝트 권한이 없습니다. 관리자에게 문의해 주세요.",
            classifyServerConnectionFailure(ServerRequestException(403)).userMessage()
        )
        assertEquals(
            "서버에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 시도하세요.",
            classifyServerConnectionFailure(IllegalStateException("raw response detail")).userMessage()
        )
    }

    @Test
    fun onlyRejectedCredentialsRequestAnotherLogin() {
        assertEquals(
            ServerConnectionStatus.LOGIN_REQUIRED,
            ServerConnectionFailure.AUTHENTICATION.status()
        )
        assertEquals(
            ServerConnectionStatus.FAILED,
            ServerConnectionFailure.PROJECT_PERMISSION.status()
        )
    }

    @Test
    fun environmentAndCheckTimeUseTheOperatorLabels() {
        assertEquals("개발", serverEnvironmentLabel(ServerEnvironment.DEVELOPMENT))
        assertEquals("시험", serverEnvironmentLabel(ServerEnvironment.TEST))
        assertEquals("운영", serverEnvironmentLabel(ServerEnvironment.PRODUCTION))
        val fixed = Instant.parse("2026-10-20T07:36:00Z").toEpochMilli()
        assertEquals("16:36", formatServerCheckTime(fixed, ZoneId.of("Asia/Seoul")))
    }
}
