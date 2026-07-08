package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.roundToInt

enum class FilterMode(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    NORMAL(R.string.filter_normal, R.string.filter_normal_desc),
    MONOCHROME(R.string.filter_monochrome, R.string.filter_monochrome_desc),
    INVERTED(R.string.filter_inverted, R.string.filter_inverted_desc),
    YELLOW(R.string.filter_yellow, R.string.filter_yellow_desc),
    RED(R.string.filter_red, R.string.filter_red_desc)
}

// A szűrő + kontraszt + fényerő kanonikus color matrix-a. A képernyős megjelenítés
// (combinedColorFilter, élő overlay) és a mentett/megosztott kép ugyanebből épül,
// hogy a kimenet pontosan az legyen, amit a felhasználó lát (WYSIWYG).
fun buildFilterMatrixValues(filterMode: FilterMode, contrast: Float, brightness: Float): FloatArray {
    val matrix = ColorMatrix()

    // 1. Apply base accessibility/night filter
    when (filterMode) {
        FilterMode.NORMAL -> { /* Keep identity */ }
        FilterMode.MONOCHROME -> {
            matrix.setToSaturation(0f)
        }
        FilterMode.INVERTED -> {
            matrix.set(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.YELLOW -> {
            // luma → (l, l, 0): megegyezik az élő nézet deszaturálás + sárga modulálás blendjével
            matrix.set(ColorMatrix(floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.RED -> {
            // luma → (l, 0, 0): megegyezik az élő nézet deszaturálás + vörös modulálás blendjével
            matrix.set(ColorMatrix(floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
    }

    // 2. Adjust contrast & brightness directly in color matrix values
    val values = matrix.values
    for (i in 0..14) {
        values[i] = values[i] * contrast
    }
    values[4] = values[4] + brightness
    values[9] = values[9] + brightness
    values[14] = values[14] + brightness

    return values
}

// A digitális zoom által képernyőn látott kivágás forrás-koordinátákban; a geometria
// megegyezik a minimap-kalkulációval (graphicsLayer középpontos skálázás + eltolás).
fun computeVisibleCropRect(width: Int, height: Int, scale: Float, panX: Float, panY: Float): android.graphics.Rect {
    if (scale <= 1.0f || width <= 0 || height <= 0) {
        return android.graphics.Rect(0, 0, width, height)
    }
    val cropW = (width / scale).roundToInt().coerceIn(1, width)
    val cropH = (height / scale).roundToInt().coerceIn(1, height)
    val left = ((width / 2f - panX / scale) - cropW / 2f).roundToInt().coerceIn(0, width - cropW)
    val top = ((height / 2f - panY / scale) - cropH / 2f).roundToInt().coerceIn(0, height - cropH)
    return android.graphics.Rect(left, top, left + cropW, top + cropH)
}

// Középre igazított vágás a cél-képarányra (a preview FILL_CENTER kivágásának megfelelően)
fun computeAspectCropRect(width: Int, height: Int, targetAspect: Float): android.graphics.Rect {
    if (width <= 0 || height <= 0 || targetAspect <= 0f) {
        return android.graphics.Rect(0, 0, width, height)
    }
    val srcAspect = width.toFloat() / height.toFloat()
    return if (srcAspect > targetAspect) {
        val cropW = (height * targetAspect).roundToInt().coerceIn(1, width)
        val left = (width - cropW) / 2
        android.graphics.Rect(left, 0, left + cropW, height)
    } else {
        val cropH = (width / targetAspect).roundToInt().coerceIn(1, height)
        val top = (height - cropH) / 2
        android.graphics.Rect(0, top, width, top + cropH)
    }
}

// Legkisebb 2-hatvány mintavételezés, amellyel a leghosszabb oldal maxDim alá kerül (OOM-védelem)
fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (maxDim <= 0) return 1
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}

// A still capture JPEG dekódolása memória-plafonnal, a viewport képarányára vágva, majd a
// kijelző tájolására forgatva. A vágás a forgatás ELŐTT történik (90/270 foknál invertált
// cél-aspecttel), így a forgatandó bitmap kisebb.
fun decodeCapturedJpeg(bytes: ByteArray, rotationDegrees: Int, targetAspect: Float, maxDim: Int = 4096): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
    }
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    val bufferAspect = if (rotationDegrees % 180 != 0 && targetAspect > 0f) 1f / targetAspect else targetAspect
    val crop = computeAspectCropRect(bitmap.width, bitmap.height, bufferAspect)
    if (crop.width() < bitmap.width || crop.height() < bitmap.height) {
        bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
    }

    if (rotationDegrees != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}

// Mentés/megosztás előtti feldolgozás. Élő módban a képernyőn látott kivágást és szűrőt
// reprodukálja; a kontraszt/fényerő élő nézetben nem látszik, ezért ott nem kerül a kimenetre.
fun processExportBitmap(
    raw: Bitmap,
    isFrozen: Boolean,
    digitalZoom: Float,
    digitalPan: Offset,
    sharpenStrength: Float,
    filterMode: FilterMode,
    contrast: Float,
    brightness: Float
): Bitmap {
    if (isFrozen) {
        return applyColorFilterToBitmap(raw, filterMode, contrast, brightness)
    }
    var result = raw
    if (digitalZoom > 1.0f) {
        val rect = computeVisibleCropRect(result.width, result.height, digitalZoom, digitalPan.x, digitalPan.y)
        result = Bitmap.createBitmap(result, rect.left, rect.top, rect.width(), rect.height())
        if (sharpenStrength > 0.0f) {
            result = sharpenBitmap(result, strength = sharpenStrength)
        }
    }
    return applyColorFilterToBitmap(result, filterMode, 1.0f, 0.0f)
}

// Helper to manually render combined color matrices to a saved or shared bitmap in background threads
fun applyColorFilterToBitmap(source: Bitmap, filterMode: FilterMode, contrast: Float, brightness: Float): Bitmap {
    if (filterMode == FilterMode.NORMAL && contrast == 1.0f && brightness == 0.0f) {
        return source
    }
    val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(buildFilterMatrixValues(filterMode, contrast, brightness))
    canvas.drawBitmap(source, 0f, 0f, paint)
    return resultBitmap
}

// Highly optimized custom 3x3 convolution sharpening filter in Kotlin with Adaptive Edge-Preservation
fun sharpenBitmap(src: android.graphics.Bitmap, strength: Float = 0.8f): android.graphics.Bitmap {
    val width = src.width
    val height = src.height
    if (width <= 2 || height <= 2) return src

    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(width * height)

    // Copy edge pixels as fallback
    System.arraycopy(pixels, 0, outPixels, 0, pixels.size)

    // Calculate kernel weights based on strength and convert to fixed-point (10-bit fraction, i.e., multiplied by 1024)
    val centerWeight = 1f + 4f * strength
    val neighborWeight = -strength

    val centerWeightInt = (centerWeight * 1024).toInt()
    val neighborWeightInt = (neighborWeight * 1024).toInt()

    for (y in 1 until height - 1) {
        val row = y * width
        val prevRow = row - width
        val nextRow = row + width
        for (x in 1 until width - 1) {
            val idx = row + x
            val center = pixels[idx]
            val top = pixels[prevRow + x]
            val bottom = pixels[nextRow + x]
            val left = pixels[idx - 1]
            val right = pixels[idx + 1]

            // Center channels
            val cA = (center ushr 24) and 0xFF
            val cR = (center ushr 16) and 0xFF
            val cG = (center ushr 8) and 0xFF
            val cB = center and 0xFF

            // Neighbor channels
            val tR = (top ushr 16) and 0xFF
            val tG = (top ushr 8) and 0xFF
            val tB = top and 0xFF

            val bR = (bottom ushr 16) and 0xFF
            val bG = (bottom ushr 8) and 0xFF
            val bB = bottom and 0xFF

            val lR = (left ushr 16) and 0xFF
            val lG = (left ushr 8) and 0xFF
            val lB = left and 0xFF

            val rR = (right ushr 16) and 0xFF
            val rG = (right ushr 8) and 0xFF
            val rB = right and 0xFF

            // Adaptive edge intensity calculation
            val maxR = maxOf(cR, tR, bR, lR, rR)
            val minR = minOf(cR, tR, bR, lR, rR)
            val maxG = maxOf(cG, tG, bG, lG, rG)
            val minG = minOf(cG, tG, bG, lG, rG)
            val maxB = maxOf(cB, tB, bB, lB, rB)
            val minB = minOf(cB, tB, bB, lB, rB)

            val edgeIntensity = (maxR - minR) + (maxG - minG) + (maxB - minB)

            // Dynamic interpolation factor (0 = flat/noise, 256 = distinct edge/detail)
            val k = when {
                edgeIntensity <= 24 -> 0
                edgeIntensity >= 96 -> 256
                else -> ((edgeIntensity - 24) * 256) / 72
            }

            if (k == 0) {
                outPixels[idx] = center
            } else {
                // Apply Laplacian filter with center weight and neighbor weight using fixed-point math
                var rSharp = (centerWeightInt * cR + neighborWeightInt * (tR + bR + lR + rR)) shr 10
                var gSharp = (centerWeightInt * cG + neighborWeightInt * (tG + bG + lG + rG)) shr 10
                var bSharp = (centerWeightInt * cB + neighborWeightInt * (tB + bB + lB + rB)) shr 10

                // Clamp to valid pixel range
                if (rSharp < 0) rSharp = 0 else if (rSharp > 255) rSharp = 255
                if (gSharp < 0) gSharp = 0 else if (gSharp > 255) gSharp = 255
                if (bSharp < 0) bSharp = 0 else if (bSharp > 255) bSharp = 255

                // Interpolate between original and sharpened pixel based on local edge intensity
                val r = (cR * (256 - k) + rSharp * k) ushr 8
                val g = (cG * (256 - k) + gSharp * k) ushr 8
                val b = (cB * (256 - k) + bSharp * k) ushr 8

                outPixels[idx] = (cA shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    val result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    result.setPixels(outPixels, 0, width, 0, 0, width, height)
    return result
}

