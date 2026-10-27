package com.example.jetsoncontroller.ui.components

import com.example.jetsoncontroller.ui.model.Tone
import android.content.res.Configuration
import com.example.jetsoncontroller.ui.components.geo.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

@Preview(name = "Operational states - light", showBackground = true, widthDp = 360)
@Preview(
    name = "Operational states - dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Operational states - 200% text",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f
)
@Composable
private fun OperationalStatesPreview() {
    JetsonControllerTheme {
        Column(
            modifier = Modifier.padding(GeoSpace.space8),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.space6)
        ) {
            GeoBadge("온라인", Tone.SUCCESS)
            GeoInlineNote("저장 공간을 확인해 주세요.", tone = Tone.WARNING)
            GeoInlineNote(
                text = "장치와 연결할 수 없습니다.",
                tone = Tone.ERROR,
                actionLabel = "다시 연결",
                onAction = {},
                onDismiss = {}
            )
            listOf("장치 찾기", "연결", "장치 확인", "인증", "상태 동기화").forEachIndexed { index, label ->
                GeoCheckRow(label, if (index < 2) "완료" else if (index == 2) "확인 중" else "대기", if (index < 2) Tone.SUCCESS else if (index == 2) Tone.PENDING else Tone.UNKNOWN)
            }

        }
    }
}

@Preview(name = "R8 표준", widthDp = 412, heightDp = 882)
@Composable private fun R8StandardPreview() = R8Catalog(GeoPalette.STANDARD)
@Preview(name = "R8 네이비 밝게", widthDp = 412, heightDp = 882)
@Composable private fun R8NavyLightPreview() = R8Catalog(GeoPalette.NAVY_LIGHT)
@Preview(name = "R8 고대비", widthDp = 412, heightDp = 882)
@Composable private fun R8HighContrastPreview() = R8Catalog(GeoPalette.HIGH_CONTRAST)
@Preview(name = "R8 그래파이트", widthDp = 412, heightDp = 882)
@Composable private fun R8GraphitePreview() = R8Catalog(GeoPalette.GRAPHITE)
@Preview(name = "R8 네이비 어둡게", widthDp = 412, heightDp = 882)
@Composable private fun R8NavyDarkPreview() = R8Catalog(GeoPalette.NAVY_DARK)
@Preview(name = "R8 흐림", widthDp = 412, heightDp = 882)
@Composable private fun R8MistPreview() = R8Catalog(GeoPalette.MIST)
@Preview(name = "R8 글꼴 200%", widthDp = 412, heightDp = 882, fontScale = 2f)
@Composable private fun R8LargeTextPreview() = R8Catalog(GeoPalette.STANDARD)

private val previewMetrics get() = listOf(GeoFigure("저장 여유", "565", unit = "GB"),
    GeoFigure("온도", "56", unit = "°C"), GeoFigure("CPU", "7", unit = "%"), GeoFigure("GPU", "42", unit = "%"))

@Composable private fun R8Catalog(palette: GeoPalette) {
    JetsonControllerTheme(palette) {
        Column {
            Box(Modifier.weight(1f)) {
                GeoLargeTitleScaffold("작업", subtitle = "오늘의 수집", primaryAction = GeoButtonAction("수집 준비", {})) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            GeoTodayCard("중앙로 동측", label = "오늘의 수집 구간", meta = "도심 도로 조사 · 할 일 2개")
                            GeoDevicePhotoCard("GEO-X-1", "연결됨", Tone.SUCCESS, "같은 네트워크 · 방금 갱신", previewMetrics)
                        }
                        GeoFilterChipRow(listOf("전체", "보내는 중 1", "확인 필요 1", "보낼 수집 2"), 0, {})
                        GeoHorizontalTiles(listOf(GeoQuickAction("카메라 영상", GeoIcons.camera, {}),
                            GeoQuickAction("위치 지도", GeoIcons.myLocation, {}), GeoQuickAction("파일 보내기", GeoIcons.send, {}),
                            GeoQuickAction("수집 기록", GeoIcons.restore, {})))
                        GeoGroupedSection("센서", Modifier.padding(horizontal = 24.dp), trailingColumn = GeoRowTrailing.Status) {
                            GeoRow("카메라", {}, meta = "1280 × 800", leading = { GeoIconTile(GeoIcons.camera) },
                                badge = { GeoBadge("연결됨", Tone.SUCCESS) })
                            GeoRow("위치 센서", {}, meta = "원인과 장치 이름을 생략하지 않습니다", divider = false,
                                leading = { GeoIconTile(GeoIcons.myLocation) }, badge = { GeoBadge("확인 중", Tone.PENDING) })
                        }
                        GeoCopyableValue("GEO-X-0001", Modifier.padding(24.dp), label = "장치 ID")
                        GeoTabs(listOf("개요", "상세", "기록"), 0, {})
                    }
                }
            }
            GeoBottomBar(GeoTab.TASKS, {})
        }
    }
}

@Preview(name = "R8 패드 1280", widthDp = 1280, heightDp = 800)
@Composable private fun R8TabletPreview() {
    JetsonControllerTheme(GeoPalette.STANDARD) {
        CompositionLocalProvider(LocalGeoWindow provides geoWindow(1280f, 800f, 1f)) {
            Row(Modifier.background(LocalGeoColors.current.canvas)) {
                GeoRail(GeoTab.TASKS, {})
                GeoPaneLayout(policy = GeoPanePolicy.WORK_HOME, list = {
                    GeoLargeTitleScaffold("작업", primaryAction = GeoButtonAction("수집 준비", {})) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            GeoTodayCard("중앙로 동측", meta = "오늘의 수집 구간")
                            GeoDevicePhotoCard("GEO-X-1", "연결됨", Tone.SUCCESS, "방금 갱신", previewMetrics)
                        }
                    }
                }, detail = {
                    GeoLargeTitleScaffold("빠른 기능") {
                        GeoQuickActions(List(6) { GeoQuickAction("기능 ${it + 1}", GeoIcons.camera, {}) })
                    }
                })
            }
        }
    }
}

@Preview(name = "R8 시트", widthDp = 412, heightDp = 882)
@Composable private fun R8SheetPreview() {
    JetsonControllerTheme {
        GeoSheet("서버 주소", {}, kind = GeoSheetKind.FORM,
            footer = { GeoSheetFooter(GeoOverlayAction("저장", {}), GeoOverlayAction("취소", {})) }) {
            GeoTextField("https://example.test", {}, "주소", inputKind = GeoInputKind.SERVER_ADDRESS)
            GeoCopyableValue("SERVER-01", label = "서버 ID")
        }
    }
}

@Preview(name = "R8 확인 대화상자", widthDp = 412, heightDp = 882)
@Composable private fun R8DialogPreview() {
    JetsonControllerTheme {
        GeoConfirmDialog("수집을 종료할까요?", "현재 장치와 수집 구간을 확인하세요.", "수집 종료", {}, {}, kind = GeoConfirmKind.DANGER)
    }
}

@Preview(name = "R8 미디어", widthDp = 1280, heightDp = 800)
@Composable private fun R8MediaPreview() {
    JetsonControllerTheme {
        CompositionLocalProvider(LocalGeoWindow provides geoWindow(1280f, 800f, 1f)) {
            GeoImmersiveMediaFrame(captureAction = {}, controls = {
                GeoMediaIconButton(GeoIcons.back, "뒤로", {})
                GeoFloatingMediaNavigation(GeoTab.DEVICES, {}, Modifier.align(androidx.compose.ui.Alignment.BottomStart))
            }) { Text("카메라 영상 영역", color = com.example.jetsoncontroller.ui.theme.GeoFixedColors.mediaText) }
        }
    }
}

@Preview(name = "R8 지도와 시트", widthDp = 412, heightDp = 882)
@Composable private fun R8MapSheetPreview() {
    JetsonControllerTheme {
        GeoMapSheetScaffold("수집 정보", GeoButtonAction("수집 종료", {}, GeoButtonStyle.DangerOutline),
            map = { Box(Modifier.fillMaxSize().background(LocalGeoColors.current.sunken)) }) {
            Text("중앙로 동측")
            Text("00:12:34", style = com.example.jetsoncontroller.ui.theme.LocalGeoType.current.clock)
            GeoMetricStrip(previewMetrics)
        }
    }
}
