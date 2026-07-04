package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Force Dark Mode for superior low-glare magnifier experience
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MagnifierApp()
                }
            }
        }
    }
}

enum class FilterMode(val displayName: String, val description: String) {
    NORMAL("Normál", "Valósághű színek"),
    MONOCHROME("Fekete-Fehér", "Megnövelt szövegolvashatóság"),
    INVERTED("Negatív", "Sötét háttér a szem kíméléséért"),
    YELLOW("Sárga-Fekete", "Gyengénlátóknak ideális kontraszt"),
    RED("Vörös", "Éjszakai látásmegőrzés")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MagnifierApp() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        MagnifierMainScreen()
    } else {
        PermissionRequiredScreen(onRequestPermission = {
            cameraPermissionState.launchPermissionRequest()
        })
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Slate 900
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF1E293B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Icon",
                    tint = Color(0xFFFBBF24), // Amber 400
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Kamera hozzáférés szükséges",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "A nagyító alkalmazás a készülék kameráját használja a kép felnagyításához. Kérjük, engedélyezze a kamera használatát.",
                color = Color(0xFF94A3B8), // Slate 400
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFBBF24), // Amber 400
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("request_permission_button")
            ) {
                Text(
                    text = "Engedélyezés",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun MagnifierMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Camera setup states
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Live preview view persistent object
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // Crucial for getBitmap() to work 100% of the time!
        }
    }

    // Interactive states
    var liveZoomRatio by remember { mutableStateOf(1.0f) }
    var minZoom by remember { mutableStateOf(1.0f) }
    var maxZoom by remember { mutableStateOf(8.0f) }

    var torchEnabled by remember { mutableStateOf(false) }

    // Exposure (live brightness/contrast adjustment)
    var exposureIndex by remember { mutableStateOf(0) }
    var minExposureIndex by remember { mutableStateOf(-4) }
    var maxExposureIndex by remember { mutableStateOf(4) }

    // Freeze Frame state
    var isFrozen by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Image enhancements (primarily applied on frozen frame for visual aid)
    var contrast by remember { mutableStateOf(1.0f) } // 1.0f (Normal) to 3.0f (High Contrast)
    var brightness by remember { mutableStateOf(0.0f) } // -100f to 100f
    var filterMode by remember { mutableStateOf(FilterMode.NORMAL) }

    // Digital Zoom/Pan states for frozen image
    var frozenScale by remember { mutableStateOf(1.0f) }
    var frozenOffset by remember { mutableStateOf(Offset.Zero) }

    // UI overlays / status
    var activeTab by remember { mutableStateOf(0) } // 0: Nagyítás, 1: Szűrők, 2: Képkorrekció
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showSavedToast by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Tap-to-focus animation feedback
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTrigger by remember { mutableStateOf(0) }

    // Helper to calculate combined color filter matrix
    val combinedColorFilter = remember(filterMode, contrast, brightness) {
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
                matrix.set(ColorMatrix(floatArrayOf(
                    0.2126f * 1.6f, 0.7152f * 1.6f, 0.0722f * 1.6f, 0f, 0f,
                    0.2126f * 1.6f, 0.7152f * 1.6f, 0.0722f * 1.6f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterMode.RED -> {
                matrix.set(ColorMatrix(floatArrayOf(
                    0.2126f * 1.8f, 0.7152f * 1.8f, 0.0722f * 1.8f, 0f, 0f,
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
        values[4] = values[4] * contrast + brightness
        values[9] = values[9] * contrast + brightness
        values[14] = values[14] * contrast + brightness

        ColorFilter.colorMatrix(ColorMatrix(values))
    }

    // Dismiss custom toast message
    LaunchedEffect(showSavedToast) {
        if (showSavedToast) {
            delay(3000)
            showSavedToast = false
        }
    }

    // Dismiss focus circle animation
    LaunchedEffect(focusTrigger) {
        if (focusPoint != null) {
            delay(1200)
            focusPoint = null
        }
    }

    // Reactive bindings to camera parameters
    LaunchedEffect(liveZoomRatio) {
        camera?.cameraControl?.setZoomRatio(liveZoomRatio)
    }

    LaunchedEffect(torchEnabled) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(exposureIndex) {
        camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
    }

    // Bind camera lifecycle
    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        
        val localImageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = localImageCapture

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            val cameraInstance = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                localImageCapture
            )
            camera = cameraInstance
            
            // Observe live zoom capabilities
            cameraInstance.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                minZoom = zoomState.minZoomRatio
                maxZoom = zoomState.maxZoomRatio
                // Safely update liveZoomRatio if out of bounds
                if (liveZoomRatio < minZoom) liveZoomRatio = minZoom
                if (liveZoomRatio > maxZoom) liveZoomRatio = maxZoom
            }

            // Observe exposure capabilities
            val exposureState = cameraInstance.cameraInfo.exposureState
            minExposureIndex = exposureState.exposureCompensationRange.lower
            maxExposureIndex = exposureState.exposureCompensationRange.upper
            exposureIndex = exposureState.exposureCompensationIndex
        } catch (exc: Exception) {
            Log.e("Magnifier", "Use case binding failed", exc)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF09090B), // Space Slate background for ultimate negative space
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF09090B))
                    .navigationBarsPadding()
            ) {
                // Controls Panel: Sliders & Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sliders depending on Active Tab
                    when (activeTab) {
                        0 -> { // ZOOM CONTROLS
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "NAGYÍTÁS MÉRTÉKE",
                                        color = Color(0xFFB180FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    val currentDisplayVal = if (isFrozen) frozenScale else liveZoomRatio
                                    Text(
                                        text = String.format("%.1fx", currentDisplayVal),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF1B1A21), CircleShape)
                                            .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                            .clickable {
                                                if (isFrozen) {
                                                    frozenScale = max(1.0f, frozenScale - 0.5f)
                                                } else {
                                                    liveZoomRatio = max(minZoom, liveZoomRatio - 0.5f)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Csökkentés",
                                            tint = Color(0xFFE6E1E5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Slider(
                                        value = if (isFrozen) frozenScale else liveZoomRatio,
                                        onValueChange = { newValue ->
                                            if (isFrozen) {
                                                frozenScale = newValue
                                            } else {
                                                liveZoomRatio = newValue.coerceIn(minZoom, maxZoom)
                                            }
                                        },
                                        valueRange = if (isFrozen) 1.0f..8.0f else minZoom..maxZoom,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFFB180FF),
                                            thumbColor = Color(0xFFB180FF),
                                            inactiveTrackColor = Color(0xFF1B1A21)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("zoom_slider")
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF1B1A21), CircleShape)
                                            .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                            .clickable {
                                                if (isFrozen) {
                                                    frozenScale = min(8.0f, frozenScale + 0.5f)
                                                } else {
                                                    liveZoomRatio = min(maxZoom, liveZoomRatio + 0.5f)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Növelés",
                                            tint = Color(0xFFE6E1E5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Quick Zoom Presets
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val presets = listOf(1.0f, 2.0f, 4.0f, 8.0f)
                                    presets.forEach { preset ->
                                        val isSelected = if (isFrozen) frozenScale == preset else liveZoomRatio == preset
                                        OutlinedButton(
                                            onClick = {
                                                if (isFrozen) {
                                                    frozenScale = preset
                                                } else {
                                                    liveZoomRatio = preset.coerceIn(minZoom, maxZoom)
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (isSelected) Color.Black else Color(0xFFE6E1E5),
                                                containerColor = if (isSelected) Color(0xFFB180FF) else Color.Transparent
                                            ),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFB180FF) else Color(0xFF2E2C33)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                        ) {
                                            Text(text = "${preset.toInt()}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // VISIBILITY FILTERS
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "OLVASÁSI SEGÉDSZŰRŐK",
                                    color = Color(0xFFB180FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(FilterMode.values()) { mode ->
                                        val selected = filterMode == mode
                                        Card(
                                            onClick = { filterMode = mode },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selected) Color(0xFF1E1C24) else Color(0xFF111115)
                                            ),
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (selected) Color(0xFFB180FF) else Color(0xFF2E2C33)
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .width(115.dp)
                                                .height(68.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(
                                                                when (mode) {
                                                                    FilterMode.NORMAL -> Color.White
                                                                    FilterMode.MONOCHROME -> Color.Gray
                                                                    FilterMode.INVERTED -> Color.Cyan
                                                                    FilterMode.YELLOW -> Color.Yellow
                                                                    FilterMode.RED -> Color.Red
                                                                },
                                                                CircleShape
                                                            )
                                                    )
                                                    Text(
                                                        text = mode.displayName,
                                                        color = if (selected) Color.White else Color(0xFFE6E1E5),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                                
                                                Text(
                                                    text = when (mode) {
                                                        FilterMode.NORMAL -> "Valósághű"
                                                        FilterMode.MONOCHROME -> "Szürkeárnyalat"
                                                        FilterMode.INVERTED -> "Sötét negatív"
                                                        FilterMode.YELLOW -> "Sárga kontraszt"
                                                        FilterMode.RED -> "Vörös éjszakai"
                                                    },
                                                    color = if (selected) Color(0xFFB180FF) else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // IMAGE CORRECTIONS
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isFrozen) "KÉPKONTRASZT KORREKCIÓ" else "KAMERA EXPOZÍCIÓ (FÉNYERŐ)",
                                        color = Color(0xFFB180FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = if (isFrozen) String.format("%.1fx", contrast) else "$exposureIndex EV",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Contrast,
                                        contentDescription = "Contrast icon",
                                        tint = Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Slider(
                                        value = if (isFrozen) contrast else exposureIndex.toFloat(),
                                        onValueChange = { newValue ->
                                            if (isFrozen) {
                                                contrast = newValue
                                            } else {
                                                exposureIndex = newValue.roundToInt()
                                            }
                                        },
                                        valueRange = if (isFrozen) 1.0f..3.0f else minExposureIndex.toFloat()..maxExposureIndex.toFloat(),
                                        steps = if (!isFrozen && (maxExposureIndex - minExposureIndex > 0)) maxExposureIndex - minExposureIndex - 1 else 0,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFFB180FF),
                                            thumbColor = Color(0xFFB180FF),
                                            inactiveTrackColor = Color(0xFF1B1A21)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                AnimatedVisibility(visible = isFrozen) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "DIGITÁLIS FÉNYERŐ",
                                                color = Color(0xFFB180FF),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = String.format("%+d", brightness.roundToInt()),
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = "Brightness icon",
                                                tint = Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Slider(
                                                value = brightness,
                                                onValueChange = { brightness = it },
                                                valueRange = -80f..80f,
                                                colors = SliderDefaults.colors(
                                                    activeTrackColor = Color(0xFFB180FF),
                                                    thumbColor = Color(0xFFB180FF),
                                                    inactiveTrackColor = Color(0xFF1B1A21)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Main Action Buttons Row (Flashlight, Save, Freeze, Share)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Flashlight Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { torchEnabled = !torchEnabled }
                                .testTag("torch_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (torchEnabled) Color(0xFFB180FF) else Color(0xFF1B1A21),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (torchEnabled) Color(0xFFB180FF) else Color(0xFF2E2C33),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Zseblámpa",
                                    tint = if (torchEnabled) Color.Black else Color(0xFFE6E1E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Lámpa",
                                color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // 2. Save Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable {
                                    isProcessing = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val bitmapToSave = if (isFrozen && frozenBitmap != null) {
                                            applyColorFilterToBitmap(frozenBitmap!!, combinedColorFilter)
                                        } else {
                                            previewView.bitmap
                                        }

                                        if (bitmapToSave != null) {
                                            val savedUri = saveBitmapToGallery(context, bitmapToSave)
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                if (savedUri != null) {
                                                    statusMessage = "Kép elmentve a Galériába!"
                                                    showSavedToast = true
                                                } else {
                                                    Toast.makeText(context, "Sikertelen mentés", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                Toast.makeText(context, "Nincs menthető kép", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .testTag("save_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF1B1A21), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Mentés",
                                    tint = Color(0xFFE6E1E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Mentés",
                                color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // 3. Freeze/Resume Shutter Button (Dual-ring camera style)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(2.dp, if (isFrozen) Color(0xFFEF4444) else Color(0xFFB180FF), CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isFrozen) Color(0xFFEF4444) else Color(0xFFB180FF),
                                        CircleShape
                                    )
                                    .clickable {
                                        if (isFrozen) {
                                            isFrozen = false
                                            frozenBitmap = null
                                            frozenScale = 1.0f
                                            frozenOffset = Offset.Zero
                                        } else {
                                            isProcessing = true
                                            val bmp = previewView.bitmap
                                            if (bmp != null) {
                                                frozenBitmap = bmp
                                                isFrozen = true
                                            } else {
                                                Toast.makeText(context, "Nem sikerült kimerevíteni", Toast.LENGTH_SHORT).show()
                                            }
                                            isProcessing = false
                                        }
                                    }
                                    .testTag("freeze_toggle_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isFrozen) "Folytatás" else "Kimerevítés",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 4. Share Button (Glass Tile)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable {
                                    isProcessing = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val bitmapToShare = if (isFrozen && frozenBitmap != null) {
                                            applyColorFilterToBitmap(frozenBitmap!!, combinedColorFilter)
                                        } else {
                                            previewView.bitmap
                                        }

                                        if (bitmapToShare != null) {
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                shareBitmap(context, bitmapToShare)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                Toast.makeText(context, "Nincs megosztható kép", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .testTag("share_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF1B1A21), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Megosztás",
                                    tint = Color(0xFFE6E1E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Megosztás",
                                color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Compact Navigation Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .height(44.dp)
                        .background(Color(0xFF131217), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF23222A), RoundedCornerShape(14.dp)),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Pair(Icons.Default.ZoomIn, "NAGYÍTÁS"),
                        Pair(Icons.Default.ColorLens, "SZŰRŐK"),
                        Pair(Icons.Default.Tune, "KORREKCIÓ")
                    )
                    
                    tabs.forEachIndexed { index, (icon, label) ->
                        val selected = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFF25212E) else Color.Transparent)
                                .clickable { activeTab = index }
                                .testTag("tab_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) Color(0xFFB180FF) else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF09090B))
        ) {
            // Viewfinder Area - maximized with slim margin and elegant rounded corners
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                // Main Viewport: Camera Live View OR Frozen Static Bitmap View
                if (!isFrozen) {
                    // LIVE MODE CAMERA
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) {
                                        liveZoomRatio = (liveZoomRatio * zoom).coerceIn(minZoom, maxZoom)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    focusPoint = offset
                                    focusTrigger++

                                    // Perform tap-to-focus on CameraX
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(offset.x, offset.y)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    camera?.cameraControl?.startFocusAndMetering(action)
                                }
                            }
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlay to apply live reading aid filter dynamically
                        if (filterMode == FilterMode.INVERTED) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.White,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Difference
                                )
                            }
                        } else if (filterMode == FilterMode.MONOCHROME) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.Gray,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                )
                            }
                        } else if (filterMode == FilterMode.YELLOW) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color(0xFFFBBF24),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                )
                            }
                        } else if (filterMode == FilterMode.RED) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.Red,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                )
                            }
                        }
                    }
                } else {
                    // FROZEN IMAGE VIEW
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    frozenScale = (frozenScale * zoom).coerceIn(1.0f, 10.0f)
                                    frozenOffset += pan
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        frozenScale = if (frozenScale > 1.0f) 1.0f else 3.0f
                                        frozenOffset = Offset.Zero
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        frozenBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Frozen frame magnifier review",
                                contentScale = ContentScale.Fit,
                                colorFilter = combinedColorFilter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = frozenScale
                                        scaleY = frozenScale
                                        translationX = frozenOffset.x
                                        translationY = frozenOffset.y
                                    }
                            )
                        }
                    }
                }
            }

            // Animated Tap-to-Focus Indicator ring
            focusPoint?.let { point ->
                val density = LocalDensity.current
                val offsetInDp = with(density) {
                    IntOffset(
                        (point.x - 40).roundToInt(),
                        (point.y - 40).roundToInt()
                    )
                }

                Box(
                    modifier = Modifier
                        .absoluteOffset { offsetInDp }
                        .size(80.dp)
                        .border(1.5.dp, Color(0xFFD0BCFF), CircleShape)
                        .background(Color(0xFFD0BCFF).copy(alpha = 0.1f), CircleShape)
                )
            }

            // Real-time custom toast overlay
            AnimatedVisibility(
                visible = showSavedToast,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage ?: "Sikeres művelet!",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Spinner loader overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFD0BCFF))
                }
            }
        }
    }
}

// Helper to manually render combined color matrices to a saved or shared bitmap in background threads
fun applyColorFilterToBitmap(source: Bitmap, colorFilter: ColorFilter): Bitmap {
    val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()
    
    // Convert Compose ColorFilter back to Android's native Graphics ColorFilter
    // Since we are using standard ColorMatrixColorFilter, we can construct one manually:
    // To ensure full thread safety and perfect compatibility, we get the matrix values and apply them.
    paint.colorFilter = colorFilter.asAndroidColorFilter()
    canvas.drawBitmap(source, 0f, 0f, paint)
    return resultBitmap
}

// Convert Jetpack Compose ColorFilter to Android Native ColorFilter
fun ColorFilter.asAndroidColorFilter(): android.graphics.ColorFilter {
    // Jetpack Compose provides custom Native ColorFilter extraction based on reflection or utility,
    // but the cleanest, standard SDK path is to manually read back matrix values or use standard mapping:
    // Here we can use standard casting as Compose's ColorFilter delegates to Native ColorFilter:
    return try {
        val field = this.javaClass.getDeclaredField("nativeColorFilter")
        field.isAccessible = true
        field.get(this) as android.graphics.ColorFilter
    } catch (e: Exception) {
        // Fallback: If reflection fails, we can reconstruct the android.graphics.ColorMatrixColorFilter
        android.graphics.ColorMatrixColorFilter(FloatArray(20).apply { this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f })
    }
}

// Helper to save Bitmap to the Device's Public/Scoped Gallery
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val filename = "Nagyito_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nagyító")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            Log.e("Magnifier", "Sikertelen kép mentés", e)
            resolver.delete(imageUri, null, null)
        }
    }
    return null
}

// Helper to share Captured Bitmap via standard Android ACTION_SEND Intent and FileProvider URI
fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "shared_images")
        cachePath.mkdirs()
        
        // Clean old cached shared files to save user disk space
        cachePath.listFiles()?.forEach { it.delete() }
        
        val file = File(cachePath, "magnifier_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/jpeg"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Fénykép megosztása"))
        }
    } catch (e: Exception) {
        Log.e("Magnifier", "Sikertelen kép megosztás", e)
    }
}
