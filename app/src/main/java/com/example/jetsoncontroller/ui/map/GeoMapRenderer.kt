package com.example.jetsoncontroller.ui.map

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PolygonOverlay
import com.naver.maps.map.overlay.PolylineOverlay
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class GeoMapBase { GENERAL, SATELLITE }

internal const val MapBlue = "#1F52B8"
internal const val MapPrecise = "#22704B"
internal const val MapApproximate = "#8A5A06"
internal const val MapUnknown = "#5D667B"
internal const val MapDevice = "#121A3D"
internal const val MapGap = "#B3372C"

/** Widths, white casings and dash lengths in dp; the dashes keep the earlier map's 6/4, 8/4 and 2/4 dp. */
internal enum class GeoMapLineStyle(val color: String, val widthDp: Float, val casingDp: Float, val dashDp: List<Float>? = null) {
    RANGE(MapBlue, 2f, 5f, listOf(6f, 4f)),
    AREA(MapBlue, 2f, 5f),
    ROUTE(MapBlue, 2f, 5f),
    PRECISE(MapPrecise, 4f, 7f),
    APPROXIMATE(MapApproximate, 4f, 7f, listOf(8f, 4f)),
    UNKNOWN(MapUnknown, 4f, 7f, listOf(2f, 4f))
}

internal data class GeoMapPolygon(val range: Boolean, val exterior: List<GeoMapPosition>, val holes: List<List<GeoMapPosition>>)

internal data class GeoMapZoneShapes(
    val polygons: List<GeoMapPolygon> = emptyList(),
    val routes: List<List<GeoMapPosition>> = emptyList(),
    val points: List<GeoMapPosition> = emptyList()
)

/**
 * NAVER fills a polygon from a clockwise exterior with counter-clockwise holes, given without the
 * closing point. GeoJSON (RFC 7946) winds the other way, so rings are turned where needed.
 */
internal fun naverRing(ring: List<GeoMapPosition>, clockwise: Boolean): List<GeoMapPosition>? {
    val open = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
    if (open.size < 3) return null
    val origin = open.first()
    // Shoelace sum with x = longitude and y = latitude, taken from the first point: positive is counter-clockwise.
    val twiceArea = open.indices.sumOf { i ->
        val a = open[i]
        val b = open[(i + 1) % open.size]
        (a.longitude - origin.longitude) * (b.latitude - origin.latitude) - (b.longitude - origin.longitude) * (a.latitude - origin.latitude)
    }
    return if ((twiceArea < 0) == clockwise) open else open.reversed()
}

internal fun mapZoneShapes(sourceJson: String): GeoMapZoneShapes = runCatching {
    val polygons = mutableListOf<GeoMapPolygon>()
    val routes = mutableListOf<List<GeoMapPosition>>()
    val points = mutableListOf<GeoMapPosition>()
    fun position(value: JsonArray) = GeoMapPosition(value[1].asDouble, value[0].asDouble)
    fun line(value: JsonArray) = value.map { position(it.asJsonArray) }.filter { it.valid }
    fun polygon(value: JsonArray, range: Boolean) {
        val rings = value.map { line(it.asJsonArray) }
        val exterior = rings.firstOrNull()?.let { naverRing(it, clockwise = true) } ?: return
        polygons += GeoMapPolygon(range, exterior, rings.drop(1).mapNotNull { naverRing(it, clockwise = false) })
    }
    JsonParser.parseString(sourceJson).asJsonObject.getAsJsonArray("features")?.forEach { element ->
        val feature = element.asJsonObject
        val geometry = feature.getAsJsonObject("geometry") ?: return@forEach
        val coordinates = geometry.getAsJsonArray("coordinates") ?: return@forEach
        val range = feature.getAsJsonObject("properties")?.get("kind")?.asString == "range"
        when (geometry.get("type")?.asString) {
            "Point" -> position(coordinates).takeIf { it.valid }?.let { points += it }
            "LineString" -> line(coordinates).takeIf { it.size >= 2 }?.let { routes += it }
            "Polygon" -> polygon(coordinates, range)
            "MultiPolygon" -> coordinates.forEach { polygon(it.asJsonArray, range) }
        }
    }
    GeoMapZoneShapes(polygons, routes, points)
}.getOrDefault(GeoMapZoneShapes())

internal data class GeoMapTrailRun(val style: GeoMapLineStyle, val points: List<GeoMapPosition>)

internal data class GeoMapTrail(val runs: List<GeoMapTrailRun> = emptyList(), val gaps: List<GeoMapPosition> = emptyList())

/** Consecutive segments of one location quality share a line, so a long trail stays a handful of overlays. */
internal fun mapTrail(data: GeoMapData): GeoMapTrail {
    val runs = mutableListOf<GeoMapTrailRun>()
    val gapPoints = mutableListOf<GeoMapPosition>()
    val gaps = data.zone.gaps
    var style: GeoMapLineStyle? = null
    var run = mutableListOf<GeoMapPosition>()
    fun close() {
        style?.let { if (run.size >= 2) runs += GeoMapTrailRun(it, run.toList()) }
        style = null
        run = mutableListOf()
    }
    data.trail.zipWithNext().forEach { (a, b) ->
        val start = GeoMapPosition(a.latitude, a.longitude)
        val end = GeoMapPosition(b.latitude, b.longitude)
        val gap = a.segment != b.segment || gaps.any { a.timestamp < (it.endedAtMillis ?: Long.MAX_VALUE) && b.timestamp > it.startedAtMillis }
        if (gap) {
            if (start.valid) gapPoints += start
            if (end.valid) gapPoints += end
        }
        val bands = listOf(locationQualityBand(a.fixState, a.sensorState), locationQualityBand(b.fixState, b.sensorState))
        if (gap || !start.valid || !end.valid || LocationQualityBand.NONE in bands) { close(); return@forEach }
        val next = when {
            bands.all { it == LocationQualityBand.PRECISE } -> GeoMapLineStyle.PRECISE
            bands.any { it in setOf(LocationQualityBand.UNKNOWN, LocationQualityBand.LEGACY_OTHER) } -> GeoMapLineStyle.UNKNOWN
            else -> GeoMapLineStyle.APPROXIMATE
        }
        if (next != style || run.lastOrNull() != start) {
            close()
            style = next
            run += start
        }
        run += end
    }
    close()
    gaps.forEach { gap -> listOfNotNull(gap.before, gap.after).filter { it.valid }.forEach { gapPoints += it } }
    return GeoMapTrail(runs, gapPoints)
}

internal data class GeoMapDeviceMark(val position: GeoMapPosition, val stale: Boolean, val uncertaintyM: Double?)

/** The uncertainty circle starts at 3 m; below that it would hide under the marker. */
internal fun mapDeviceMark(data: GeoMapData): GeoMapDeviceMark? = data.device?.takeIf { it.valid }?.let {
    GeoMapDeviceMark(it, data.deviceStale, data.uncertaintyM?.takeIf { m -> m.isFinite() && m >= 3 })
}

internal data class GeoMapAppearance(val satellite: Boolean, val night: Boolean, val rangeFillOpacity: Float)

/** Dark palettes switch the general map to NAVER's night style. Satellite imagery stays as it is. */
internal fun mapAppearance(base: GeoMapBase, palette: GeoPalette, baseMap: Boolean): GeoMapAppearance {
    val night = base == GeoMapBase.GENERAL && palette.isDark
    return GeoMapAppearance(base == GeoMapBase.SATELLITE, night, if (night && baseMap) .22f else .16f)
}

/** One NAVER client per process. A rejected client ID applies to every map, so the failure is kept here. */
internal object GeoNaverMaps {
    var authFailure by mutableStateOf<String?>(null)
        private set
    private var listening = false
    private var client: String? = null

    fun prepare(context: Context, clientId: String) {
        val sdk = NaverMapSdk.getInstance(context.applicationContext)
        if (!listening) {
            sdk.onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { error ->
                // Only the code is logged; the client ID stays out of logs.
                Log.w("GeoMap", "NAVER map authorization failed: ${error.errorCode}")
                authFailure = error.errorCode
            }
            listening = true
        }
        if (clientId.isNotBlank() && client != clientId) {
            sdk.client = NaverMapSdk.NcpKeyClient(clientId)
            client = clientId
        }
    }
}

class GeoMapCamera {
    internal var map: NaverMap? = null
    internal var savedPosition: CameraPosition? = null
    internal var data = GeoMapData()
    internal var density = 1f
    internal var bottomPadding = 0
    internal var endPadding = 0
    var following by mutableStateOf(false)
        internal set
    fun zoomIn() { map?.moveCamera(CameraUpdate.zoomIn().animate(CameraAnimation.Easing)) }
    fun zoomOut() { map?.moveCamera(CameraUpdate.zoomOut().animate(CameraAnimation.Easing)) }
    fun device() { following = true; follow() }
    internal fun follow() {
        data.device?.takeIf { it.valid }?.let { p ->
            map?.moveCamera(CameraUpdate.scrollTo(LatLng(p.latitude, p.longitude)).animate(CameraAnimation.Easing, 240))
        }
    }
    fun fitZone() { following = false; fit(initialMapPositions(data)) }
    internal fun fit(points: List<GeoMapPosition>) {
        val target = map ?: return
        val unique = points.filter { it.valid }.distinct()
        if (unique.isEmpty()) return
        if (unique.size == 1) {
            target.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(unique[0].latitude, unique[0].longitude), 16.0))
        } else {
            val padding = (24 * density).toInt()
            val bounds = LatLngBounds.from(unique.map { LatLng(it.latitude, it.longitude) })
            target.moveCamera(CameraUpdate.fitBounds(bounds, padding, padding, padding + endPadding, padding + bottomPadding))
        }
    }
}

/** Cards and the full screen share one lifecycle, the overlays and the camera. */
@Composable
fun GeoMapRenderer(
    data: GeoMapData, modifier: Modifier = Modifier,
    base: GeoMapBase = GeoMapBase.GENERAL, interactive: Boolean = true,
    active: Boolean = true, followDevice: Boolean = false,
    panelBottomDp: Int = 0, panelEndDp: Int = 0,
    camera: GeoMapCamera = remember { GeoMapCamera() },
    clientId: String = BuildConfig.NAVER_MAP_CLIENT_ID,
    onLoading: (Boolean) -> Unit = {},
    onBaseMapFailureChanged: (Boolean) -> Unit = {}
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (active && started) {
        GeoMapSurface(data, modifier, base, interactive, followDevice, panelBottomDp, panelEndDp,
            camera, clientId, onLoading, onBaseMapFailureChanged)
    } else {
        // Disposing the surface releases native tiles/textures, while keeping the layout and camera.
        Box(modifier.background(LocalGeoColors.current.sunken))
    }
}

@Composable
private fun GeoMapSurface(
    data: GeoMapData, modifier: Modifier, base: GeoMapBase, interactive: Boolean,
    followDevice: Boolean, panelBottomDp: Int, panelEndDp: Int, camera: GeoMapCamera,
    clientId: String, onLoading: (Boolean) -> Unit, onBaseMapFailureChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = LocalGeoColors.current
    val palette = LocalGeoPalette.current
    val loading by rememberUpdatedState(onLoading)
    val failureChanged by rememberUpdatedState(onBaseMapFailureChanged)
    val background = colors.sunken.toArgb()
    val view = remember {
        GeoNaverMaps.prepare(context, clientId)
        // A texture view clips to the card's rounded corners and scrolls with the page.
        // Without a background resource NAVER fills unloaded areas with the palette colour instead of its grid.
        MapView(context, NaverMapOptions()
            .camera(camera.savedPosition ?: CameraPosition(LatLng(36.5, 127.5), 7.0))
            .useTextureView(!interactive)
            .backgroundResource(0).backgroundColor(background)
            .compassEnabled(false).scaleBarEnabled(false).zoomControlEnabled(false)
            .locationButtonEnabled(false).indoorLevelPickerEnabled(false)
        ).apply { onCreate(null); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    }
    val controller = remember { GeoMapController(view, camera, clientId.isNotBlank(), { loading(it) }, { failureChanged(it) }) }
    DisposableEffect(view, lifecycle) {
        var started = false
        var resumed = false
        fun sync() {
            val nextStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val nextResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !nextResume) { view.onPause(); resumed = false }
            if (started && !nextStart) {
                view.onStop(); controller.stopped(); view.onLowMemory(); started = false
            }
            if (!started && nextStart) { view.onStart(); controller.started(); started = true }
            if (!resumed && nextResume) { view.onResume(); resumed = true }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) { view.onStop(); controller.stopped() }
        }
    }
    DisposableEffect(view) {
        val application = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = view.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) view.onLowMemory()
            }
        }
        application.registerComponentCallbacks(callbacks)
        onDispose {
            application.unregisterComponentCallbacks(callbacks)
            controller.destroy()
            view.onDestroy()
        }
    }
    val appearance = mapAppearance(base, palette, clientId.isNotBlank())
    val authFailed = GeoNaverMaps.authFailure != null
    AndroidView(factory = { view }, modifier = modifier, update = {
        controller.update(data, appearance, background, interactive, followDevice, panelBottomDp, panelEndDp, authFailed)
    })
}

private enum class GeoMapIcon(val outerDp: Float) { POINT(7f), GAP(8f), DEVICE(13f), DEVICE_STALE(12f), PHONE(18f) }

/** Marker images drawn once per screen density; a stroke sits outside its circle, as the earlier map drew it. */
private class GeoMapIcons(private val density: Float) {
    private val images = mutableMapOf<GeoMapIcon, Pair<OverlayImage, Int>>()

    fun image(icon: GeoMapIcon): Pair<OverlayImage, Int> = images.getOrPut(icon) {
        val size = ceil((icon.outerDp * 2 + 2) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun disc(radius: Float, color: Int) {
            paint.style = Paint.Style.FILL; paint.color = color
            canvas.drawCircle(center, center, radius * density, paint)
        }
        fun ring(radius: Float, width: Float, color: Int) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = width * density; paint.color = color
            canvas.drawCircle(center, center, (radius + width / 2) * density, paint)
        }
        val blue = Color.parseColor(MapBlue)
        val device = Color.parseColor(MapDevice)
        when (icon) {
            GeoMapIcon.POINT -> { disc(5f, blue); ring(5f, 2f, Color.WHITE) }
            GeoMapIcon.GAP -> { ring(4f, 4f, Color.WHITE); ring(4f, 2f, Color.parseColor(MapGap)) }
            GeoMapIcon.DEVICE -> { disc(9f, device); ring(9f, 3f, Color.WHITE); ring(12f, 1f, device) }
            GeoMapIcon.DEVICE_STALE -> { disc(9f, Color.WHITE); ring(9f, 3f, device) }
            GeoMapIcon.PHONE -> { disc(18f, withAlpha(blue, .12f)); disc(7f, blue); ring(7f, 3f, Color.WHITE) }
        }
        OverlayImage.fromBitmap(bitmap) to size
    }
}

private fun withAlpha(color: Int, alpha: Float): Int = ((alpha * 255).roundToInt() shl 24) or (color and 0xFFFFFF)

private data class LineSpec(val points: List<LatLng>, val width: Int, val color: Int, val pattern: List<Int>, val round: Boolean)
private data class PolygonSpec(val exterior: List<LatLng>, val holes: List<List<LatLng>>, val fill: Int,
                               val outlineWidth: Int, val outline: Int, val pattern: List<Int>)
private data class MarkerSpec(val position: LatLng, val icon: GeoMapIcon)
private data class CircleSpec(val center: LatLng, val radiusM: Double)

/** Keeps one overlay per spec and touches an overlay only when its spec changes. */
private class OverlayPool<S, O : Overlay>(private val z: Int, private val create: () -> O, private val apply: O.(S) -> Unit) {
    private val overlays = mutableListOf<O>()
    private val specs = mutableListOf<S>()

    fun sync(map: NaverMap, next: List<S>) {
        next.forEachIndexed { index, spec ->
            if (index < overlays.size) {
                if (specs[index] != spec) { overlays[index].apply(spec); specs[index] = spec }
            } else {
                // Coordinates must be in place before an overlay joins the map.
                val overlay = create()
                overlay.globalZIndex = z
                overlay.apply(spec)
                overlay.map = map
                overlays += overlay; specs += spec
            }
        }
        while (overlays.size > next.size) {
            overlays.removeAt(overlays.lastIndex).map = null
            specs.removeAt(specs.lastIndex)
        }
    }
}

private class GeoMapController(
    private val view: MapView, private val camera: GeoMapCamera, private val hasClient: Boolean,
    private val loading: (Boolean) -> Unit, private val failureChanged: (Boolean) -> Unit
) {
    private val density = view.resources.displayMetrics.density
    private val icons = GeoMapIcons(density)
    private var map: NaverMap? = null
    private var destroyed = false
    private var published = false
    private var positioned = camera.savedPosition != null
    private var zonePositioned = camera.savedPosition != null
    private var cameraTouched = camera.savedPosition != null
    private var followInitialized = camera.savedPosition != null
    private var pendingData = GeoMapData()
    private var previousData: GeoMapData? = null
    private var renderedAppearance: GeoMapAppearance? = null
    private var appearance = GeoMapAppearance(satellite = false, night = false, rangeFillOpacity = .16f)
    private var background = 0
    private var interactive = true
    private var panelBottomDp = 0
    private var zoneSource: String? = null
    private var zoneShapes = GeoMapZoneShapes()
    private var loadState = GeoMapLoadState(clientConfigured = hasClient)

    // Global z order follows the earlier style: zone, then trail, then the device and the phone.
    // Shapes stay under NAVER's labels (z < 0); the device and phone markers sit above them.
    private val zoneFills = polygons(-190_000)
    private val zoneEdges = polygons(-189_900)
    private val routeCasings = lines(-189_800)
    private val routes = lines(-189_700)
    private val zonePoints = markers(-189_600)
    private val trailCasings = lines(-180_000)
    private val trails = lines(-179_900)
    private val gapRings = markers(-179_800)
    private val uncertainty = OverlayPool<CircleSpec, CircleOverlay>(-170_000, ::CircleOverlay) { spec ->
        center = spec.center; radius = spec.radiusM
        color = withAlpha(Color.parseColor(MapDevice), .08f)
        outlineWidth = px(1f); outlineColor = withAlpha(Color.parseColor(MapDevice), .4f)
    }
    private val deviceMarker = markers(210_000)
    private val phoneMarker = markers(220_000)

    private fun px(dp: Float) = (dp * density).roundToInt().coerceAtLeast(1)
    private fun lines(z: Int) = OverlayPool<LineSpec, PolylineOverlay>(z, ::PolylineOverlay) { spec ->
        coords = spec.points; width = spec.width; color = spec.color
        setPattern(*spec.pattern.toIntArray())
        capType = if (spec.round) PolylineOverlay.LineCap.Round else PolylineOverlay.LineCap.Butt
        joinType = PolylineOverlay.LineJoin.Round
    }
    private fun polygons(z: Int) = OverlayPool<PolygonSpec, PolygonOverlay>(z, ::PolygonOverlay) { spec ->
        coords = spec.exterior; holes = spec.holes; color = spec.fill
        outlineWidth = spec.outlineWidth; outlineColor = spec.outline
        setOutlinePattern(*spec.pattern.toIntArray())
    }
    private fun markers(z: Int) = OverlayPool<MarkerSpec, Marker>(z, ::Marker) { spec ->
        val (image, size) = icons.image(spec.icon)
        position = spec.position; icon = image; width = size; height = size
        anchor = PointF(.5f, .5f)
    }

    private val stall = Runnable { if (!destroyed) publish(loadState.loadStalled()) }
    private val loadListener = NaverMap.OnLoadListener { if (!destroyed) publish(loadState.mapLoaded()) }
    private val cameraListener = NaverMap.OnCameraChangeListener { reason, _ ->
        if (reason == CameraUpdate.REASON_GESTURE) { camera.following = false; cameraTouched = true }
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> render() }

    init {
        view.addOnLayoutChangeListener(layoutListener)
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready; camera.map = ready
            ready.addOnCameraChangeListener(cameraListener)
            ready.addOnLoadListener(loadListener)
            configure()
            if (ready.isLoaded) publish(loadState.mapLoaded())
            render()
        }
    }

    private fun publish(next: GeoMapLoadState) {
        loadState = next
        published = true
        loading(next.loading)
        failureChanged(next.failed)
    }

    fun started() {
        view.removeCallbacks(stall)
        if (hasClient && !loadState.loaded) view.postDelayed(stall, MapLoadStallMillis)
    }

    fun stopped() { view.removeCallbacks(stall) }

    fun update(data: GeoMapData, appearance: GeoMapAppearance, background: Int, interactive: Boolean, follow: Boolean,
               bottom: Int, end: Int, authFailed: Boolean) {
        pendingData = data
        this.appearance = appearance
        this.background = background
        this.interactive = interactive
        panelBottomDp = bottom
        camera.data = data
        camera.density = density
        camera.bottomPadding = (bottom * density).toInt()
        camera.endPadding = (end * density).toInt()
        if (!followInitialized) { camera.following = follow; followInitialized = true }
        when {
            authFailed && !loadState.authFailed -> publish(loadState.authFailure())
            !published -> publish(loadState)
        }
        configure()
        render()
    }

    private fun configure() {
        val map = map ?: return
        val type = when {
            !hasClient -> NaverMap.MapType.None
            appearance.satellite -> NaverMap.MapType.Hybrid
            else -> NaverMap.MapType.Basic
        }
        if (map.mapType != type) map.mapType = type
        if (map.isNightModeEnabled != appearance.night) map.isNightModeEnabled = appearance.night
        if (map.backgroundColor != background) map.backgroundColor = background
        map.uiSettings.apply {
            setAllGesturesEnabled(interactive)
            isCompassEnabled = interactive
            // NAVER's logo must stay visible: it takes the old attribution's corner, above the panel.
            logoGravity = Gravity.BOTTOM or Gravity.START
            val inset = if (interactive) 12f else 8f
            setLogoMargin(px(inset), 0, 0, px(panelBottomDp + inset))
        }
    }

    private fun render() {
        val map = map ?: return
        val data = pendingData
        val previous = previousData
        if (previous == null || previous.geometry != data.geometry || renderedAppearance != appearance) syncZone(map, data)
        if (previous == null || previous.trail != data.trail || previous.zone.gaps != data.zone.gaps) syncTrail(map, data)
        if (previous == null || previous.device != data.device || previous.deviceStale != data.deviceStale ||
            previous.uncertaintyM != data.uncertaintyM) syncDevice(map, data)
        if (previous == null || previous.phone != data.phone) {
            phoneMarker.sync(map, listOfNotNull(data.phone?.takeIf { it.valid }?.let { MarkerSpec(latLng(it), GeoMapIcon.PHONE) }))
        }
        previousData = data
        renderedAppearance = appearance
        val zoneArrived = !zonePositioned && data.geometry?.positions?.isNotEmpty() == true && !cameraTouched
        if ((!positioned || zoneArrived) && initialMapPositions(data).isNotEmpty() && view.width > 0 && view.height > 0) {
            camera.fit(initialMapPositions(data)); positioned = true
            zonePositioned = data.geometry?.positions?.isNotEmpty() == true
        }
        if (positioned && camera.following && previous?.device != data.device) camera.follow()
    }

    private fun syncZone(map: NaverMap, data: GeoMapData) {
        val source = data.geometry?.sourceJson
        if (source != zoneSource) {
            zoneSource = source
            zoneShapes = source?.let(::mapZoneShapes) ?: GeoMapZoneShapes()
        }
        val fill = withAlpha(Color.parseColor(MapBlue), appearance.rangeFillOpacity)
        zoneFills.sync(map, zoneShapes.polygons.map {
            PolygonSpec(it.exterior.map(::latLng), it.holes.map { hole -> hole.map(::latLng) }, fill,
                px(GeoMapLineStyle.AREA.casingDp), Color.WHITE, emptyList())
        })
        zoneEdges.sync(map, zoneShapes.polygons.map {
            val style = if (it.range) GeoMapLineStyle.RANGE else GeoMapLineStyle.AREA
            PolygonSpec(it.exterior.map(::latLng), it.holes.map { hole -> hole.map(::latLng) }, Color.TRANSPARENT,
                px(style.widthDp), Color.parseColor(style.color), style.dashDp.orEmpty().map(::px))
        })
        val route = GeoMapLineStyle.ROUTE
        routeCasings.sync(map, zoneShapes.routes.map { LineSpec(it.map(::latLng), px(route.casingDp), Color.WHITE, emptyList(), true) })
        routes.sync(map, zoneShapes.routes.map { LineSpec(it.map(::latLng), px(route.widthDp), Color.parseColor(route.color), emptyList(), true) })
        zonePoints.sync(map, zoneShapes.points.map { MarkerSpec(latLng(it), GeoMapIcon.POINT) })
    }

    private fun syncTrail(map: NaverMap, data: GeoMapData) {
        val trail = mapTrail(data)
        trailCasings.sync(map, trail.runs.map { LineSpec(it.points.map(::latLng), px(it.style.casingDp), Color.WHITE, emptyList(), true) })
        trails.sync(map, trail.runs.map {
            LineSpec(it.points.map(::latLng), px(it.style.widthDp), Color.parseColor(it.style.color),
                it.style.dashDp.orEmpty().map(::px), it.style.dashDp == null)
        })
        gapRings.sync(map, trail.gaps.map { MarkerSpec(latLng(it), GeoMapIcon.GAP) })
    }

    private fun syncDevice(map: NaverMap, data: GeoMapData) {
        val mark = mapDeviceMark(data)
        uncertainty.sync(map, listOfNotNull(mark?.uncertaintyM?.let { CircleSpec(latLng(mark.position), it) }))
        deviceMarker.sync(map, listOfNotNull(mark?.let {
            MarkerSpec(latLng(it.position), if (it.stale) GeoMapIcon.DEVICE_STALE else GeoMapIcon.DEVICE)
        }))
    }

    fun destroy() {
        destroyed = true
        view.removeCallbacks(stall)
        view.removeOnLayoutChangeListener(layoutListener)
        map?.removeOnLoadListener(loadListener)
        map?.removeOnCameraChangeListener(cameraListener)
        camera.savedPosition = map?.cameraPosition ?: camera.savedPosition
        camera.map = null
        map = null
    }
}

private fun latLng(position: GeoMapPosition) = LatLng(position.latitude, position.longitude)
