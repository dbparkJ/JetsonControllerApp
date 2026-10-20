package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.TrashEntry
import com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost
import com.example.jetsoncontroller.ui.field.historyFolderLabel
import com.example.jetsoncontroller.ui.field.runFolderDisplayName
import com.example.jetsoncontroller.ui.field.runNameInstant
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import java.time.Instant

@Composable
fun LocalTrashScreen(
    state: LocalTrashUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onEmptyTrash: (String, List<String>) -> Unit,
    onDismissMessage: (String) -> Unit,
    ongoingRun: @Composable () -> Unit = {},
    developerModeEnabled: Boolean = false,
    showTechnicalInfo: Boolean = false
) {
    var emptySnapshot by remember(state.deviceId) { mutableStateOf<LocalTrashEmptySnapshot?>(null) }
    var menu by remember(state.deviceId) { mutableStateOf(false) }
    val menuOpener = remember { FocusRequester() }
    val busy = state.loading || state.restoringId != null || state.emptying
    emptySnapshot?.let { snapshot ->
        val fontScale = LocalDensity.current.fontScale
        GeoConfirmDialog("${state.deviceName} 휴지통을 비울까요?",
            "표시된 ${snapshot.trashIds.size}개 항목을 영구 삭제합니다. 되돌릴 수 없습니다.",
            "영구 삭제", {
                if (state.deviceId == snapshot.deviceId) onEmptyTrash(snapshot.deviceId, snapshot.trashIds)
                emptySnapshot = null
            }, { if (!state.emptying) emptySnapshot = null },
            modifier = Modifier.testTag("local-trash-empty-dialog-title").semantics { this[TrashDialogFontScaleKey] = fontScale },
            kind = GeoConfirmKind.DANGER,
            confirmEnabled = !busy && state.deviceId == snapshot.deviceId && state.online && state.emptySupported,
            disabledReason = "장치와 휴지통 상태를 다시 확인하세요.", busy = state.emptying,
            openerFocusRequester = menuOpener)
    }
    StoragePage("휴지통", onBack, actions = {
        Box {
            GeoIconButton(GeoIcons.more, "휴지통 메뉴", { menu = true }, Modifier.focusRequester(menuOpener), chrome = true)
            GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("휴지통 비우기", {
                emptySnapshot = localTrashEmptySnapshot(state)
            }, destructive = true, enabled = localTrashEmptySnapshot(state) != null && !busy,
                disabledReason = if (!state.emptySupported) "이 장치는 휴지통 비우기를 지원하지 않습니다."
                    else "장치와 휴지통 상태를 다시 확인하세요.")), openerFocusRequester = menuOpener)
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            StorageRefresh(state.loading, state.online && !busy, onRefresh) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(storageGutter, 16.dp)) {
                    item { ongoingRun() }
                    item {
                        // The freshness line holds the screen's one "다시 확인"; the notes below never repeat it.
                        StorageFreshness(state.refreshedAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                            onRefresh, state.deviceName, server = false)
                        GeoInlineNote(when {
                            !state.online -> "장치에 연결하면 복원 가능한 항목과 영구 삭제 지원 여부를 확인할 수 있습니다."
                            !state.hasLoaded -> "장치 휴지통을 확인하고 있습니다."
                            state.emptySupported -> "복원 가능한 항목은 원래 위치로 되돌릴 수 있습니다. 휴지통을 비우면 표시된 항목이 영구 삭제됩니다."
                            else -> "이 장치는 휴지통 비우기를 지원하지 않습니다. 장치 소프트웨어 업데이트가 필요합니다."
                        })
                        if (busy) GeoProgress()
                        state.error?.let { GeoInlineNote(it, tone = Tone.WARNING, kind = GeoNoteKind.LIVE_ERROR) }
                    }
                    items(state.entries, key = { it.trashId }) { entry ->
                        LocalTrashEntry(entry, state.online, busy) { onRestore(entry) }
                    }
                    if (state.entries.isEmpty() && !busy && state.online && state.hasLoaded && state.error == null) item {
                        Spacer(Modifier.height(56.dp))
                        GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "휴지통이 비어 있습니다",
                            "휴지통으로 옮긴 장치 파일과 수집 기록이 여기에 표시됩니다."))
                    }
                    val renamed = localTrashTechnicalNames(state.entries)
                    if (developerModeEnabled && showTechnicalInfo && renamed.isNotEmpty()) item {
                        GeoTechInfo(renamed, developerModeEnabled, showTechnicalInfo)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { GeoOperationMessageHost(state.message, onDismissMessage) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalTrashEntry(entry: TrashEntry, online: Boolean, busy: Boolean, onRestore: () -> Unit) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val restorable = localTrashCanRestore(entry)
    val actionLabel = if (restorable) "원래 위치로 복원" else "복원할 수 없음"
    val actionWidth = with(density) {
        measurer.measure(AnnotatedString(actionLabel), if (restorable) type.button else type.caption).size.width.toDp()
    } + if (restorable) 32.dp else 0.dp
    BoxWithConstraints(Modifier.fillMaxWidth().drawBehind {
        drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }.padding(vertical = 12.dp)) {
        val body: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(localTrashTitle(entry), style = type.titleSm, color = colors.text)
                    GeoBadge(localTrashStateLabel(entry), when (entry.state) {
                        "PURGING", "RESTORING", "MOVING_TO_TRASH" -> Tone.PENDING
                        "TRASHED" -> if (entry.restoreSupported) Tone.UNKNOWN else Tone.WARNING
                        else -> Tone.WARNING
                    })
                }
                Text(localTrashMeta(entry), style = type.caption, color = colors.textSecondary)
            }
        }
        val action: @Composable (Modifier) -> Unit = { modifier ->
            if (restorable) GeoButton(actionLabel, onRestore, modifier, style = GeoButtonStyle.Secondary,
                enabled = online && !busy,
                disabledReason = if (!online) "장치에 연결되면 쓸 수 있습니다." else "복원 상태를 확인하고 있습니다.")
            else Text(actionLabel, modifier, style = type.caption, color = colors.textSecondary)
        }
        if (actionWidth + 8.dp + 160.dp > maxWidth) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            body(Modifier.fillMaxWidth())
            action(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            body(Modifier.weight(1f))
            action(Modifier.width(actionWidth))
        }
    }
}

private fun categoryLabel(category: String): String = when (category) {
    "UPLOADED_SOURCE" -> "업로드 원본"
    "RUN_HISTORY" -> "수집 기록"
    else -> "장치 데이터"
}

/**
 * Run logs and run folders are named after the run ("run-20260916T073608.169374Z-….log"), and R3 keeps run IDs in
 * 기술 정보: such rows read by the run's time, "9월 16일 16:36 수집 기록" (a folder: "… 수집").
 */
internal fun localTrashTitle(entry: TrashEntry): String {
    if (historyFolderLabel(entry.name) != null) return entry.name
    if (entry.category != "RUN_HISTORY") return runFolderDisplayName(entry.name, null) ?: categoryLabel(entry.category)
    return runNameInstant(entry.name)?.let { "${Formats.time(it)} 수집 기록" } ?: categoryLabel(entry.category)
}

/** A 수집 기록 title carries the collection time, so the meta says the other time is when it moved to the trash. */
internal fun localTrashMeta(entry: TrashEntry): String = listOfNotNull(categoryLabel(entry.category),
    if (entry.category != "RUN_HISTORY") "수집 자료"
    else entry.trashedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { "${Formats.time(it)} 이동" }
).joinToString(" · ")

/** 기술 정보 keeps the real name of each row whose title stands in for it. */
internal fun localTrashTechnicalNames(entries: List<TrashEntry>): List<GeoTechnicalValue> = entries.mapNotNull { entry ->
    localTrashTitle(entry).takeIf { it != entry.name }?.let { GeoTechnicalValue(it, entry.name) }
}

internal fun localTrashCanRestore(entry: TrashEntry): Boolean = entry.restoreSupported && entry.state == "TRASHED"

internal fun localTrashStateLabel(entry: TrashEntry): String = when (entry.state) {
    "TRASHED" -> if (entry.restoreSupported) "복원 가능" else "복원할 수 없음"
    "MOVING_TO_TRASH" -> "이동 상태 확인 중"
    "RESTORING" -> "복원 중"
    "PURGING" -> "삭제 처리 중"
    "RECOVERY_REQUIRED" -> "복구 확인 필요"
    else -> "상태 확인 필요"
}

internal data class LocalTrashEmptySnapshot(val deviceId: String, val trashIds: List<String>)
internal val TrashDialogFontScaleKey = SemanticsPropertyKey<Float>("TrashDialogFontScale")

internal fun localTrashEmptySnapshot(state: LocalTrashUiState): LocalTrashEmptySnapshot? {
    val deviceId = state.deviceId ?: return null
    if (!state.online || !state.emptySupported) return null
    val ids = state.entries.filter(::isPurgeEligible).map(TrashEntry::trashId).distinct()
    return ids.takeIf { it.isNotEmpty() }?.let { LocalTrashEmptySnapshot(deviceId, it) }
}
