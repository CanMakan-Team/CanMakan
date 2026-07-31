package sg.edu.nus.iss.canmakan.features.product.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val onAnalysisError: (Throwable) -> Unit = {},
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()
) : ImageAnalysis.Analyzer, Closeable {

    private val isProcessing = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get() || isPaused.get() || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val isFrameFinished = AtomicBoolean(false)
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            finishFrame(imageProxy, isFrameFinished)
            return
        }

        val inputImage = try {
            InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
        } catch (error: Throwable) {
            reportAnalysisError(error)
            finishFrame(imageProxy, isFrameFinished)
            return
        }

        try {
            barcodeScanner.process(inputImage)
                .addOnCompleteListener { task ->
                    try {
                        when {
                            task.isSuccessful -> {
                                val rawValue = task.result
                                    .firstNotNullOfOrNull { barcode ->
                                        barcode.rawValue?.takeIf { it.isNotBlank() }
                                    }

                                if (rawValue != null && isPaused.compareAndSet(false, true)) {
                                    try {
                                        onBarcodeDetected(rawValue)
                                    } catch (error: Throwable) {
                                        isPaused.set(false)
                                        reportAnalysisError(error)
                                    }
                                }
                            }

                            !task.isCanceled -> {
                                reportAnalysisError(
                                    task.exception
                                        ?: IllegalStateException("Barcode analysis failed")
                                )
                            }
                        }
                    } catch (error: Throwable) {
                        reportAnalysisError(error)
                    } finally {
                        finishFrame(imageProxy, isFrameFinished)
                    }
                }
        } catch (error: Throwable) {
            reportAnalysisError(error)
            finishFrame(imageProxy, isFrameFinished)
        }
    }

    fun resumeScanning() {
        if (!isClosed.get()) {
            isPaused.set(false)
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            isPaused.set(true)
            barcodeScanner.close()
        }
    }

    private fun finishFrame(
        imageProxy: ImageProxy,
        isFrameFinished: AtomicBoolean
    ) {
        if (!isFrameFinished.compareAndSet(false, true)) {
            return
        }

        try {
            imageProxy.close()
        } finally {
            isProcessing.set(false)
        }
    }

    private fun reportAnalysisError(error: Throwable) {
        try {
            onAnalysisError(error)
        } catch (_: Throwable) {
        }
    }
}
