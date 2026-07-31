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
import androidx.collection.intFloatMapOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import sg.edu.nus.iss.canmakan.R
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.DepressedBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.MutedBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// The main scanner screen.
@Composable
fun ScannerScreen(
    activeProfile: DietaryProfile,
    activeRestrictions: List<String>,
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBarcodeDetected: (String) -> Unit = {},
    onScanError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
                Spacer(modifier = Modifier.height(8.dp))
                ActiveProfileChip(profile = activeProfile)
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.scanner_barcode),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(text = stringResource(id = R.string.scanner_instructions), color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))

            if (hasCameraPermission) {
                CameraPreview(
                    onBarcodeDetected = onBarcodeDetected,
                    onScanError = onScanError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DepressedBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(id = R.string.scanner_camera_permission_required), color = MutedBlue)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${stringResource(id = R.string.dietary_profile_restrictions).uppercase()} - ${activeProfile.name.uppercase()}",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        activeRestrictions.forEach { restriction ->
                            RestrictPill(text = restriction)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit = {},
    onScanError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val currentOnBarcodeDetected = rememberUpdatedState(onBarcodeDetected)
    val currentOnScanError = rememberUpdatedState(onScanError)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeAnalyzer = remember {
        BarcodeAnalyzer(
            onBarcodeDetected = { barcode ->
                currentOnBarcodeDetected.value(barcode)
            },
            onAnalysisError = { error ->
                currentOnScanError.value(error)
            }
        )
    }
    val preview = remember { Preview.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val isDisposed = remember { AtomicBoolean(false) }

    DisposableEffect(
        cameraProviderFuture,
        preview,
        imageAnalysis,
        barcodeAnalyzer,
        analysisExecutor
    ) {
        imageAnalysis.setAnalyzer(analysisExecutor, barcodeAnalyzer)

        onDispose {
            isDisposed.set(true)
            imageAnalysis.clearAnalyzer()

            if (cameraProviderFuture.isDone) {
                try {
                    cameraProviderFuture.get().unbind(preview, imageAnalysis)
                } catch (e: Exception) {
                    Timber.e(e, "Camera Use Case Unbinding Failed")
                }
            }

            barcodeAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = stringResource(id = R.string.scanner_redlines_transition))
    val lineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = stringResource(id = R.string.scanner_redlines_progress)
    )

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    if (!isDisposed.get()) {
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            preview.surfaceProvider = previewView.surfaceProvider
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Camera Use Case Binding Failed")
                        }
                    }
                }, executor)
                previewView
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentY = size.height * lineProgress

            drawLine(
                color = Color.Red,
                start = Offset(0f, currentY),
                end = Offset(x = size.width, y = currentY),
                strokeWidth = 6f
            )
        }
    }
}
@Composable
private fun RestrictPill(text: String) {
    Text(
        text = text,
        color = PrimaryGreen,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LightGreenBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
