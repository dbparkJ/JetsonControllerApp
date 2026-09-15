package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import java.net.ConnectException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {
    @Test
    fun `shell hides raw discovery details outside developer mode`() {
        val raw = "LAN 장비 주소 확인 실패: 3 /111.111.111.110:44181"

        val operatorMessage = userFacingConnectionMessage(raw, developerModeEnabled = false)

        assertTrue(operatorMessage?.contains("같은 네트워크") == true)
        assertFalse(operatorMessage.orEmpty().contains("111.111.111.110"))
        assertFalse(operatorMessage.orEmpty().contains("실패: 3"))
        assertTrue(userFacingConnectionMessage(raw, developerModeEnabled = true) == raw)
    }

    @Test
    fun `shell preserves already actionable connection guidance`() {
        val guidance = "로컬 네트워크 권한을 허용해 주세요."

        assertTrue(userFacingConnectionMessage(guidance, false) == guidance)
    }

    @Test
    fun `connection details stay out of operator message`() {
        val raw = "Failed to connect to /111.111.111.110:44181 from /10.0.0.2:52731"

        val failure = userFacingFailure(ConnectException(raw))

        assertTrue(failure.message.contains("장비에 연결할 수 없습니다"))
        assertFalse(failure.message.contains("111.111.111.110"))
        assertFalse(failure.message.contains("ConnectException"))
        assertTrue(failure.technicalDetail.contains(raw))
    }

    @Test
    fun `unknown command result gives a state check action without raw identifiers`() {
        val error = JetsonCommandResultUnknownException(
            operation = "pipeline start request-123",
            stateQueryResult = Result.failure<Unit>(ConnectException("/111.111.111.110:44181")),
            cause = ConnectException("socket /111.111.111.110:44181")
        )

        val failure = userFacingFailure(error)

        assertTrue(failure.message.contains("상태 확인"))
        assertFalse(failure.message.contains("request-123"))
        assertFalse(failure.message.contains("111.111.111.110"))
        assertTrue(failure.technicalDetail.contains("request-123"))
    }
}
