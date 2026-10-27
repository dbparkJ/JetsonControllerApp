package com.example.jetsoncontroller.ui.onboarding

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R8OnboardingLayoutTest {
    @Test fun firstRegistrationKeepsTheQrFooterAndServerOnlyPath() {
        val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
            File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
        val onboarding = File(ui, "onboarding/FirstDeviceOnboardingScreen.kt").readText()
        assertTrue(onboarding.contains("GeoLargeTitleScaffold(\"장치 등록\""))
        assertTrue(onboarding.contains("primaryAction = GeoButtonAction(\"QR 코드 스캔\", onScanQr)"))
        assertTrue(onboarding.contains("GeoButtonAction(\"장치 없이 서버 파일 보기\", it, GeoButtonStyle.Tertiary)"))
        assertTrue(onboarding.contains("GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY"))
        assertTrue(onboarding.contains("if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)"))
        assertTrue(onboarding.contains("PermissionRow(GeoIcons.myLocation, \"위치\""))
        assertFalse(onboarding.contains("PermissionRow(GeoIcons.bluetooth, \"위치\""))
        assertTrue(onboarding.contains("GeoGroupedSection(\"등록에 필요한 권한\")"))
        assertFalse(onboarding.contains("WorkSurfaceCard"))
    }
}
