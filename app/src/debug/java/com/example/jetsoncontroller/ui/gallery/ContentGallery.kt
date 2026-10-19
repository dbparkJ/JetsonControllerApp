package com.example.jetsoncontroller.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

/** Debug only; the shell owns system insets and the gallery activity registration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentGallery(modifier: Modifier = Modifier, highContrast: Boolean = false) {
    CompositionLocalProvider(LocalContentHighContrast provides highContrast) {
        val colors = contentColors
        var event by remember { mutableStateOf("아직 선택한 항목이 없습니다.") }
        var showHelp by remember { mutableStateOf(true) }
        var lineCount by remember { mutableIntStateOf(48) }
        val logState = rememberLazyListState()
        val receipt = remember {
            GeoReceiptState(
                collection = GeoReceiptItem("종료", StatusTone.SUCCESS, done = true),
                storage = GeoReceiptItem("저장 확인", StatusTone.SUCCESS, "1,204개 · 1.2 GB", done = true),
                server = GeoReceiptItem(meta = "이 휴대폰에서 보낸 기록이 없습니다."),
                report = GeoReceiptItem(word = "보고 전", meta = "서버 수신을 확인한 뒤 보고할 수 있습니다.")
            )
        }
        val facts = listOf("54°C", "저장 512\u00a0GB 남음")
        Surface(modifier, color = colors.surface) {
            BoxWithConstraints {
                val gutter = when { maxWidth >= 390.dp -> 24.dp; maxWidth >= 340.dp -> 20.dp; else -> 16.dp }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 40.dp)) {
                    item {
                        GeoSection("목록과 상태", first = true, note = "글꼴 배율 ${LocalDensity.current.fontScale}") {
                            Text(event, color = colors.secondary, style = ContentType.body)
                            GeoDeviceStrip(GeoDeviceStripState("GEO-X-1", "연결됨", StatusTone.SUCCESS, facts, "방금 갱신"),
                                onClick = { event = "GEO-X-1 · 상세 보기" })
                            GeoDeviceStrip(GeoDeviceStripState("GEO-X-현장지원차량-2", "기본 연결", StatusTone.WARNING,
                                facts, "방금 갱신", firstAttention = "카메라 지연"), onClick = { event = "GEO-X-현장지원차량-2 · 상세 보기" })
                        }
                    }
                    item {
                        GeoSection("행", actionLabel = "전체 기록", onAction = { event = "전체 기록" }) {
                            GeoRow("장치 Wi-Fi", onClick = { event = "장치 Wi-Fi" }, value = "GEON-5G")
                            GeoRow("신월4거리 북측 보조 구간 2차 재수집", meta = "마감 10월 21일 · 담당 김현우 · 350 m",
                                badge = { GeoBadge("할 일", StatusTone.UNKNOWN) }, onClick = { event = "수집 구간 · 상세 보기" })
                            GeoRow("장치 파일", onClick = {}, enabled = false, disabledReason = "연결되면 쓸 수 있습니다.")
                            GeoActionRow("20261018_0912", "다시 보내기", meta = "연결이 끊겨 멈췄습니다.",
                                onOpen = { event = "20261018_0912 · 상세 보기" }, onAction = { event = "다시 보내기" })
                            GeoActionRow("신월4거리 북측 보조 구간 2차 재수집", "보내기", onOpen = { event = "상세 보기" },
                                onAction = {}, actionEnabled = false, disabledReason = "같은 네트워크에 연결하세요.")
                            GeoFactRow("마감", "10월 21일")
                            GeoFactRow("수집 구간", "신월4거리 북측 보조 구간 2차 재수집")
                            GeoNumericRows(listOf(GeoNumericRow("수집 파일", "896", "MB"),
                                GeoNumericRow("장치 저장", "14.7", "GB"), GeoNumericRow("저장 여유", "512", "GB"),
                                GeoNumericRow("서버 수신", null)))
                            GeoCheckRow("저장 공간", "512\u00a0GB 남음", StatusTone.SUCCESS)
                            GeoCheckRow("장치 상태", "확인 중", StatusTone.PENDING)
                            GeoCheckRow("저장 공간", "12\u00a0GB 남음", StatusTone.WARNING,
                                failureReason = "저장 공간이 수집 설정의 최소값보다 적습니다.",
                                remedyLabel = "장치 파일", onRemedy = { event = "장치 파일" })
                        }
                    }
                    item {
                        GeoSection("상태", count = "6개") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("연결됨" to StatusTone.SUCCESS, "기본 연결" to StatusTone.WARNING,
                                    "인증 필요" to StatusTone.ERROR, "수집 중" to StatusTone.INFO,
                                    "확인 중" to StatusTone.PENDING, "미확인" to StatusTone.UNKNOWN).forEach { (word, tone) -> GeoBadge(word, tone) }
                                GeoBadge("구역 안", StatusTone.SUCCESS, variant = GeoBadgeVariant.ON_MAP)
                                GeoBadge("연결됨", StatusTone.SUCCESS, highContrast = true)
                                GeoBadge("실시간", StatusTone.SUCCESS, variant = GeoBadgeVariant.ON_DARK)
                                GeoBadge("지연", StatusTone.WARNING, variant = GeoBadgeVariant.ON_DARK)
                                GeoBadge("대기 중", StatusTone.UNKNOWN, variant = GeoBadgeVariant.ON_DARK)
                                GeoBadge("받지 못함", StatusTone.ERROR, variant = GeoBadgeVariant.ON_DARK)
                            }
                        }
                    }
                    item {
                        GeoSection("지표") {
                            GeoFigureGrid(listOf(GeoFigure("온도", "54°C"), GeoFigure("저장 여유", "512\u00a0GB", detail = "57% 남음"),
                                GeoFigure("사용률", "0%"), GeoFigure("메모리", null),
                                GeoFigure("온도", "54°C", stale = true), GeoFigure("온도", "81°C", exceededThreshold = "80°C")))
                        }
                    }
                    item {
                        GeoSection("패널 안 지표") {
                            GeoFigureGrid(listOf(GeoFigure("온도", "81°C", stale = true, exceededThreshold = "80°C"),
                                GeoFigure("저장 여유", "512\u00a0GB")), contained = true)
                        }
                    }
                    item { GeoSection("수집 결과") { GeoReceipt(receipt); GeoReceiptLine(receipt) } }
                    item {
                        GeoSection("안내") {
                            if (showHelp) GeoInlineNote("공유기 없이 휴대폰과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.",
                                onDismiss = { showHelp = false })
                            Spacer(Modifier.height(8.dp))
                            GeoInlineNote("GEO-X-1 · 연결이 끊겼습니다. 같은 네트워크에 연결하세요.", tone = StatusTone.WARNING,
                                kind = GeoNoteKind.LIVE_ERROR, actionLabel = "연결 방법", onAction = { event = "연결 방법" })
                        }
                    }
                    item {
                        GeoSection("빈 상태와 복구") {
                            listOf(
                                GeoStateContent(GeoStateKind.EMPTY, "수집 기록이 없습니다", "수집을 마치면 여기에 남습니다."),
                                GeoStateContent(GeoStateKind.ERROR, "불러오지 못했습니다", "서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.", "다시 시도"),
                                GeoStateContent(GeoStateKind.MISSING, "수집 기록을 찾지 못했습니다", "수집 기록 목록으로 돌아가세요.", "목록으로"),
                                GeoStateContent(GeoStateKind.PERMISSION, "위치 권한이 꺼져 있습니다", "장치 위치를 보려면 권한을 허용하세요.", "권한 허용"),
                                GeoStateContent(GeoStateKind.OFFLINE, "GEO-X-1 · 오프라인", "마지막 값 기준", "연결 방법"),
                                GeoStateContent(GeoStateKind.UNSUPPORTED, "사용할 수 없습니다", "이 장치에서는 이 기능을 지원하지 않습니다.")
                            ).forEach { state ->
                                GeoStateBlock(state, onAction = { event = state.actionLabel.orEmpty() })
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                    item {
                        GeoTechInfo(listOf(GeoTechnicalValue("실행 ID", "20261018_0912")), developerMode = true, showTechnicalInfo = true)
                        GeoTechInfo(listOf(GeoTechnicalValue("실행 ID", "20261018_0912"),
                            GeoTechnicalValue("출력 경로", "/collections/신월4거리북측보조구간2차재수집/20261018_0912/recordings")),
                            developerMode = true, showTechnicalInfo = true, initiallyExpanded = true)
                    }
                    item {
                        GeoSection("로그", actionLabel = "로그 추가", onAction = { lineCount += 5 },
                            note = "위로 스크롤한 뒤 로그를 추가하면 최신 로그로 이동하지 않습니다.") {
                            GeoLogView((0 until lineCount).map { GeoLogLine("line-$it", "$it · 수집 상태를 받았습니다.") },
                                live = true, listState = logState, modifier = Modifier.fillMaxWidth().height(280.dp))
                            Spacer(Modifier.height(16.dp))
                            GeoLogView(listOf(GeoLogLine("archive", "수집이 종료되었습니다.")), live = false,
                                modifier = Modifier.fillMaxWidth().height(160.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Content 412", widthDp = 412, heightDp = 882)
@Composable
private fun ContentPreview() { JetsonControllerTheme(darkTheme = false) { ContentGallery() } }

@Preview(name = "Content large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun ContentLargeTextPreview() { JetsonControllerTheme(darkTheme = false) { ContentGallery() } }

@Preview(name = "Content landscape", widthDp = 720, heightDp = 412)
@Composable
private fun ContentLandscapePreview() { JetsonControllerTheme(darkTheme = true) { ContentGallery() } }
