package com.example.jetsoncontroller.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.ui.theme.AppSpacing
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

@Preview(name = "Operational states - light", showBackground = true, widthDp = 360)
@Preview(
    name = "Operational states - dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Operational states - 200% text",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f
)
@Composable
private fun OperationalStatesPreview() {
    JetsonControllerTheme {
        Column(
            modifier = Modifier.padding(AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            StatusBadge("온라인", StatusTone.SUCCESS)
            AppBanner("저장 공간을 확인해 주세요.", StatusTone.WARNING)
            AppBanner(
                message = "Jetson과 연결할 수 없습니다.",
                tone = StatusTone.ERROR,
                actionLabel = "다시 연결",
                onAction = {},
                onDismiss = {}
            )
            ConnectionStepper(
                labels = listOf("장비 찾기", "연결", "장비 확인", "인증", "상태 동기화"),
                currentStep = 2
            )
        }
    }
}
