package com.edukasyon.studentai.ui.share

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.concurrent.futures.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.ai.ExtractedClass
import com.edukasyon.studentai.core.share.ShareCode
import com.edukasyon.studentai.core.share.SharePayload
import com.edukasyon.studentai.core.share.SharePayloadResult
import com.edukasyon.studentai.core.share.ShareRepository
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.domain.repository.JeviRepository
import com.edukasyon.studentai.ui.components.EmptyState
import com.edukasyon.studentai.ui.components.ErrorBanner
import com.edukasyon.studentai.ui.components.StudentAiCard
import com.edukasyon.studentai.ui.components.StudentAiSnackbarHost
import com.edukasyon.studentai.ui.components.TimetablePopulateAnimation
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.ScheduleScanStatus
import com.edukasyon.studentai.ui.viewmodel.sharedAiViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.view.View

/**
 * Redeem side of private shares: type a 6-char code or scan the share QR,
 * preview what's inside, then import. Schedules replay the scanner's
 * TimetablePopulateAnimation through the shared AiViewModel path; decks are
 * copied into fresh local entities with review state zeroed.
 */

@HiltViewModel
class RedeemViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
    private val jeviRepository: JeviRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val code: String = "",
        val redeeming: Boolean = false,
        val error: String? = null,
        val preview: SharePayloadResult? = null,
        val deckImported: Boolean = false,
        val importedMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val prefilledCode: String? = savedStateHandle.get<String>("code")
        ?.uppercase(Locale.US)
        ?.takeIf { ShareCode.isValid(it) }

    init {
        prefilledCode?.let { redeem(it) }
    }

    fun onCodeChange(raw: String) {
        _uiState.update { it.copy(code = normalizeShareCodeInput(raw), error = null) }
    }

    fun redeem(code: String = _uiState.value.code) {
        if (!ShareCode.isValid(code) || _uiState.value.redeeming) return
        viewModelScope.launch {
            _uiState.update { it.copy(code = code, redeeming = true, error = null, preview = null, deckImported = false) }
            shareRepository.redeem(code)
                .onSuccess { doc ->
                    when (val result = SharePayload.parse(doc.payloadJson)) {
                        is SharePayloadResult.Invalid -> _uiState.update {
                            it.copy(redeeming = false, error = "This share couldn't be read (${result.reason}).")
                        }
                        else -> _uiState.update { it.copy(redeeming = false, preview = result) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(redeeming = false, error = e.message ?: "Couldn't fetch that share.") }
                }
        }
    }

    fun fillFromQr(rawText: String) {
        val code = normalizeShareCodeInput(rawText.substringAfterLast('/'))
        if (!ShareCode.isValid(code)) {
            _uiState.update { it.copy(error = "That QR isn't a SchedMate share code.") }
            return
        }
        redeem(code)
    }

    /** Imports a shared deck as a fresh local copy (no review history). */
    fun importDeck(deckOk: SharePayloadResult.DeckOk) {
        if (_uiState.value.redeeming || _uiState.value.deckImported) return
        viewModelScope.launch {
            _uiState.update { it.copy(redeeming = true) }
            runCatching {
                val now = System.currentTimeMillis()
                val existingTitles = jeviRepository.observeDecks().first()
                    .map { it.title.trim().lowercase(Locale.US) }
                    .toSet()
                val baseTitle = deckOk.title.trim().ifBlank { "Shared deck" }
                val title = if (baseTitle.lowercase(Locale.US) in existingTitles) {
                    "$baseTitle (shared)"
                } else {
                    baseTitle
                }
                val deckId = UUID.randomUUID().toString()
                jeviRepository.createDeck(
                    JeviDeck(
                        id = deckId,
                        title = title,
                        description = deckOk.description,
                        subjectId = null,
                        sourceNoteId = null,
                        colorHex = deckOk.colorHex,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val cards = deckOk.cards.map { card ->
                    Flashcard(
                        id = UUID.randomUUID().toString(),
                        question = card.question,
                        answer = card.answer,
                        subjectId = null,
                        deckId = deckId,
                        topic = card.topic,
                        difficulty = "medium",
                        reviewCount = 0,
                        correctCount = 0,
                        incorrectCount = 0,
                        lastReviewedAt = null,
                        nextReviewAt = null,
                        easeFactor = 2.5,
                        intervalDays = 1,
                    )
                }
                jeviRepository.saveFlashcardsToDeck(deckId, cards)
            }.onSuccess {
                _uiState.update {
                    it.copy(redeeming = false, deckImported = true, importedMessage = "Deck added")
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(redeeming = false, error = e.message ?: "Couldn't import the deck.")
                }
            }
        }
    }

    fun consumeImportedMessage() {
        _uiState.update { it.copy(importedMessage = null) }
    }

    fun reset() {
        _uiState.update { UiState() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemShareScreen(
    onBack: () -> Unit,
    viewModel: RedeemViewModel = hiltViewModel(),
    aiViewModel: AiViewModel = sharedAiViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCamera by remember { mutableStateOf(false) }
    val scheduleImporting = aiState.scheduleScanStatus == ScheduleScanStatus.CONFIRMING
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Import shared content") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.error?.let {
                ErrorBanner(
                    message = it,
                    title = "Couldn't import",
                    onDismiss = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }

            when (val preview = state.preview) {
                is SharePayloadResult.ScheduleOk -> SchedulePreview(
                    classes = preview.classes,
                    busy = state.redeeming || scheduleImporting,
                    onConfirm = {
                        aiViewModel.importSharedClasses(
                            preview.classes.map { cls ->
                                ExtractedClass(
                                    subject = cls.subject,
                                    teacher = cls.teacher,
                                    room = cls.room,
                                    day = cls.day,
                                    startTime = cls.startTime,
                                    endTime = cls.endTime,
                                )
                            },
                        )
                        showCamera = false
                    },
                    onCancel = viewModel::reset,
                )
                is SharePayloadResult.DeckOk -> DeckPreview(
                    deck = preview,
                    busy = state.redeeming,
                    imported = state.deckImported,
                    onConfirm = { viewModel.importDeck(preview) },
                    onCancel = viewModel::reset,
                )
                null -> CodeEntry(
                    code = state.code,
                    redeeming = state.redeeming,
                    onCodeChange = viewModel::onCodeChange,
                    onRedeem = { viewModel.redeem() },
                    onScanQr = { showCamera = true },
                )
                is SharePayloadResult.Invalid -> Unit
            }
        }

        // Camera QR scanning overlays the whole screen while active.
        if (showCamera && state.preview == null) {
            QrScanCamera(
                active = hasCameraPermission,
                onPermissionResult = { granted -> hasCameraPermission = granted },
                onCodeScanned = { raw ->
                    showCamera = false
                    viewModel.fillFromQr(raw)
                },
                onClose = { showCamera = false },
            )
        }

        // Shared schedule imports replay the scanner's populate animation,
        // exactly like confirmScannedClasses does on the camera path.
        val isImportingSchedule = aiState.scheduleScanStatus == ScheduleScanStatus.CONFIRMING &&
            aiState.classesBeingImported.isNotEmpty()
        if (isImportingSchedule) {
            TimetablePopulateAnimation(
                classes = aiState.classesBeingImported.ifEmpty { aiState.scannedClasses },
                modifier = Modifier.fillMaxSize(),
                onComplete = {
                    aiViewModel.dismissPopulateAnimation()
                    viewModel.reset()
                    scope.launch { snackbarHostState.showSnackbar("Schedule imported") }
                },
            )
        }
        }
    }

    // Duplicate/dedupe feedback from the shared import path.
    LaunchedEffect(aiState.statusMessage) {
        aiState.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            aiViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(state.importedMessage) {
        state.importedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeImportedMessage()
            onBack()
        }
    }
}

@Composable
private fun CodeEntry(
    code: String,
    redeeming: Boolean,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onScanQr: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Enter a 6-character share code",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Someone shared a schedule or deck with you — type their code or scan their QR to import it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Share code") },
            placeholder = { Text("ABC123") },
            singleLine = true,
            enabled = !redeeming,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Button(
            onClick = onRedeem,
            enabled = ShareCode.isValid(code) && !redeeming,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (redeeming) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Looking up…")
            } else {
                Text("Look up share")
            }
        }
        OutlinedButton(
            onClick = onScanQr,
            enabled = !redeeming,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan QR")
        }
    }
}

@Composable
private fun SchedulePreview(
    classes: List<com.edukasyon.studentai.core.share.SharedClass>,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val byDay = classes.groupBy { DayOfWeek.fromString(it.day) ?: DayOfWeek.MONDAY }
    val daysPresent = DayOfWeek.entries.filter { byDay.containsKey(it) }
    val dayRange = daysPresent.firstOrNull()?.let { first ->
        daysPresent.lastOrNull()?.let { last ->
            "${first.displayName.take(3)}–${last.displayName.take(3)}"
        }
    }.orEmpty()
    StudentAiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${classes.size} classes${dayRange.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            daysPresent.forEach { day ->
                Text(
                    "${day.displayName}: ${byDay[day]?.size ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Confirm import")
                }
            }
        }
    }
}

@Composable
private fun DeckPreview(
    deck: SharePayloadResult.DeckOk,
    busy: Boolean,
    imported: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    StudentAiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                deck.title.ifBlank { "Shared deck" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${deck.cards.size} ${if (deck.cards.size == 1) "card" else "cards"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            deck.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm, enabled = !busy && !imported, modifier = Modifier.weight(1f)) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (imported) "Imported" else "Confirm import")
                }
            }
        }
    }
}

/**
 * cameraX preview + ML Kit barcode analysis (QR only), mirroring the schedule
 * scanner's permission/lifecycle/error handling. Emits the raw barcode value
 * (typically "schedmate://share/XXXXXX").
 */
@Composable
private fun QrScanCamera(
    active: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraBindFailed by remember { mutableStateOf<String?>(null) }
    var bindRetryCount by remember { mutableIntStateOf(0) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        if (!active) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner, active, bindRetryCount, previewViewRef) {
        if (!active) {
            runCatching { ProcessCameraProvider.getInstance(context).await().unbindAll() }
            return@LaunchedEffect
        }
        val previewView = previewViewRef ?: return@LaunchedEffect
        cameraBindFailed = null
        try {
            previewView.awaitAttachedToWindowForQr()
            val cameraProvider = withContext(NonCancellable) {
                ProcessCameraProvider.getInstance(context).await()
            }
            withContext(NonCancellable) {
                val preview = Preview.Builder().build().also { useCase ->
                    useCase.setSurfaceProvider(previewView.surfaceProvider)
                }
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                            val media = imageProxy.image
                            if (media == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(input)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let { value -> onCodeScanned(value) }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cameraBindFailed = "Camera unavailable — enter the code manually"
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (active && cameraBindFailed == null) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { previewViewRef = it }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        ) {
            cameraBindFailed?.let {
                ErrorBanner(
                    message = it,
                    title = "Camera unavailable",
                    onDismiss = { bindRetryCount++ },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!active) {
                EmptyState(
                    title = "Camera access needed",
                    message = "Allow camera access to scan a share QR, or close this view and type the code instead.",
                    actionLabel = "Grant Permission",
                    onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Type the code instead")
            }
        }
    }
}

private suspend fun PreviewView.awaitAttachedToWindowForQr() {
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
        continuation.invokeOnCancellation { removeOnAttachStateChangeListener(listener) }
    }
}
