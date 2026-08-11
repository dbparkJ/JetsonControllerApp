package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStorageScreen(
    state: DeviceStorageUiState,
    onBack: () -> Unit,
    onRootClick: (RemoteRoot) -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.currentRoot?.label ?: "저장 장치") },
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
                .padding(horizontal = 22.dp)
        ) {
            if (state.currentRoot == null) {
                RootsView(state.roots, onRootClick)
            } else {
                DirectoryView(state, onDirectoryClick, onUploadClick)
            }
        }
    }
}

@Composable
private fun RootsView(roots: List<RemoteRoot>, onRootClick: (RemoteRoot) -> Unit) {
    Text(
        text = "사용 가능한 저장소",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(roots) { root ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onRootClick(root) },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = root.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DirectoryView(
    state: DeviceStorageUiState,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (state.currentPath.isEmpty()) "/" else state.currentPath,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Button(
            onClick = { onUploadClick(state.currentRoot!!.id, state.currentPath) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("업로드")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.entries) { entry ->
            FileEntryRow(entry, onDirectoryClick)
        }
    }
}

@Composable
private fun FileEntryRow(entry: RemoteFileEntry, onDirectoryClick: (RemoteFileEntry) -> Unit) {
    val icon = if (entry.type == RemoteEntryType.DIRECTORY) Icons.Default.Folder else Icons.Default.Description
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.type == RemoteEntryType.DIRECTORY) { onDirectoryClick(entry) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (entry.type == RemoteEntryType.DIRECTORY) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = entry.name, style = MaterialTheme.typography.bodyLarge)
            if (entry.sizeBytes != null) {
                Text(text = "${entry.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
