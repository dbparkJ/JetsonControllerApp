package com.example.jetsoncontroller.ui.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoChrome
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** Legacy entries stay keyed by TaskRun.id, including their original pipeline/log target. */
@Composable
internal fun HistoryEntryScreen(
    entry: TaskRun,
    deviceName: String,
    online: Boolean,
    developerModeEnabled: Boolean,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenLog: (() -> Unit)?,
    savedLog: String?,
    onDismissLog: () -> Unit,
    resultMessage: String? = null,
    onTrash: (() -> Unit)? = null,
    snackbar: @Composable () -> Unit = {},
    statusStrip: @Composable () -> Unit = {},
    mapCard: @Composable () -> Unit = {}
) {
    val screenState = rememberSaveableStateHolder()
    if (developerModeEnabled && savedLog != null) {
        SavedHistoryLogScreen(entry.label, savedLog, onDismissLog, statusStrip)
        return
    }
    screenState.SaveableStateProvider("history:${entry.id}") {
        FieldDetailFrame("수집 기록", onBack, onTrash = onTrash, snackbar = snackbar, statusStrip = statusStrip,
            trashEnabled = online && !entry.isActiveRun(),
            trashDisabledReason = if (!online) "장치에 연결한 뒤 기록을 휴지통으로 옮길 수 있습니다."
                else "수집이 끝난 뒤 기록을 휴지통으로 옮길 수 있습니다.") {
            val c = LocalGeoColors.current
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = LocalGeoWindow.current.gutterDp.dp)
                .padding(top = 16.dp, bottom = 40.dp)) {
                Text(historyRecordTitle(entry),
                    color = c.text, style = LocalGeoType.current.boardTitle,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(listOfNotNull(entry.contextSnapshot?.surveyProjectLabel, deviceName).joinToString(" · "),
                    color = c.textSecondary, style = LocalGeoType.current.caption)
                Spacer(Modifier.height(24.dp))
                resultMessage?.let { GeoInlineNote(it) }
                GeoPropertyList(listOf("시작", "작업", "결과", "저장 폴더")) {
                    GeoFactRow("시작", resultTime(entry.startedAt))
                    GeoFactRow("작업", entry.label)
                    GeoFactRow("결과", historyRunPresentation(entry, online, false).first.removePrefix("결과 "))
                    entry.output?.takeIf { it.manifest != null }?.let { output ->
                        GeoPropertyRow("저장 폴더",
                            value = runFolderDisplayName(output.path, entry.startedAt, entry.runId, output.manifest?.runId) ?: "미확인",
                            onClick = onOpenFiles, enabled = online,
                            disabledReason = "장치에 연결한 뒤 저장 폴더를 볼 수 있습니다.")
                    }
                }
                mapCard()
                FieldTechnicalInfo(
                    identity = entry.id,
                    values = buildList {
                        add(GeoTechnicalValue("기록 ID", entry.id))
                        entry.runId?.let { add(GeoTechnicalValue("수집 ID", it)) }
                        entry.output?.let { add(GeoTechnicalValue("경로", "${it.rootId}/${it.path}")) }
                    },
                    developerModeEnabled = developerModeEnabled,
                    online = online,
                    onOpenLog = onOpenLog
                )
            }
        }
    }
}

@Composable
internal fun FieldTechnicalInfo(
    identity: String,
    values: List<GeoTechnicalValue>,
    developerModeEnabled: Boolean,
    online: Boolean,
    onOpenLog: (() -> Unit)?
) {
    val showTechnicalInfo by com.example.jetsoncontroller.ui.dev.rememberTechnicalInfo()
    if (!developerModeEnabled || !showTechnicalInfo) return
    var expanded by rememberSaveable(identity) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(top = 28.dp)) {
        GeoRow("기술 정보", { expanded = !expanded }, value = if (expanded) "접기" else "펼치기")
        if (expanded) {
            values.forEach { value ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    GeoFactRow(value.label, value.value, Modifier.weight(1f))
                    GeoIconButton(GeoIcons.copy, "${value.label} 복사", {
                        clipboard.setText(AnnotatedString(value.value))
                    })
                }
            }
            if (onOpenLog != null) GeoRow("실행 로그", onOpenLog, meta = "최근 64KB",
                enabled = online, disabledReason = "장치에 연결한 뒤 실행 로그를 볼 수 있습니다.")
        }
    }
}

@Composable
internal fun SavedHistoryLogScreen(label: String, text: String, onBack: () -> Unit,
    statusStrip: @Composable () -> Unit = {}) {
    FieldDetailFrame("실행 로그", onBack, statusStrip = statusStrip) {
        Column(Modifier.fillMaxSize().padding(horizontal = LocalGeoWindow.current.gutterDp.dp)
            .padding(top = 16.dp, bottom = 40.dp)) {
            Text("$label · 최근 64KB", color = LocalGeoColors.current.textSecondary,
                style = LocalGeoType.current.caption)
            Spacer(Modifier.height(8.dp))
            val lines = remember(text) { text.lineSequence().mapIndexed { index, line ->
                GeoLogLine(index.toString(), line)
            }.toList() }
            GeoLogView(lines, live = false, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** The app shell owns system insets and navigation; this frame owns only the detail title row. */
@Composable
internal fun FieldDetailFrame(
    title: String,
    onBack: () -> Unit,
    onTrash: (() -> Unit)? = null,
    trashEnabled: Boolean = true,
    trashDisabledReason: String? = null,
    snackbar: @Composable () -> Unit = {},
    statusStrip: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    val colors = LocalGeoColors.current
    val chrome = LocalGeoChrome.current
    val density = LocalDensity.current
    var topDp by remember { mutableFloatStateOf(56f) }
    var menu by rememberSaveable(title) { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(onTrash, trashEnabled) { if (onTrash == null || !trashEnabled) menu = false }
    Scaffold(containerColor = colors.surface, contentWindowInsets = WindowInsets(0),
        snackbarHost = snackbar,
        topBar = {
            Column(Modifier.fillMaxWidth().onSizeChanged { topDp = it.height / density.density }) {
                Row(Modifier.fillMaxWidth().background(colors.topBar)
                    .heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
                    Text(title, Modifier.weight(1f).padding(start = 12.dp), color = colors.onTopBar,
                        style = LocalGeoType.current.title)
                    if (onTrash != null) Box {
                        GeoIconButton(GeoIcons.more, "더 보기", { menu = true },
                            modifier = Modifier.focusRequester(menuFocus), chrome = true)
                        GeoMenu(menu, { menu = false }, listOf(GeoMenuItem(
                            "기록을 휴지통으로", onTrash, destructive = true, enabled = trashEnabled,
                            disabledReason = trashDisabledReason
                        )), openerFocusRequester = menuFocus)
                    }
                }
                statusStrip()
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalGeoChrome provides chrome.copy(topDp = chrome.topDp + topDp)) {
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}
