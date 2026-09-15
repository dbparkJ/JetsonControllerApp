package com.example.jetsoncontroller.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.field.GeoRunDashboard
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineUiState
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TaskFlowScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startOpensContextualPreparationWithoutSendingLegacyCommand() {
        val pipeline = ManagedPipeline("capture", "테스트 수집", state = PipelineState.STOPPED,
            entrypoint = "capture.py", config = "config.yaml", virtualenv = "venv")
        var state by mutableStateOf(PipelineUiState(deviceId = "A", controlAvailable = true,
            pipelines = listOf(pipeline), observedAtMillis = 1000))
        var refreshes = 0
        var starts = 0
        var preparations = 0
        compose.setContent { JetsonControllerTheme {
            PipelineListScreen(state, {}, { refreshes++; state = state.copy(isLoading = true) }, {},
                { _, _ -> starts++ }, {}, {}, {}, {}, {}, {}, startCapability = true, nowMillis = 1001,
                onPrepareRun = { preparations++ })
        } }
        compose.onNodeWithText("작업 시작").performScrollTo().performClick()
        compose.onNodeWithText("작업 시작 · 최종 점검").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, preparations)
            assertEquals(0, refreshes)
            assertEquals(0, starts)
        }
    }

    @Test fun historyDeletionRequiresConfirmationAndRunningRunsCannotBeDeleted() {
        val run = TaskRun("capture/log", "capture", "완료된 수집", "log", "2026-09-14T01:00:00Z", state = "COMPLETED")
        var state by mutableStateOf(FieldState(deviceId = "A", online = true, runs = listOf(run)))
        var deleted = 0
        compose.setContent { JetsonControllerTheme {
            GeoRunDashboard(state, emptyList(), "테스트 장비", 0, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onDeleteRun = { deleted++; state = state.copy(runs = emptyList()) })
        } }
        compose.onNodeWithTag("task-run-capture/log").performScrollTo().performTouchInput { swipeRight() }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, deleted) }
        compose.onNodeWithTag("task-run-capture/log").performTouchInput { swipeRight() }
        compose.onNode(hasText("휴지통으로 이동") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("완료된 수집").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, deleted); state = state.copy(runs = listOf(run.copy(state = "RUNNING"))) }
        compose.onNodeWithTag("task-run-capture/log").performScrollTo().performTouchInput { swipeRight() }
        compose.onNodeWithText("작업 이력을 휴지통으로 옮길까요?").assertDoesNotExist()
        compose.onNodeWithText("휴지통으로 이동").assertDoesNotExist()
    }

    @Test fun taskTabClearsSavedDetailsAndAlwaysReturnsToFreshTaskSelection() {
        lateinit var navigation: NavHostController
        compose.setContent { JetsonControllerTheme {
            navigation = rememberNavController()
            Column {
                TextButton(onClick = { navigateToTaskStart(navigation) }) { Text("작업 탭") }
                NavHost(navigation, startDestination = "dashboard") {
                    composable("dashboard") { Text("홈 화면") }
                    composable("pipelines") {
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        Column {
                            Text(if (expanded) "펼쳐진 작업" else "작업 선택 첫 화면")
                            TextButton(onClick = { expanded = true }) { Text("펼치기") }
                            TextButton(onClick = { navigation.navigate("detail") }) { Text("상세로 이동") }
                        }
                    }
                    composable("detail") { Text("작업 상세 화면") }
                    composable("settings") { Text("설정 화면") }
                }
            }
        } }
        compose.onNodeWithText("작업 탭").performClick()
        compose.onNodeWithText("상세로 이동").performClick()
        compose.runOnIdle { navigation.navigate("settings") {
            popUpTo("dashboard") { saveState = true }; restoreState = true
        } }
        compose.onNodeWithText("작업 탭").performClick()
        compose.onNodeWithText("작업 선택 첫 화면").assertIsDisplayed()
        compose.onNodeWithText("작업 상세 화면").assertDoesNotExist()
        compose.onNodeWithText("펼치기").performClick()
        compose.onNodeWithText("작업 탭").performClick()
        compose.onNodeWithText("작업 선택 첫 화면").assertIsDisplayed()
    }
    @Test fun homeTabDiscardsSavedChildScreenAndOpensFreshDashboard() {
        lateinit var navigation: NavHostController
        compose.setContent { JetsonControllerTheme {
            navigation = rememberNavController()
            Column {
                TextButton(onClick = { navigateToDashboard(navigation) }) { Text("홈 탭") }
                NavHost(navigation, startDestination = "dashboard") {
                    composable("dashboard") {
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        Column {
                            Text(if (expanded) "홈 펼침 상태" else "대시보드 첫 화면")
                            TextButton(onClick = { expanded = true }) { Text("홈 펼치기") }
                        }
                    }
                    composable("sensors") { Text("센서 상세 화면") }
                    composable("settings") { Text("설정 화면") }
                }
            }
        } }
        compose.runOnIdle {
            navigation.navigate("sensors")
            navigation.navigate("settings") { popUpTo("dashboard") { saveState = true }; restoreState = true }
        }
        compose.onNodeWithText("홈 탭").performClick()
        compose.onNodeWithText("대시보드 첫 화면").assertIsDisplayed()
        compose.onNodeWithText("센서 상세 화면").assertDoesNotExist()
        compose.onNodeWithText("홈 펼치기").performClick()
        compose.onNodeWithText("홈 탭").performClick()
        compose.onNodeWithText("대시보드 첫 화면").assertIsDisplayed()
    }

}
