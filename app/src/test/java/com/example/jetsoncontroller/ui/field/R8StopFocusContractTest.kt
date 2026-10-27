package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8StopFocusContractTest {
    @Test fun sheetAndPaneStopActionsShareTheConfirmationOpener() {
        val screen = r8UiSource("field/ActiveRunScreen.kt")
        assertTrue(screen.contains("openerFocusRequester = stopFocus,"))
        assertFalse(screen.contains("stopFocus.takeIf"))
        assertTrue(screen.contains("primaryActionModifier = Modifier.focusRequester(stopFocus).testTag(\"request-stop\")"))
        assertTrue(screen.contains("GeoFixedActionBar(action, Modifier.focusRequester(stopFocus).testTag(\"request-stop\"))"))
        assertTrue(screen.contains("GeoConfirmKind.DANGER"))
        assertTrue(screen.contains("stopEvent(RunStopEvent.CONFIRM)"))
    }
}
