package com.example.jetsoncontroller.ui.sensors

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.data.location.MobileLocationFix
import com.example.jetsoncontroller.model.GnssSensorStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon as MapMarkerIcon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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

private val JetsonMarkerColor = Color(0xFF1565C0)
private val MobileMarkerColor = Color(0xFFD84315)
private val JetsonMarkerArgb = 0xFF1565C0.toInt()
private val MobileMarkerArgb = 0xFFD84315.toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GnssMapScreen(
    gnss: GnssSensorStatus,
    telemetryFresh: Boolean,
    deviceOnline: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mobileLocationViewModel: MobileLocationViewModel = viewModel(
        factory = remember(context.applicationContext) {
            MobileLocationViewModel.Factory(context.applicationContext)
        }
    )
    val mobileLocation by mobileLocationViewModel.uiState.collectAsStateWithLifecycle()
    MobileLocationLifecycleEffect(mobileLocationViewModel)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mobileLocationViewModel.refresh()
    }
    var layer by remember { mutableStateOf(VWorldLayer.BASE) }
    val gnssActive = deviceOnline && telemetryFresh && gnss.active
    val deviceAvailability = deviceLocationAvailability(
        deviceOnline = deviceOnline,
        telemetryFresh = telemetryFresh,
        gnssActive = gnss.active,
        hasValidLocation = gnss.hasValidLocation()
    )
    val mobileAvailability = mobileLocationAvailability(mobileLocation)
    val receptionLabel = gnssReceptionLabel(
        gnssActive = gnssActive,
        fixType = gnss.fixType,
        rtkStatus = gnss.rtkStatus
    )

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
        },
        bottomBar = {
            GnssStatusBand(
                gnss = gnss,
                telemetryFresh = telemetryFresh,
                deviceOnline = deviceOnline,
                mobileLocation = mobileLocation,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                onOpenLocationSettings = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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
                    mobileFix = mobileLocation.fix,
                    showDeviceMarker = deviceAvailability == DeviceLocationAvailability.ACTIVE,
                    showMobileMarker = mobileAvailability == MobileLocationAvailability.ACTIVE,
                    layer = layer,
                    apiKey = BuildConfig.VWORLD_API_KEY,
                    deviceMarkerTitle = "Jetson · $receptionLabel"
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

        }
    }
}

@Composable
private fun MobileLocationLifecycleEffect(viewModel: MobileLocationViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }
}

@Composable
private fun GnssStatusBand(
    gnss: GnssSensorStatus,
    telemetryFresh: Boolean,
    deviceOnline: Boolean,
    mobileLocation: MobileLocationUiState,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gnssActive = deviceOnline && telemetryFresh && gnss.active
    val receptionLabel = gnssReceptionLabel(
        gnssActive = gnssActive,
        fixType = gnss.fixType,
        rtkStatus = gnss.rtkStatus
    )
    val deviceAvailability = deviceLocationAvailability(
        deviceOnline = deviceOnline,
        telemetryFresh = telemetryFresh,
        gnssActive = gnss.active,
        hasValidLocation = gnss.hasValidLocation()
    )
    val mobileAvailability = mobileLocationAvailability(mobileLocation)
    val headline = when (deviceAvailability) {
        DeviceLocationAvailability.OFFLINE,
        DeviceLocationAvailability.STALE -> deviceLocationAvailabilityLabel(deviceAvailability)
        else -> receptionLabel
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (gnssReceptionState(gnssActive, gnss.fixType, gnss.rtkStatus)) {
                        GnssReceptionState.RTK_FIXED -> MaterialTheme.colorScheme.primary
                        GnssReceptionState.RTK_FLOAT -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    if (gnssActive) "1 Hz · 실시간" else "데이터 대기",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PositionStatusRow(
                markerColor = JetsonMarkerColor,
                title = "Jetson 장치 위치",
                stateText = deviceLocationAvailabilityLabel(deviceAvailability),
                coordinateText = gnssCoordinateText(
                    gnss,
                    isCurrent = deviceAvailability == DeviceLocationAvailability.ACTIVE
                ),
                detailText = listOfNotNull(
                    gnss.satellites?.let { "위성 ${it}개" },
                    gnss.hdop?.let { "HDOP ${formatNumber(it, 1)}" },
                    gnss.altitudeM?.let { "고도 ${formatNumber(it, 1)} m" },
                    gnss.ntripMountpoint?.takeIf { gnss.ntripConnected }?.let { "NTRIP $it" }
                ).joinToString(" · ").ifBlank { null }
            )
            PositionStatusRow(
                markerColor = MobileMarkerColor,
                title = "모바일 위치",
                stateText = mobileLocationAvailabilityLabel(mobileAvailability),
                coordinateText = mobileCoordinateText(
                    mobileLocation.fix,
                    isCurrent = mobileAvailability == MobileLocationAvailability.ACTIVE
                ),
                detailText = mobileLocation.fix?.let { fix ->
                    listOfNotNull(
                        fix.accuracyM?.let { "정확도 ${formatNumber(it.toDouble(), 1)} m" },
                        fix.altitudeM?.let { "고도 ${formatNumber(it, 1)} m" }
                    ).joinToString(" · ").ifBlank { null }
                },
                action = when (mobileAvailability) {
                    MobileLocationAvailability.PERMISSION_REQUIRED -> "권한 허용" to onRequestPermission
                    MobileLocationAvailability.PROVIDER_DISABLED -> "위치 켜기" to onOpenLocationSettings
                    else -> null
                }
            )
            mobileLocation.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PositionStatusRow(
    markerColor: Color,
    title: String,
    stateText: String,
    coordinateText: String,
    detailText: String? = null,
    action: Pair<String, () -> Unit>? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(11.dp)
                .background(markerColor, CircleShape)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        stateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                action?.let { (label, onClick) ->
                    TextButton(onClick = onClick) { Text(label) }
                }
            }
            Text(coordinateText, style = MaterialTheme.typography.bodyMedium)
            detailText?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun gnssCoordinateText(
    gnss: GnssSensorStatus,
    isCurrent: Boolean = true
): String =
    if (gnss.hasValidLocation()) {
        val prefix = if (isCurrent) "" else "마지막 위치 · "
        "$prefix${formatNumber(gnss.latitude!!, 7)}, ${formatNumber(gnss.longitude!!, 7)}"
    } else {
        "유효한 위치 수신 대기 중"
    }

internal fun mobileCoordinateText(
    fix: MobileLocationFix?,
    isCurrent: Boolean = true
): String =
    if (fix?.hasValidCoordinates() == true) {
        val prefix = if (isCurrent) "" else "마지막 위치 · "
        "$prefix${formatNumber(fix.latitude, 7)}, ${formatNumber(fix.longitude, 7)}"
    } else {
        "유효한 위치 수신 대기 중"
    }

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

@Composable
private fun VWorldRasterMap(
    gnss: GnssSensorStatus,
    mobileFix: MobileLocationFix?,
    showDeviceMarker: Boolean,
    showMobileMarker: Boolean,
    layer: VWorldLayer,
    apiKey: String,
    deviceMarkerTitle: String,
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
                devicePosition = gnss.toLatLngOrNull().takeIf { showDeviceMarker },
                mobilePosition = mobileFix.toLatLngOrNull().takeIf { showMobileMarker },
                deviceMarkerTitle = deviceMarkerTitle
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
    private var deviceMarker: Marker? = null
    private var mobileMarker: Marker? = null
    private var deviceIcon: MapMarkerIcon? = null
    private var mobileIcon: MapMarkerIcon? = null
    private var currentLayer: VWorldLayer? = null
    private var lastDevicePosition: LatLng? = null
    private var lastMobilePosition: LatLng? = null
    private var lastDeviceTitle: String? = null
    private var lastCameraPositions: Pair<LatLng?, LatLng?>? = null
    private var positioned = false
    private var styleReady = false
    private var styleGeneration = 0L
    private var pendingDevicePosition: LatLng? = null
    private var pendingMobilePosition: LatLng? = null
    private var pendingDeviceMarkerTitle: String = ""

    fun update(
        mapView: MapView,
        layer: VWorldLayer,
        apiKey: String,
        devicePosition: LatLng?,
        mobilePosition: LatLng?,
        deviceMarkerTitle: String
    ) {
        pendingDevicePosition = devicePosition
        pendingMobilePosition = mobilePosition
        pendingDeviceMarkerTitle = deviceMarkerTitle
        ensureMarkerIcons(mapView)
        mapView.getMapAsync { readyMap ->
            if (currentLayer != layer) {
                deviceMarker?.let(readyMap::removeMarker)
                mobileMarker?.let(readyMap::removeMarker)
                currentLayer = layer
                deviceMarker = null
                mobileMarker = null
                lastDevicePosition = null
                lastMobilePosition = null
                lastDeviceTitle = null
                lastCameraPositions = null
                styleReady = false
                styleGeneration += 1L
                val requestedGeneration = styleGeneration
                readyMap.setStyle(vworldStyle(layer, apiKey)) {
                    if (
                        requestedGeneration == styleGeneration &&
                        currentLayer == layer
                    ) {
                        styleReady = true
                        updatePositions(
                            targetMap = readyMap,
                            devicePosition = pendingDevicePosition,
                            mobilePosition = pendingMobilePosition,
                            deviceMarkerTitle = pendingDeviceMarkerTitle,
                            force = true
                        )
                    }
                }
            } else if (styleReady) {
                updatePositions(
                    targetMap = readyMap,
                    devicePosition = pendingDevicePosition,
                    mobilePosition = pendingMobilePosition,
                    deviceMarkerTitle = pendingDeviceMarkerTitle
                )
            }
        }
    }

    private fun ensureMarkerIcons(mapView: MapView) {
        if (deviceIcon != null && mobileIcon != null) return
        val density = mapView.resources.displayMetrics.density
        val iconFactory = IconFactory.getInstance(mapView.context)
        deviceIcon = iconFactory.fromBitmap(
            createMarkerBitmap(density, JetsonMarkerArgb, "J", pinCenterFraction = 0.27f)
        )
        mobileIcon = iconFactory.fromBitmap(
            createMarkerBitmap(density, MobileMarkerArgb, "M", pinCenterFraction = 0.73f)
        )
    }

    private fun updatePositions(
        targetMap: MapLibreMap,
        devicePosition: LatLng?,
        mobilePosition: LatLng?,
        deviceMarkerTitle: String,
        force: Boolean = false
    ) {
        if (
            force || devicePosition != lastDevicePosition ||
            deviceMarkerTitle != lastDeviceTitle
        ) {
            deviceMarker?.let(targetMap::removeMarker)
            deviceMarker = devicePosition?.let { position ->
                targetMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(deviceMarkerTitle)
                        .icon(deviceIcon)
                )
            }
            lastDevicePosition = devicePosition
            lastDeviceTitle = deviceMarkerTitle
        }
        if (force || mobilePosition != lastMobilePosition) {
            mobileMarker?.let(targetMap::removeMarker)
            mobileMarker = mobilePosition?.let { position ->
                targetMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("모바일 위치")
                        .icon(mobileIcon)
                )
            }
            lastMobilePosition = mobilePosition
        }

        updateCamera(targetMap, devicePosition, mobilePosition, force)
    }

    private fun updateCamera(
        targetMap: MapLibreMap,
        devicePosition: LatLng?,
        mobilePosition: LatLng?,
        force: Boolean
    ) {
        val positions = devicePosition to mobilePosition
        if (!force && !positions.movedSince(lastCameraPositions)) return
        lastCameraPositions = positions

        if (
            devicePosition != null && mobilePosition != null &&
            devicePosition.isDistinctFrom(mobilePosition)
        ) {
            val bounds = LatLngBounds.Builder()
                .include(devicePosition)
                .include(mobilePosition)
                .build()
            targetMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, MapBoundsPaddingPx),
                MapCameraAnimationMs
            )
            positioned = true
            return
        }

        val position = devicePosition ?: mobilePosition ?: return
        if (!positioned) {
            targetMap.cameraPosition = CameraPosition.Builder()
                .target(position)
                .zoom(DefaultMapZoom)
                .build()
            positioned = true
        } else {
            targetMap.animateCamera(
                CameraUpdateFactory.newLatLng(position),
                MapCameraAnimationMs
            )
        }
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

private fun createMarkerBitmap(
    density: Float,
    color: Int,
    label: String,
    pinCenterFraction: Float
): Bitmap {
    val width = (72f * density).toInt().coerceAtLeast(72)
    val height = (72f * density).toInt().coerceAtLeast(72)
    val anchorX = width / 2f
    val anchorY = height / 2f
    val centerX = width * pinCenterFraction
    val radius = 14f * density
    val centerY = anchorY - radius * 1.12f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val pointer = Path().apply {
        moveTo(centerX - radius * 0.55f, centerY + radius * 0.55f)
        lineTo(anchorX, anchorY)
        lineTo(centerX + radius * 0.55f, centerY + radius * 0.55f)
        close()
    }
    canvas.drawPath(pointer, fill)
    canvas.drawCircle(centerX, centerY, radius, fill)
    canvas.drawCircle(centerX, centerY, radius, outline)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        textSize = radius * 1.05f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(label, centerX, textY, textPaint)
    return bitmap
}

private fun GnssSensorStatus.toLatLngOrNull(): LatLng? =
    if (hasValidLocation()) LatLng(latitude!!, longitude!!) else null

private fun MobileLocationFix?.toLatLngOrNull(): LatLng? =
    this?.takeIf(MobileLocationFix::hasValidCoordinates)?.let {
        LatLng(it.latitude, it.longitude)
    }

private fun LatLng.isDistinctFrom(other: LatLng): Boolean =
    distanceMetersTo(other) > MinBoundsDistanceMeters

private fun Pair<LatLng?, LatLng?>.movedSince(
    previous: Pair<LatLng?, LatLng?>?
): Boolean {
    if (previous == null) return true
    val deviceMoved = first.movedFrom(previous.first)
    val mobileMoved = second.movedFrom(previous.second)
    return deviceMoved || mobileMoved
}

private fun LatLng?.movedFrom(previous: LatLng?): Boolean = when {
    this == null || previous == null -> this != previous
    else -> distanceMetersTo(previous) >= MinCameraMovementMeters
}

private fun LatLng.distanceMetersTo(other: LatLng): Double {
    val latitudeRadians = Math.toRadians(latitude)
    val otherLatitudeRadians = Math.toRadians(other.latitude)
    val latitudeDelta = otherLatitudeRadians - latitudeRadians
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val haversine = kotlin.math.sin(latitudeDelta / 2.0).let { it * it } +
        kotlin.math.cos(latitudeRadians) * kotlin.math.cos(otherLatitudeRadians) *
        kotlin.math.sin(longitudeDelta / 2.0).let { it * it }
    return EarthRadiusMeters * 2.0 * kotlin.math.asin(
        kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0))
    )
}

private const val DefaultMapZoom = 17.0
private const val MapCameraAnimationMs = 450
private const val MapBoundsPaddingPx = 160
private const val MinBoundsDistanceMeters = 50.0
private const val MinCameraMovementMeters = 5.0
private const val EarthRadiusMeters = 6_371_000.0
