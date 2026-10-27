package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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

    fun firstIncomplete(): GeoReceiptStep? = rows().firstOrNull { !receiptDone(it.first, it.second) }?.first
}

@Composable
fun GeoReceipt(state: GeoReceiptState, modifier: Modifier = Modifier) {
    val rows = state.rows()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReceiptTrack(state)
        GeoPropertyList(rows.map { it.first.title }) {
            rows.forEach { (step, item) ->
                GeoPropertyRow(step.title, item.word, tone = receiptTone(step, item), supportingText = item.meta)
            }
        }
    }
}

@Composable
fun GeoReceiptLine(state: GeoReceiptState, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        val colors = LocalGeoColors.current
        Canvas(modifier.size(44.dp, 8.dp).semantics { contentDescription = receiptDescription(state) }) {
            state.rows().forEachIndexed { index, (step, item) ->
                val center = Offset((4 + index * 12).dp.toPx(), size.height / 2)
                if (receiptDone(step, item)) drawCircle(colors.tone(receiptTone(step, item)), 4.dp.toPx(), center)
                else drawCircle(colors.borderStrong, 3.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
        }
    } else ReceiptTrack(state, modifier)
}

internal fun receiptDescription(state: GeoReceiptState): String =
    state.rows().joinToString(", ") { (step, item) -> "${step.title}: ${item.word}" }

internal fun receiptTrackWord(index: Int, word: String): String =
    if (word == listOf("종료", "저장", "수신", "보고")[index]) "완료" else word

internal fun receiptDone(step: GeoReceiptStep, item: GeoReceiptItem): Boolean =
    item.done && !(step == GeoReceiptStep.REPORT && item.word == "해당 없음")

internal fun receiptTone(step: GeoReceiptStep, item: GeoReceiptItem): Tone = when {
    step == GeoReceiptStep.REPORT && item.word == "해당 없음" -> Tone.UNKNOWN
    item.tone == Tone.WARNING || item.tone == Tone.ERROR -> item.tone
    receiptDone(step, item) -> Tone.SUCCESS
    step == GeoReceiptStep.REPORT && item.word == "보고 전" -> Tone.UNKNOWN
    else -> Tone.PENDING
}

@Composable
private fun ReceiptTrack(state: GeoReceiptState, modifier: Modifier = Modifier) {
    val colors = LocalGeoColors.current
    val rows = state.rows()
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val cellWidth = size.width / 4f
            val radius = 6.dp.toPx()
            rows.forEachIndexed { index, (step, item) ->
                val x = cellWidth * (index + .5f)
                if (index < 3) drawLine(colors.borderStrong,
                    Offset(x + radius, center.y), Offset(x + cellWidth - radius, center.y), 1.dp.toPx())
                val tone = receiptTone(step, item)
                if (tone == Tone.WARNING || tone == Tone.ERROR) {
                    val ink = colors.tone(tone)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, center.y - radius)
                        lineTo(x + radius, center.y + radius)
                        lineTo(x - radius, center.y + radius)
                        close()
                    }
                    drawPath(path, ink)
                } else if (receiptDone(step, item)) drawCircle(colors.tone(tone), radius, Offset(x, center.y))
                else drawCircle(colors.borderStrong, radius - .5.dp.toPx(), Offset(x, center.y), style = Stroke(1.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (step, item) ->
                Column(Modifier.weight(1f).padding(horizontal = 2.dp).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(listOf("종료", "저장", "수신", "보고")[index], color = colors.text,
                        style = LocalGeoType.current.caption, textAlign = TextAlign.Center)
                    Text(receiptTrackWord(index, item.word), color = colors.tone(receiptTone(step, item)),
                        style = LocalGeoType.current.caption, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
