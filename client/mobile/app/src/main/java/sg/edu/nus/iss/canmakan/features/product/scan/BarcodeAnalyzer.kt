package sg.edu.nus.iss.canmakan.features.product.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Custom Image Analyzer to detect barcode in CameraX frames and process
 * it to numeric strings through the utilization of Google ML Kit.
 */
class BarcodeAnalyzer (
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // 1. Configure the barcode scanner to look only for Standard Product Barcodes.
    //      This aims to optimize the scanning process and battery utilization.
    private var isScanningEnabled = AtomicBoolean(true)
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        ).build()

    private val scanner = BarcodeScanning.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        // 2. Skip image analysis if scanning is not enabled or image is null
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isScanningEnabled.get()) {
            imageProxy.close()
            return
        }

        // 3. Convert CameraX Image to ML Kit Input Image
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        // 4. Process the ML Kit Input Image
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcodeValue = barcodes.firstNotNullOfOrNull { it.rawValue }

                if (barcodeValue != null && isScanningEnabled.compareAndSet(true, false)) {
                    onBarcodeScanned(barcodeValue)
                }
            }
            .addOnFailureListener {
                Timber.e(it, "Barcode Scanning Failed")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Resumes the scanner to allow capturing another barcode.
     */
    fun resumeScanning() {
        isScanningEnabled.set(true)
    }

    /**
     * Releases ML Kit resources. Call this when the analyzer is no longer needed.
     */
    fun close() {
        scanner.close()
    }
}