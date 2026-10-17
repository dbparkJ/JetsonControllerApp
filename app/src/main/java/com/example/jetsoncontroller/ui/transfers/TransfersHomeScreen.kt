package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.components.statusVisuals
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/**
 * 전송 root: what is moving now, what is waiting, what failed and why, and which uploads the
 * server has confirmed. Callbacks carry job IDs; the integrator owns the navigation.
 */
@Composable
internal fun TransfersHomeScreen(
    state: TransfersHomeUiState,
    onPickFiles: () -> Unit,
    onOpenServerFiles: () -> Unit,
    onOpenTransfer: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (String) -> Unit
) {
    val c = LocalGeoColors.current
    var confirmCancelJobId by remember { mutableStateOf<String?>(null) }
    confirmCancelJobId?.let { jobId ->
        AlertDialog(
            onDismissRequest = { confirmCancelJobId = null },
            containerColor = c.surface,
            title = { Text("전송을 취소할까요?") },
            text = { Text("취소한 전송은 처음부터 다시 보내야 합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancelJobId = null
                    onCancelTransfer(jobId)
                }) { Text("전송 취소", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelJobId = null }) { Text("계속 보내기") }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("전송", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onPickFiles) {
                Text("보낼 파일 고르기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.primary)
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.active?.let { active ->
                item {
                    ActiveTransferCard(
                        active = active,
                        onOpen = { onOpenTransfer(active.jobId) },
                        onCancel = { confirmCancelJobId = active.jobId }
                    )
                }
            }
            if (state.failed.isNotEmpty()) {
                item {
                    TransferSection(title = "확인이 필요한 전송") {
                        state.failed.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                            FailedTransferRow(row, onRetry = { onRetryTransfer(row.jobId) })
                        }
                    }
                }
            }
            if (state.queued.isNotEmpty()) {
                item {
                    TransferSection(title = "대기 중") {
                        state.queued.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                            PlainTransferRow(row, onClick = { onOpenTransfer(row.jobId) })
                        }
                    }
                }
            }
            if (state.verified.isNotEmpty()) {
                item {
                    TransferSection(title = "서버 수신 확인", trailing = {
                        TextButton(onClick = onOpenServerFiles) {
                            Text("서버 파일", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.primary)
                        }
                    }) {
                        state.verified.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                            PlainTransferRow(row, onClick = { onOpenTransfer(row.jobId) })
                        }
                    }
                }
            }
            state.emptyText?.let { text ->
                item {
                    Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.muted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            item { ExplanationLine(state.explanation, state.explanationTone) }
        }
    }
}

@Composable
private fun ActiveTransferCard(active: ActiveTransferState, onOpen: () -> Unit, onCancel: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        active.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(active.subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                StatusBadge(active.stateLabel, active.stateTone)
            }
            val progress = active.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = c.primary,
                    trackColor = c.subtle
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = c.primary,
                    trackColor = c.subtle
                )
            }
            MetricsRow(active)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) { Text("자세히", color = c.ink) }
                if (active.canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) { Text("전송 취소", color = c.danger) }
                }
            }
        }
    }
}

/** Sent bytes always; speed and remaining time only when they were actually computed. */
@Composable
private fun MetricsRow(active: ActiveTransferState) {
    val c = LocalGeoColors.current
    val cells = buildList {
        add("보낸 용량" to active.sentLabel)
        active.speedLabel?.let { add("속도" to it) }
        active.remainingLabel?.let { add("남은 시간" to it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransferSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink)
            trailing?.invoke()
        }
        Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun FailedTransferRow(row: FailedTransferState, onRetry: () -> Unit) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(StatusTone.ERROR)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(visuals.icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(row.cause, style = MaterialTheme.typography.bodySmall, color = c.inkStrong)
            }
        }
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 40.dp)
        ) { Text("다시 시도", color = c.ink) }
    }
}

@Composable
private fun PlainTransferRow(row: TransferRowState, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(row.tone)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(visuals.icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.inkSubtle, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExplanationLine(text: String, tone: StatusTone) {
    val c = LocalGeoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = statusVisuals(tone).content,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.inkStrong)
    }
}
