package com.example.jetsoncontroller.ui.pairing

import android.annotation.SuppressLint
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
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
    val haptic = LocalHapticFeedback.current
    val submitCode: (String) -> Boolean = { rawValue ->
        val accepted = onQrScanned(rawValue.trim())
        if (accepted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        accepted
    }

    if (showManualEntry) {
        AlertDialog(
            onDismissRequest = { showManualEntry = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text("등록 코드 직접 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "QR을 읽기 어렵다면 QR 아래의 전체 등록 코드를 입력하세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = manualValue,
                        onValueChange = { manualValue = it.take(4096) },
                        label = { Text("등록 코드") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (submitCode(manualValue)) {
                            showManualEntry = false
                        }
                    },
                    enabled = manualValue.isNotBlank()
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntry = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("장비 추가") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermissionGranted) {
                CameraScannerContent(
                    errorMessage = errorMessage,
                    onQrScanned = submitCode,
                    onManualEntry = { showManualEntry = true }
                )
            } else {
                CameraPermissionContent(
                    onRequestPermission = onRequestCameraPermission,
                    onManualEntry = { showManualEntry = true }
                )
            }
        }
    }
}

@Composable
private fun CameraScannerContent(
    errorMessage: String?,
    onQrScanned: (String) -> Boolean,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) {
        ContextCompat.getMainExecutor(context)
    }
    val analysisExecutor = remember {
        Executors.newSingleThreadExecutor()
    }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val qrDetected = remember { AtomicBoolean(false) }
    val latestOnQrScanned by rememberUpdatedState(onQrScanned)
    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember {
        mutableStateOf<String?>(null)
    }

    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(
                    analysisExecutor,
                    QrAnalyzer(
                        scanner = scanner,
                        mainExecutor = mainExecutor
                    ) { rawValue ->
                        if (qrDetected.compareAndSet(false, true)) {
                            val accepted = latestOnQrScanned(rawValue)
                            if (!accepted) {
                                qrDetected.set(false)
                            }
                        }
                    }
                )
            }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) {
                    return@addListener
                }

                try {
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )

                    cameraProvider = provider
                    cameraError = null
                } catch (_: Exception) {
                    cameraError =
                        "카메라를 시작할 수 없습니다. 다른 앱에서 카메라를 사용 중인지 확인해 주세요."
                }
            },
            mainExecutor
        )

        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            cameraProvider?.unbindAll()
            camera = null
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        QrOverlay()

        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small
            ) {
                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (torchEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = if (torchEnabled) "손전등 끄기" else "손전등 켜기"
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val visibleError = errorMessage ?: cameraError
            if (visibleError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = visibleError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Jetson QR 코드를 스캔하세요",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Jetson 본체의 QR 코드를\n사각형 안에 맞춰주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(onClick = onManualEntry) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("등록 코드 직접 입력", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun CameraPermissionContent(
    onRequestPermission: () -> Unit,
    onManualEntry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.padding(12.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "카메라 권한이 필요합니다",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Jetson 본체의 QR 코드를 읽기 위해 카메라 사용을 허용해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("카메라 권한 허용")
        }

        Spacer(modifier = Modifier.height(12.dp))

        FilledTonalButton(
            onClick = onManualEntry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Text("등록 코드 직접 입력", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun QrOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val overlaySize = size.width * 0.7f
        val left = (size.width - overlaySize) / 2
        val top = (size.height - overlaySize) / 2
        val rect = Rect(Offset(left, top), Size(overlaySize, overlaySize))

        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            )
        }

        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.6f))
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
        if (!analyzing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            analyzing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes
                    .firstNotNullOfOrNull { it.rawValue }

                if (rawValue != null) {
                    mainExecutor.execute {
                        onResult(rawValue)
                    }
                }
            }
            .addOnCompleteListener {
                analyzing.set(false)
                imageProxy.close()
            }
    }
}
