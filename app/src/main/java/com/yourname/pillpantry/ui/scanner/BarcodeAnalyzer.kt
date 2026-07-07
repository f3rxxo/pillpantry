package com.yourname.pillpantry.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Wraps ML Kit's on-device barcode scanner as a CameraX analyzer.
 * Calls [onBarcodeDetected] with the raw barcode value the first time a
 * barcode is found in a frame; callers are responsible for de-duplicating
 * rapid repeat detections (see [ScannerScreen]'s `scanned` guard).
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes: List<Barcode> ->
                val value = barcodes.firstOrNull()?.rawValue
                if (value != null) {
                    onBarcodeDetected(value)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
