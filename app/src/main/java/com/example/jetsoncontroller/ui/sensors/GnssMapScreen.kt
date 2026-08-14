package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.model.GnssSensorStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.util.Locale

private enum class VWorldLayer(val title: String, val path: String, val extension: String) {
    BASE("일반", "Base", "png"),
    SATELLITE("위성", "Satellite", "jpeg")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GnssMapScreen(
    gnss: GnssSensorStatus,
    telemetryFresh: Boolean,
    onBack: () -> Unit
) {
    var layer by remember { mutableStateOf(VWorldLayer.BASE) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GNSS 위치") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            if (BuildConfig.VWORLD_API_KEY.isBlank()) {
                Text(
                    "VWorld API 키가 설정되지 않았습니다.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                VWorldRasterMap(
                    modifier = Modifier.fillMaxSize(),
                    gnss = gnss,
                    layer = layer,
                    apiKey = BuildConfig.VWORLD_API_KEY
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 2.dp
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.padding(4.dp)) {
                    VWorldLayer.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = layer == option,
                            onClick = { layer = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VWorldLayer.entries.size
                            )
                        ) {
                            Text(option.title)
                        }
                    }
                }
            }

            GnssStatusBand(
                gnss = gnss,
                telemetryFresh = telemetryFresh,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun GnssStatusBand(
    gnss: GnssSensorStatus,
    telemetryFresh: Boolean,
    modifier: Modifier = Modifier
) {
    val live = telemetryFresh && gnss.active
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    gnssFixLabel(gnss.fixType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (live) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (live) "1 Hz · 실시간" else "데이터 대기",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                gnssCoordinateText(gnss),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                listOfNotNull(
                    gnss.satellites?.let { "위성 ${it}개" },
                    gnss.hdop?.let { "HDOP ${formatNumber(it, 1)}" },
                    gnss.altitudeM?.let { "고도 ${formatNumber(it, 1)} m" },
                    gnss.ntripMountpoint?.takeIf { gnss.ntripConnected }?.let { "NTRIP $it" }
                ).joinToString(" · ").ifBlank { "GNSS 부가정보 없음" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun gnssCoordinateText(gnss: GnssSensorStatus): String =
    if (gnss.hasValidLocation()) {
        "${formatNumber(gnss.latitude!!, 7)}, ${formatNumber(gnss.longitude!!, 7)}"
    } else {
        "유효한 위치 수신 대기 중"
    }

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

@Composable
private fun VWorldRasterMap(
    gnss: GnssSensorStatus,
    layer: VWorldLayer,
    apiKey: String,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapViewWithLifecycle()
    val controller = remember { VWorldMapController() }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            controller.update(
                mapView = it,
                layer = layer,
                apiKey = apiKey,
                latitude = gnss.latitude,
                longitude = gnss.longitude,
                markerTitle = gnssFixLabel(gnss.fixType)
            )
        }
    )
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (resumed) mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> {
                    if (started) mapView.onStop()
                    started = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private class VWorldMapController {
    private var map: MapLibreMap? = null
    private var marker: Marker? = null
    private var currentLayer: VWorldLayer? = null
    private var lastPosition: LatLng? = null
    private var positioned = false

    fun update(
        mapView: MapView,
        layer: VWorldLayer,
        apiKey: String,
        latitude: Double?,
        longitude: Double?,
        markerTitle: String
    ) {
        mapView.getMapAsync { readyMap ->
            map = readyMap
            if (currentLayer != layer) {
                currentLayer = layer
                marker = null
                readyMap.setStyle(vworldStyle(layer, apiKey)) {
                    updatePosition(readyMap, latitude, longitude, markerTitle, force = true)
                }
            } else {
                updatePosition(readyMap, latitude, longitude, markerTitle)
            }
        }
    }

    private fun updatePosition(
        targetMap: MapLibreMap,
        latitude: Double?,
        longitude: Double?,
        markerTitle: String,
        force: Boolean = false
    ) {
        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return
        val position = LatLng(latitude, longitude)
        if (!force && lastPosition == position) return
        marker?.let(targetMap::removeMarker)
        marker = targetMap.addMarker(
            MarkerOptions().position(position).title(markerTitle)
        )
        if (!positioned) {
            targetMap.cameraPosition = CameraPosition.Builder()
                .target(position)
                .zoom(17.0)
                .build()
            positioned = true
        } else {
            targetMap.animateCamera(CameraUpdateFactory.newLatLng(position), 450)
        }
        lastPosition = position
    }

    private fun vworldStyle(layer: VWorldLayer, apiKey: String): Style.Builder {
        val tileUrl =
            "https://api.vworld.kr/req/wmts/1.0.0/$apiKey/${layer.path}/{z}/{y}/{x}.${layer.extension}"
        val tileSet = TileSet("2.1.0", tileUrl).apply {
            attribution = "VWorld"
            minZoom = 0f
            maxZoom = 19f
        }
        return Style.Builder()
            .withSource(RasterSource("vworld-source", tileSet, 256))
            .withLayer(RasterLayer("vworld-layer", "vworld-source"))
    }
}
