package com.example.jetsoncontroller.ui.gallery

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

private enum class OverlayGalleryPanel { CHOICE, FORM, INFO, CONFIRM, DANGER, DISABLED, INFORMATION, CHECKING, PASSED, FAILED }

/** Debug only. The caller owns chrome, safe drawing and theme/font-scale selection. */
@Composable
fun OverlayGallery(modifier: Modifier = Modifier) {
    val t = LocalGeoColors.current
    var panel by remember { mutableStateOf<OverlayGalleryPanel?>(null) }
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    var strip by remember { mutableStateOf<GeoStripKind?>(GeoStripKind.DISCONNECTED) }
    var showMenu by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf(true) }
    var event by remember { mutableStateOf("GEO-X-현장지원차량-2") }
    var project by remember { mutableStateOf("수원시 도로대장") }
    val dismiss = { panel = null }
    val device = "GEO-X-현장지원차량-2"
    val section = "신월4거리 북측 보조 구간 2차 재수집"
    fun notice(label: String) { event = label }

    Surface(modifier, color = t.surface, contentColor = t.text) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 128.dp)) {
                GalleryHeading("막대와 겹침")
                GeoStatusStrip(listOfNotNull(strip?.let { kind ->
                    val message = when (kind) {
                        GeoStripKind.AUTHENTICATION_ERROR -> "인증 정보를 확인할 수 없습니다."
                        GeoStripKind.DISCONNECTED -> "연결이 끊겼습니다 · 마지막 응답 5분 전"
                        GeoStripKind.OUTSIDE_ZONE -> "구역 밖 · 35 m"
                        GeoStripKind.COLLECTING -> "$section 수집 중 · 00:12:34"
                        GeoStripKind.LAST_SYNC -> "마지막 동기화 16:36"
                    }
                    val actions = when (kind) {
                        GeoStripKind.AUTHENTICATION_ERROR -> listOf(GeoOverlayAction("QR 코드로 다시 등록", { notice("QR 코드로 다시 등록") }))
                        GeoStripKind.DISCONNECTED -> listOf(GeoOverlayAction("연결 방법", { opener = null; panel = OverlayGalleryPanel.CHOICE }),
                            GeoOverlayAction("다시 연결", { notice("다시 연결") }))
                        GeoStripKind.OUTSIDE_ZONE -> listOf(GeoOverlayAction("지도 보기", { notice("지도 보기") }))
                        GeoStripKind.COLLECTING -> listOf(GeoOverlayAction("진행 보기", { notice("진행 보기") }))
                        GeoStripKind.LAST_SYNC -> listOf(GeoOverlayAction("다시 확인", { notice("다시 확인") }))
                    }
                    GeoStatusStripState(kind, device, message, actions)
                }))
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event, style = LocalGeoType.current.body)
                    listOf("인증 오류" to GeoStripKind.AUTHENTICATION_ERROR, "연결 끊김" to GeoStripKind.DISCONNECTED,
                        "구역 밖" to GeoStripKind.OUTSIDE_ZONE, "수집 중" to GeoStripKind.COLLECTING,
                        "마지막 동기화" to GeoStripKind.LAST_SYNC).forEach { (label, kind) ->
                        GalleryButton(label) { strip = kind }
                    }
                    GalleryButton("상태 띠 숨기기") { strip = null }
                    GeoFocusBlock(GeoFocusBlockState(
                        "$device 연결이 끊겼습니다", "마지막 응답 5분 전 · 수집은 장치에서 계속될 수 있습니다.",
                        Tone.WARNING, GeoOverlayAction("다시 연결", { notice("다시 연결") }),
                        GeoOverlayAction("연결 방법", { opener = null; panel = OverlayGalleryPanel.CHOICE })
                    ))
                    GalleryHeading("선택·입력·정보 시트")
                    listOf("연결 방법" to OverlayGalleryPanel.CHOICE, "새 프로젝트" to OverlayGalleryPanel.FORM,
                        "위치 품질" to OverlayGalleryPanel.INFO).forEach { (label, target) ->
                        GalleryButton(label) { focus -> opener = focus; panel = target }
                    }
                    GalleryHeading("수집 시작 확인")
                    listOf("확인 중" to OverlayGalleryPanel.CHECKING, "수집 시작" to OverlayGalleryPanel.PASSED,
                        "실패" to OverlayGalleryPanel.FAILED).forEach { (label, target) ->
                        GalleryButton(label) { focus -> opener = focus; panel = target }
                    }
                    GalleryHeading("확인과 정보")
                    listOf("완료 보고" to OverlayGalleryPanel.CONFIRM, "재부팅" to OverlayGalleryPanel.DANGER,
                        "전원 종료" to OverlayGalleryPanel.DISABLED, "연결 상태" to OverlayGalleryPanel.INFORMATION).forEach { (label, target) ->
                        GalleryButton(label) { focus -> opener = focus; panel = target }
                    }
                    Box {
                        GalleryButton("메뉴") { focus -> opener = focus; showMenu = true }
                        GeoMenu(showMenu, { showMenu = false }, listOf(
                            GeoMenuItem("선택", { notice("선택") }),
                            GeoMenuItem("휴지통으로 이동", { undo = true; operation = "수집 기록을 휴지통으로 옮겼습니다." }, destructive = true),
                            GeoMenuItem("휴지통", { notice("휴지통") })
                        ), openerFocusRequester = opener)
                    }
                    GalleryButton("실행 취소 메시지") { undo = true; operation = "수집 기록을 휴지통으로 옮겼습니다." }
                    GalleryButton("동작 없는 메시지") { undo = false; operation = "$device 연결을 끊었습니다." }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                GeoOperationMessageHost(operation, { shown -> if (operation == shown) operation = null },
                    actionLabel = if (undo) "실행 취소" else null,
                    onAction = if (undo) ({ notice("수집 기록을 복원했습니다.") }) else null)
            }
        }
    }

    when (panel) {
        OverlayGalleryPanel.CHOICE -> GeoSheet("$device 연결 방법", dismiss, subtitle = "마지막 응답 5분 전", openerFocusRequester = opener) {
            listOf("같은 네트워크에서 다시 찾기", "장치에 직접 연결", "블루투스로 기본 연결").forEach { label ->
                GalleryChoice(label) { notice(label); dismiss() }
            }
        }
        OverlayGalleryPanel.FORM -> GeoSheet("새 프로젝트", dismiss, kind = GeoSheetKind.FORM, openerFocusRequester = opener,
            footer = { GeoSheetFooter(GeoOverlayAction("프로젝트 만들기", { notice(project); dismiss() }), GeoOverlayAction("취소", dismiss)) }) {
            OutlinedTextField(project, { project = it }, modifier = Modifier.fillMaxWidth(), label = { Text("프로젝트 이름") })
        }
        OverlayGalleryPanel.INFO -> GeoSheet("위치 품질", dismiss, kind = GeoSheetKind.INFO, openerFocusRequester = opener) {
            repeat(8) { GalleryFact("장치", device); GalleryFact("위치 품질", "정밀 위치"); GalleryFact("구간", section) }
        }
        OverlayGalleryPanel.CONFIRM -> GeoConfirmDialog("${section}을 완료로 보고할까요?",
            "GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다.",
            "완료 보고", { notice("완료 보고"); dismiss() }, dismiss, openerFocusRequester = opener)
        OverlayGalleryPanel.DANGER -> GeoConfirmDialog("${device}를 재부팅할까요?",
            "재부팅하는 동안 연결이 끊깁니다. 진행 중인 전송은 멈춥니다.", "재부팅", { notice("재부팅"); dismiss() }, dismiss,
            kind = GeoConfirmKind.DANGER, openerFocusRequester = opener)
        OverlayGalleryPanel.DISABLED -> GeoConfirmDialog("${device}의 전원을 끌까요?",
            "다시 켜려면 장치의 전원 버튼을 눌러야 합니다. 진행 중인 전송은 멈춥니다.", "전원 종료", {}, dismiss,
            kind = GeoConfirmKind.DANGER, confirmEnabled = false, disabledReason = "수집 중에는 전원을 끌 수 없습니다.", openerFocusRequester = opener)
        OverlayGalleryPanel.INFORMATION -> GeoInfoDialog("연결 상태", dismiss, openerFocusRequester = opener) {
            GalleryFact("휴대폰 Wi-Fi", "GEON-5G")
            GalleryFact("장치 Wi-Fi", "GEON-5G")
            Text("같은 네트워크입니다.", style = LocalGeoType.current.body)
            repeat(8) { GalleryFact("장치", device); GalleryFact("구간", section) }
        }
        OverlayGalleryPanel.CHECKING, OverlayGalleryPanel.PASSED, OverlayGalleryPanel.FAILED -> {
            val phase = when (panel) {
                OverlayGalleryPanel.CHECKING -> GeoStartPhase.CHECKING
                OverlayGalleryPanel.FAILED -> GeoStartPhase.FAILED
                else -> GeoStartPhase.PASSED
            }
            val checks = listOf("연결", "장치 시간", "저장 공간", "카메라", "위치(GNSS)").map { label ->
                val failed = phase == GeoStartPhase.FAILED && label == "저장 공간"
                GeoStartCheck(label, if (failed) GeoStartPhase.FAILED else if (phase == GeoStartPhase.CHECKING) phase else GeoStartPhase.PASSED,
                    value = if (failed) "남은 공간 12 GB" else "완료",
                    reason = if (failed) "저장 공간이 수집 설정의 최소값보다 적습니다 · 남은 공간 12 GB" else null,
                    remedy = if (failed) GeoOverlayAction("장치 파일", { notice("장치 파일") }) else null)
            }
            GeoStartSheet(GeoStartSheetState(section, project, device, "구역 안", phase, checks, ticketLinked = true, insideZone = true),
                onStart = { notice("수집 시작"); dismiss() }, onRecheck = { panel = OverlayGalleryPanel.CHECKING },
                onDismissRequest = dismiss, openerFocusRequester = opener)
        }
        null -> Unit
    }
}

@Composable
private fun GalleryHeading(text: String) {
    Text(text, Modifier.padding(horizontal = 24.dp, vertical = 16.dp).semantics { heading() }, style = LocalGeoType.current.title)
}

@Composable
private fun GalleryButton(label: String, onClick: (FocusRequester) -> Unit) {
    val focus = remember { FocusRequester() }
    GeoOverlayButton(GeoOverlayAction(label, { onClick(focus) }), Modifier.fillMaxWidth().focusRequester(focus))
}

@Composable
private fun GalleryChoice(label: String, onClick: () -> Unit) {
    GeoOverlayButton(GeoOverlayAction(label, onClick), Modifier.fillMaxWidth().heightIn(min = 56.dp))
}

@Composable
private fun GalleryFact(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.body)
        Text(value, style = LocalGeoType.current.body)
    }
}

@Preview(widthDp = 412, heightDp = 882)
@Preview(name = "가로·글꼴 2.0", widthDp = 882, heightDp = 412, fontScale = 2f)
@Composable
private fun OverlayGalleryPreview() {
    JetsonControllerTheme { OverlayGallery() }
}
