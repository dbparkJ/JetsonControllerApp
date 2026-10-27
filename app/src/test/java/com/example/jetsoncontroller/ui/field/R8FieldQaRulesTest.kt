package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8FieldQaRulesTest {
    @Test fun missingResultMetricsUseOneUnknownState() {
        val source = resultTestRun()
        val missing = source.copy(startedAt = "unknown", finishedAt = null, quality = null,
            telemetry = null, output = source.output.copy(manifest = null))
        val figures = resultFigures(missing, null)
        assertEquals(4, figures.size)
        assertTrue(figures.all { it.value == null })
        assertTrue(figures.all { figurePresentation(it).unknown })
        assertEquals(1, figures.map(::figurePresentation).distinct().size)
    }

    @Test fun zeroResultMetricsRemainMeasuredValues() {
        val source = resultTestRun()
        val run = source.copy(finishedAt = source.startedAt,
            output = source.output.copy(manifest = source.output.manifest!!.copy(bytesTotal = 0L)),
            quality = RunQuality(rtkFixRatio = 0.0, rtkObservedDurationMillis = 1_000L))
        val figures = resultFigures(run, ResultZoneObservation(0, 0L, 0L))
        assertTrue(figures.none { figurePresentation(it).unknown })
        assertEquals(listOf("1분 미만", "0\u00a0B", "0%", "0회"), figures.map { it.value })
    }

    @Test fun unverifiedTelemetryAndStorageCannotSupplyResultCapacity() {
        val run = runStopFixture()
        val mismatched = run.copy(telemetry = run.telemetry!!.copy(runId = "another-run"))
        assertNull(resultFigures(mismatched, null)[1].value)
        val stored = resultTestRun()
        assertNull(resultFigures(stored.copy(output = stored.output.copy(manifestState = "PENDING")), null)[1].value)
    }

    @Test fun clockUsesUnknownTextRoleAndSeparatesUnitAtTheBaseline() {
        val screen = r8UiSource("field/ActiveRunScreen.kt")
        assertTrue(screen.contains("if (elapsed?.unknown != false) type.titleSm else type.clock"))
        assertTrue(screen.contains("if (capacityShown?.unknown != false) type.titleSm else capacityValueStyle"))
        assertTrue(screen.contains("type.clock.lineHeight.toDp()"))
        assertTrue(screen.contains("capacityValueStyle.lineHeight.toDp()"))
        assertTrue(screen.contains("Text(capacityParts.second, Modifier.alignByBaseline()"))
        assertTrue(screen.contains("style = type.unit"))
        assertFalse(screen.contains("Text(shown.value, color = colors.text, style = capacityStyle)"))
    }

    @Test fun runStatusOwnsCurrentPositionAndObservedQualityTogether() {
        val screen = r8UiSource("field/ActiveRunScreen.kt")
        assertTrue(screen.contains("GeoGroupedSection(\"수집 상태\""))
        assertTrue(screen.contains("GeoPropertyRow(\"현재 위치\", it, tone = positionTone)"))
        assertTrue(screen.contains("\"정밀 위치 비율\" else figure.label"))
        assertTrue(screen.contains("GeoPropertyRow(\"마지막 확인\", observedLabel)"))
        assertFalse(screen.contains("GeoBadge(it, positionTone"))
        assertFalse(screen.contains("GeoBadgeVariant.SOFT"))
    }

    @Test fun actionNotesStayWithTheirFixedActions() {
        val active = r8UiSource("field/ActiveRunScreen.kt")
        val result = r8UiSource("field/RunResultScreen.kt")
        assertTrue(active.contains("note = { GeoActionNote(readiness, icon = noteIcon) }"))
        assertFalse(active.contains("GeoReadinessLine(readiness"))
        assertTrue(result.contains("GeoActionNote(\"\$deviceName · \$connectionLabel · \$actionExplanation\")"))
        assertFalse(result.contains("GeoReadinessLine("))
        assertTrue(result.contains("enabled = chain.enabled && callbackAvailable"))
    }

    @Test fun historyCopyValuesShareThePropertyRowsWithoutDuplicateLabels() {
        val history = r8UiSource("field/HistoryEntryScreen.kt").substringBefore("internal fun FieldTechnicalInfo")
        assertTrue(history.contains("GeoPropertyRow(\"기록 ID\", entry.id, valueContent = { GeoCopyableValue(entry.id) })"))
        assertTrue(history.contains("GeoPropertyRow(\"수집 ID\", id, valueContent = { GeoCopyableValue(id) })"))
        assertTrue(history.contains("GeoPropertyRow(\"경로\", path, valueContent = { GeoCopyableValue(path) })"))
        assertFalse(history.contains("GeoCopyableValue(entry.id, label ="))
    }

    @Test fun developerRootKeepsCommonSwitchInsetsAndSemanticDoorIcons() {
        val developer = r8UiSource("field/DeveloperScreen.kt")
        assertTrue(developer.contains("GeoIconTile(developerDoorIcon(door.destination))"))
        assertTrue(developer.contains("DevDestination.TERMINAL -> GeoIcons.terminal"))
        assertTrue(developer.contains("DevDestination.FAN -> GeoIcons.fan"))
        assertTrue(developer.contains("DevDestination.NETWORK -> GeoIcons.wifi"))
        assertTrue(developer.contains("DevDestination.SENSOR_TEST -> GeoIcons.camera"))
        assertTrue(developer.contains("DevDestination.PIPELINES -> GeoIcons.log"))
        assertTrue(developer.contains("DevDestination.NEW_PIPELINE -> GeoIcons.add"))
        assertTrue(developer.contains("description = \"상세 화면 끝에 기술 정보를 보여 줍니다.\", divider = false"))
    }

    @Test fun fieldScreensDelegateToolbarInsetsAndKeepMapStopPlacement() {
        val active = r8UiSource("field/ActiveRunScreen.kt")
        val history = r8UiSource("field/HistoryEntryScreen.kt")
        assertTrue(active.contains("GeoLargeTitleScaffold(\"수집 중\", onBack = onBack)"))
        assertTrue(history.contains("GeoLargeTitleScaffold(title, Modifier.padding(padding), subtitle = subtitle, onBack = onBack"))
        assertTrue(active.contains("map = { map() }, sheetContent = { content() }"))
        assertTrue(active.contains("primaryActionModifier = Modifier.focusRequester(stopFocus).testTag(\"request-stop\")"))
    }
}
