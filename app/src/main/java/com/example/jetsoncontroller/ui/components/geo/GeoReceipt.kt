package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone

enum class GeoReceiptStep(val title: String) {
    COLLECTION("수집 종료"), STORAGE("장치 저장"), SERVER("서버 수신"), REPORT("GEO& Work 보고")
}

data class GeoReceiptItem(
    val word: String = "미확인",
    val tone: Tone = Tone.UNKNOWN,
    val meta: String? = null,
    val done: Boolean = false
)

data class GeoReceiptState(
    val collection: GeoReceiptItem = GeoReceiptItem(),
    val storage: GeoReceiptItem = GeoReceiptItem(),
    val server: GeoReceiptItem = GeoReceiptItem(),
    val report: GeoReceiptItem = GeoReceiptItem(word = "보고 전")
) {
    fun rows(): List<Pair<GeoReceiptStep, GeoReceiptItem>> = listOf(
        GeoReceiptStep.COLLECTION to collection, GeoReceiptStep.STORAGE to storage,
        GeoReceiptStep.SERVER to server, GeoReceiptStep.REPORT to report
    )

    fun firstIncomplete(): GeoReceiptStep? = rows().firstOrNull { !it.second.done }?.first
}

@Composable
fun GeoReceipt(state: GeoReceiptState, modifier: Modifier = Modifier) {
    val rows = state.rows()
    GeoPropertyList(rows.map { it.first.title }, modifier) {
        rows.forEach { (step, item) ->
            GeoPropertyRow(step.title, item.word, tone = item.tone, supportingText = item.meta)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoReceiptLine(state: GeoReceiptState, modifier: Modifier = Modifier) {
    val onePerLine = LocalDensity.current.fontScale >= 1.5f
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = if (onePerLine) 1 else Int.MAX_VALUE) {
        state.rows().take(3).forEach { (step, item) ->
            ReceiptPair(step.title, item, if (onePerLine) Modifier.fillMaxWidth() else Modifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptPair(label: String, item: GeoReceiptItem, modifier: Modifier) {
    val colors = LocalGeoColors.current
    FlowRow(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.textSecondary, style = LocalGeoType.current.caption)
        GeoBadge(item.word, item.tone)
    }
}
