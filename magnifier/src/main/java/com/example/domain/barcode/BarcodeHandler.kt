package com.example.domain.barcode

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Dekódolt vonalkód és QR-kód eredményszerkezet.
 */
data class BarcodeResult(
    val text: String,
    val typeLabel: String,
    val isUrl: Boolean
)

/**
 * Vonalkód- és QR-kód olvasó domain modul (ML Kit Barcode Scanning).
 * Felelőssége: Kép alapján QR- és vonalkódok felismerése és típusosztályozása.
 */
object BarcodeHandler {

    fun processImage(
        bitmap: Bitmap,
        onSuccess: (BarcodeResult) -> Unit,
        onEmpty: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]
                    val rawText = barcode.rawValue ?: ""
                    val displayText = barcode.displayValue ?: rawText
                    val isUrl = barcode.valueType == Barcode.TYPE_URL
                    val typeLabel = resolveTypeLabel(barcode.valueType)

                    onSuccess(
                        BarcodeResult(
                            text = displayText,
                            typeLabel = typeLabel,
                            isUrl = isUrl
                        )
                    )
                } else {
                    onEmpty()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("BarcodeHandler", "Vonalkódolvasási hiba", exception)
                onFailure(exception)
            }
    }

    private fun resolveTypeLabel(valueType: Int): String {
        return when (valueType) {
            Barcode.TYPE_URL -> "Webcím (URL)"
            Barcode.TYPE_TEXT -> "Szöveg"
            Barcode.TYPE_PRODUCT -> "Termékkód"
            Barcode.TYPE_ISBN -> "Könyv (ISBN)"
            Barcode.TYPE_WIFI -> "WiFi Hálózat"
            Barcode.TYPE_CONTACT_INFO -> "Kapcsolati adatok"
            else -> "Vonalkód / QR-kód"
        }
    }
}
