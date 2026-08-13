package com.example.jetsoncontroller.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTargetSettingsScreen(
    targets: List<UploadTarget>,
    isLoading: Boolean,
    message: String?,
    error: String?,
    onSave: (targetId: String, label: String, baseUrl: String, token: String?) -> Unit,
    onDelete: (targetId: String) -> Unit,
    onRefresh: () -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit
) {
    var editingTarget by remember { mutableStateOf<UploadTarget?>(null) }
    var showNewTarget by rememberSaveable { mutableStateOf(false) }
    var deletingTarget by remember { mutableStateOf<UploadTarget?>(null) }
    var editorSubmitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message, isLoading, editorSubmitted) {
        if (editorSubmitted && message != null && !isLoading) {
            showNewTarget = false
            editingTarget = null
            editorSubmitted = false
        }
    }

    if (showNewTarget || editingTarget != null) {
        UploadTargetEditorDialog(
            target = editingTarget,
            isSaving = isLoading,
            error = if (editorSubmitted) error else null,
            onDismiss = {
                showNewTarget = false
                editingTarget = null
                editorSubmitted = false
            },
            onSave = { targetId, label, baseUrl, token ->
                editorSubmitted = true
                onSave(targetId, label, baseUrl, token)
            }
        )
    }

    deletingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isLoading) deletingTarget = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("${target.label} 삭제") },
            text = { Text("이 서버의 주소와 저장된 접근 토큰이 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deletingTarget = null
                    },
                    enabled = !isLoading
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingTarget = null },
                    enabled = !isLoading
                ) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 서버") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(
                        onClick = {
                            onClearFeedback()
                            editorSubmitted = false
                            showNewTarget = true
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "서버 추가")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                message?.let {
                    item {
                        InlineMessage(
                            message = it,
                            isError = false,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                error?.let {
                    item {
                        InlineMessage(
                            message = it,
                            isError = true,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                if (targets.isEmpty() && !isLoading) {
                    item {
                        EmptyState(
                            title = "등록된 서버가 없습니다",
                            message = "현재 선택할 수 있는 업로드 서버가 없습니다.",
                            actionLabel = "새 서버",
                            onAction = {
                                onClearFeedback()
                                editorSubmitted = false
                                showNewTarget = true
                            }
                        )
                    }
                }
                items(targets, key = { it.id }) { target ->
                    ListItem(
                        headlineContent = {
                            Text(
                                target.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    target.baseUrl ?: target.id,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!target.editable) {
                                    Text(
                                        "관리자 설정",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                if (target.editable) Icons.Default.Dns else Icons.Default.Lock,
                                contentDescription = null
                            )
                        },
                        trailingContent = if (target.editable) {
                            {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            onClearFeedback()
                                            editorSubmitted = false
                                            editingTarget = target
                                        },
                                        enabled = !isLoading
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "서버 수정")
                                    }
                                    IconButton(
                                        onClick = { deletingTarget = target },
                                        enabled = !isLoading
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "서버 삭제")
                                    }
                                }
                            }
                        } else {
                            null
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun UploadTargetEditorDialog(
    target: UploadTarget?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (targetId: String, label: String, baseUrl: String, token: String?) -> Unit
) {
    val isNew = target == null
    var targetId by rememberSaveable(target?.id) { mutableStateOf(target?.id.orEmpty()) }
    var label by rememberSaveable(target?.id) { mutableStateOf(target?.label.orEmpty()) }
    var baseUrl by rememberSaveable(target?.id) { mutableStateOf(target?.baseUrl.orEmpty()) }
    var token by rememberSaveable(target?.id) { mutableStateOf("") }
    var tokenVisible by rememberSaveable(target?.id) { mutableStateOf(false) }
    val validId = targetId.matches(Regex("[a-z0-9][a-z0-9.-]{0,63}"))
    val canSave = validId && label.isNotBlank() && isValidHttpsUrl(baseUrl) &&
        (!isNew || token.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
        title = { Text(if (isNew) "업로드 서버 추가" else "업로드 서버 수정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { value ->
                        targetId = value.lowercase().filter {
                            it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.'
                        }.take(64)
                    },
                    label = { Text("서버 ID") },
                    singleLine = true,
                    enabled = isNew && !isSaving,
                    isError = targetId.isNotEmpty() && !validId,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(64) },
                    label = { Text("표시 이름") },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it.take(2048) },
                    label = { Text("HTTPS 주소") },
                    placeholder = { Text("https://upload.example.com") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = baseUrl.isNotEmpty() && !isValidHttpsUrl(baseUrl),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.replace("\n", "").take(4096) },
                    label = { Text(if (isNew) "접근 토큰" else "새 접근 토큰") },
                    placeholder = if (isNew) null else {
                        { Text("변경하지 않음") }
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (tokenVisible) "토큰 숨기기" else "토큰 보기"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        targetId.trim(),
                        label.trim(),
                        baseUrl.trim().trimEnd('/'),
                        token.trim().ifEmpty { null }
                    )
                },
                enabled = canSave && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("취소")
            }
        }
    )
}

private fun isValidHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.query == null &&
        uri.fragment == null
}.getOrDefault(false)
