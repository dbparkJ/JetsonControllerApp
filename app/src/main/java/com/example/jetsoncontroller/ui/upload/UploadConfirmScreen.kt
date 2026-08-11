package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadConfirmScreen(
    rootId: String,
    path: String,
    targets: List<UploadTarget>,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedTargetId by remember { mutableStateOf(targets.firstOrNull()?.id ?: "primary") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 확인") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Text(text = "업로드할 폴더", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(text = path.ifEmpty { "/" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(text = "업로드 대상 서버", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            
            targets.forEach { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTargetId = target.id }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedTargetId == target.id, onClick = { selectedTargetId = target.id })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = target.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onConfirm(selectedTargetId) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("업로드 시작")
            }
        }
    }
}
