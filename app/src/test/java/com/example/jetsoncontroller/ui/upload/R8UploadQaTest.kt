package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8UploadQaTest {
    @Test fun detailTitleAndResultToneAreIndependentOfButtonAvailability() {
        val source = r8UiSource("upload/UploadProgressScreen.kt")
        assertTrue(source.contains("TransferPage(job?.let(::transferSourceName)"))
        assertTrue(source.contains("subtitleStatus = actions?.let"))
        assertFalse(source.contains("primaryEnabled -> Tone.SUCCESS"))
        assertTrue(source.contains("else -> actions.tone"))
        assertTrue(source.contains("primaryAction = progressAction, note = {"))
    }

    @Test fun logMenuOccupiesTheTrailingColumnWithoutAnExtraRowOrSecondArrow() {
        val source = r8UiSource("upload/UploadQueueScreen.kt")
        assertFalse(source.contains("extra ="))
        assertTrue(source.contains("navigable = false"))
        assertTrue(source.contains("else GeoIconButton(GeoIcons.chevronRight"))
        assertTrue(source.contains("enabled = !isLoading"))
        assertTrue(source.contains("visible.isEmpty() && !isLoading && error == null"))
        assertTrue(source.contains("if (queue.isEmpty()) Tone.ERROR else Tone.WARNING"))
    }

    @Test fun uploadServerErrorsExcludeEmptyStateAndAddressBelongsToTheServerRow() {
        val source = r8UiSource("upload/UploadTargetSettingsScreen.kt")
        assertTrue(source.contains("error != null && targets.isEmpty() -> Unit"))
        assertTrue(source.contains("GeoRow(target.label, { open(target) }, meta = target.baseUrl"))
        assertFalse(source.contains("DevAddressRow("))
    }

    @Test fun confirmationDoesNotRepeatTheFolderNameOrRetryAction() {
        val source = r8UiSource("upload/UploadConfirmScreen.kt")
        assertFalse(source.contains("surveyProjectLabel, summary?.folderName"))
        assertFalse(source.contains("actionLabel = \"다시 확인\""))
        assertTrue(source.contains("enabled = !isLoading && !isCalculatingSource"))
    }
}
