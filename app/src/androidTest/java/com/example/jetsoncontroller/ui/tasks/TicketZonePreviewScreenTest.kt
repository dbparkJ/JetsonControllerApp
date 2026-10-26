package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.components.geo.GeoStartCheck
import com.example.jetsoncontroller.ui.components.geo.GeoStartPhase
import com.example.jetsoncontroller.ui.components.geo.GeoStartSheet
import com.example.jetsoncontroller.ui.components.geo.GeoStartSheetState
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Slot fixtures exercise layout and opening actions without a map SDK or device connection. */
class TicketZonePreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailAddsAndRemovesTheMapWhenWebGeometryChanges() {
        var ticket by mutableStateOf(ServerTicket("row", "수집 구간", geometry = JsonParser.parseString("""
            {"schemaVersion":1,"features":[{"type":"Feature","properties":{"role":"POINT"},
            "geometry":{"type":"Point","coordinates":[127,37]}}],"range":null,"revision":1}
        """)))
        compose.setContent {
            JetsonControllerTheme {
                TicketDetailScreen(ticket, "OPERATOR", null, {}, {},
                    mapCard = { Text("수집 구역", Modifier.testTag("ticket-zone-preview")) })
            }
        }
        compose.onNodeWithTag("ticket-zone-preview").assertIsDisplayed()
        compose.runOnIdle { ticket = ticket.copy(geometry = null) }
        compose.onNodeWithTag("ticket-zone-preview").assertDoesNotExist()
    }

    @Test fun startSheetShowsTheTappablePreviewBeforePropertiesAndChecks() {
        var opened = 0
        compose.setContent {
            JetsonControllerTheme {
                GeoStartSheet(GeoStartSheetState("북측", "도로 조사", "장치", "구역 안",
                    GeoStartPhase.CHECKING, listOf(GeoStartCheck("저장 공간", GeoStartPhase.CHECKING)),
                    ticketLinked = true, insideZone = true), {}, {}, {},
                    mapPreview = {
                        Box(Modifier.fillMaxWidth().height(200.dp).testTag("start-zone-preview")
                            .clickable { opened++ }) { Text("구역 안") }
                    })
            }
        }
        val preview = compose.onNodeWithTag("start-zone-preview").assertIsDisplayed()
        assertTrue(preview.fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("시작 전 점검").fetchSemanticsNode().boundsInRoot.top)
        preview.performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
