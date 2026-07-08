package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val OffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

data class AppThemeColor(@StringRes val nameRes: Int, val color: Color)

@Composable
fun NoCameraScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            // Beautiful crossed-out camera icon container
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color(0xFF1F1E26), CircleShape)
                    .border(2.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.no_camera_icon),
                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                        modifier = Modifier.size(56.dp)
                    )
                    // Draw a thick diagonal crossed-out line over the camera icon
                    Canvas(modifier = Modifier.size(56.dp)) {
                        drawLine(
                            color = Color(0xFFEF4444),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 6f
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Text(
                text = stringResource(R.string.no_camera_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.no_camera_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Clean modern action button with a green checkmark
            Button(
                onClick = {
                    (context as? ComponentActivity)?.finish()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("exit_app_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MagnifierMainScreen() {
    val context = LocalContext.current
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "27.0"
        } catch (e: Exception) {
            "27.0"
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val magnifierViewModel: MagnifierViewModel = viewModel()

    val themeOptions = remember {
        listOf(
            AppThemeColor(R.string.theme_purple, Color(0xFFB180FF)),
            AppThemeColor(R.string.theme_green, Color(0xFF00FF87)),
            AppThemeColor(R.string.theme_gold, Color(0xFFFFB300)),
            AppThemeColor(R.string.theme_blue, Color(0xFF00D2FF)),
            AppThemeColor(R.string.theme_orange, Color(0xFFFF6B00))
        )
    }
    var currentThemeIndex by rememberSaveable { mutableStateOf(0) }
    val themeColor = themeOptions[currentThemeIndex].color

    // Camera setup states
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCameraBindingFailed by remember { mutableStateOf(false) }
    var isCameraCheckingFinished by remember { mutableStateOf(false) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Live preview view persistent object
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // Crucial for getBitmap() to work 100% of the time!
        }
    }

    // Interactive states
    var liveZoomRatio by rememberSaveable { mutableStateOf(1.0f) }
    var minZoom by remember { mutableStateOf(1.0f) }
    var maxZoom by remember { mutableStateOf(8.0f) }

    // Multi-camera states
    var availableCameras by remember { mutableStateOf<List<androidx.camera.core.CameraInfo>>(emptyList()) }
    var selectedCameraIndex by rememberSaveable { mutableStateOf(0) }

    // Additional software digital zoom and pan states for active camera preview
    var extraDigitalZoom by rememberSaveable { mutableStateOf(1.0f) }
    var extraDigitalPan by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    var torchEnabled by rememberSaveable { mutableStateOf(false) }

    // Exposure (live brightness/contrast adjustment)
    var exposureIndex by rememberSaveable { mutableStateOf(0) }
    var minExposureIndex by remember { mutableStateOf(-4) }
    var maxExposureIndex by remember { mutableStateOf(4) }

    // Freeze Frame and UI processing state
    var isProcessing by remember { mutableStateOf(false) }
    var isFrozen by rememberSaveable { mutableStateOf(false) }

    val sliderMin by remember(isFrozen, minZoom) {
        derivedStateOf { if (isFrozen) 0.5f else minZoom }
    }
    val sliderMax = 64.0f
    var presets by remember { mutableStateOf(listOf(1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f)) }
    LaunchedEffect(isFrozen, minZoom, maxZoom) {
        if (isFrozen) {
            presets = listOf(0.5f, 1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f)
        } else {
            val steps = withContext(Dispatchers.IO) {
                getOpticalSteps(context, minZoom, sliderMax)
            }
            presets = steps
        }
    }

    // Az összes élő zoom-vezérlő (pinch, dupla koppintás, −/+ gomb, slider, presetek) közös
    // belépési pontja: a cél teljes nagyítást elosztja kamera- és digitális zoomra.
    fun applyTotalZoom(target: Float, resetPan: Boolean) {
        val distribution = computeZoomDistribution(target.coerceIn(minZoom, sliderMax), minZoom, maxZoom)
        liveZoomRatio = distribution.cameraZoom
        extraDigitalZoom = distribution.digitalZoom
        if (resetPan || distribution.digitalZoom <= 1.0f) {
            extraDigitalPan = Offset.Zero
        }
    }

    var rawFrozenBitmap by magnifierViewModel::rawFrozenBitmap
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharpenStrength by rememberSaveable { mutableStateOf(0.0f) } // 0.0f (Off) to 2.0f (Very Strong)
    var liveSharpenedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Process death után a savedInstanceState visszaáll, de a ViewModel-beli bitmap már nincs meg —
    // ilyenkor vissza élő módba.
    LaunchedEffect(Unit) {
        if (isFrozen && rawFrozenBitmap == null) {
            isFrozen = false
        }
    }

    // Background processing to sharpen frozen image asynchronously
    LaunchedEffect(rawFrozenBitmap, sharpenStrength) {
        val raw = rawFrozenBitmap
        if (raw != null) {
            isProcessing = true
            withContext(Dispatchers.Default) {
                val sharpened = if (sharpenStrength > 0.0f) {
                    sharpenBitmap(raw, strength = sharpenStrength)
                } else {
                    raw
                }
                withContext(Dispatchers.Main) {
                    frozenBitmap = sharpened
                    isProcessing = false
                }
            }
        } else {
            frozenBitmap = null
        }
    }

    // Image enhancements (primarily applied on frozen frame for visual aid)
    var contrast by rememberSaveable { mutableStateOf(1.0f) } // 1.0f (Normal) to 3.0f (High Contrast)
    var brightness by rememberSaveable { mutableStateOf(0.0f) } // -100f to 100f
    var filterMode by rememberSaveable { mutableStateOf(FilterMode.NORMAL) }

    // Digital Zoom/Pan states for frozen image
    var frozenScale by rememberSaveable { mutableStateOf(1.0f) }
    var frozenOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    // Viewfinder layout size (a pan-határok és a minimap számításához)
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Dynamic boundary constraints to prevent pan offsets from drifting when zoom scale is reduced
    LaunchedEffect(extraDigitalZoom) {
        extraDigitalPan = clampPan(extraDigitalPan, extraDigitalZoom, viewportSize)
    }

    LaunchedEffect(frozenScale) {
        frozenOffset = clampPan(frozenOffset, frozenScale, viewportSize)
    }

    // UI overlays / status
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0: Nagyítás, 1: Szűrők, 2: Képkorrekció
    var toastIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector>(Icons.Default.CheckCircle) }
    var toastSubIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var toastColor by remember { mutableStateOf(Color(0xFF10B981)) }
    var showSavedToast by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    // Viewfinder and layout states
    var liveThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Grab thumbnail dynamically and generate sharpened live overlay when digital zoom is active in live mode.
    // Az overlay kizárólag aktív élesítésnél jelenik meg: élesítés nélkül a natív preview
    // élesebb, mint bármilyen leskálázott bitmap-másolat.
    LaunchedEffect(extraDigitalZoom > 1.0f) {
        if (extraDigitalZoom > 1.0f) {
            while (true) {
                try {
                    val bmp = previewView.bitmap
                    if (bmp != null) {
                        liveThumbnailBitmap = bmp
                        val strength = sharpenStrength
                        if (strength > 0.0f) {
                            // Process scaling and sharpening asynchronously in background thread to avoid UI block
                            val sharpened = withContext(Dispatchers.Default) {
                                // Scale down to a maximum dimension of 540px to ensure ultra-fast processing (<15ms)
                                val maxDim = 540
                                val scale = if (bmp.width > maxDim || bmp.height > maxDim) {
                                    maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat()
                                } else {
                                    1.0f
                                }

                                val workingBmp = if (scale < 1.0f) {
                                    val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
                                    val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
                                    Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                                } else {
                                    bmp
                                }

                                val sharp = sharpenBitmap(workingBmp, strength = strength)

                                // Clean up temporary scaled bitmap if created
                                if (workingBmp != bmp && workingBmp != sharp) {
                                    workingBmp.recycle()
                                }

                                sharp
                            }
                            liveSharpenedBitmap = sharpened
                        } else {
                            liveSharpenedBitmap = null
                        }
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // ignore errors fetching bitmap
                }
                delay(120) // 8-10 FPS is more than enough for live digital zoom overlay and preserves battery/CPU
            }
        } else {
            liveThumbnailBitmap = null
            liveSharpenedBitmap = null
        }
    }

    // Tap-to-focus animation feedback
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTrigger by remember { mutableStateOf(0) }

    // A megjelenítés és a mentés ugyanabból a kanonikus mátrixból dolgozik (WYSIWYG)
    val combinedColorFilter = remember(filterMode, contrast, brightness) {
        ColorFilter.colorMatrix(ColorMatrix(buildFilterMatrixValues(filterMode, contrast, brightness)))
    }
    // Élő módban a kontraszt/fényerő nem látszik a previewn, ezért a minimap is szűrő-only mátrixot kap
    val liveColorFilter = remember(filterMode) {
        ColorFilter.colorMatrix(ColorMatrix(buildFilterMatrixValues(filterMode, 1.0f, 0.0f)))
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

    // Reactive bindings to camera parameters — a camera kulcs biztosítja, hogy rebind
    // (forgatás, kameraváltás) után a visszaállított értékek újra érvényesüljenek
    LaunchedEffect(camera, liveZoomRatio) {
        try {
            camera?.cameraControl?.setZoomRatio(liveZoomRatio)
        } catch (e: Exception) {
            Log.e("Magnifier", "Failed to set zoom ratio", e)
        }
    }

    LaunchedEffect(camera, torchEnabled) {
        try {
            camera?.cameraControl?.enableTorch(torchEnabled)
        } catch (e: Exception) {
            Log.e("Magnifier", "Failed to set torch status", e)
        }
    }

    LaunchedEffect(camera, exposureIndex) {
        try {
            camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
        } catch (e: Exception) {
            Log.e("Magnifier", "Failed to set exposure index", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                if (future.isDone) {
                    future.get().unbindAll()
                }
            } catch (e: Exception) {
                Log.e("Magnifier", "Failed to unbind camera on dispose", e)
            }
        }
    }

    // Bind camera lifecycle
    LaunchedEffect(selectedCameraIndex) {
        if (!hasCameraPermission(context)) {
            isCameraCheckingFinished = true
            return@LaunchedEffect
        }
        try {
            val cameraProvider = awaitListenableFuture(ProcessCameraProvider.getInstance(context), context)
            val cameraInfos = cameraProvider.availableCameraInfos
            availableCameras = cameraInfos
            
            if (cameraInfos.isEmpty()) {
                isCameraBindingFailed = true
                previewUseCase = null
                isCameraCheckingFinished = true
                return@LaunchedEffect
            }
     
            // Ensure selectedCameraIndex is within bounds
            val index = selectedCameraIndex.coerceIn(0, cameraInfos.lastIndex)
            val selectedCameraInfo = cameraInfos[index]

            val preview = Preview.Builder().build()
            
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
     
            isCameraBindingFailed = false
            cameraProvider.unbindAll()
            val cameraInstance = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                localImageCapture
            )
            camera = cameraInstance
            previewUseCase = preview
            
            // Observe live zoom capabilities
            cameraInstance.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                val mz = if (zoomState.minZoomRatio <= 0f || zoomState.minZoomRatio.isNaN() || zoomState.minZoomRatio.isInfinite()) 1.0f else zoomState.minZoomRatio
                val xz = if (zoomState.maxZoomRatio <= 0f || zoomState.maxZoomRatio.isNaN() || zoomState.maxZoomRatio.isInfinite()) 8.0f else zoomState.maxZoomRatio
                minZoom = mz
                maxZoom = maxOf(mz, xz)
                // Safely update liveZoomRatio if out of bounds
                if (liveZoomRatio < minZoom) liveZoomRatio = minZoom
                if (liveZoomRatio > maxZoom) liveZoomRatio = maxZoom
            }
     
            // Observe exposure capabilities; a mentett exposure értéket az új kamera
            // tartományába szorítjuk a felülírás helyett
            val exposureState = cameraInstance.cameraInfo.exposureState
            minExposureIndex = exposureState.exposureCompensationRange.lower
            maxExposureIndex = exposureState.exposureCompensationRange.upper
            exposureIndex = exposureIndex.coerceIn(minExposureIndex, maxExposureIndex)
        } catch (exc: Exception) {
            Log.e("Magnifier", "Use case binding failed", exc)
            isCameraBindingFailed = true
            previewUseCase = null
        } finally {
            isCameraCheckingFinished = true
        }
    }

    if (!isCameraCheckingFinished) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = themeColor)
        }
        return
    }

    if (availableCameras.isEmpty() || isCameraBindingFailed) {
        NoCameraScreen()
        return
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { previewView },
                            update = {
                                previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = extraDigitalZoom
                                    scaleY = extraDigitalZoom
                                    translationX = extraDigitalPan.x
                                    translationY = extraDigitalPan.y
                                }
                        )

                        // Real-time background sharpened image overlay during digital zoom
                        liveSharpenedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_live_sharpened),
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = extraDigitalZoom
                                        scaleY = extraDigitalZoom
                                        translationX = extraDigitalPan.x
                                        translationY = extraDigitalPan.y
                                    }
                            )
                        }

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
                            // Deszaturálás + sárga modulálás: luma → (l, l, 0), megegyezik a
                            // mentésnél használt color matrix-szal (WYSIWYG)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.Gray,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                )
                                drawRect(
                                    color = Color.Yellow,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Modulate
                                )
                            }
                        } else if (filterMode == FilterMode.RED) {
                            // Deszaturálás + vörös modulálás: luma → (l, 0, 0)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.Gray,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                )
                                drawRect(
                                    color = Color.Red,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Modulate
                                )
                            }
                        }

                        // Transparent Touch Interceptor Overlay Box on top of AndroidView/Canvas to capture all gestures reliably
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (zoom != 1f) {
                                            // Seamless unified zoom logic
                                            applyTotalZoom(liveZoomRatio * extraDigitalZoom * zoom, resetPan = false)
                                        }
                                        
                                        // Handle panning/dragging when digitally zoomed in
                                        extraDigitalPan = clampPan(extraDigitalPan + pan, extraDigitalZoom, viewportSize)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            val currentZoom = liveZoomRatio * extraDigitalZoom
                                            if (Math.abs(currentZoom - 1.0f) > 0.05f) {
                                                applyTotalZoom(1.0f, resetPan = true)
                                            } else {
                                                applyTotalZoom((minZoom + maxZoom) / 2.0f, resetPan = true)
                                            }
                                        },
                                        onTap = { offset ->
                                            if (!controlsVisible) {
                                                controlsVisible = true
                                            } else {
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
                                        }
                                    )
                                }
                        )
                    }
                } else {
                    // FROZEN IMAGE VIEW
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    frozenScale = (frozenScale * zoom).coerceIn(0.5f, sliderMax)
                                    frozenOffset = clampPan(frozenOffset + pan, frozenScale, viewportSize)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        frozenScale = if (frozenScale > 1.0f) 1.0f else 3.0f
                                        frozenOffset = Offset.Zero
                                    },
                                    onTap = {
                                        if (!controlsVisible) {
                                            controlsVisible = true
                                        }
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        frozenBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_frozen_image),
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
                val isDigitalZoomActive = (if (isFrozen) frozenScale > 1.0f else extraDigitalZoom > 1.0f) && controlsVisible
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
                                        contentDescription = stringResource(R.string.cd_minimap_frozen),
                                        contentScale = ContentScale.FillBounds,
                                        colorFilter = combinedColorFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                liveThumbnailBitmap?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.cd_minimap_live),
                                        contentScale = ContentScale.FillBounds,
                                        colorFilter = liveColorFilter,
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp, start = 16.dp)
                ) {
                    Row(
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
                                contentDescription = stringResource(R.string.cd_toggle_controls),
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
                                            // Kameraváltásnál a vaku kikapcsol (forgatásnál viszont megmarad)
                                            torchEnabled = false
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
                                        contentDescription = stringResource(R.string.cd_switch_camera),
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
                                    
                                    val cameraLabel = if (activeCameraInfo != null) {
                                        when (activeCameraInfo.lensFacing) {
                                            0 -> stringResource(R.string.camera_front)
                                            1 -> {
                                                val backCameras = availableCameras.filter { it.lensFacing == 1 }
                                                if (backCameras.size > 1) {
                                                    val idx = backCameras.indexOf(activeCameraInfo)
                                                    stringResource(R.string.camera_back_n, idx + 1)
                                                } else {
                                                    stringResource(R.string.camera_back)
                                                }
                                            }
                                            else -> stringResource(R.string.camera_external)
                                        }
                                    } else {
                                        stringResource(R.string.camera_generic)
                                    }
                                    
                                    Icon(
                                        imageVector = cameraIcon,
                                        contentDescription = cameraLabel,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                                                contentDescription = stringResource(R.string.tab_zoom),
                                                tint = themeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "v$appVersion",
                                                color = themeColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isDigitalRange) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Memory,
                                                        contentDescription = stringResource(R.string.cd_digital_zoom),
                                                        tint = Color(0xFFD0BCFF),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
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
                                                        frozenScale = max(0.5f, frozenScale - 0.5f)
                                                    } else {
                                                        applyTotalZoom(liveZoomRatio * extraDigitalZoom - 0.5f, resetPan = false)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = stringResource(R.string.cd_zoom_out),
                                                tint = Color(0xFFE6E1E5),
                                                modifier = Modifier.size(18.dp)
                                             )
                                        }

                                        Slider(
                                            value = currentTotalZoom.coerceIn(sliderMin, sliderMax),
                                            onValueChange = { newValue ->
                                                if (isFrozen) {
                                                    frozenScale = newValue
                                                } else {
                                                    applyTotalZoom(newValue, resetPan = false)
                                                }
                                            },
                                            valueRange = sliderMin..sliderMax,
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
                                                        frozenScale = min(sliderMax, frozenScale + 0.5f)
                                                    } else {
                                                        applyTotalZoom(liveZoomRatio * extraDigitalZoom + 0.5f, resetPan = false)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.cd_zoom_in),
                                                tint = Color(0xFFE6E1E5),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = String.format("%.1fx", currentTotalZoom),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.widthIn(min = 55.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    // Quick Presets (Non-scrollable, responsive fitting)
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val availableWidth = maxWidth
                                        val itemWidth = 52.dp
                                        val spacing = 6.dp
                                        // Calculate how many items can fully fit in the available width:
                                        // k * itemWidth + (k - 1) * spacing <= availableWidth
                                        val maxFit = ((availableWidth + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)
                                        val visiblePresets = presets.take(maxFit)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            visiblePresets.forEach { preset ->
                                                val isSelected = if (isFrozen) {
                                                    abs(frozenScale - preset) < 0.15f
                                                } else {
                                                    abs((liveZoomRatio * extraDigitalZoom) - preset) < 0.15f
                                                }
                                                
                                                val isPresetPossible = isFrozen || preset <= sliderMax
                                                if (isPresetPossible) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(itemWidth)
                                                            .heightIn(min = 40.dp)
                                                            .background(
                                                                if (isSelected) themeColor else Color(0xFF1B1A21),
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .border(1.dp, if (isSelected) themeColor else Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                if (isFrozen) {
                                                                    frozenScale = preset
                                                                } else {
                                                                    applyTotalZoom(preset, resetPan = true)
                                                                }
                                                            }
                                                            .padding(vertical = 8.dp),
                                                        contentAlignment = Alignment.Center
                                                     ) {
                                                        Text(
                                                            text = if (preset % 1.0f == 0.0f) String.format("%.0fx", preset) else String.format("%.1fx", preset),
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
                                            contentDescription = stringResource(R.string.tab_filters),
                                            tint = themeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "v$appVersion",
                                            color = themeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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
                                                            contentDescription = stringResource(R.string.cd_selected),
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                                                contentDescription = stringResource(R.string.tab_tune),
                                                tint = themeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "v$appVersion",
                                                color = themeColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                                            contentDescription = stringResource(if (isFrozen) R.string.label_contrast else R.string.label_exposure),
                                            tint = themeColor,
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
                                                activeTrackColor = themeColor,
                                                thumbColor = themeColor,
                                                inactiveTrackColor = Color(0xFF1B1A21)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (isFrozen) String.format("%.1fx", contrast) else "$exposureIndex EV",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 55.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    if (isFrozen) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LightMode,
                                                contentDescription = stringResource(R.string.label_brightness),
                                                tint = themeColor,
                                                modifier = Modifier.size(18.dp)
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
                                            Text(
                                                text = String.format("%+d", brightness.roundToInt()),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.widthIn(min = 55.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    // Dynamic Sharpening Strength Slider (available for both live digital zoom and frozen images)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = stringResource(R.string.label_sharpen),
                                            tint = themeColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Slider(
                                            value = sharpenStrength,
                                            onValueChange = { sharpenStrength = it },
                                            valueRange = 0.0f..10.0f,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = themeColor,
                                                thumbColor = themeColor,
                                                inactiveTrackColor = Color(0xFF1B1A21)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("sharpen_strength_slider")
                                        )
                                        Text(
                                            text = if (sharpenStrength == 0.0f) "0.0" else String.format("%.1fx", sharpenStrength),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.widthIn(min = 55.dp),
                                            textAlign = TextAlign.End
                                        )
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
                                            contentDescription = stringResource(R.string.tab_theme),
                                            tint = themeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "v$appVersion",
                                            color = themeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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
                                                            contentDescription = stringResource(R.string.cd_selected),
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
                                contentDescription = stringResource(R.string.cd_torch),
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
                                        // Kattintáskori állapot rögzítése, hogy a háttérfeldolgozás
                                        // alatti állításások ne szivárogjanak a kimenetbe
                                        val frozenNow = isFrozen
                                        val digitalZoomNow = extraDigitalZoom
                                        val digitalPanNow = extraDigitalPan
                                        val sharpenNow = sharpenStrength
                                        val filterNow = filterMode
                                        val contrastNow = contrast
                                        val brightnessNow = brightness
                                        isProcessing = true
                                        coroutineScope.launch(Dispatchers.Default) {
                                            val exportBitmap = processExportBitmap(
                                                rawBitmap, frozenNow, digitalZoomNow, digitalPanNow,
                                                sharpenNow, filterNow, contrastNow, brightnessNow
                                            )
                                            val savedUri = withContext(Dispatchers.IO) {
                                                saveBitmapToGallery(context, exportBitmap)
                                            }
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
                                contentDescription = stringResource(R.string.cd_save),
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
                                            rawFrozenBitmap = null
                                            frozenScale = 1.0f
                                            frozenOffset = Offset.Zero
                                        } else {
                                            val bmp = previewView.bitmap
                                            if (bmp != null) {
                                                // Azonnali fagyasztás a preview-snapshottal, hogy ne legyen érzékelhető késleltetés
                                                rawFrozenBitmap = bmp
                                                isFrozen = true
                                                // Transfer current extra digital zoom and pan seamlessly to the frozen frame view
                                                frozenScale = extraDigitalZoom
                                                frozenOffset = extraDigitalPan

                                                // Háttérben natív felbontású still capture, ami megérkezéskor lecseréli a snapshotot
                                                val capture = imageCapture
                                                val targetAspect = if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 0f
                                                if (capture != null) {
                                                    coroutineScope.launch {
                                                        val jpeg = awaitCapturedJpeg(capture, context)
                                                        val hiRes = jpeg?.let {
                                                            withContext(Dispatchers.Default) {
                                                                decodeCapturedJpeg(it.bytes, it.rotationDegrees, targetAspect)
                                                            }
                                                        }
                                                        // Csak akkor cserélünk, ha még ugyanez a fagyasztás él
                                                        if (hiRes != null && isFrozen && rawFrozenBitmap === bmp) {
                                                            rawFrozenBitmap = hiRes
                                                        }
                                                    }
                                                }
                                            } else {
                                                toastIcon = Icons.Default.Pause
                                                toastSubIcon = Icons.Default.Error
                                                toastColor = Color(0xFFEF4444) // red
                                                showSavedToast = true
                                            }
                                        }
                                    }
                                    .testTag("freeze_toggle_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = stringResource(if (isFrozen) R.string.cd_resume else R.string.cd_freeze),
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
                                        val frozenNow = isFrozen
                                        val digitalZoomNow = extraDigitalZoom
                                        val digitalPanNow = extraDigitalPan
                                        val sharpenNow = sharpenStrength
                                        val filterNow = filterMode
                                        val contrastNow = contrast
                                        val brightnessNow = brightness
                                        isProcessing = true
                                        coroutineScope.launch(Dispatchers.Default) {
                                            val exportBitmap = processExportBitmap(
                                                rawBitmap, frozenNow, digitalZoomNow, digitalPanNow,
                                                sharpenNow, filterNow, contrastNow, brightnessNow
                                            )
                                            withContext(Dispatchers.Main) {
                                                isProcessing = false
                                                shareBitmap(context, exportBitmap)
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
                                contentDescription = stringResource(R.string.cd_share),
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
                            Pair(Icons.Default.ZoomIn, stringResource(R.string.tab_zoom)),
                            Pair(Icons.Default.ColorLens, stringResource(R.string.tab_filters)),
                            Pair(Icons.Default.Tune, stringResource(R.string.tab_tune)),
                            Pair(Icons.Default.Palette, stringResource(R.string.tab_theme))
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
                            contentDescription = stringResource(R.string.cd_notification),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        toastSubIcon?.let { subIcon ->
                            Icon(
                                imageVector = subIcon,
                                contentDescription = stringResource(R.string.cd_notification_detail),
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

