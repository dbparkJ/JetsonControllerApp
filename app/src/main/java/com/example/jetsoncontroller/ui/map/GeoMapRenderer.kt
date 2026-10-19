package com.example.jetsoncontroller.ui.map

import android.view.Gravity
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.ui.field.LocationQualityBand
import com.example.jetsoncontroller.ui.field.locationQualityBand
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import kotlin.math.cos

enum class GeoMapBase { GENERAL, SATELLITE }

internal const val MapBlue = "#1F52B8"
internal const val MapPrecise = "#22704B"
internal const val MapApproximate = "#8A5A06"
internal const val MapUnknown = "#5D667B"
internal const val MapDevice = "#121A3D"
internal const val MapGap = "#B3372C"

/** Style JSON creates native FillLayer / LineLayer / CircleLayer objects; no annotation API. */
internal fun mapStyleJson(base: GeoMapBase, apiKey: String, background: String, dim: Float): String {
    val sources = linkedMapOf<String, Any>()
    val layers = mutableListOf<Map<String, Any>>()
    fun layer(id: String, type: String, source: String? = null, kind: Any? = null,
              paint: Map<String, Any>, layout: Map<String, Any> = emptyMap()) {
        layers += buildMap {
            put("id", id); put("type", type); put("paint", paint)
            source?.let { put("source", it) }
            kind?.let { put("filter", if (it is String) listOf("==", listOf("get", "kind"), it) else it) }
            if (layout.isNotEmpty()) put("layout", layout)
        }
    }
    layer("background", "background", paint = mapOf("background-color" to background))
    if (apiKey.isNotBlank()) {
        val path = if (base == GeoMapBase.GENERAL) "Base/{z}/{y}/{x}.png" else "Satellite/{z}/{y}/{x}.jpeg"
        sources["raster"] = mapOf("type" to "raster", "tileSize" to 256, "maxzoom" to 19,
            "tiles" to listOf("https://api.vworld.kr/req/wmts/1.0.0/$apiKey/$path"), "attribution" to "© VWorld")
        layer("raster", "raster", "raster", paint = mapOf("raster-brightness-max" to if (base == GeoMapBase.GENERAL) dim else 1f,
            "raster-saturation" to if (base == GeoMapBase.GENERAL && dim < 1f) -0.2f else 0f))
    }
    listOf("zone", "trail", "device", "phone").forEach {
        sources[it] = mapOf("type" to "geojson", "data" to mapOf("type" to "FeatureCollection", "features" to emptyList<Any>()))
    }
    fun line(id: String, source: String, kind: String, color: String, width: Float, dash: List<Float>? = null) {
        layer(id, "line", source, kind, buildMap {
            put("line-color", color); put("line-width", width)
            dash?.let { put("line-dasharray", it) }
        }, mapOf("line-cap" to if (dash == null) "round" else "butt", "line-join" to "round"))
    }
    fun circle(id: String, source: String, kind: String, radius: Any, color: String, stroke: String,
               width: Float, opacity: Float = 1f, strokeOpacity: Float = 1f) {
        layer(id, "circle", source, kind, mapOf("circle-radius" to radius, "circle-color" to color,
            "circle-stroke-color" to stroke, "circle-stroke-width" to width, "circle-opacity" to opacity,
            "circle-stroke-opacity" to strokeOpacity))
    }
    layer("range-fill", "fill", "zone", listOf("match", listOf("get", "kind"), listOf("range", "area"), true, false),
        mapOf("fill-color" to MapBlue, "fill-opacity" to if (base == GeoMapBase.GENERAL && dim < 1f && apiKey.isNotBlank()) .22f else .16f))
    line("range-casing", "zone", "range", "#FFFFFF", 5f)
    line("range-edge", "zone", "range", MapBlue, 2f, listOf(3f, 2f))
    line("route-casing", "zone", "route", "#FFFFFF", 5f)
    line("route", "zone", "route", MapBlue, 2f)
    line("area-casing", "zone", "area", "#FFFFFF", 5f)
    line("area-edge", "zone", "area", MapBlue, 2f)
    circle("points", "zone", "point", 5f, MapBlue, "#FFFFFF", 2f)
    layer("trail-casing", "line", "trail", listOf("!=", listOf("get", "kind"), "gap"),
        mapOf("line-color" to "#FFFFFF", "line-width" to 7f), mapOf("line-cap" to "round", "line-join" to "round"))
    line("trail-precise", "trail", "precise", MapPrecise, 4f)
    // Style dash lengths are multiples of line-width: these are 8/4 and 2/4 dp.
    line("trail-approximate", "trail", "approximate", MapApproximate, 4f, listOf(2f, 1f))
    line("trail-unknown", "trail", "unknown", MapUnknown, 4f, listOf(.5f, 1f))
    circle("gap-casing", "trail", "gap", 4f, "#FFFFFF", "#FFFFFF", 4f, 0f)
    circle("gap-rings", "trail", "gap", 4f, "#FFFFFF", MapGap, 2f, 0f)
    val meterRadius = listOf("interpolate", listOf("exponential", 2), listOf("zoom"), 0,
        listOf("get", "radius0"), 24, listOf("*", listOf("get", "radius0"), 16_777_216))
    circle("device-uncertainty", "device", "uncertainty", meterRadius, MapDevice, MapDevice, 1f, .08f, .4f)
    circle("device-outer", "device", "device", 11f, MapDevice, MapDevice, 1f, 0f)
    circle("device-marker", "device", "device", 9f, MapDevice, "#FFFFFF", 3f)
    circle("device-stale", "device", "device-stale", 9f, "#FFFFFF", MapDevice, 3f)
    circle("phone-halo", "phone", "phone", 18f, MapBlue, MapBlue, 0f, .12f)
    circle("phone-marker", "phone", "phone", 7f, MapBlue, "#FFFFFF", 3f)
    return Gson().toJson(mapOf("version" to 8, "sources" to sources, "layers" to layers))
}

internal fun mapTrailJson(data: GeoMapData): String {
    val features = mutableListOf<JsonObject>()
    val gaps = data.zone.gaps
    data.trail.zipWithNext().forEach { (a, b) ->
        val start = GeoMapPosition(a.latitude, a.longitude)
        val end = GeoMapPosition(b.latitude, b.longitude)
        val gap = a.segment != b.segment || gaps.any { a.timestamp < (it.endedAtMillis ?: Long.MAX_VALUE) && b.timestamp > it.startedAtMillis }
        if (gap) {
            if (start.valid) features += mapPoint(start, "gap")
            if (end.valid) features += mapPoint(end, "gap")
        }
        if (gap || !start.valid || !end.valid) return@forEach
        val bands = listOf(locationQualityBand(a.fixState, a.sensorState), locationQualityBand(b.fixState, b.sensorState))
        if (LocationQualityBand.NONE in bands) return@forEach
        val kind = when {
            bands.all { it == LocationQualityBand.PRECISE } -> "precise"
            bands.any { it in setOf(LocationQualityBand.UNKNOWN, LocationQualityBand.LEGACY_OTHER) } -> "unknown"
            else -> "approximate"
        }
        features += mapFeature(JsonObject().apply {
            addProperty("type", "LineString")
            add("coordinates", JsonArray().apply {
                add(JsonArray().apply { add(a.longitude); add(a.latitude) })
                add(JsonArray().apply { add(b.longitude); add(b.latitude) })
            })
        }, kind)
    }
    gaps.forEach { gap -> listOfNotNull(gap.before, gap.after).filter { it.valid }.forEach { features += mapPoint(it, "gap") } }
    return mapCollection(features)
}

private fun accuracyPoint(p: GeoMapPosition, kind: String, meters: Double): JsonObject = mapPoint(p, kind).apply {
    getAsJsonObject("properties").addProperty("radius0", meters / (78_271.516964 * cos(Math.toRadians(p.latitude)).coerceAtLeast(.00001)))
}

internal fun mapDeviceJson(data: GeoMapData): String = mapCollection(buildList {
    data.device?.takeIf { it.valid }?.let { p ->
        data.uncertaintyM?.takeIf { it.isFinite() && it >= 3 }?.let { add(accuracyPoint(p, "uncertainty", it)) }
        add(mapPoint(p, if (data.deviceStale) "device-stale" else "device"))
    }
})

private fun mapPhoneJson(data: GeoMapData): String = mapCollection(listOfNotNull(data.phone?.takeIf { it.valid }?.let {
    accuracyPoint(it, "phone", data.phoneAccuracyM?.takeIf { a -> a.isFinite() && a >= 0 } ?: 5.0)
}))

class GeoMapCamera {
    internal var map: MapLibreMap? = null
    internal var data = GeoMapData()
    internal var density = 1f
    internal var bottomPadding = 0
    internal var endPadding = 0
    var following by mutableStateOf(false)
        internal set
    fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
    fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
    fun device() { following = true; follow() }
    internal fun follow() { data.device?.let { p -> map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(p.latitude, p.longitude)), 240) } }
    fun fitZone() { following = false; fit(initialMapPositions(data)) }
    internal fun fit(points: List<GeoMapPosition>) {
        val target = map ?: return
        if (points.isEmpty()) return
        val unique = points.distinct()
        if (unique.size == 1) {
            target.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(unique[0].latitude, unique[0].longitude), 16.0))
        } else {
            val bounds = LatLngBounds.Builder().includes(unique.map { LatLng(it.latitude, it.longitude) }).build()
            val padding = (24 * density).toInt()
            target.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding, padding, padding + endPadding, padding + bottomPadding))
        }
    }
}

/** Cards and the full screen use the same sources, lifecycle, style-generation guard and camera. */
@Composable
fun GeoMapRenderer(
    data: GeoMapData, modifier: Modifier = Modifier,
    base: GeoMapBase = GeoMapBase.GENERAL, interactive: Boolean = true,
    active: Boolean = true, followDevice: Boolean = false,
    panelBottomDp: Int = 0, panelEndDp: Int = 0,
    camera: GeoMapCamera = remember { GeoMapCamera() },
    apiKey: String = BuildConfig.VWORLD_API_KEY,
    onLoading: (Boolean) -> Unit = {}, onTilesFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = LocalGeoColors.current
    val palette = LocalGeoPalette.current
    val loading by rememberUpdatedState(onLoading)
    val failed by rememberUpdatedState(onTilesFailed)
    val view = remember {
        // Native tile errors can include request URLs. Keep those URLs, including the key, out of logs.
        MapLibre.getInstance(context)
        // Timber is MapLibre's private runtime dependency in 13.4.1, not an exported compile API.
        runCatching { Class.forName("timber.log.Timber").getMethod("uprootAll").invoke(null) }
        MapView(context).apply { onCreate(null); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    }
    val controller = remember { GeoMapController(view, camera, { loading(it) }, { failed() }) }
    DisposableEffect(view, lifecycle, active) {
        var started = false
        var resumed = false
        fun sync() {
            val nextStart = active && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val nextResume = active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !nextResume) { view.onPause(); resumed = false }
            if (started && !nextStart) { view.onStop(); started = false }
            if (!started && nextStart) { view.onStart(); started = true }
            if (!resumed && nextResume) { view.onResume(); resumed = true }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
        }
    }
    DisposableEffect(view) { onDispose { controller.destroy(); view.onDestroy() } }
    val dim = when (palette) { GeoPalette.GRAPHITE, GeoPalette.NAVY_DARK -> .75f; GeoPalette.MIST -> .85f; else -> 1f }
    val background = String.format("#%06X", colors.sunken.toArgb() and 0xFFFFFF)
    val style = remember(base, apiKey, background, dim) { mapStyleJson(base, apiKey, background, dim) }
    AndroidView(factory = { view }, modifier = modifier, update = {
        controller.update(data, style, interactive, followDevice, panelBottomDp, panelEndDp)
    })
}

private class GeoMapController(
    private val view: MapView, private val camera: GeoMapCamera,
    private val loading: (Boolean) -> Unit, private val failed: () -> Unit
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var requestedStyle: String? = null
    private var generation = 0L
    private var destroyed = false
    private var positioned = false
    private var zonePositioned = false
    private var cameraTouched = false
    private var followInitialized = false
    private val rendered = mutableMapOf<String, String>()
    private var pendingData = GeoMapData()
    private var interactive = true
    private var previousData: GeoMapData? = null
    private val errorListener = MapView.OnDidFailLoadingMapListener { failed() }
    private val tileListener = MapView.OnTileActionListener { operation, _, _, _, _, _, source ->
        if (operation == TileOperation.Error && source == "raster") failed()
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> render() }
    init {
        view.addOnDidFailLoadingMapListener(errorListener)
        view.addOnTileActionListener(tileListener)
        view.addOnLayoutChangeListener(layoutListener)
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready; camera.map = ready
            configure()
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(36.5, 127.5)).zoom(7.0).build()
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) { camera.following = false; cameraTouched = true }
            }
            load()
        }
    }
    fun update(data: GeoMapData, styleJson: String, interactive: Boolean, follow: Boolean, bottom: Int, end: Int) {
        pendingData = data
        this.interactive = interactive
        camera.data = data
        camera.density = view.resources.displayMetrics.density
        camera.bottomPadding = (bottom * camera.density).toInt()
        camera.endPadding = (end * camera.density).toInt()
        if (!followInitialized) { camera.following = follow; followInitialized = true }
        configure()
        if (requestedStyle != styleJson) {
            requestedStyle = styleJson
            load()
        } else render()
    }
    private fun configure() {
        map?.uiSettings?.apply {
            setAllGesturesEnabled(interactive)
            isCompassEnabled = interactive
            compassGravity = Gravity.TOP or Gravity.START
            setCompassMargins((12 * camera.density).toInt(), (12 * camera.density).toInt(), 0, 0)
            isAttributionEnabled = false
            isLogoEnabled = false
        }
    }
    private fun load() {
        val target = map ?: return
        val json = requestedStyle ?: return
        val request = ++generation
        style = null; rendered.clear(); previousData = null; loading(true)
        target.setStyle(Style.Builder().fromJson(json)) { ready ->
            if (destroyed || request != generation) return@setStyle
            style = ready
            render()
            loading(false)
        }
    }
    private fun render() {
        val style = style ?: return
        val data = pendingData
        val previous = previousData
        val updates = buildMap {
            if (previous == null || previous.geometry != data.geometry) put("zone", data.geometry?.sourceJson ?: emptyMapCollection())
            if (previous == null || previous.trail != data.trail || previous.zone.gaps != data.zone.gaps) put("trail", mapTrailJson(data))
            if (previous == null || previous.device != data.device || previous.deviceStale != data.deviceStale || previous.uncertaintyM != data.uncertaintyM) put("device", mapDeviceJson(data))
            if (previous == null || previous.phone != data.phone || previous.phoneAccuracyM != data.phoneAccuracyM) put("phone", mapPhoneJson(data))
        }
        updates.forEach { (id, json) ->
            if (rendered[id] != json) {
                style.getSourceAs<GeoJsonSource>(id)?.setGeoJson(json)
                rendered[id] = json
            }
        }
        previousData = data
        val zoneArrived = !zonePositioned && data.geometry?.positions?.isNotEmpty() == true && !cameraTouched
        if ((!positioned || zoneArrived) && initialMapPositions(data).isNotEmpty() && view.width > 0 && view.height > 0) {
            camera.fit(initialMapPositions(data)); positioned = true
            zonePositioned = data.geometry?.positions?.isNotEmpty() == true
        }
        if (positioned && camera.following && previous?.device != data.device) camera.follow()
    }
    fun destroy() {
        destroyed = true; generation++; camera.map = null
        view.removeOnDidFailLoadingMapListener(errorListener)
        view.removeOnTileActionListener(tileListener)
        view.removeOnLayoutChangeListener(layoutListener)
    }
}
