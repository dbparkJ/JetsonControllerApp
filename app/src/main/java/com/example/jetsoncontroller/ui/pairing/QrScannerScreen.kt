package com.example.jetsoncontroller.ui.pairing

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Preview frames stay in memory; decoded scanner payloads are never rendered.
internal object PairingScannerFrame {
    var image: ImageBitmap? = null
}

@Composable
fun QrScannerScreen(
    cameraPermissionGranted: Boolean,
    errorMessage: String?,
    onRequestCameraPermission: () -> Unit,
    onQrScanned: (String) -> Boolean,
    onBack: () -> Unit
) {
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var manualValue by rememberSaveable { mutableStateOf("") }
    var manualError by rememberSaveable { mutableStateOf<String?>(null) }
    var freezePreview by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val manualOpener = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val submit: (String) -> Boolean = { rawValue ->
        val accepted = onQrScanned(rawValue.trim())
        if (accepted) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        accepted
    }
    val submitManual = {
        if (submit(manualValue)) {
            focus.clearFocus()
            manualValue = ""
            manualError = null
            showManualEntry = false
        } else manualError = INVALID_PAIRING_CODE
    }
    GeoImmersiveMediaFrame(controls = {
        ScannerControls(onBack, { freezePreview?.invoke(); showManualEntry = true },
            Modifier.focusRequester(manualOpener)) {
            if (!cameraPermissionGranted && !showManualEntry) CameraPermissionContent(onRequestCameraPermission)
            else ScannerFrameOverlay(errorMessage?.let { INVALID_PAIRING_CODE } ?: cameraError)
        }
    }) {
            when {
                showManualEntry -> ScannerStage(PairingScannerFrame.image)
                cameraPermissionGranted -> CameraScannerContent(
                    onQrScanned = submit,
                    onFreezePreview = { freezePreview = it },
                    onCameraError = { cameraError = it }
                )
                else -> Unit
            }
    }
    if (showManualEntry) {
        GeoSheet(
            title = "등록 코드 입력",
            onDismissRequest = { showManualEntry = false },
            kind = GeoSheetKind.FORM,
            openerFocusRequester = manualOpener,
            footer = {
                GeoSheetFooter(GeoOverlayAction("다음", submitManual,
                    enabled = manualValue.isNotBlank(), disabledReason = "등록 코드를 입력하세요."))
            }
        ) {
            GeoTextField(
                value = manualValue,
                onValueChange = { manualValue = it.take(4096); manualError = null },
                label = "등록 코드",
                singleLine = false,
                helper = "QR 아래에 있는 전체 코드를 입력하세요.",
                error = manualError,
                inputKind = GeoInputKind.IDENTIFIER,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { if (manualValue.isNotBlank()) submitManual() })
            )
        }
    }
}

@Composable
internal fun FrozenPairingScanner(onBack: () -> Unit) {
    GeoImmersiveMediaFrame(controls = { ScannerControls(onBack, onBack) }) {
        ScannerStage(PairingScannerFrame.image)
    }
}

@Composable
private fun BoxScope.ScannerControls(onBack: () -> Unit, onManual: () -> Unit, manualModifier: Modifier = Modifier,
    center: @Composable BoxScope.() -> Unit = { ScannerFrameOverlay(null) }) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.fillMaxWidth().padding(end = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GeoMediaIconButton(GeoIcons.back, "뒤로", onBack)
        Text("QR 코드 스캔", Modifier.weight(1f).background(GeoFixedColors.mediaPlate, GeoShape.card).padding(12.dp),
            style = LocalGeoType.current.titleSm, color = GeoFixedColors.mediaText)
    }
    Box(Modifier.weight(1f).fillMaxWidth(), content = center)
    Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GeoMediaIconButton(GeoIcons.qr, "코드 직접 입력", onManual, manualModifier)
        Text("코드 직접 입력", style = LocalGeoType.current.caption, color = GeoFixedColors.mediaText)
    }
    }
}

@Composable
private fun ScannerStage(image: ImageBitmap? = null) {
    Box(Modifier.fillMaxSize().background(GeoFixedColors.mediaStage)) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        CompositionLocalProvider(LocalGeoColors provides LocalGeoColors.current.copy(
            text = GeoFixedColors.mediaText, textSecondary = GeoFixedColors.mediaText)) {
            GeoStateBlock(GeoStateContent(GeoStateKind.PERMISSION, "카메라 권한이 꺼져 있습니다",
                "QR 코드를 읽으려면 카메라 권한이 필요합니다."))
        }
        if (requested) {
            GeoButton("설정에서 허용", {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            })
        } else {
            GeoButton("권한 허용", { requested = true; onRequestPermission() })
        }
    }
}

@Composable
private fun CameraScannerContent(
    onQrScanned: (String) -> Boolean,
    onFreezePreview: ((() -> Unit)?) -> Unit,
    onCameraError: (String?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val detected = remember { AtomicBoolean(false) }
    val active = remember { AtomicBoolean(true) }
    val latestOnScanned by rememberUpdatedState(onQrScanned)
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cameraError) { onCameraError(cameraError) }
    val preview = remember { Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) } }
    val analysis = remember {
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { useCase ->
            useCase.setAnalyzer(analysisExecutor, QrAnalyzer(scanner, mainExecutor) { rawValue ->
                if (active.get() && detected.compareAndSet(false, true)) {
                    val frame = runCatching { previewView.bitmap?.asImageBitmap() }.getOrNull()
                    if (latestOnScanned(rawValue)) PairingScannerFrame.image = frame else detected.set(false)
                }
            })
        }
    }
    val latestFreezeCallback by rememberUpdatedState(onFreezePreview)
    DisposableEffect(previewView) {
        latestFreezeCallback { PairingScannerFrame.image = runCatching { previewView.bitmap?.asImageBitmap() }.getOrNull() }
        onDispose { latestFreezeCallback(null) }
    }
    DisposableEffect(lifecycleOwner, previewView) {
        active.set(true)
        providerFuture.addListener({
            if (active.get()) {
                try {
                    provider = providerFuture.get()
                    camera = provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    cameraError = "카메라를 시작할 수 없습니다. 코드 직접 입력을 이용하세요."
                }
            }
        }, mainExecutor)
        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            camera?.cameraControl?.enableTorch(false)
            provider?.unbind(preview, analysis)
            camera = null
            scanner.close()
            analysisExecutor.shutdown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(24.dp)) {
                    GeoMediaIconButton(if (torchEnabled) GeoIcons.torchOff else GeoIcons.torch,
                        if (torchEnabled) "손전등 끄기" else "손전등 켜기", {
                            torchEnabled = !torchEnabled
                            camera?.cameraControl?.enableTorch(torchEnabled)
                        })
            }
        }
    }
}

@Composable
private fun ScannerFrameOverlay(error: String?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = minOf(240.dp, maxWidth, maxHeight / 2).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(frame)) {
                val edge = 40.dp.toPx().coerceAtMost(size.width / 3)
                val stroke = 4.dp.toPx()
                for (right in listOf(false, true)) for (bottom in listOf(false, true)) {
                    val x = if (right) size.width - stroke / 2 else stroke / 2
                    val y = if (bottom) size.height - stroke / 2 else stroke / 2
                    drawLine(GeoFixedColors.mediaText, Offset(x, y), Offset(x + if (right) -edge else edge, y), stroke)
                    drawLine(GeoFixedColors.mediaText, Offset(x, y), Offset(x, y + if (bottom) -edge else edge), stroke)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(error ?: "장치 본체의 QR 코드를 사각형 안에 맞추세요.",
                Modifier.background(GeoFixedColors.mediaPlate, GeoShape.badge).padding(horizontal = 12.dp, vertical = 8.dp),
                color = GeoFixedColors.mediaText, style = LocalGeoType.current.body, textAlign = TextAlign.Center)
        }
    }
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val mainExecutor: Executor,
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val analyzing = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!analyzing.compareAndSet(false, true)) { imageProxy.close(); return }
        val mediaImage = imageProxy.image
        if (mediaImage == null) { analyzing.set(false); imageProxy.close(); return }
        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstNotNullOfOrNull { it.rawValue }?.let { rawValue -> mainExecutor.execute { onResult(rawValue) } }
                }
                .addOnCompleteListener { analyzing.set(false); imageProxy.close() }
        } catch (_: Exception) {
            analyzing.set(false)
            imageProxy.close()
        }
    }
}
