package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

// Egy cél teljes nagyítás felosztása a kamera (optikai/hibrid) és a kijelző-oldali digitális
// zoom között: ameddig a kamera zoom-tartománya elviszi, ott marad; fölötte digitális szorzó.
data class ZoomDistribution(val cameraZoom: Float, val digitalZoom: Float)

fun computeZoomDistribution(target: Float, minZoom: Float, maxZoom: Float): ZoomDistribution {
    val clamped = target.coerceAtLeast(minZoom)
    return if (clamped <= maxZoom) {
        ZoomDistribution(clamped, 1.0f)
    } else {
        ZoomDistribution(maxZoom, if (maxZoom > 0f) clamped / maxZoom else 1.0f)
    }
}

// Pan-eltolás korlátozása úgy, hogy a nagyított tartalom széle ne szakadjon el a viewport szélétől
fun clampPan(pan: Offset, scale: Float, viewport: IntSize): Offset {
    if (scale <= 1.0f) return Offset.Zero
    val maxPanX = (scale - 1.0f) * viewport.width / 2f
    val maxPanY = (scale - 1.0f) * viewport.height / 2f
    return Offset(
        x = pan.x.coerceIn(-maxPanX, maxPanX),
        y = pan.y.coerceIn(-maxPanY, maxPanY)
    )
}

