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

    @Test fun logUsesTheSharedNavigationArrowAndKeepsDeletionAsAnIndependentMenu() {
        val source = r8UiSource("upload/UploadQueueScreen.kt")
        assertFalse(source.contains("extra ="))
        assertTrue(source.contains("navigable = true"))
        assertFalse(source.contains("GeoIcons.chevronRight"))
        assertFalse(source.contains("Modifier.geoClickable"))
        assertTrue(source.contains("badge = if (isDeletableUploadJob(job))"))
        assertTrue(source.contains("GeoIconButton(GeoIcons.more"))
        assertTrue(source.contains("deletion = transferActionTarget(deviceId.orEmpty(), job) to job"))
        assertTrue(source.contains("enabled = !isLoading"))
        assertTrue(source.contains("visible.isEmpty() && !isLoading && error == null"))
        assertTrue(source.contains("if (queue.isEmpty()) Tone.ERROR else Tone.WARNING"))
    }

    @Test fun uploadServerErrorsExcludeEmptyStateAndAddressBelongsToTheServerRow() {
        val source = r8UiSource("upload/UploadTargetSettingsScreen.kt")
        assertTrue(source.contains("error != null && targets.isEmpty() -> Unit"))
        assertTrue(source.contains("meta = target.baseUrl?.let(::geoBreakableValue)"))
        assertFalse(source.contains("DevAddressRow("))
    }

    @Test fun confirmationDoesNotRepeatTheFolderNameOrRetryAction() {
        val source = r8UiSource("upload/UploadConfirmScreen.kt")
        assertFalse(source.contains("surveyProjectLabel, summary?.folderName"))
        assertFalse(source.contains("actionLabel = \"다시 확인\""))
        assertTrue(source.contains("enabled = !isLoading && !isCalculatingSource"))
        assertTrue(source.contains("surveySectionLabel?.takeIf(String::isNotBlank) ?: \"수집 자료\""))
        assertFalse(source.contains("summary?.folderName"))
        assertFalse(source.contains("path.substringAfterLast"))
        assertEquals(1, Regex("GeoCopyableValue\\(").findAll(source).count())
        assertTrue(source.contains("GeoCopyableValue(path, label = \"원본 경로\")"))
        val heading = r8UiSource("transfers/TransferLayout.kt").substringAfter("internal fun TransferHeading(")
        assertFalse(heading.contains("GeoCopyableValue("))
        assertTrue(heading.contains("Text(title"))
    }
}
