package com.example.jetsoncontroller.ui.components

import com.example.jetsoncontroller.ui.model.Tone
import android.content.res.Configuration
import com.example.jetsoncontroller.ui.components.geo.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.ui.theme.GeoSpace
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
            modifier = Modifier.padding(GeoSpace.space8),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.space6)
        ) {
            GeoBadge("온라인", Tone.SUCCESS)
            GeoInlineNote("저장 공간을 확인해 주세요.", tone = Tone.WARNING)
            GeoInlineNote(
                text = "장치와 연결할 수 없습니다.",
                tone = Tone.ERROR,
                actionLabel = "다시 연결",
                onAction = {},
                onDismiss = {}
            )
            listOf("장치 찾기", "연결", "장치 확인", "인증", "상태 동기화").forEachIndexed { index, label ->
                GeoCheckRow(label, if (index < 2) "완료" else if (index == 2) "확인 중" else "대기", if (index < 2) Tone.SUCCESS else if (index == 2) Tone.PENDING else Tone.UNKNOWN)
            }

        }
    }
}
