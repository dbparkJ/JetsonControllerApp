package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class GeoLogLine(val id: String, val text: String)

fun logAtEnd(totalItems: Int, lastVisibleIndex: Int?, lastItemBottom: Int?, viewportEnd: Int): Boolean =
    totalItems == 0 || (lastVisibleIndex == totalItems - 1 && lastItemBottom != null && lastItemBottom <= viewportEnd)

fun logFollowAfterScroll(following: Boolean, atEnd: Boolean, userScrolling: Boolean, programmatic: Boolean): Boolean =
    when {
        programmatic -> following
        atEnd -> true
        userScrolling -> false
        else -> following
    }

fun shouldFollowLogAppend(following: Boolean, hasLines: Boolean, userScrolling: Boolean): Boolean =
    following && hasLines && !userScrolling

/** Give this view a bounded height. Stable line ids retain the user's position on append. */
@Composable
fun GeoLogView(
    lines: List<GeoLogLine>,
    live: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val colors = contentColors
    var following by remember(listState) { mutableStateOf(true) }
    var programmatic by remember(listState) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            Triple(listState.isScrollInProgress, programmatic,
                logAtEnd(layout.totalItemsCount, last?.index,
                    last?.let { it.offset + it.size + layout.afterContentPadding }, layout.viewportEndOffset))
        }.distinctUntilChanged().collect { (scrolling, automatic, atEnd) ->
            following = logFollowAfterScroll(following, atEnd, scrolling, automatic)
        }
    }
    LaunchedEffect(lines.size, lines.lastOrNull(), listState) {
        if (lines.isEmpty()) following = true
        if (shouldFollowLogAppend(following, lines.isNotEmpty(), listState.isScrollInProgress)) {
            programmatic = true
            try { scrollToLogEnd(listState, lines.lastIndex) } finally { programmatic = false }
        }
    }

    Column(modifier) {
        Text(if (live) "실시간" else "보관 로그", color = colors.secondary, style = ContentType.caption,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(bottom = 72.dp)) {
                    items(lines, key = { it.id }) { line ->
                        Text(line.text, color = colors.text, style = ContentType.mono, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (!following && lines.isNotEmpty()) {
                LatestLogAction(Modifier.align(Alignment.BottomEnd)) {
                    scope.launch {
                        programmatic = true
                        following = true
                        try { scrollToLogEnd(listState, lines.lastIndex) } finally { programmatic = false }
                    }
                }
            }
        }
    }
}

private suspend fun scrollToLogEnd(state: LazyListState, lastIndex: Int) {
    state.scrollToItem(lastIndex)
    val layout = state.layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    // A wrapped log line can be taller than the viewport; showing its start is not the end.
    val remaining = last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset
    if (remaining > 0) state.scrollBy(remaining.toFloat())
}

// TODO(3-B merge): replace with the shared floating tertiary button.
@Composable
private fun LatestLogAction(modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Surface(modifier, color = contentColors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), shadowElevation = 1.dp) {
        Box(Modifier.contentTarget(onClick = onClick).heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text("최신 로그", color = contentColors.primary, style = ContentType.button)
        }
    }
}
