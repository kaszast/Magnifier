package com.example

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

fun hasCameraPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

suspend fun <T> awaitListenableFuture(
    future: com.google.common.util.concurrent.ListenableFuture<T>,
    context: Context
): T = suspendCoroutine { continuation ->
    future.addListener({
        try {
            continuation.resume(future.get())
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }, androidx.core.content.ContextCompat.getMainExecutor(context))
}

class CapturedJpeg(val bytes: ByteArray, val rotationDegrees: Int)

// Natív felbontású still capture JPEG byte-okként; hibánál null — a hívó a
// preview-snapshotra esik vissza, a felhasználó felé nincs külön hibaút.
suspend fun awaitCapturedJpeg(imageCapture: ImageCapture, context: Context): CapturedJpeg? =
    suspendCoroutine { continuation ->
        imageCapture.takePicture(
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        CapturedJpeg(bytes, image.imageInfo.rotationDegrees)
                    } catch (t: Throwable) {
                        Log.e("Magnifier", "Failed to read captured image", t)
                        null
                    } finally {
                        image.close()
                    }
                    continuation.resume(result)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Magnifier", "High-res capture failed", exception)
                    continuation.resume(null)
                }
            }
        )
    }

fun getOpticalSteps(context: Context, minZoom: Float, maxZoom: Float): List<Float> {
    val steps = mutableSetOf<Float>()
    
    // Always include minZoom and 1.0f (if within range)
    steps.add(minZoom)
    if (1.0f in minZoom..maxZoom) {
        steps.add(1.0f)
    }
    
    // 1. Check for emulator to avoid any camera2 service locks/crashes
    val isEmulator = Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT

    if (isEmulator) {
        listOf(2.0f, 4.0f, 8.0f).forEach {
            if (it in minZoom..maxZoom) steps.add(it)
        }
        return steps.toList().sorted()
    }

    // 2. Check for Xiaomi 15 Ultra or Xiaomi 15 series for direct perfect mapping
    val model = Build.MODEL.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isXiaomi15Ultra = model.contains("xiaomi 15 ultra") || model.contains("25010pn30") || (manufacturer.contains("xiaomi") && model.contains("15 ultra"))
    if (isXiaomi15Ultra) {
        val ultraSteps = listOf(0.6f, 1.0f, 2.0f, 4.3f, 10.0f, 30.0f, 120.0f)
        for (step in ultraSteps) {
            if (step in minZoom..maxZoom) {
                steps.add(step)
            }
        }
        return steps.toList().sorted()
    }

    // 3. Fallback to generic safe CameraCharacteristics query for other actual devices
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cameraManager.cameraIdList) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    
                    if (focalLengths != null && sensorSize != null) {
                        val diagonal = Math.sqrt((sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()).toFloat()
                        if (diagonal > 0f) {
                            for (f in focalLengths) {
                                val eqFocal = f * (43.27f / diagonal)
                                val rawZoom = eqFocal / 26.0f
                                
                                val rounded = when {
                                    rawZoom < 0.8f -> {
                                        if (abs(rawZoom - minZoom) < 0.15f) minZoom else if (rawZoom < 0.55f) 0.5f else 0.6f
                                    }
                                    rawZoom < 1.3f -> 1.0f
                                    rawZoom < 2.5f -> 2.0f
                                    rawZoom < 3.5f -> 3.0f
                                    rawZoom < 4.8f -> 4.3f
                                    rawZoom < 6.0f -> 5.0f
                                    else -> ((rawZoom * 2f).roundToInt() / 2f)
                                }
                                if (rounded in minZoom..maxZoom) {
                                    steps.add(rounded)
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("Magnifier", "Error characteristics query for camera id $id", t)
            }
        }
    } catch (t: Throwable) {
        Log.e("Magnifier", "Error querying CameraManager", t)
    }
    
    // Add default fallbacks if list is too small
    if (steps.size <= 2) {
        listOf(2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f).forEach {
            if (it in minZoom..maxZoom) steps.add(it)
        }
    }
    
    // Add some high digital/hybrid zoom milestones if maxZoom is very large (e.g. 64x+)
    if (maxZoom >= 30.0f) {
        listOf(2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f).forEach {
            if (it in minZoom..maxZoom) steps.add(it)
        }
    }
    if (maxZoom >= 100.0f) {
        if (50.0f in minZoom..maxZoom) steps.add(50.0f)
        if (100.0f in minZoom..maxZoom) steps.add(100.0f)
        steps.add(maxZoom)
    }
    
    // Sort and return as a nice list, max 7 presets to prevent cluttering the UI
    val sorted = steps.toList().sorted()
    return if (sorted.size > 7) {
        val result = mutableSetOf<Float>()
        result.add(sorted.first())
        result.add(sorted.last())
        if (1.0f in sorted) result.add(1.0f)
        
        val remaining = sorted.filter { it != sorted.first() && it != sorted.last() && it != 1.0f }
        val stepSize = max(1, remaining.size / (7 - result.size))
        var idx = 0
        while (result.size < 7 && idx < remaining.size) {
            result.add(remaining[idx])
            idx += stepSize
        }
        result.toList().sorted()
    } else {
        sorted
    }
}

