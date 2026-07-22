package com.example.domain.camera

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.core.content.ContextCompat
import com.example.applyFocusSettings

/**
 * Kamera fókuszálást és haptikus rezgési visszajelzést kezelő modul.
 */
object CameraFocusHandler {

    fun updateFocus(
        camera: Camera?,
        focusMode: String,
        manualFocusDistance: Float,
        context: Context,
        view: View
    ) {
        val cam = camera ?: return
        applyFocusSettings(cam, focusMode, manualFocusDistance)

        when (focusMode) {
            "auto" -> triggerAutoFocusWithHaptic(cam, context, view)
            "manual" -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun triggerAutoFocusWithHaptic(
        camera: Camera,
        context: Context,
        view: View
    ) {
        try {
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val point = factory.createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
            val future = camera.cameraControl.startFocusAndMetering(action)

            future.addListener({
                try {
                    val result = future.get()
                    if (result.isFocusSuccessful) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                } catch (e: Exception) {
                    // Ignoráljuk a fókusz mérés megszakításának kivételét
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("CameraFocusHandler", "Nem sikerült elindítani az autofókuszt", e)
        }
    }
}
