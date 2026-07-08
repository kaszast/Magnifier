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

// A kimerevített nyers képkocka nem fér el a savedInstanceState-ben (TransactionTooLargeException),
// ezért konfigurációváltásnál (pl. forgatás) ViewModel őrzi meg.
class MagnifierViewModel : ViewModel() {
    var rawFrozenBitmap by mutableStateOf<Bitmap?>(null)
}

private val OffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

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

private class CapturedJpeg(val bytes: ByteArray, val rotationDegrees: Int)

// Natív felbontású still capture JPEG byte-okként; hibánál null — a hívó a
// preview-snapshotra esik vissza, a felhasználó felé nincs külön hibaút.
private suspend fun awaitCapturedJpeg(imageCapture: ImageCapture, context: Context): CapturedJpeg? =
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
                        contentDescription = "Nincs kamera elérhető",
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
                text = "Nem található kamera",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "A nagyító alkalmazás működéséhez fizikai kamera szükséges. Kérjük, futtassa az alkalmazást egy kamerával rendelkező fizikai eszközön.",
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
                        contentDescription = "Zöld pipa",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bezárás",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
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

    LaunchedEffect(cameraPermissionState) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
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
            AppThemeColor("Lila", Color(0xFFB180FF)),
            AppThemeColor("Zöld", Color(0xFF00FF87)),
            AppThemeColor("Arany", Color(0xFFFFB300)),
            AppThemeColor("Kék", Color(0xFF00D2FF)),
            AppThemeColor("Narancs", Color(0xFFFF6B00))
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
                                contentDescription = "Live Sharpened Zoom Preview",
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
                                    
                                    val cameraLabel = if (activeCameraInfo != null) {
                                        when (activeCameraInfo.lensFacing) {
                                            0 -> "Előlapi"
                                            1 -> {
                                                val backCameras = availableCameras.filter { it.lensFacing == 1 }
                                                if (backCameras.size > 1) {
                                                    val idx = backCameras.indexOf(activeCameraInfo)
                                                    "Hátlapi ${idx + 1}"
                                                } else {
                                                    "Hátlapi"
                                                }
                                            }
                                            else -> "Külső"
                                        }
                                    } else {
                                        "Kamera"
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
                                                contentDescription = "Nagyítás",
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
                                                        contentDescription = "Szoftveres",
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
                                                contentDescription = "Csökkentés",
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
                                                contentDescription = "Növelés",
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
                                            contentDescription = "Szűrők",
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                                                contentDescription = "Korrekció",
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
                                            contentDescription = "Contrast icon",
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
                                                contentDescription = "Brightness icon",
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
                                            contentDescription = "Sharpen strength icon",
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
                                            contentDescription = "Téma",
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
