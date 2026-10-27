package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8ServerProfilesQaTest {
    @Test fun failedProfileLoadsOfferRetryAndKeepAdditionSecondary() {
        val screen = r8UiSource("dev/DevServerScreens.kt").substringBefore("internal fun DevServerProfileEditor(")
        assertTrue(screen.contains("state.isLoadingProfiles || state.isLoading || state.isConnecting"))
        assertTrue(screen.contains("GeoButtonStyle.Secondary else GeoButtonStyle.Primary"))
        val error = screen.substringAfter("ServerProfilesContent.ERROR ->").substringBefore("ServerProfilesContent.EMPTY ->")
        assertTrue(error.contains("이 기기에 저장된 프로필을 읽지 못했습니다."))
        assertTrue(error.contains("\"다시 불러오기\""))
        assertTrue(error.contains("onAction = onRefresh"))
        assertFalse(error.contains("프로필을 추가"))
        assertTrue(r8UiSource("navigation/DevGraph.kt").contains("onRefresh = app.directServerViewModel::refreshProfiles"))
    }

    @Test fun retryRestartsProfileObservationAndHandlesAnotherFailure() {
        val model = r8UiSource("storage/DirectServerViewModel.kt")
        val retry = model.substringAfter("fun refreshProfiles()").substringBefore("fun selectSection(")
        assertTrue(retry.contains("profilesJob?.cancel()"))
        assertTrue(retry.contains("isLoadingProfiles = true"))
        assertTrue(retry.contains("profilesJob = viewModelScope.launch"))
        assertTrue(retry.contains("store.profiles.catch"))
        assertTrue(retry.contains("collectLatest { profiles ->"))
        assertEquals(2, Regex("isLoadingProfiles = false").findAll(retry).count())
        assertFalse(retry.contains(".connect("))
    }
}
