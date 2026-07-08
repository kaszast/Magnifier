package com.example

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
