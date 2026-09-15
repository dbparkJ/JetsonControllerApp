package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class TransferResultScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun receiptDistinguishesVerifiedAndNotYetVerifiedResults() {
        var job by mutableStateOf(completedJob(files = 12, bytes = 2_400_000_000))
        var verification by mutableStateOf<UploadVerification?>(
            UploadVerification(
                jobId = "job-1",
                targetId = "server-1",
                state = "COMPLETED",
                matched = true,
                deletionAllowed = true,
                filesTotal = 12,
                bytesTotal = 2_400_000_000
            )
        )
        var serverOpens = 0
        var verifyRequests = 0

        compose.setContent {
            JetsonControllerTheme {
                UploadProgressScreen(
                    job = job,
                    verification = verification,
                    isLoading = false,
                    message = null,
                    error = null,
                    onCancel = {},
                    onRetry = {},
                    onVerify = { verifyRequests++ },
                    onDeleteSource = {},
                    onRestoreSource = {},
                    onBack = {},
                    targetLabel = "서울 도로관리 서버",
                    onServerData = { serverOpens++ }
                )
            }
        }

        compose.onNodeWithText("서버 수신이 확인됐습니다").assertIsDisplayed()
        compose.onNodeWithText("서울 도로관리 서버").assertIsDisplayed()
        compose.onNodeWithText("서버 파일 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, serverOpens) }
        capture("transfer-receipt-verified")

        compose.runOnIdle {
            job = completedJob(files = null, bytes = null)
            verification = null
        }
        compose.onNodeWithText("진행률 미확인").assertIsDisplayed()
        compose.onNodeWithText("전송 용량 미확인").assertIsDisplayed()
        compose.onNodeWithText("0 B / 0 B").assertDoesNotExist()
        compose.onNodeWithText("개수 미확인 · 용량 미확인").assertIsDisplayed()
        compose.onNodeWithText("서버 파일 보기").assertDoesNotExist()
        compose.onNode(hasText("서버 수신 확인") and hasClickAction())
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, verifyRequests) }
        capture("transfer-receipt-unverified")
    }

    @Test fun activeUploadCanLeaveForHomeOrFilesWithoutCancelling() {
        var home = 0
        var files = 0
        var cancelled = 0
        compose.setContent {
            JetsonControllerTheme {
                UploadProgressScreen(
                    job = completedJob(files = 12, bytes = 2_400_000_000).copy(
                        state = UploadJobState.UPLOADING,
                        bytesTransferred = 1_200_000_000
                    ),
                    verification = null,
                    isLoading = false,
                    message = null,
                    error = null,
                    onCancel = { cancelled++ },
                    onRetry = {}, onVerify = {}, onDeleteSource = {}, onRestoreSource = {}, onBack = {},
                    onHome = { home++ },
                    onFiles = { files++ }
                )
            }
        }

        compose.onNodeWithContentDescription("홈으로").performClick()
        compose.onNodeWithContentDescription("파일로").performClick()
        compose.runOnIdle {
            assertEquals(1, home)
            assertEquals(1, files)
            assertEquals(0, cancelled)
        }
    }

    private fun completedJob(files: Int?, bytes: Long?) = UploadJob(
        id = "job-1",
        rootId = "recordings",
        relativePath = "gangnam-section-1",
        targetId = "server-1",
        state = UploadJobState.COMPLETED,
        bytesTotal = bytes,
        bytesTransferred = bytes,
        filesTotal = files,
        filesTransferred = files,
        currentFile = null,
        errorMessage = null,
        folderName = "강남대로 1구간",
        deletionEligible = true
    )

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "transfer-captures").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
