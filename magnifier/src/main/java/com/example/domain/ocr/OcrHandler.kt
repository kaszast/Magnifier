package com.example.domain.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Szövegfelismerő domain modul (ML Kit OCR).
 * Felelőssége: Kép alapján felismerhető szöveg kinyerése aszinkron módon.
 */
object OcrHandler {

    fun processImage(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onEmpty: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isNotBlank()) {
                    onSuccess(text)
                } else {
                    onEmpty()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("OcrHandler", "Szövegfelismerési hiba", exception)
                onFailure(exception)
            }
    }
}
