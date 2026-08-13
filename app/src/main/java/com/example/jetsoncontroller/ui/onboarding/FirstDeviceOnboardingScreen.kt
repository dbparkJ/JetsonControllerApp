package com.example.jetsoncontroller.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.ConnectionStepper
import com.example.jetsoncontroller.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstDeviceOnboardingScreen(
    onScanQr: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("새 장비 등록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppSpacing.screen),
            verticalArrangement = Arrangement.Center
        ) {
            ConnectionStepper(
                labels = listOf("장비 등록", "안전한 인증", "Wi-Fi 설정"),
                currentStep = 0
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Jetson을 등록합니다",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = "본체의 QR을 확인한 뒤 가까운 장비와 안전하게 인증합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.xxLarge))
            OnboardingStep(
                icon = Icons.Default.QrCodeScanner,
                title = "QR 확인",
                description = "장비 본체에 표시된 등록 QR을 준비합니다."
            )
            OnboardingStep(
                icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                title = "장비 연결",
                description = "앱이 같은 장비를 Bluetooth로 찾습니다."
            )
            OnboardingStep(
                icon = Icons.Default.CheckCircle,
                title = "인증 완료",
                description = "인증 정보는 이 기기에 안전하게 저장됩니다."
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("QR 스캔", modifier = Modifier.padding(start = AppSpacing.small))
            }
        }
    }
}

@Composable
private fun OnboardingStep(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.medium)
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
