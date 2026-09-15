package com.example.jetsoncontroller.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.LocationPrecisionDuration
import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.ui.sensors.GnssMapScreen
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Fixture-only map checks. No live location, sensor, or device operation is triggered. */
class GnssMapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mixedPrecisionMapUsesHumanLabelsAndOpensQualityDetails() {
        val returns = AtomicInteger()
        setMapContent(fontScale = 1f, onReturn = { returns.incrementAndGet() })

        compose.onNodeWithText("정밀 위치 50%").assertIsDisplayed()
        compose.onAllNodesWithText("FIXED", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("RTK", substring = true).assertCountEquals(0)
        val detailAction = compose.onNodeWithText("위치 상세").assertIsDisplayed()
        assertTrue(
            detailAction.fetchSemanticsNode().boundsInRoot.bottom <=
                compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        )
        detailAction.performClick()
        compose.onNodeWithText("위치 품질 상세").assertIsDisplayed()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("닫기")))
            .performScrollToNode(hasText("닫기"))
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("이전 화면으로").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(returns.get() == 1) }
        captureWindow("map-quality-normal")
    }

    @Test
    fun largeFontKeepsQualityActionReachableAndDetailsScrollable() {
        setMapContent(fontScale = 2f)

        assertTrue(compose.onNodeWithTag("gnss-map-pane").fetchSemanticsNode().boundsInRoot.height > 100f)
        val backgroundHeadlineHeight = compose.onNodeWithText("정밀 위치 50%")
            .fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("위치 상세").assertIsDisplayed().performClick()
        val sheetTitleHeight = compose.onNodeWithText("위치 품질 상세")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.height
        assertTrue(sheetTitleHeight >= backgroundHeadlineHeight)
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("닫기")))
            .performScrollToNode(hasText("닫기"))
        compose.onNodeWithText("닫기").assertIsDisplayed()
        compose.onAllNodesWithText("FIXED", substring = true).assertCountEquals(0)
        captureWindow("map-quality-large-font")
    }

    private fun setMapContent(fontScale: Float, onReturn: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale)
            ) {
                JetsonControllerTheme {
                    GnssMapScreen(
                        gnss = GnssSensorStatus(
                            configured = true,
                            connected = true,
                            active = true,
                            fixType = "FIXED",
                            fixName = "RTK FIX",
                            rtkStatus = "FIXED",
                            latitude = 37.5665,
                            longitude = 126.9780,
                            satellites = 18
                        ),
                        telemetryFresh = true,
                        deviceOnline = true,
                        route = route,
                        quality = quality,
                        routeLabel = "도심 도로 조사 구간 이름이 길어져도 안전하게 표시되는 수집",
                        onBack = onReturn
                    )
                }
            }
        }
    }

    private fun captureWindow(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "map-screen-captures"
        ).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            instrumentation.uiAutomation.takeScreenshot().compress(
                android.graphics.Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }
    }

    private val route = listOf(
        RoutePoint(37.5660, 126.9775, 1, fixState = "FIXED", sensorState = "ACTIVE"),
        RoutePoint(37.5665, 126.9780, 2, fixState = "FLOAT", sensorState = "ACTIVE"),
        RoutePoint(37.5670, 126.9785, 3, fixState = "NO_FIX", sensorState = "ACTIVE")
    )

    private val quality = RunQuality(
        sampleState = "OBSERVED",
        elapsedDurationMillis = 120_000,
        rtkObservedDurationMillis = 100_000,
        rtkUnknownDurationMillis = 20_000,
        rtkFixDurationMillis = 50_000,
        rtkFixRatio = .5,
        observationCount = 20,
        locationPrecision = listOf(
            LocationPrecisionDuration("FIXED", 50_000, .5),
            LocationPrecisionDuration("FLOAT", 30_000, .3),
            LocationPrecisionDuration("NO_FIX", 20_000, .2)
        ),
        problemIntervals = List(8) { index ->
            QualityProblemInterval(
                kind = if (index % 2 == 0) "RTK_NOT_FIXED" else "GNSS_LOST",
                startedAtEpochMillis = 1_757_894_400_000L + index * 1_000L,
                endedAtEpochMillis = 1_757_894_400_500L + index * 1_000L,
                durationMillis = 500L,
                startRoutePointIndex = index.coerceAtMost(route.lastIndex)
            )
        }
    )
}
