package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.ui.zone.runPresentation

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.sensors.MobileLocationAvailability
import com.example.jetsoncontroller.ui.sensors.MobileLocationViewModel
import com.example.jetsoncontroller.ui.sensors.mobileLocationAvailability
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMapScreen(
    mode: GeoMapMode, data: GeoMapData, onBack: () -> Unit,
    gnss: GnssSensorStatus = GnssSensorStatus(),
    runElapsed: String? = null, onStopRequest: (() -> Unit)? = null,
    stopEnabled: Boolean = true, stopBusy: Boolean = false,
    stopError: String? = null, stopOpener: androidx.compose.ui.focus.FocusRequester? = null,
    developerMode: Boolean = false,
    deviceName: String = "장치",
    mapClientId: String = BuildConfig.NAVER_MAP_CLIENT_ID
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mobile: MobileLocationViewModel = viewModel(factory = remember(context.applicationContext) {
        MobileLocationViewModel.Factory(context.applicationContext)
    })
    val phone by mobile.uiState.collectAsStateWithLifecycle()
    // Deliberately not saveable: every new visit starts with phone location off.
    var showPhone by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { mobile.refresh() }
    LaunchedEffect(showPhone) { mobile.setTrackingRequested(showPhone) }
    DisposableEffect(mobile, lifecycle) {
        fun sync() { mobile.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer); sync()
        onDispose { lifecycle.removeObserver(observer); mobile.setTrackingRequested(false); mobile.setVisible(false) }
    }
    val availability = mobileLocationAvailability(phone)
    val phoneMessage = if (!showPhone) null else when (availability) {
        MobileLocationAvailability.PERMISSION_REQUIRED -> "휴대폰 위치를 보려면 위치 권한이 필요합니다."
        MobileLocationAvailability.PROVIDER_DISABLED -> "휴대폰 위치 서비스가 꺼져 있습니다."
        MobileLocationAvailability.NO_FIX -> "휴대폰 위치를 확인할 수 없습니다."
        MobileLocationAvailability.STALE -> "휴대폰 위치가 지연되고 있습니다."
        MobileLocationAvailability.OFF -> "휴대폰 위치를 확인할 수 없습니다."
        MobileLocationAvailability.ACTIVE -> null
    }
    val requestPermission: () -> Unit = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val phoneRemedy: Pair<String, () -> Unit>? = when {
        !showPhone -> null
        availability == MobileLocationAvailability.PERMISSION_REQUIRED -> "권한 허용" to requestPermission
        availability == MobileLocationAvailability.PROVIDER_DISABLED -> "위치 켜기" to { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        else -> null
    }
    val renderedData = data.copy(
        device = data.device.takeUnless { mode == GeoMapMode.ROUTE },
        geometry = data.geometry.takeUnless { mode == GeoMapMode.LOCATION },
        trail = if (mode in setOf(GeoMapMode.ZONE, GeoMapMode.LOCATION)) emptyList() else data.trail,
        phone = if (showPhone && availability == MobileLocationAvailability.ACTIVE) phone.fix?.let { GeoMapPosition(it.latitude, it.longitude) } else null,
        phoneAccuracyM = phone.fix?.accuracyM?.toDouble()
    )
    var base by rememberSaveable { mutableStateOf(GeoMapBase.GENERAL) }
    var sideExpanded by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var baseFailed by remember { mutableStateOf(false) }
    val camera = remember { GeoMapCamera() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(sheetState)
    var peekDp by remember(mode) { mutableIntStateOf(if (mode == GeoMapMode.RUN) 236 else 128) }
    DisposableEffect(Unit) {
        GeoMapSessions.fullMaps++
        onDispose { GeoMapSessions.fullMaps-- }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(colors.topBar).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
            Text(mode.title, style = type.title, color = colors.onTopBar, modifier = Modifier.weight(1f))
        }
        if (mode == GeoMapMode.RUN) GeoStatusStrip(listOfNotNull(data.zone.runPresentation(deviceName).strip))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val side = mapUsesSidePanel(maxWidth.value, maxHeight.value)
            val largeType = LocalDensity.current.fontScale >= 1.5f
            LaunchedEffect(side, largeType) { if (!side && largeType) sheetState.expand() }
            val expanded = if (side) sideExpanded else sheetState.currentValue == SheetValue.Expanded
            val toggle: () -> Unit = {
                if (side) sideExpanded = !sideExpanded
                else scope.launch { if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else sheetState.expand() }
            }
            val panel: @Composable (Modifier) -> Unit = { modifier ->
                GeoMapPanel(mode, renderedData, gnss, expanded, toggle, showPhone,
                    onPhoneShown = { enabled ->
                        showPhone = enabled
                        if (enabled && !phone.permissionGranted) requestPermission()
                        if (!enabled) mobile.setTrackingRequested(false)
                    }, modifier = modifier,
                    runElapsed = runElapsed, onStopRequest = onStopRequest, stopEnabled = stopEnabled, stopBusy = stopBusy,
                    stopError = stopError, stopOpener = stopOpener,
                    phoneMessage = phoneMessage, phoneRemedy = phoneRemedy, developerMode = developerMode,
                    onPeekHeight = { peekDp = it.coerceAtLeast(128) })
            }
            val map: @Composable (Int, Int) -> Unit = { bottom, end ->
                Box(Modifier.fillMaxSize().testTag("gnss-map-pane")) {
                    GeoMapRenderer(renderedData, Modifier.fillMaxSize(), base, followDevice = mode == GeoMapMode.RUN,
                        panelBottomDp = bottom, panelEndDp = end, camera = camera, clientId = mapClientId,
                        onLoading = { loading = it }, onBaseMapFailureChanged = { baseFailed = it })
                    val controlsEnd = end.dp + 12.dp
                    Column(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = controlsEnd),
                        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapBasePicker(base) { base = it }
                        MapControl(GeoIcons.myLocation, "장치 위치로", camera::device, selected = camera.following,
                            enabled = renderedData.device != null)
                        if (renderedData.geometry?.positions?.isNotEmpty() == true) MapControl(GeoIcons.fitZone, "구역 전체 보기", camera::fitZone)
                    }
                    Column(Modifier.align(Alignment.BottomEnd).padding(end = controlsEnd, bottom = bottom.dp + 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapControl(GeoIcons.zoomIn, "확대", camera::zoomIn)
                        MapControl(GeoIcons.zoomOut, "축소", camera::zoomOut)
                    }
                    val message = when {
                        loading -> "지도를 불러오는 중입니다"
                        baseFailed -> "배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다."
                        mapClientId.isBlank() -> "배경 지도를 쓸 수 없습니다. 구역은 그대로 표시합니다."
                        renderedData.deviceStale -> "마지막 위치 ${mapSampleTime(gnss.lastSampleAtEpochMillis)}"
                        renderedData.device == null && mode != GeoMapMode.ROUTE -> "장치 위치를 확인할 수 없습니다."
                        else -> null
                    }
                    message?.let {
                        Text(it, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = end.dp + 72.dp, bottom = bottom.dp + 40.dp)
                            .background(colors.mapPlate, RoundedCornerShape(6.dp)).padding(8.dp), color = colors.textSecondary, style = type.caption)
                    }
                }
            }
            if (side) {
                val panelWidth = if (sideExpanded) 320.dp else 56.dp
                Box(Modifier.fillMaxSize()) {
                    map(0, panelWidth.value.toInt())
                    if (sideExpanded) panel(Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight())
                    else Box(Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight().background(colors.surface)) {
                        GeoIconButton(GeoIcons.expandPanel, "패널 펼치기", toggle)
                    }
                }
            } else {
                val height = maxHeight
                BottomSheetScaffold(scaffoldState = scaffold, sheetPeekHeight = peekDp.dp.coerceAtMost(height),
                    sheetDragHandle = null, sheetContainerColor = colors.surface, sheetContentColor = colors.text,
                    sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    sheetContent = { panel(Modifier.fillMaxWidth().height((height - 48.dp).coerceAtLeast(128.dp))) },
                    containerColor = colors.sunken) { map(peekDp, 0) }
            }
        }
    }
}

@Composable
private fun MapBasePicker(base: GeoMapBase, onSelect: (GeoMapBase) -> Unit) {
    val colors = LocalGeoColors.current
    Row(Modifier.width(IntrinsicSize.Max).shadow(1.dp, CircleShape).background(colors.mapPlate, CircleShape)
        .selectableGroup().semantics { contentDescription = "지도 종류" }.padding(horizontal = 4.dp)) {
        GeoMapBase.entries.forEach { option ->
            val selected = base == option
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp)
                .onFocusChanged { focused = it.isFocused }.geoFocusRing(CircleShape, focused, tint = colors.focus)
                .clip(CircleShape).selectable(selected, role = Role.Tab, onClick = { onSelect(option) })
                .background(if (selected) colors.sunken else colors.mapPlate)
                .then(if (selected) Modifier.border(1.dp, colors.borderStrong, CircleShape) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(if (option == GeoMapBase.GENERAL) "일반" else "위성", style = geoLabelTextStyle(),
                    color = if (selected) colors.text else colors.textSecondary)
            }
        }
    }
}

@Composable
private fun MapControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit,
                       selected: Boolean = false, enabled: Boolean = true) {
    val c = LocalGeoColors.current
    GeoIconButton(icon, label, action,
        Modifier.shadow(1.dp, CircleShape).background(if (selected) c.primaryTint else c.mapPlate, CircleShape)
            .semantics { this.selected = selected }, enabled = enabled)
}
