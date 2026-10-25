package com.example.jetsoncontroller.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.JetsonApplication
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.survey.SurveySelectionStore
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.TaskRoute
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.field.ResultZoneObservation
import com.example.jetsoncontroller.ui.components.geo.GeoBadge
import com.example.jetsoncontroller.ui.components.geo.GeoBadgeVariant
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.geoCardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The monitor's host can provide its snapshot without a dependency on the parallel 6-C package. */
val LocalGeoMapZoneOutput = staticCompositionLocalOf<GeoMapZoneOutput> { GeoMapZoneSnapshot() }
val LocalGeoMapData = staticCompositionLocalOf { GeoMapData() }

internal object GeoMapSessions {
    var fullMaps by mutableIntStateOf(0)
}

@Composable
fun GeoMapCard(
    mode: GeoMapMode, data: GeoMapData, onOpen: () -> Unit,
    modifier: Modifier = Modifier, fullMapOpen: Boolean = false,
    compactHeight: Boolean = LocalGeoWindow.current.compactHeight,
    mapClientId: String = BuildConfig.NAVER_MAP_CLIENT_ID
) {
    if ((data.geometry == null || data.geometry.state == GeoGeometryState.ABSENT) &&
        (mode != GeoMapMode.ROUTE || data.trail.isEmpty())) return
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val rootView = LocalView.current.rootView
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var loading by remember { mutableStateOf(true) }
    var baseFailure by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val caption = if (mode == GeoMapMode.ROUTE) mapResultCaption(data.zone) else mapPositionSentence(data)
    val qualifier = if (mode == GeoMapMode.ROUTE && data.zone.phoneObserved && caption.isNotEmpty()) "관찰한 시간 기준" else ""
    val summary = data.geometry?.summary.orEmpty()
    val description = listOf(mode.title + " 지도", summary, caption, qualifier, "크게 보기").filter { it.isNotEmpty() }.joinToString(", ")
    val badge = zoneBadge(mode, data)
    val rendered = data.copy(device = data.device.takeUnless { mode == GeoMapMode.ROUTE },
        phone = null, phoneAccuracyM = null,
        trail = if (mode == GeoMapMode.ZONE) emptyList() else data.trail)
    Column(modifier.fillMaxWidth().clickable(onClickLabel = "지도 크게 보기", onClick = onOpen)
        .clearAndSetSemantics { contentDescription = description; role = Role.Button; onClick("지도 크게 보기") { onOpen(); true } }) {
        val shape = GeoShape.card
        Box(Modifier.fillMaxWidth().height(mapCardHeight(mode, compactHeight).dp).geoCardShadow(shape).clip(shape).background(c.sunken)
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = it.isAttached && bounds.width > 0 && bounds.height > 0 && bounds.bottom > 0 && bounds.right > 0 &&
                    bounds.top < rootView.height && bounds.left < rootView.width
            }) {
            GeoMapRenderer(rendered, Modifier.fillMaxSize(), interactive = false,
                active = mapCardActive(visible, resumed, fullMapOpen || GeoMapSessions.fullMaps > 0), clientId = mapClientId,
                onLoading = { loading = it }, onBaseMapFailureChanged = { baseFailure = it })
            if (loading) Text("지도를 불러오는 중입니다", Modifier.align(Alignment.Center).background(c.mapPlate).padding(8.dp),
                style = type.body, color = c.textSecondary)
            badge?.let { GeoBadge(it.first, it.second, Modifier.align(Alignment.TopStart).padding(12.dp), GeoBadgeVariant.ON_MAP) }
            Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (summary.isNotEmpty()) Text(summary, color = c.textSecondary, style = type.caption)
            if (caption.isNotEmpty()) Text(caption, color = c.textSecondary, style = type.caption)
            if (qualifier.isNotEmpty()) Text(qualifier, color = c.textSecondary, style = type.caption)
            val notice = when {
                baseFailure -> "배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다."
                mapClientId.isBlank() -> "배경 지도를 쓸 수 없습니다. 구역은 그대로 표시합니다."
                else -> null
            }
            notice?.let { Text(it, color = c.textSecondary, style = type.caption) }
            if (data.deviceStale) Text("마지막 위치 ${mapSampleTime(data.lastPositionAtMillis)}", color = c.textSecondary, style = type.caption)
            if (data.geometryChanged) Text("수집 구역이 바뀌었습니다. 이 수집은 시작할 때의 구역으로 표시합니다.", color = c.textSecondary, style = type.caption)
        }
    }
}

/** Default slot adapters own their focused overlay; callers may still supply a graph navigation slot. */
@Composable
internal fun GeoTicketMapCard(ticket: ServerTicket, deviceName: String) {
    val context = LocalContext.current
    val root = context.noBackupFilesDir
    val repository = (context.applicationContext as? JetsonApplication)?.repository
    val status = repository?.status?.collectAsStateWithLifecycle()?.value
    val selected = repository?.selectedDeviceId?.collectAsStateWithLifecycle()?.value
    val registered = repository?.registeredDevices?.collectAsStateWithLifecycle()?.value.orEmpty()
    val target = remember(ticket.rowId, deviceName) { registered.firstOrNull { it.deviceName == deviceName }?.deviceId }
    val transport = repository?.transportState?.collectAsStateWithLifecycle()?.value as? TransportState.Connected
    val current = target != null && target.equals(selected, true) && transport?.deviceId.equals(target, true)
    val gnss = status?.gnssSensor ?: GnssSensorStatus()
    val inherited = LocalGeoMapData.current
    val geometry = remember(ticket) { mapGeometry(ticket) }
    LaunchedEffect(ticket) { withContext(Dispatchers.IO) { GeoMapRunStore(root).cache(ticket) } }
    var checking by remember(ticket.rowId, geometry.hash) { mutableStateOf(com.example.jetsoncontroller.ui.zone.ZoneMonitorState()) }
    val contract = remember(ticket) { com.example.jetsoncontroller.data.zone.ZoneContract.fromTicket(ticket).contract }
    LaunchedEffect(current, gnss, status?.collectedAtEpochMillis) {
        checking = com.example.jetsoncontroller.ui.zone.ZoneMonitor.evaluate(contract?.range,
            if (current) com.example.jetsoncontroller.ui.zone.ZoneSample.fromStatus(gnss) else null,
            status?.collectedAtEpochMillis, checking, runActive = false).monitor
    }
    val info = GeoMapZoneSnapshot(state = when (checking.state) {
        com.example.jetsoncontroller.ui.zone.ZoneState.INSIDE -> GeoZoneState.INSIDE
        com.example.jetsoncontroller.ui.zone.ZoneState.OUTSIDE -> GeoZoneState.OUTSIDE
        com.example.jetsoncontroller.ui.zone.ZoneState.NEAR_BOUNDARY -> GeoZoneState.NEAR
        else -> GeoZoneState.UNKNOWN
    }, distanceM = checking.signedDistanceM?.coerceAtLeast(0.0))
    val data = inherited.copy(geometry = geometry, zone = info,
        device = if (current) mapDevice(gnss) else inherited.device,
        deviceStale = !current || mapDeviceStale(gnss, true, status?.sensorTelemetryFresh == true, status?.collectedAtEpochMillis),
        lastPositionAtMillis = if (current) gnss.lastSampleAtEpochMillis else inherited.lastPositionAtMillis,
        uncertaintyM = if (current) mapUncertainty(gnss) else inherited.uncertaintyM)
    GeoMapCardWithDestination(GeoMapMode.ZONE, data, if (current) gnss else GnssSensorStatus())
}

@Composable
internal fun GeoResultMapCard(run: PipelineRun?, history: TaskRun?, field: FieldState?, observation: ResultZoneObservation?, revisionChanged: Boolean = false,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runId = run?.runId ?: history?.runId
    val deviceId = run?.deviceId ?: history?.deviceId
    val stored by produceState<GeoMapGeometry?>(null, runId, deviceId) {
        value = withContext(Dispatchers.IO) {
            val store = GeoMapRunStore(context.noBackupFilesDir)
            store.read(deviceId, runId) ?: if (runId != null && deviceId != null) {
                val start = SurveySelectionStore(context.applicationContext).state(deviceId).first().acceptedStarts[runId]
                start?.let { store.bindAccepted(deviceId, runId, it, emptyList()) }
            } else null
        }
    }
    val inherited = LocalGeoMapData.current
    val routeMatches = runId != null && deviceId != null && field?.selectedRun?.let { it.runId == runId && it.deviceId.equals(deviceId, true) } == true
    val repository = (context.applicationContext as? JetsonApplication)?.repository
    val selectedDevice = repository?.selectedDeviceId?.collectAsStateWithLifecycle()?.value
    val route by produceState<TaskRoute?>(null, runId, deviceId, selectedDevice, routeMatches) {
        value = if (!routeMatches && deviceId != null && runId != null && selectedDevice.equals(deviceId, true)) {
            val pipeline = run?.pipelineId ?: history?.pipelineId
            val log = run?.logId ?: history?.logId
            if (pipeline != null && log != null) repository?.taskRoute(pipeline, log)?.getOrNull()
                ?.takeIf { repository?.selectedDeviceId?.value.equals(deviceId, true) } else null
        } else null
    }
    val storedZone = com.example.jetsoncontroller.ui.zone.rememberRunZoneOutput(deviceId, runId)
    val zone = storedZone ?: observation?.let { GeoMapZoneSnapshot(outsideCount = it.excursions,
        outsideMillis = it.outsideMillis, unobservedMillis = it.unobservedMillis, gaps = LocalGeoMapZoneOutput.current.gaps) }
        ?: GeoMapZoneSnapshot()
    val data = inherited.copy(geometry = stored ?: inherited.geometry,
        trail = if (routeMatches) field!!.route else route?.points ?: inherited.trail,
        device = null, quality = route?.quality ?: run?.quality ?: history?.quality, zone = zone, geometryChanged = revisionChanged)
    GeoMapCardWithDestination(GeoMapMode.ROUTE, data, modifier = modifier)
}

@Composable
internal fun GeoMapCardWithDestination(mode: GeoMapMode, data: GeoMapData, gnss: GnssSensorStatus = GnssSensorStatus(),
    modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    GeoMapCard(mode, data, { open = true }, modifier.focusRequester(focus), fullMapOpen = open)
    if (open) {
        GeoMapWindow({ open = false }) {
            GeoMapScreen(mode, data, { open = false }, gnss = gnss)
        }
        DisposableEffect(Unit) { onDispose { runCatching { focus.requestFocus() } } }
    }
}
