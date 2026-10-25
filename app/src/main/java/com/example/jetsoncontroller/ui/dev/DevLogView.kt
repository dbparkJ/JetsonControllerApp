package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.jetsoncontroller.ui.storage.R7Card

/** The developer log adds file size and row padding while sharing the follow rules. */
@Composable
internal fun DevLogView(lines: List<GeoLogLine>, live: Boolean, sizeBytes: Long?, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(true) }
    var automatic by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow {
            val layout = list.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            Triple(list.isScrollInProgress, automatic, logAtEnd(layout.totalItemsCount, last?.index,
                last?.let { it.offset + it.size + layout.afterContentPadding }, layout.viewportEndOffset))
        }.distinctUntilChanged().collect { (scrolling, programmatic, atEnd) ->
            following = logFollowAfterScroll(following, atEnd, scrolling, programmatic)
        }
    }
    LaunchedEffect(lines.size, lines.lastOrNull()) {
        if (shouldFollowLogAppend(following, lines.isNotEmpty(), list.isScrollInProgress)) {
            automatic = true
            try { devLogEnd(list, lines.lastIndex) } finally { automatic = false }
        }
    }
    R7Card(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GeoBadge(if (live) "실시간" else "보관 로그", if (live) Tone.SUCCESS else Tone.UNKNOWN)
            Spacer(Modifier.weight(1f))
            DevText(Formats.size(sizeBytes), secondary = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize(), state = list,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)) {
                    items(lines, key = { it.id }) { line ->
                        Text(line.text, Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            color = LocalGeoColors.current.text, style = LocalGeoType.current.mono)
                    }
                }
            }
            if (!following && lines.isNotEmpty()) GeoButton("최신 로그", {
                scope.launch {
                    automatic = true
                    following = true
                    try { devLogEnd(list, lines.lastIndex) } finally { automatic = false }
                }
            }, Modifier.align(Alignment.BottomEnd), style = GeoButtonStyle.Tertiary)
        }
    }
}

private suspend fun devLogEnd(list: LazyListState, index: Int) {
    list.scrollToItem(index)
    val layout = list.layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull { it.index == index } ?: return
    val remaining = last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset
    if (remaining > 0) list.scrollBy(remaining.toFloat())
}
