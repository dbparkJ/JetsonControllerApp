package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8StorageQaTest {
    @Test fun fileRowsShareGroupsAndKeepCopyingInThePreview() {
        val browser = r8UiSource("storage/FileBrowser.kt")
        val selection = r8UiSource("storage/R7FileSelectionRow.kt")
        val direct = r8UiSource("storage/DirectServerScreen.kt")
        val proxy = r8UiSource("storage/ServerStorageScreen.kt")
        assertTrue(browser.contains("R7Section(day"))
        assertTrue(browser.contains("maxContentWidth = 720.dp"))
        assertTrue(browser.contains("GeoCopyableValue(state.currentPath, label = \"폴더 경로\")"))
        listOf(browser, direct, proxy, selection).forEach {
            assertFalse(it.contains("extra = { GeoCopyableValue("))
        }
        assertFalse(selection.contains("R7Card("))
        assertTrue(selection.contains("Checkbox(selected, { onSelect() }"))
        assertTrue(selection.contains("geoClickable(enabled = enabled, onClick = onOpen)"))
        val preview = r8UiSource("storage/DeviceStorageScreen.kt")
        assertTrue(preview.contains("GeoCopyableValue(name, label = \"파일명\")"))
        assertTrue(preview.contains("dimensions?.let { \"해상도\" to it }"))
        assertFalse(preview.contains("Text(it, Modifier.align(Alignment.BottomStart)"))
    }

    @Test fun initialLoadingDoesNotUseThePullRefreshIndicatorOrFreshnessLine() {
        val source = r8UiSource("storage/StorageLayout.kt")
        assertTrue(source.contains("PullToRefreshBox(refreshing && userRefresh"))
        assertTrue(source.contains("if (loading && instant == null) return"))
        assertTrue(source.contains("enabled = !loading"))
        assertTrue(r8UiSource("storage/FileBrowser.kt").contains("forceVisible = failure != null"))
        assertTrue(r8UiSource("storage/FileBrowser.kt").contains("if (state.entries.isEmpty()) Tone.ERROR else Tone.WARNING"))
    }

    @Test fun serverNavigationLivesUnderEachPaneTitleAndUsesTabs() {
        val direct = r8UiSource("storage/DirectServerScreen.kt")
        val listPane = direct.substringAfter("list = { GeoLargeTitleScaffold(").substringBefore("detail = {")
        assertTrue(listPane.contains("serverContext()"))
        assertTrue(listPane.indexOf("serverContext()") < listPane.indexOf("JobsList("))
        assertTrue(direct.contains("GeoTabs(listOf(\"수신 결과\", \"휴지통\")"))
        assertFalse(direct.contains("GeoFilterChipRow("))
    }

    @Test fun proxyLoadingStaysInTheListAndEmptyStatesUseTheGroupInsets() {
        val proxy = r8UiSource("storage/ServerStorageScreen.kt")
        val detail = proxy.substringAfter("GeoCopyableValue(state.selectedSession.displayFolderName")
        assertTrue(detail.indexOf("MediaFilters(") < detail.indexOf("GeoSkeleton("))
        assertFalse(proxy.contains("DevIdentity("))
        assertFalse(proxy.contains("DevEmpty(\"수신 결과가 없습니다\""))
        assertFalse(proxy.contains("DevEmpty(\"빈 폴더입니다\""))
        assertTrue(proxy.contains("count = if (!state.isLoading && state.error == null && state.selectedTarget != null)"))
        assertTrue(detail.contains("if (deletionEnabled) GeoSection(\"관리\", danger = true)"))
    }
}
