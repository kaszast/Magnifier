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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs
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

fun hasCameraPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

data class AppThemeColor(val name: String, val color: Color)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MagnifierApp() {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var isPermissionGranted by remember {
        mutableStateOf(hasCameraPermission(context))
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        isPermissionGranted = hasCameraPermission(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isPermissionGranted) {
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
                    .size(120.dp)
                    .background(Color(0xFF1E293B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color(0xFFFBBF24), // Amber 400
                    modifier = Modifier.size(56.dp)
                )
                // Lock overlay at top-right indicating permission is needed/locked
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                        .align(Alignment.TopEnd)
                        .border(2.dp, Color(0xFF0F172A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Permission locked",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981), // Emerald Green for "Allow/Grant"
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("request_permission_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Grant Permission",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
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

    val themeOptions = remember {
        listOf(
            AppThemeColor("Lila", Color(0xFFB180FF)),
            AppThemeColor("Zöld", Color(0xFF00FF87)),
            AppThemeColor("Arany", Color(0xFFFFB300)),
            AppThemeColor("Kék", Color(0xFF00D2FF)),
            AppThemeColor("Narancs", Color(0xFFFF6B00))
        )
    }
    var currentThemeIndex by remember { mutableStateOf(0) }
    val themeColor = themeOptions[currentThemeIndex].color

    val customLifecycleOwner = remember {
        object : androidx.lifecycle.LifecycleOwner {
            private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle = lifecycleRegistry
            
            init {
                lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.INITIALIZED
            }
            
            fun setCurrentState(state: androidx.lifecycle.Lifecycle.State) {
                lifecycleRegistry.currentState = state
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_CREATE -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED)
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED)
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.RESUMED)
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED)
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED)
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.DESTROYED)
                }
                else -> {}
            }
        }
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.RESUMED)
        } else if (currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
            customLifecycleOwner.setCurrentState(androidx.lifecycle.Lifecycle.State.CREATED)
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    // Multi-camera states
    var availableCameras by remember { mutableStateOf<List<androidx.camera.core.CameraInfo>>(emptyList()) }
    var selectedCameraIndex by remember { mutableStateOf(0) }

    // Additional software digital zoom and pan states for active camera preview
    var extraDigitalZoom by remember { mutableStateOf(1.0f) }
    var extraDigitalPan by remember { mutableStateOf(Offset.Zero) }

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
    var toastIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector>(Icons.Default.CheckCircle) }
    var toastSubIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var toastColor by remember { mutableStateOf(Color(0xFF10B981)) }
    var showSavedToast by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Viewfinder and layout states
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var liveThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Grab thumbnail dynamically when digital zoom is active in live mode
    LaunchedEffect(extraDigitalZoom > 1.0f) {
        if (extraDigitalZoom > 1.0f) {
            while (true) {
                try {
                    val bmp = previewView.bitmap
                    if (bmp != null) {
                        liveThumbnailBitmap = bmp
                    }
                } catch (e: Throwable) {
                    // ignore errors fetching bitmap
                }
                delay(80)
            }
        } else {
            liveThumbnailBitmap = null
        }
    }

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
    LaunchedEffect(cameraProviderFuture, selectedCameraIndex) {
        if (!hasCameraPermission(context)) return@LaunchedEffect
        val cameraProvider = cameraProviderFuture.get()
        val cameraInfos = cameraProvider.availableCameraInfos
        availableCameras = cameraInfos
        
        if (cameraInfos.isEmpty()) return@LaunchedEffect
 
        // Ensure selectedCameraIndex is within bounds
        val index = selectedCameraIndex.coerceIn(0, cameraInfos.lastIndex)
        val selectedCameraInfo = cameraInfos[index]
 
        // Reset torch state when swapping cameras
        torchEnabled = false
 
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        
        val localImageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = localImageCapture
 
        // Create selector that targets this specific physical camera
        val cameraSelector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { it == selectedCameraInfo }
            }
            .build()
 
        try {
            cameraProvider.unbindAll()
            val cameraInstance = cameraProvider.bindToLifecycle(
                customLifecycleOwner,
                cameraSelector,
                preview,
                localImageCapture
            )
            camera = cameraInstance
            
            // Observe live zoom capabilities
            cameraInstance.cameraInfo.zoomState.observe(customLifecycleOwner) { zoomState ->
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
        containerColor = Color(0xFF09090B)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B))
        ) {
            // Viewfinder Area - full screen edge-to-edge for absolute maximum viewport size
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { viewportSize = it }
            ) {
                // Main Viewport: Camera Live View OR Frozen Static Bitmap View
                if (!isFrozen) {
                    // LIVE MODE CAMERA
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (zoom != 1f) {
                                        // Seamless unified zoom logic
                                        val currentZoom = liveZoomRatio * extraDigitalZoom
                                        val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom * 6.0f)
                                        if (newZoom <= maxZoom) {
                                            liveZoomRatio = newZoom
                                            extraDigitalZoom = 1.0f
                                            extraDigitalPan = Offset.Zero
                                        } else {
                                            liveZoomRatio = maxZoom
                                            extraDigitalZoom = newZoom / maxZoom
                                        }
                                    }
                                    
                                    // Handle panning/dragging when digitally zoomed in
                                    if (extraDigitalZoom > 1.0f) {
                                        extraDigitalPan += pan
                                        // Restrict pan bounds based on digital zoom level
                                        val maxPanX = (extraDigitalZoom - 1.0f) * 500f
                                        val maxPanY = (extraDigitalZoom - 1.0f) * 800f
                                        extraDigitalPan = Offset(
                                            x = extraDigitalPan.x.coerceIn(-maxPanX, maxPanX),
                                            y = extraDigitalPan.y.coerceIn(-maxPanY, maxPanY)
                                        )
                                    } else {
                                        extraDigitalPan = Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (extraDigitalZoom > 1.0f || liveZoomRatio > minZoom) {
                                            extraDigitalZoom = 1.0f
                                            extraDigitalPan = Offset.Zero
                                            liveZoomRatio = minZoom
                                        } else {
                                            val targetZoom = (minZoom + maxZoom) / 2.0f
                                            if (targetZoom <= maxZoom) {
                                                liveZoomRatio = targetZoom
                                                extraDigitalZoom = 1.0f
                                            } else {
                                                liveZoomRatio = maxZoom
                                                extraDigitalZoom = targetZoom / maxZoom
                                            }
                                            extraDigitalPan = Offset.Zero
                                        }
                                    },
                                    onTap = { offset ->
                                        focusPoint = offset
                                        focusTrigger++

                                        // Perform tap-to-focus on CameraX with zoom correction
                                        val factory = previewView.meteringPointFactory
                                        val correctedX = (offset.x - extraDigitalPan.x) / extraDigitalZoom
                                        val correctedY = (offset.y - extraDigitalPan.y) / extraDigitalZoom
                                        val point = factory.createPoint(correctedX, correctedY)
                                        val action = FocusMeteringAction.Builder(point).build()
                                        camera?.cameraControl?.startFocusAndMetering(action)
                                    }
                                )
                            }
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = extraDigitalZoom
                                    scaleY = extraDigitalZoom
                                    translationX = extraDigitalPan.x
                                    translationY = extraDigitalPan.y
                                }
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

                // Elegant Picture-in-Picture Viewfinder (Minimap) for Digital Zoom
                val isDigitalZoomActive = if (isFrozen) frozenScale > 1.0f else extraDigitalZoom > 1.0f
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDigitalZoomActive,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xE60D0C11)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier
                            .width(110.dp)
                            .height(170.dp)
                            .border(1.5.dp, themeColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 1. Render the un-zoomed source (either frozen bitmap or live preview periodic thumbnail)
                            if (isFrozen) {
                                frozenBitmap?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Frozen Full Image",
                                        contentScale = ContentScale.FillBounds,
                                        colorFilter = combinedColorFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                liveThumbnailBitmap?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Live Full Image Preview",
                                        contentScale = ContentScale.FillBounds,
                                        colorFilter = combinedColorFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = themeColor,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            // 2. Overlay view bounds highlighted box with custom drawing canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasW = size.width
                                val canvasH = size.height

                                val scale = if (isFrozen) frozenScale else extraDigitalZoom
                                val pan = if (isFrozen) frozenOffset else extraDigitalPan

                                val wWidth = viewportSize.width.toFloat().coerceAtLeast(1f)
                                val wHeight = viewportSize.height.toFloat().coerceAtLeast(1f)

                                val boxWidthFraction = 1f / scale
                                val boxHeightFraction = 1f / scale

                                val centerXFraction = 0.5f - (pan.x / (scale * wWidth))
                                val centerYFraction = 0.5f - (pan.y / (scale * wHeight))

                                val rectW = canvasW * boxWidthFraction
                                val rectH = canvasH * boxHeightFraction

                                val rectX = ((canvasW * centerXFraction) - (rectW / 2f)).coerceIn(0f, canvasW - rectW)
                                val rectY = ((canvasH * centerYFraction) - (rectH / 2f)).coerceIn(0f, canvasH - rectH)

                                // Draw dimmed non-visible outer region
                                // Top
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    topLeft = Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(canvasW, rectY)
                                )
                                // Bottom
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    topLeft = Offset(0f, rectY + rectH),
                                    size = androidx.compose.ui.geometry.Size(canvasW, canvasH - (rectY + rectH))
                                )
                                // Left
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    topLeft = Offset(0f, rectY),
                                    size = androidx.compose.ui.geometry.Size(rectX, rectH)
                                )
                                // Right
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    topLeft = Offset(rectX + rectW, rectY),
                                    size = androidx.compose.ui.geometry.Size(canvasW - (rectX + rectW), rectH)
                                )

                                // Draw glowing neon viewport border
                                drawRect(
                                    color = themeColor,
                                    topLeft = Offset(rectX, rectY),
                                    size = androidx.compose.ui.geometry.Size(rectW, rectH),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Row on the left containing the Full Screen (visibility) toggle and the Camera Swap button in a separated area
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp, start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Controls Visibility Toggle Button (Full Screen)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF09090B).copy(alpha = 0.75f), CircleShape)
                            .border(1.5.dp, themeColor.copy(alpha = 0.6f), CircleShape)
                            .clickable { controlsVisible = !controlsVisible }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (controlsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Kezelőszervek",
                            tint = themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Floating Camera Indicator and Swap Button (in a separated box)
                    if (!isFrozen && availableCameras.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF09090B).copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                                .border(1.5.dp, themeColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .clickable {
                                    if (availableCameras.isNotEmpty()) {
                                        selectedCameraIndex = (selectedCameraIndex + 1) % availableCameras.size
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwitchCamera,
                                    contentDescription = "Kamera váltás",
                                    tint = themeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                
                                val activeCameraInfo = availableCameras.getOrNull(selectedCameraIndex)
                                val cameraIcon = if (activeCameraInfo != null) {
                                    when (activeCameraInfo.lensFacing) {
                                        0 -> Icons.Default.Person // selfie/front
                                        1 -> Icons.Default.PhotoCamera // back
                                        else -> Icons.Default.Videocam // external
                                    }
                                } else {
                                    Icons.Default.PhotoCamera
                                }
                                
                                Icon(
                                    imageVector = cameraIcon,
                                    contentDescription = "Aktív kamera",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sleek, semi-transparent frosted card container at the bottom with animations
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                        .background(Color(0xE60D0C11), RoundedCornerShape(28.dp))
                        .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Controls content depending on Active Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        when (activeTab) {
                            0 -> { // ZOOM CONTROLS (UNIFIED SINGLE SLIDER)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val currentTotalZoom = if (isFrozen) frozenScale else (liveZoomRatio * extraDigitalZoom)
                                    val isDigitalRange = !isFrozen && (liveZoomRatio * extraDigitalZoom > maxZoom)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ZoomIn,
                                                contentDescription = "Nagyítás",
                                                tint = themeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            if (isDigitalRange) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Memory,
                                                        contentDescription = "Szoftveres",
                                                        tint = Color(0xFFD0BCFF),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Text(
                                            text = String.format("%.1fx", currentTotalZoom),
                                            color = Color.White,
                                            fontSize = 15.sp,
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
                                                .size(48.dp)
                                                .background(Color(0xFF1B1A21), CircleShape)
                                                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                                .clickable {
                                                    if (isFrozen) {
                                                        frozenScale = max(1.0f, frozenScale - 0.5f)
                                                    } else {
                                                        val targetZoom = max(minZoom, (liveZoomRatio * extraDigitalZoom) - 0.5f)
                                                        if (targetZoom <= maxZoom) {
                                                            liveZoomRatio = targetZoom
                                                            extraDigitalZoom = 1.0f
                                                            extraDigitalPan = Offset.Zero
                                                        } else {
                                                            liveZoomRatio = maxZoom
                                                            extraDigitalZoom = targetZoom / maxZoom
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Csökkentés",
                                                tint = Color(0xFFE6E1E5),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Slider(
                                            value = currentTotalZoom,
                                            onValueChange = { newValue ->
                                                if (isFrozen) {
                                                    frozenScale = newValue
                                                } else {
                                                    if (newValue <= maxZoom) {
                                                        liveZoomRatio = newValue
                                                        extraDigitalZoom = 1.0f
                                                        extraDigitalPan = Offset.Zero
                                                    } else {
                                                        liveZoomRatio = maxZoom
                                                        extraDigitalZoom = newValue / maxZoom
                                                    }
                                                }
                                            },
                                            valueRange = if (isFrozen) 1.0f..10.0f else minZoom..(maxZoom * 6.0f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = themeColor,
                                                thumbColor = themeColor,
                                                inactiveTrackColor = Color(0xFF1B1A21)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("zoom_slider")
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0xFF1B1A21), CircleShape)
                                                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                                .clickable {
                                                    if (isFrozen) {
                                                        frozenScale = min(10.0f, frozenScale + 0.5f)
                                                    } else {
                                                        val targetZoom = min(maxZoom * 6.0f, (liveZoomRatio * extraDigitalZoom) + 0.5f)
                                                        if (targetZoom <= maxZoom) {
                                                            liveZoomRatio = targetZoom
                                                            extraDigitalZoom = 1.0f
                                                        } else {
                                                            liveZoomRatio = maxZoom
                                                            extraDigitalZoom = targetZoom / maxZoom
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Növelés",
                                                tint = Color(0xFFE6E1E5),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Quick Presets
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val presets = listOf(1.0f, 2.0f, 4.0f, 8.0f, 12.0f, 16.0f)
                                        presets.forEach { preset ->
                                            val isSelected = if (isFrozen) {
                                                abs(frozenScale - preset) < 0.15f
                                            } else {
                                                abs((liveZoomRatio * extraDigitalZoom) - preset) < 0.15f
                                            }
                                            
                                            val isPresetPossible = isFrozen || preset <= (maxZoom * 6.0f)
                                            if (isPresetPossible) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .background(
                                                            if (isSelected) themeColor else Color(0xFF1B1A21),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .border(1.dp, if (isSelected) themeColor else Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            if (isFrozen) {
                                                                frozenScale = preset
                                                            } else {
                                                                if (preset <= maxZoom) {
                                                                    liveZoomRatio = preset.coerceIn(minZoom, maxZoom)
                                                                    extraDigitalZoom = 1.0f
                                                                    extraDigitalPan = Offset.Zero
                                                                } else {
                                                                    liveZoomRatio = maxZoom
                                                                    extraDigitalZoom = preset / maxZoom
                                                                    extraDigitalPan = Offset.Zero
                                                                }
                                                            }
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = String.format("%.0fx", preset),
                                                        color = if (isSelected) Color.Black else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> { // FILTERS TAB (COMPACT SINGLE ROW PILLS)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ColorLens,
                                            contentDescription = "Szűrők",
                                            tint = themeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterMode.values().forEach { mode ->
                                            val selected = filterMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 52.dp)
                                                    .background(
                                                        if (selected) Color(0xFF231D30) else Color(0xFF111115),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (selected) themeColor else Color(0xFF2E2C33),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable { filterMode = mode }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(
                                                            brush = when (mode) {
                                                                FilterMode.NORMAL -> {
                                                                    Brush.sweepGradient(
                                                                        colors = listOf(
                                                                            Color(0xFFFF007F), Color(0xFFFF0000), Color(0xFFFF7F00),
                                                                            Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
                                                                            Color(0xFF0000FF), Color(0xFF7F00FF), Color(0xFFFF007F)
                                                                        )
                                                                    )
                                                                }
                                                                FilterMode.MONOCHROME -> {
                                                                    Brush.linearGradient(
                                                                        colors = listOf(Color.White, Color.Black)
                                                                    )
                                                                }
                                                                FilterMode.INVERTED -> {
                                                                    Brush.linearGradient(
                                                                        colors = listOf(Color.Cyan, Color.Black)
                                                                    )
                                                                }
                                                                FilterMode.YELLOW -> {
                                                                    Brush.linearGradient(
                                                                        colors = listOf(Color(0xFFFBBF24), Color.Black)
                                                                    )
                                                                }
                                                                FilterMode.RED -> {
                                                                    Brush.linearGradient(
                                                                        colors = listOf(Color.Red, Color.Black)
                                                                    )
                                                                }
                                                            },
                                                            shape = CircleShape
                                                        )
                                                        .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.5f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (selected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Kiválasztva",
                                                            tint = if (mode == FilterMode.MONOCHROME) Color.Black else Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> { // TUNE/CORRECTION TAB (COMPACT SLIDERS)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                                            contentDescription = "Korrekció",
                                            tint = themeColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isFrozen) String.format("%.1fx", contrast) else "$exposureIndex EV",
                                            color = Color.White,
                                            fontSize = 12.sp,
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
                                            modifier = Modifier.size(16.dp)
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
                                                activeTrackColor = themeColor,
                                                thumbColor = themeColor,
                                                inactiveTrackColor = Color(0xFF1B1A21)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (isFrozen) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = "Fényerő",
                                                tint = themeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = String.format("%+d", brightness.roundToInt()),
                                                color = Color.White,
                                                fontSize = 12.sp,
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
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Slider(
                                                value = brightness,
                                                onValueChange = { brightness = it },
                                                valueRange = -80f..80f,
                                                colors = SliderDefaults.colors(
                                                    activeTrackColor = themeColor,
                                                    thumbColor = themeColor,
                                                    inactiveTrackColor = Color(0xFF1B1A21)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                            3 -> { // THEMES TAB
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = "Téma",
                                            tint = themeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        themeOptions.forEachIndexed { index, option ->
                                            val selected = currentThemeIndex == index
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 52.dp)
                                                    .background(
                                                        if (selected) option.color.copy(alpha = 0.15f) else Color(0xFF111115),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (selected) option.color else Color(0xFF2E2C33),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable { currentThemeIndex = index }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(option.color, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (selected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Kiválasztva",
                                                            tint = Color.Black,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Compact divider line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF2E2C33).copy(alpha = 0.35f))
                    )

                    // 3. Main Action Buttons Row (Torch, Save, Freeze/Resume Hero button, Share)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Torch Button (compact circular glass button)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (torchEnabled) themeColor else Color(0xFF1F1E26),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (torchEnabled) themeColor else Color(0xFF2E2C33),
                                    CircleShape
                                )
                                .clickable { torchEnabled = !torchEnabled }
                                .testTag("torch_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Zseblámpa",
                                tint = if (torchEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Save Button (compact circular glass button)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1F1E26), CircleShape)
                                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                .clickable {
                                    val rawBitmap = if (isFrozen && frozenBitmap != null) {
                                        frozenBitmap
                                    } else {
                                        previewView.bitmap
                                    }
                                    if (rawBitmap != null) {
                                        isProcessing = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val filteredBitmap = applyColorFilterToBitmap(rawBitmap, filterMode, contrast, brightness)
                                            val savedUri = saveBitmapToGallery(context, filteredBitmap)
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                if (savedUri != null) {
                                                    toastIcon = Icons.Default.Save
                                                    toastSubIcon = Icons.Default.CheckCircle
                                                    toastColor = Color(0xFF10B981) // emerald green
                                                    showSavedToast = true
                                                } else {
                                                    toastIcon = Icons.Default.Save
                                                    toastSubIcon = Icons.Default.Error
                                                    toastColor = Color(0xFFEF4444) // red
                                                    showSavedToast = true
                                                }
                                            }
                                        }
                                    } else {
                                        toastIcon = Icons.Default.Save
                                        toastSubIcon = Icons.Default.Warning
                                        toastColor = Color(0xFFFFB300) // amber yellow
                                        showSavedToast = true
                                    }
                                }
                                .testTag("save_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Mentés",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Hero Freeze/Resume Shutter Button (Dual-ring camera shutter style)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(3.dp, if (isFrozen) Color(0xFFEF4444) else themeColor, CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isFrozen) Color(0xFFEF4444) else themeColor,
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
                                                // Transfer current extra digital zoom and pan seamlessly to the frozen frame view
                                                frozenScale = extraDigitalZoom
                                                frozenOffset = extraDigitalPan
                                            } else {
                                                toastIcon = Icons.Default.Pause
                                                toastSubIcon = Icons.Default.Error
                                                toastColor = Color(0xFFEF4444) // red
                                                showSavedToast = true
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
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Share Button (compact circular glass button)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1F1E26), CircleShape)
                                .border(1.dp, Color(0xFF2E2C33), CircleShape)
                                .clickable {
                                    val rawBitmap = if (isFrozen && frozenBitmap != null) {
                                        frozenBitmap
                                    } else {
                                        previewView.bitmap
                                    }
                                    if (rawBitmap != null) {
                                        isProcessing = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val filteredBitmap = applyColorFilterToBitmap(rawBitmap, filterMode, contrast, brightness)
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                shareBitmap(context, filteredBitmap)
                                            }
                                        }
                                    } else {
                                        toastIcon = Icons.Default.Share
                                        toastSubIcon = Icons.Default.Warning
                                        toastColor = Color(0xFFFFB300) // amber yellow
                                        showSavedToast = true
                                    }
                                }
                                .testTag("share_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Megosztás",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 4. Compact pill-shaped Segmented Control Navigation Tab Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF131217), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF23222A), RoundedCornerShape(16.dp)),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Pair(Icons.Default.ZoomIn, "NAGYÍTÁS"),
                            Pair(Icons.Default.ColorLens, "SZŰRŐK"),
                            Pair(Icons.Default.Tune, "KORREKCIÓ"),
                            Pair(Icons.Default.Palette, "TÉMA")
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
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) themeColor else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
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
                    colors = CardDefaults.cardColors(containerColor = toastColor),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = toastIcon,
                            contentDescription = "Notification primary",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        toastSubIcon?.let { subIcon ->
                            Icon(
                                imageVector = subIcon,
                                contentDescription = "Notification secondary",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
fun applyColorFilterToBitmap(source: Bitmap, filterMode: FilterMode, contrast: Float, brightness: Float): Bitmap {
    val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()
    
    val matrix = androidx.compose.ui.graphics.ColorMatrix()
    
    // 1. Apply base accessibility/night filter
    when (filterMode) {
        FilterMode.NORMAL -> { /* Keep identity */ }
        FilterMode.MONOCHROME -> {
            matrix.setToSaturation(0f)
        }
        FilterMode.INVERTED -> {
            matrix.set(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.YELLOW -> {
            matrix.set(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                0.2126f * 1.6f, 0.7152f * 1.6f, 0.0722f * 1.6f, 0f, 0f,
                0.2126f * 1.6f, 0.7152f * 1.6f, 0.0722f * 1.6f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.RED -> {
            matrix.set(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
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

    paint.colorFilter = android.graphics.ColorMatrixColorFilter(values)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return resultBitmap
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
