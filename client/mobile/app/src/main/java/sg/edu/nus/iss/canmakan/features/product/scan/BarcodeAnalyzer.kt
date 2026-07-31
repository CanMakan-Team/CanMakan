package sg.edu.nus.iss.canmakan.features.product.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber


/**
 * Custom Image Analyzer to detect barcode in CameraZ frames and process
 * it to numeric strings through the utilization of Google ML Kit.
 */
class BarcodeAnalyzer (
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // 1. Configure the barcode scanner to look only for Standard Product Barcodes.
    //      This aims to optimize the scanning process and battery utilization.
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
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            // 2. Convert CameraX Image to ML Kit Input Image
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // 3. Process the ML Kit Input Image
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { barcodeValue ->
                            onBarcodeScanned(barcodeValue)
                            return@addOnSuccessListener
                        }
                    }
                }
                .addOnFailureListener {
                    Timber.e(it, "Barcode Scanning Failed")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }   else {
            imageProxy.close()
        }
    }
}