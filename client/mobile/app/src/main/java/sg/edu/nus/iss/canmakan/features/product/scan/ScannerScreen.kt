package sg.edu.nus.iss.canmakan.features.product.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sg.edu.nus.iss.canmakan.R
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.*
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * The Scanner Screen. Display the Dietary Profile and Restrictions.
 */
@Composable
fun ScannerScreen(
    activeProfile: DietaryProfile?,
    activeRestrictions: List<String>,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSetUpProfile: () -> Unit = {},
    onVerdictReady: (VerdictDetail) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    // Prevents re-firing the same code while it remains in the camera frame.
    var lastProcessedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    val processState by viewModel.processState.collectAsState()
    val verdictDetail by viewModel.verdictDetail.collectAsState()
    val latestProcessState by rememberUpdatedState(processState)
    val latestProfileId by rememberUpdatedState(activeProfile?.id)

    val isProcessing = processState == ScanProcessState.VALIDATING ||
        processState == ScanProcessState.ASSESSING ||
        processState == ScanProcessState.FETCHING_ALTERNATIVES
    val canAcceptNewScan = processState == ScanProcessState.IDLE ||
        processState == ScanProcessState.INVALID ||
        processState == ScanProcessState.ERROR

    LaunchedEffect(processState, verdictDetail) {
        if (processState == ScanProcessState.SUCCESS && verdictDetail != null) {
            onVerdictReady(verdictDetail!!)
            scannedBarcode = null
            lastProcessedBarcode = null
            viewModel.resetState()
        }
    }

    // 1. Check for Camera Permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 2. Request Camera Permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 3. Assembling of Components on the Scanner Screen
    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                )
                activeProfile?.let { ActiveProfileChip(profile = it) } ?: run {
                    // Show a setup prompt if no profile is active
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSetUpProfile() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tap here to set up your dietary profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.scanner_barcode),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(id = R.string.scanner_instructions),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DepressedBlue)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        onBarcodeScanned = { barcode ->
                            val state = latestProcessState
                            val acceptScan = state == ScanProcessState.IDLE ||
                                state == ScanProcessState.INVALID ||
                                state == ScanProcessState.ERROR
                            if (!acceptScan) return@CameraPreview
                            if (barcode == lastProcessedBarcode) return@CameraPreview

                            lastProcessedBarcode = barcode
                            scannedBarcode = barcode
                            latestProfileId?.let { profileId ->
                                viewModel.processBarcode(
                                    barcode = barcode,
                                    profileId = profileId,
                                )
                            }
                        }
                    )
                    ValidationOverlay(viewModel = viewModel)
                } else {
                    Text(
                        text = stringResource(id = R.string.scanner_camera_permission_required),
                        color = MutedBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Status / retry control. Successful reads auto-process; tap only retries after failure.
            val statusLabel = when {
                isProcessing -> stringResource(id = R.string.scanner_checking)
                processState == ScanProcessState.INVALID ||
                    processState == ScanProcessState.ERROR ->
                    stringResource(id = R.string.scanner_try_again)
                else -> stringResource(id = R.string.scanner_ready)
            }
            Button(
                onClick = {
                    scannedBarcode?.let { barcode ->
                        activeProfile?.id?.let { profileId ->
                            viewModel.processBarcode(
                                barcode = barcode,
                                profileId = profileId,
                            )
                        }
                    }
                },
                enabled = (processState == ScanProcessState.INVALID ||
                    processState == ScanProcessState.ERROR) && scannedBarcode != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Bold
                )
            }
            scannedBarcode?.takeIf { canAcceptNewScan || isProcessing }?.let { barcode ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = barcode,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${stringResource(id = R.string.dietary_profile_restrictions).uppercase()} - ${activeProfile?.profileName?.uppercase() ?: "NONE"}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow (
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeRestrictions.forEach { restriction ->
                            RestrictPill(text = restriction)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Camera Preview composable.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    // Dedicated executor for barcode analysis to offload work from the main thread
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // 1. Retain a reference to the analyzer and ensure it is cleaned up.
    val barcodeAnalyzer = remember {
        BarcodeAnalyzer { barcode ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnBarcodeScanned(barcode)
        }
    }

    // 2. Clean up resources when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            barcodeAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, barcodeAnalyzer)
                    }

                // Explicitly unbind all to avoid session conflicts, but catch potential errors
                // from previous sessions that might be in an error state.
                try {
                    cameraProvider.unbindAll()
                } catch (e: Exception) {
                    Timber.e(e, "Error unbinding camera use cases")
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Timber.e(e, "Camera binding failed")
            }
        }, mainExecutor)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )
        ScanningOverlay()
    }
}

/**
 * Scanner Overlay composable.
 */
@Composable
private fun ScanningOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = stringResource(id = R.string.scanner_redlines_transition))
    val lineProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = stringResource(id = R.string.scanner_redlines_progress)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val currentY = size.height * lineProgress

        drawLine(
            color = AvoidRed,
            start = Offset(0f, currentY),
            end = Offset(x = size.width, y = currentY),
            strokeWidth = 2.dp.toPx(),
            alpha = 0.8f
        )
    }
}

/**
 * Validation Overlay composable.
 */
@Composable
fun ValidationOverlay(viewModel: ScannerViewModel) {
    val state by viewModel.processState.collectAsState()

    val (backgroundColor, statusText) = when (state) {
        ScanProcessState.IDLE, ScanProcessState.SUCCESS -> Pair(Color.Transparent, 0)
        ScanProcessState.VALIDATING -> Pair(OpaqueBlack, R.string.validation_state_validating)
        ScanProcessState.ASSESSING -> Pair(OpaqueDarkGreen, R.string.validation_state_assessing)
        ScanProcessState.FETCHING_ALTERNATIVES -> Pair(OpaqueDarkGreen, R.string.validation_state_fetching_alternatives)
        ScanProcessState.INVALID -> Pair(OpaqueDeepRed, R.string.validation_state_invalid)
        ScanProcessState.ERROR -> Pair(OpaqueDeepRed, R.string.validation_state_error)
    }

    if (state != ScanProcessState.IDLE && state != ScanProcessState.SUCCESS) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state == ScanProcessState.VALIDATING ||
                    state == ScanProcessState.ASSESSING ||
                    state == ScanProcessState.FETCHING_ALTERNATIVES
                ) {
                    CircularProgressIndicator(color = OnDark)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = stringResource(id = statusText),
                    color = OnDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Restrict Pill composable.
 */
@Composable
private fun RestrictPill(text: String) {
    Surface(
        color = LightGreenBackground,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            color = PrimaryGreen,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(LightGreenBackground)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
