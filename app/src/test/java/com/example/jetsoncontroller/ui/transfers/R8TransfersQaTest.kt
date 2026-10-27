package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8TransfersQaTest {
    @Test fun activeCardShowsOnePercentageAndCancellationLivesInTheDetail() {
        val source = r8UiSource("transfers/TransfersHomeScreen.kt")
        assertTrue(source.contains("GeoProgress(active.progress, showPercentage = false)"))
        assertFalse(source.contains("GeoActionRow(active.title"))
        assertFalse(source.contains("GeoButton(\"전송 취소\""))
        assertTrue(source.contains("GeoRow(\"전송 상세\""))
        assertTrue(r8UiSource("upload/UploadProgressScreen.kt").contains("TransferMutation.CANCEL"))
    }

    @Test fun readinessIsOwnedByTheActionNoteAndEmptyStateUsesSharedInsets() {
        val source = r8UiSource("transfers/TransfersHomeScreen.kt")
        val list = source.substringAfter("LazyColumn(")
        assertTrue(source.contains("onPickFiles), note = {"))
        assertFalse(list.contains("GeoReadinessLine("))
        assertFalse(source.contains("Modifier.padding(horizontal = 8.dp"))
        assertTrue(r8UiSource("transfers/TransferLayout.kt").contains("note = note"))
    }
}
