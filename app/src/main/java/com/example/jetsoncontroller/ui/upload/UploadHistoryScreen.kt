package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadHistoryScreen(
    history: List<UploadJob>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("업로드 기록이 없습니다.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { job ->
                    UploadHistoryCard(job)
                }
            }
        }
    }
}

@Composable
private fun UploadHistoryCard(job: UploadJob) {
    val (icon, color, label) = when (job.state) {
        UploadJobState.COMPLETED -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "완료")
        UploadJobState.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "실패")
        UploadJobState.UPLOADING, UploadJobState.SCANNING, UploadJobState.QUEUED -> Triple(Icons.Default.FileUpload, MaterialTheme.colorScheme.primary, "진행 중")
        UploadJobState.CANCELLED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.outline, "취소됨")
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = job.relativePath.split("/").last().ifEmpty { "/" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "$label · ${job.targetId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.bytesTotal != null) {
                Text(text = formatSize(job.bytesTotal), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        else -> "%.1f MB".format(mb)
    }
}
