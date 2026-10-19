package com.example.jetsoncontroller.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*

/** The host owns theme, font scale and insets; this gallery never talks to a device. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ControlsGallery(modifier: Modifier = Modifier) {
    val colors = geoControlColors()
    var busy by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var queue by rememberSaveable { mutableStateOf(0) }
    var temperature by rememberSaveable { mutableStateOf(80f) }
    var name by rememberSaveable { mutableStateOf("GEO-X-현장지원차량-2") }
    var password by rememberSaveable { mutableStateOf("geo-x") }
    var numeric by rememberSaveable { mutableStateOf("80") }
    var notes by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAction by rememberSaveable { mutableStateOf("수집 시작") }
    val queues = listOf("할 일", "진행 중", "완료")
    val sections = listOf("신월4거리 북측 보조 구간 2차 재수집", "신월4거리 북측 보조 구간")
    var section by rememberSaveable { mutableStateOf(0) }

    Column(modifier.fillMaxWidth().testTag("controls-gallery").verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlsGalleryTitle("단추")
        GeoCheckboxRow("처리 중", busy, { busy = it })
        GeoButton("서버로 보내기", { lastAction = "서버로 보내기" }, icon = GeoIcons.send,
            busy = busy, busyLabel = "보내는 중")
        GeoButton("수집 시작", {}, enabled = false, disabledReason = "점검을 통과해야 시작할 수 있습니다.")
        GeoButtonActions(listOf(
            GeoButtonAction("연결 방법", { lastAction = "연결 방법" }, GeoButtonStyle.Secondary),
            GeoButtonAction("다시 연결", { lastAction = "다시 연결" })
        ))
        // Both a long label and large font scales exercise the measured stacking rule.
        GeoButtonActions(listOf(
            GeoButtonAction("취소", { lastAction = "취소" }, GeoButtonStyle.Tertiary),
            GeoButtonAction("이전 수신 결과 더 불러오기", { lastAction = "이전 수신 결과 더 불러오기" })
        ), layout = GeoActionLayout.Dialog)
        Text("GEO-X-1 수집을 종료합니다.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        GeoButton("수집 종료", { lastAction = "수집 종료" }, style = GeoButtonStyle.DangerOutline, actionArea = true)
        GeoButtonActions(listOf(
            GeoButtonAction("취소", { lastAction = "취소" }, GeoButtonStyle.Tertiary),
            GeoButtonAction("수집 종료", { lastAction = "수집 종료" }, GeoButtonStyle.DangerFilled)
        ), layout = GeoActionLayout.Dialog)
        GeoButton("다시 시도", { lastAction = "다시 시도" }, style = GeoButtonStyle.Tertiary, icon = GeoIcons.refresh)
        Text(lastAction, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeoIconButton(GeoIcons.back, "뒤로", { lastAction = "뒤로" })
            GeoIconButton(GeoIcons.close, "닫기", { lastAction = "닫기" })
            GeoIconButton(GeoIcons.more, "더 보기", { lastAction = "더 보기" })
            GeoIconButton(GeoIcons.bell, "알림", { lastAction = "알림" })
        }

        ControlsGalleryTitle("입력")
        GeoTextField(name, { name = it }, "장치 이름")
        GeoTextField(password, { password = it }, "비밀번호", password = true)
        GeoTextField(numeric, { numeric = it }, "장치 온도", numeric = true, unit = "°C")
        GeoTextField(notes, { notes = it }, "기술 정보", singleLine = false)
        GeoTextField("", {}, "장치 이름", error = "장치 이름을 입력하세요.")
        GeoTextField("GEO-X-1", {}, "장치 이름", enabled = false, disabledReason = "장치 연결 후 사용할 수 있습니다.")
        GeoPickerField("수집 구간", sections[section], { picker = "section" })
        GeoPickerField("수집 구간", sections[section], {}, enabled = false, disabledReason = "장치 연결 후 사용할 수 있습니다.")

        ControlsGalleryTitle("선택")
        GeoSwitchRow("수집 시작과 종료", checked, { checked = it }, description = "작업 알림을 받습니다.")
        GeoSwitchRow("수집 시작과 종료", false, {}, enabled = false, disabledReason = "알림 권한이 꺼져 있습니다.")
        GeoCheckboxRow("신월4거리 북측 보조 구간 2차 재수집", checked, { checked = it })
        GeoCheckboxRow("수집 구간", true, {}, enabled = false, disabledReason = "장치 연결 후 사용할 수 있습니다.")
        GeoRadioGroup("화면 모드", listOf("시스템 설정", "밝게", "어둡게"), selected, { selected = it })
        GeoRadioRow("시스템 설정", false, {}, enabled = false, disabledReason = "장치 연결 후 사용할 수 있습니다.")
        GeoSegmented("수집 구간", queues, queue, { queue = it }, { picker = "queue" })
        GeoSegmented("수집 구간", listOf("신월4거리 북측 보조 구간 2차 재수집", "신월4거리 북측 보조 구간"),
            section, { section = it }, { picker = "section" })
        GeoSliderRow("장치 온도", temperature, "${temperature.toInt()}°C 이상", { temperature = it }, 40f..110f)
        GeoSliderRow("장치 온도", 80f, "80°C 이상", {}, 40f..110f,
            enabled = false, disabledReason = "장치 연결 후 사용할 수 있습니다.")

        ControlsGalleryTitle("진행")
        GeoProgress(0f)
        GeoProgress(0.649f)
        GeoProgress(1f)
        GeoProgress()
        GeoSkeleton(loading = true)

        ControlsGalleryTitle("아이콘")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GeoIcons.all(colors.textSecondary, colors.surface).forEach { (name, vector) ->
                Column(Modifier.width(104.dp)) {
                    Icon(vector, contentDescription = name, modifier = Modifier.size(24.dp),
                        tint = if (name.endsWith("-filled")) Color.Unspecified else colors.textSecondary)
                    Text(name, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (picker != null) {
        val isQueue = picker == "queue"
        ModalBottomSheet(onDismissRequest = { picker = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                GeoRadioGroup("수집 구간", if (isQueue) queues else sections,
                    if (isQueue) queue else section, { if (isQueue) queue = it else section = it })
                GeoButton("닫기", { picker = null }, style = GeoButtonStyle.Tertiary)
            }
        }
    }
}

@Composable
private fun ControlsGalleryTitle(title: String) {
    Text(title, Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        color = geoControlColors().text)
}
