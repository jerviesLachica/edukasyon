package com.edukasyon.studentai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.view.Surface
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.ui.components.EmptyState
import com.edukasyon.studentai.ui.components.ErrorBanner
import com.edukasyon.studentai.ui.components.ScanningOverlay
import com.edukasyon.studentai.ui.components.ScheduleScanFailureOverlay
import com.edukasyon.studentai.ui.components.StudentAiCard
import com.edukasyon.studentai.ui.components.StudentAiSnackbarHost
import com.edukasyon.studentai.ui.components.TimetablePopulateAnimation
import com.edukasyon.studentai.core.mlkit.rememberDocumentScanLauncher
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.ScheduleScanStatus
import com.edukasyon.studentai.ui.viewmodel.sharedAiViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScannerScreen(
    onBack: () -> Unit,
    viewModel: AiViewModel = sharedAiViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        viewModel.onScheduleScannerOpened()
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var cameraBindFailed by remember { mutableStateOf<String?>(null) }
    var isCameraInitializing by remember { mutableStateOf(false) }
    var bindRetryCount by remember { mutableIntStateOf(0) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var pendingScanImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val showCamera = state.scannedClasses.isEmpty()
    val isScanning = state.scheduleScanStatus == ScheduleScanStatus.SCANNING
    val isConfirming = state.scheduleScanStatus == ScheduleScanStatus.CONFIRMING
    val isImageOnlyRetry = state.scheduleScanRetryCount == 2
    val showScanFailure = state.scheduleScanStatus == ScheduleScanStatus.UNREADABLE ||
        state.scheduleScanStatus == ScheduleScanStatus.RETRY_LATER
    val cooldownActive = state.scheduleScanRetryAfterMillis?.let { System.currentTimeMillis() < it } == true
    val scanBlocked = isScanning || showScanFailure || cooldownActive
    val showPopulateAnimation = isConfirming && (state.classesBeingImported.isNotEmpty() || state.scannedClasses.isNotEmpty())

    fun submitScanImage(bytes: ByteArray) {
        pendingScanImageBytes = bytes
        viewModel.analyzeScheduleImage(bytes)
    }

    val launchDocumentScan = rememberDocumentScanLauncher(
        onResult = { bytes -> submitScanImage(bytes) },
        onError = { message -> captureError = message },
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner, hasCameraPermission, showCamera, bindRetryCount, previewViewRef) {
        if (!hasCameraPermission || !showCamera) {
            cameraReady = false
            imageCapture = null
            isCameraInitializing = false
            runCatching { ProcessCameraProvider.getInstance(context).await().unbindAll() }
            return@LaunchedEffect
        }

        val previewView = previewViewRef ?: return@LaunchedEffect

        cameraBindFailed = null
        cameraReady = false
        isCameraInitializing = true

        try {
            previewView.awaitAttachedToWindow()

            val cameraProvider = withContext(NonCancellable) {
                ProcessCameraProvider.getInstance(context).await()
            }

            withContext(NonCancellable) {
                val preview = Preview.Builder().build().also { useCase ->
                    useCase.setSurfaceProvider(previewView.surfaceProvider)
                }
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(85)
                    .setTargetRotation(rotation)
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
                imageCapture = capture
            }
            cameraReady = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cameraReady = false
            imageCapture = null
            cameraBindFailed = userFacingCameraError(e)
        } finally {
            isCameraInitializing = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        captureError = null
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onSuccess { bytes ->
            if (bytes != null && bytes.isNotEmpty()) {
                submitScanImage(bytes)
            } else {
                captureError = "Could not read the selected image"
            }
        }.onFailure {
            captureError = it.message ?: "Could not read the selected image"
        }
    }

    fun captureAndAnalyze() {
        val capture = imageCapture
        if (capture == null || !cameraReady) {
            captureError = "Camera is still starting. Please wait a moment and try again."
            return
        }
        captureError = null
        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bytes = imageProxyToJpeg(image)
                        if (bytes.isEmpty()) {
                            scope.launch { captureError = "Captured photo was empty. Please try again." }
                            return
                        }
                        scope.launch {
                            submitScanImage(bytes)
                        }
                    } catch (e: Exception) {
                        scope.launch { captureError = e.message ?: "Failed to process photo" }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch { captureError = exception.message ?: "Capture failed" }
                }
            },
        )
    }

    val showPermissionRationale = !hasCameraPermission && permissionDenied &&
        (context as? Activity)?.let { activity ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        } == true

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Scan Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasCameraPermission) {
                EmptyState(
                    title = if (showPermissionRationale) "Camera access needed" else "Camera permission required",
                    message = if (showPermissionRationale) {
                        "Camera access is required to scan your class schedule. Tap below to grant permission."
                    } else {
                        "Allow camera access to scan your class schedule, or pick a photo from Gallery after granting access."
                    },
                    actionLabel = "Grant Permission",
                    onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            } else {
                if (showCamera) {
                    Box(
                        Modifier.weight(1f).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }.also { previewViewRef = it }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isScanning) {
                            ScanningOverlay(
                                imageBytes = pendingScanImageBytes,
                                extractedText = state.scheduleScanExtractedText,
                                isImageOnlyRetry = isImageOnlyRetry,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (showScanFailure) {
                            ScheduleScanFailureOverlay(
                                imageBytes = pendingScanImageBytes,
                                status = state.scheduleScanStatus,
                                retryCount = state.scheduleScanRetryCount,
                                retryAfterMillis = state.scheduleScanRetryAfterMillis,
                                onRetry = { viewModel.retryScheduleScan() },
                                onDismiss = {
                                    pendingScanImageBytes = null
                                    viewModel.dismissScheduleScanFailure()
                                },
                                onEnterManually = {
                                    pendingScanImageBytes = null
                                    viewModel.dismissScheduleScanFailure()
                                    onBack()
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        when {
                            isCameraInitializing && !scanBlocked && cameraBindFailed == null -> CircularProgressIndicator()
                            cameraBindFailed != null -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp),
                                ) {
                                    Text(
                                        text = cameraBindFailed!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OutlinedButton(onClick = { bindRetryCount++ }) {
                                        Text("Retry Camera")
                                    }
                                }
                            }
                        }
                    }
                }
                cameraBindFailed?.let {
                    ErrorBanner(
                        message = it,
                        title = "Camera unavailable",
                        onDismiss = { cameraBindFailed = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                captureError?.let {
                    ErrorBanner(
                        message = it,
                        title = "Capture failed",
                        onDismiss = { captureError = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                state.error?.let {
                    ErrorBanner(
                        message = it,
                        title = "Analysis failed",
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (showCamera) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                enabled = !scanBlocked,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Gallery")
                            }
                            OutlinedButton(
                                onClick = launchDocumentScan,
                                enabled = !scanBlocked,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.DocumentScanner, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Doc scan")
                            }
                        }
                        Button(
                            onClick = { captureAndAnalyze() },
                            enabled = !scanBlocked && cameraReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isScanning) "Scanning…" else "Capture & Analyze")
                        }
                    }
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (state.scannedClasses.isNotEmpty()) {
                Text(
                    "Review extracted classes",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.scannedClasses) { cls ->
                        StudentAiCard {
                            Text(cls.subject, style = MaterialTheme.typography.titleSmall)
                            Text("${cls.day} ${cls.startTime}-${cls.endTime}")
                            cls.teacher?.let { Text("Teacher: $it") }
                            cls.room?.let { Text("Room: $it") }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingScanImageBytes = null
                                    viewModel.clearScannedClasses()
                                    bindRetryCount++
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isConfirming,
                            ) {
                                Text("Rescan")
                            }
                            Button(
                                onClick = {
                                    viewModel.confirmScannedClasses()
                                    // onBack() is deferred until the populate animation
                                    // completes (handled by showPopulateAnimation / onComplete)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isConfirming,
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isConfirming) "Adding…" else "Import")
                            }
                        }
                    }
                }
            }
        }

        if (showPopulateAnimation) {
            TimetablePopulateAnimation(
                classes = state.classesBeingImported.ifEmpty { state.scannedClasses },
                modifier = Modifier.fillMaxSize(),
                onComplete = {
                    pendingScanImageBytes = null
                    viewModel.dismissPopulateAnimation()
                    onBack()
                },
            )
        }
    }
}

private suspend fun PreviewView.awaitAttachedToWindow() {
    if (isAttachedToWindow) return
    suspendCancellableCoroutine { continuation ->
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }
        addOnAttachStateChangeListener(listener)
        continuation.invokeOnCancellation {
            removeOnAttachStateChangeListener(listener)
        }
    }
}

private fun userFacingCameraError(@Suppress("UNUSED_PARAMETER") error: Exception): String =
    "Camera unavailable — use Gallery instead"

private fun imageProxyToJpeg(image: ImageProxy): ByteArray {
    val rotationDegrees = image.imageInfo.rotationDegrees

    if (image.format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        // Apply rotation for JPEG format as well - critical for scan accuracy
        if (rotationDegrees != 0) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return bytes
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
            bitmap.recycle()
            return ByteArrayOutputStream().use { stream ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        }
        return bytes
    }

    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val jpegStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, jpegStream)
    var bitmap = BitmapFactory.decodeByteArray(jpegStream.toByteArray(), 0, jpegStream.size())
        ?: throw IllegalStateException("Could not decode captured image")

    if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    return ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        stream.toByteArray()
    }
}
