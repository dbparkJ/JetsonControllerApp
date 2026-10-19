package com.example.jetsoncontroller.ui.startup

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.GeoButton
import com.example.jetsoncontroller.ui.components.geo.GeonLoader
import com.example.jetsoncontroller.ui.theme.*
import kotlinx.coroutines.delay


private enum class StartupScene { LOADING, ERROR, APP }

@Composable
internal fun StartupContent(
    state: AppRestoreState,
    timing: StartupTiming,
    onRetry: () -> Unit,
    palette: GeoPalette,
    content: @Composable () -> Unit
) {
    val reducedMotion = geoReducedMotion()
    // A restore resolved behind the system splash never composes a loader transition.
    if (state !is AppRestoreState.Loading && timing.loadingVisibleAtMillis == null) {
        StartupTheme(palette, appVisible = state is AppRestoreState.Restored) {
            when (state) {
                is AppRestoreState.Restored -> content()
                is AppRestoreState.Failed -> StartupErrorScene(state.cause, { timing.retry(); onRetry() })
                else -> Unit
            }
        }
        return
    }
    var minimumElapsed by remember(state) {
        mutableStateOf(timing.remainingLoadingMillis(SystemClock.uptimeMillis()) == 0L)
    }
    LaunchedEffect(state) {
        if (state !is AppRestoreState.Loading) {
            var remaining = timing.remainingLoadingMillis(SystemClock.uptimeMillis())
            while (remaining > 0) {
                delay(remaining)
                remaining = timing.remainingLoadingMillis(SystemClock.uptimeMillis())
            }
            minimumElapsed = true
        }
    }
    val scene = when {
        state is AppRestoreState.Loading || !minimumElapsed -> StartupScene.LOADING
        state is AppRestoreState.Failed -> StartupScene.ERROR
        else -> StartupScene.APP
    }
    val duration = timing.crossfadeMillis(reducedMotion)
    StartupTheme(palette, appVisible = scene == StartupScene.APP) {
        Crossfade(scene, animationSpec = tween(duration), label = "startup") { displayed ->
            when (displayed) {
                StartupScene.LOADING -> StartupLoadingScene(Modifier.drawWithContent {
                    timing.loadingFrame(SystemClock.uptimeMillis())
                    drawContent()
                }, reducedMotion)
                StartupScene.ERROR -> StartupErrorScene(
                    cause = (state as? AppRestoreState.Failed)?.cause ?: return@Crossfade,
                    onRetry = { timing.retry(); onRetry() }
                )
                StartupScene.APP -> content()
            }
        }
    }
}

@Composable
private fun StartupTheme(palette: GeoPalette, appVisible: Boolean, content: @Composable () -> Unit) {
    // Navy light differs from Standard only in chrome; P10 has surface-coloured system bars.
    val scenePalette = if (!appVisible && palette == GeoPalette.NAVY_LIGHT) GeoPalette.STANDARD else palette
    JetsonControllerTheme(palette = scenePalette, navigationBarUsesSurface = !appVisible, content = content)
}

@Composable
fun StartupLoadingScene(modifier: Modifier = Modifier, reducedMotion: Boolean = geoReducedMotion()) {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(view, visible) {
        if (visible) {
            @Suppress("DEPRECATION")
            view.announceForAccessibility("장치 정보를 불러오는 중입니다")
        }
    }
    StartupSceneColumn(modifier.drawWithContent {
        visible = true
        drawContent()
    }.clearAndSetSemantics {
        contentDescription = "장치 정보를 불러오는 중입니다"
    }) {
        GeonLoader(size = 88.dp, reducedMotion = reducedMotion)
        Spacer(Modifier.height(20.dp))
        Text("GEO-X", style = LocalGeoType.current.title, color = LocalGeoColors.current.text)
        Spacer(Modifier.height(8.dp))
        Text("장치 정보를 불러오는 중입니다.", style = LocalGeoType.current.body,
            color = LocalGeoColors.current.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun StartupErrorScene(cause: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    StartupSceneColumn(modifier) {
        Image(painterResource(R.drawable.geo_symbol), contentDescription = null,
            modifier = Modifier.size(56.dp), alpha = 0.45f,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
        Spacer(Modifier.height(20.dp))
        Text("장치 정보를 불러오지 못했습니다", style = type.title, color = colors.text,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(restoreFailureWords(cause), modifier = Modifier.widthIn(max = 360.dp),
            style = type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        GeoButton("다시 시도", onRetry, Modifier.widthIn(max = 240.dp).fillMaxWidth().heightIn(min = 52.dp),
            actionArea = true)
    }
}

@Composable
private fun StartupSceneColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(LocalGeoColors.current.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, content = content)
    }
}
