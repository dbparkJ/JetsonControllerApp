package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/**
 * Read-only task detail for normal operators: status and last result. Developer mode adds the
 * identifiers and the entry points to logs and configuration; nothing here edits the task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailScreen(
    pipeline: ManagedPipeline,
    connected: Boolean,
    developerModeEnabled: Boolean,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
    onOpenConfig: () -> Unit = {}
) {
    val c = LocalGeoColors.current
    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text(pipeline.label, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas, titleContentColor = c.ink)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                DetailCard(taskDetailRows(pipeline, connected))
            }
            if (developerModeEnabled) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("개발자 모드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink)
                        DetailCard(taskDeveloperRows(pipeline))
                        OutlinedButton(
                            onClick = onOpenLogs,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("실행 로그 보기", color = c.ink) }
                        OutlinedButton(
                            onClick = onOpenConfig,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("작업 설정 보기", color = c.ink) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(rows: List<TaskDetailRow>) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(0.42f))
                    Text(row.value, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(0.58f))
                }
            }
        }
    }
}
