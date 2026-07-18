/*
 * ============================================================================
 *  MagnifierScreen.kt — a nagyító-alkalmazás fő képernyője ("orchestrátor")
 * ============================================================================
 *
 * MI EZ A FÁJL?
 * Ez az alkalmazás legnagyobb, központi fájlja. Két @Composable függvényt tartalmaz:
 *   - NoCameraScreen()     : hibaképernyő, ha nincs használható kamera.
 *   - MagnifierMainScreen(): maga a nagyító-képernyő — ez az "orchestrátor".
 *
 * MIT JELENT AZ "ORCHESTRÁTOR" SZEREP?
 * A MagnifierMainScreen NEM rajzol minden pixelt maga. A tényleges UI-darabok
 * (alsó vezérlőkártya fülei, minimap, gombsorok) külön fájlokba vannak kiemelve:
 *   - ControlPanels.kt      : ZoomTabContent, FiltersTabContent, TuneTabContent, ThemeTabContent
 *   - MagnifierComponents.kt: ZoomMinimap, TopLeftControls, ActionButtonsRow, ControlTabBar
 *   - ImageProcessing.kt    : szűrő-mátrix, élesítés (sharpen), export-feldolgozás
 *   - CameraCapture.kt      : kamera-engedély, still capture, optikai zoom-lépcsők
 *   - ZoomLogic.kt          : zoom-elosztás (computeZoomDistribution) és pan-korlát (clampPan)
 *   - MagnifierViewModel.kt : a nyers kimerevített bitmap túlélő tárolója
 *
 * Ez a fájl tartja ÖSSZE a szálakat: itt él az ÖSSZES állapot (state) és mellékhatás
 * (side effect / effect), és innen hívjuk a fenti komponenseket, átadva nekik az
 * értékeket és a visszahívó lambdákat. Ezt a mintát Compose-ban "state hoisting"-nak
 * hívják: az állapot fentebb (a szülőben) él, a gyerek-komponens csak megjeleníti és
 * eseményt jelez visszafelé. Így a gyerekek "buták" (stateless) és jól tesztelhetők.
 *
 * MI AZ A @Composable?
 * A @Composable annotációval jelölt függvények írják le a UI-t Jetpack Compose-ban.
 * Nem egyszer futnak le: a Compose futásidőben újra és újra meghívja őket, amikor a
 * bemeneti állapotuk megváltozik — ezt hívják "recomposition"-nek. Ezért egy Composable
 * függvény lényegében egy "az aktuális állapotból következő képernyő" leírás, nem pedig
 * klasszikus, egyszer lefutó eljárás.
 */
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

// Egyedi "Saver" az Offset típushoz a rememberSaveable számára (lásd lentebb az ÁLLAPOTOK
// szekcióban). A rememberSaveable csak olyasmit tud automatikusan elmenteni, amit a rendszer
// Bundle-be tud tenni (Int, Float, String, Parcelable...). Az Offset nem ilyen, ezért megmondjuk,
// hogyan bontsuk szét menthető darabokra (két Float: x, y) és hogyan rakjuk össze visszatöltéskor.
//   - save   : Offset -> List<Float>  (az x és y koordináta)
//   - restore: List<Float> -> Offset  (visszaépítés a listából)
private val OffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

// Egy témaszín-opció: a megjelenítendő név egy string-erőforrás azonosítója (@StringRes → fordítható
// szöveg a strings.xml-ből, nem beégetett magyar/angol string) és a hozzá tartozó szín.
data class AppThemeColor(@StringRes val nameRes: Int, val color: Color)

// ============================================================================
//  NoCameraScreen — hibaképernyő, ha nincs használható kamera
// ============================================================================
// Akkor jelenik meg, ha az eszközön nem található kamera, vagy a CameraX bind
// (a kamera "bekötése" az életciklusba) meghiúsult. Egy áthúzott kamera-ikont, egy
// magyarázó szöveget és egy "Bezárás" gombot mutat. Nincs saját állapota — tisztán
// statikus UI (a gomb kivételével, ami bezárja az Activity-t).
//
// Fontos Compose-építőelemek, amiket itt látni fogsz:
//   - LocalContext.current : a Compose-fán keresztül "leszedett" Android Context
//     (a CompositionLocal mechanizmussal). A Context sok Android API-hoz kell.
//   - Box / Column / Row    : elrendező (layout) konténerek. Box = egymásra pakol
//     (rétegek), Column = függőleges sor, Row = vízszintes sor.
//   - Modifier              : a komponens megjelenését/viselkedését állító "díszítő"
//     lánc (méret, háttér, keret, padding, kattinthatóság...). A sorrend SZÁMÍT.
//   - stringResource(R.string.x): a strings.xml-ből olvassa ki a szöveget (i18n).
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
            
            /*
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
            */
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Clean modern action button with a green checkmark
            Button(
                onClick = {
                    // A Context valójában egy Activity is szokott lenni. A "safe cast" (as?)
                    // null-t ad, ha mégsem az — így a ?.finish() csak akkor fut, ha tényleg
                    // ComponentActivity, és bezárja a képernyőt. Így nincs ClassCastException.
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
                    /*
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    */
                }
            }
        }
    }
}

// ============================================================================
//  MagnifierMainScreen — a fő nagyító-képernyő (az orchestrátor)
// ============================================================================
// Ez a fájl legfontosabb és leghosszabb függvénye. Az egész nagyító itt áll össze:
//   1. ÁLLAPOTOK  : minden mutable állapot (zoom, szűrő, fagyasztás, téma...) itt él.
//   2. EFFEKTEK   : mellékhatások (kamera-parancsok, háttérfeldolgozás, takarítás).
//   3. KAMERA-BIND: a CameraX kamera "bekötése" az életciklusba.
//   4. UI         : a képernyő felépítése a fenti állapotból + a kiemelt komponensek hívása.
//
// Mivel @Composable, a Compose runtime többször is meghívja (recomposition), amikor
// bármely olvasott állapot változik. Ezért itt NEM lehet sima lokális változóba tenni
// olyan értéket, aminek túl kell élnie egy recompositiont — arra való a remember { } és
// a rememberSaveable { } (lásd az ÁLLAPOTOK szekció részletes magyarázatát).
@Composable
fun MagnifierMainScreen(launchCount: Int = 0) {
    // --- Alap-függőségek (dependencies), amiket a Compose-fából "szedünk le" ---

    // A Context sok Android API-hoz kell (csomaginfó, kamera, fájlmentés...).
    val context = LocalContext.current

    // SharedPreferences és állapotok a bemutatóhoz és az értékeléshez
    val prefs = remember { context.getSharedPreferences("magnifier_prefs", Context.MODE_PRIVATE) }
    var showWalkthrough by remember {
        mutableStateOf(!prefs.getBoolean("walkthrough_shown", false))
    }
    var showRateDialog by remember {
        mutableStateOf(false)
    }

    var currentLanguage by remember {
        mutableStateOf(prefs.getString("app_lang", "hu") ?: "hu")
    }

    // Nyelvváltó segédfüggvény, ami elmenti a választást és újraindítja az Activity-t
    val onChangeLanguage: (String) -> Unit = remember {
        { langCode ->
            prefs.edit().putString("app_lang", langCode).apply()
            currentLanguage = langCode
            var ctx = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is android.app.Activity) {
                    ctx.recreate()
                    break
                }
                ctx = ctx.baseContext
            }
        }
    }

    // Automatikus értékelés kérése bizonyos számú indítás után
    LaunchedEffect(Unit) {
        val rateNever = prefs.getBoolean("rate_never", false)
        val rateDone = prefs.getBoolean("rate_done", false)
        if (launchCount >= 5 && !rateNever && !rateDone) {
            showRateDialog = true
        }
    }

    // Az app verziónevének kiolvasása a package-infóból, egyszeri számítással.
    // A remember { } gondoskodik róla, hogy ez a (viszonylag drága) lekérdezés NE fusson
    // le minden recompositionkor, csak egyszer, az első komponáláskor. A ?: "elvis"-operátor
    // a null esetére ad tartalék értéket; a catch a ritka hibát nyeli el ugyanazzal a fallbackkel.
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "27.0"
        } catch (e: Exception) {
            "27.0"
        }
    }
    // A lifecycleOwner (jellemzően az Activity) kell a kamera bindToLifecycle-jéhez és az
    // observerek regisztrálásához: a CameraX ehhez igazítja, mikor induljon/álljon le a kamera.
    val lifecycleOwner = LocalLifecycleOwner.current
    // Coroutine-scope, amely a Composition élettartamához kötött. Innen indítunk aszinkron
    // munkát (pl. mentés, still capture) az onClick lambdákban — a launch { } NEM blokkolja
    // a fő szálat, és a scope automatikusan lezárja a coroutine-okat, ha a képernyő eltűnik.
    val coroutineScope = rememberCoroutineScope()
    // A ViewModel túléli a konfigurációváltást (pl. képernyőforgatás), mert a rendszer NEM
    // dobja el forgatáskor, csak akkor, ha a képernyő végleg megszűnik. Ezért tesszük ide a
    // nyers kimerevített bitmapet (lásd rawFrozenBitmap lentebb): túl nagy a savedInstanceState-hez.
    val magnifierViewModel: MagnifierViewModel = viewModel()

    // ========================================================================
    //  1. ÁLLAPOTOK (state) — a képernyő teljes "memóriája"
    // ========================================================================
    //
    // HÁROMFÉLE TÁROLÁST HASZNÁLUNK, más-más túlélési garanciával:
    //
    //  (a) remember { mutableStateOf(x) }
    //      - Túléli a recompositiont (a Composable ismételt lefutását).
    //      - NEM éli túl a konfigurációváltást (pl. képernyőforgatás) és a process death-t
    //        (amikor a rendszer memóriahiány miatt kilövi a folyamatot a háttérben).
    //      - Ide való minden, ami forgatás után nyugodtan újraszámolható vagy nem baj, ha
    //        alaphelyzetbe áll (pl. a kamera-objektum, amit úgyis újra bindolunk).
    //
    //  (b) rememberSaveable { mutableStateOf(x) }
    //      - Mindent tud, amit (a), PLUSZ túléli a forgatást és a process death-t is,
    //        mert a rendszer a savedInstanceState Bundle-be menti/visszatölti az értéket.
    //      - Ide való minden felhasználói beállítás, amit kár lenne elveszíteni forgatáskor
    //        (zoom, szűrő, kontraszt, téma, fagyasztott-e...).
    //      - KORLÁT: csak "kicsi", Bundle-be tehető adat mehet bele. Nagy objektum (pl. egy
    //        teljes felbontású Bitmap) TILOS — TransactionTooLargeException-t okozna. Ezért van
    //        a nyers kimerevített bitmap a ViewModel-ben (lásd rawFrozenBitmap), nem itt.
    //
    //  (c) ViewModel-mező (magnifierViewModel.rawFrozenBitmap)
    //      - Túléli a forgatást (a ViewModel nem szűnik meg), de a process death-t NEM.
    //      - Nagy, nem-Bundle-elhető objektumoknak (a nyers bitmap) való.
    //
    // A "by" DELEGATE SZINTAXIS:
    //   var x by remember { mutableStateOf(0) }
    //   A "by" egy property-delegate. Nélküle az érték egy MutableState<Int> doboz volna, és
    //   x.value-t kellene írni/olvasni. A "by"-jal a fordító mögöttünk becseréli a .value elérést,
    //   így egyszerűen x-et írunk (olvasás) és x = ...-t (írás). Compose alatt az OLVASÁS közben a
    //   runtime feljegyzi, hogy ez a Composable függ ettől az állapottól, az ÍRÁS pedig kiváltja az
    //   érintett Composable-ök újrakomponálását (recomposition). Innen jön a UI reaktivitása.

    // A választható témaszínek listája. remember { }, mert konstans — nem kell újraépíteni
    // minden recompositionkor, és forgatáskor sem változik (az AKTÍV index viszont mentendő, lásd lentebb).
    val themeOptions = remember {
        listOf(
            AppThemeColor(R.string.theme_purple, Color(0xFFB180FF)),
            AppThemeColor(R.string.theme_green, Color(0xFF00FF87)),
            AppThemeColor(R.string.theme_gold, Color(0xFFFFB300)),
            AppThemeColor(R.string.theme_blue, Color(0xFF00D2FF)),
            AppThemeColor(R.string.theme_orange, Color(0xFFFF6B00))
        )
    }
    // A kiválasztott téma indexe felhasználói beállítás → SharedPreferences-ből betöltve
    var currentThemeIndex by rememberSaveable {
        mutableStateOf(prefs.getInt("theme_index", 0))
    }
    // Származtatott (derived) érték: az aktuális témaszín. Nem külön állapot, csak az indexből
    // olvasott érték — minden recompositionkor újraszámolódik, ami itt triviálisan olcsó.
    val themeColor = themeOptions[currentThemeIndex].color

    // --- Kamera-beállítási állapotok ---
    // Ezek remember (nem Saveable): forgatás után úgyis újra bindoljuk a kamerát, és ezek az
    // objektumok/flag-ek a bind során frissen előállnak. A Camera nem is Bundle-elhető.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCameraBindingFailed by remember { mutableStateOf(false) } // a bind meghiúsult-e
    var isCameraCheckingFinished by remember { mutableStateOf(false) } // lefutott-e már a kamera-ellenőrzés (loader vs. tartalom)
    var previewUseCase by remember { mutableStateOf<Preview?>(null) } // a CameraX Preview use case (élő kép forrása)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) } // a still capture use case (fotó fagyasztáshoz/mentéshez)

    // A klasszikus Android View (PreviewView), amely az élő kameraképet rajzolja. Egyszer hozzuk
    // létre remember-rel, hogy STABIL objektum maradjon (ne készüljön újra minden recompositionkor).
    //   - FILL_CENTER: a preview kitölti a nézetet, a széleket levágja (a képarány-eltéréshez).
    //   - COMPATIBLE (TextureView) mód: kritikus, mert így a previewView.bitmap (getBitmap())
    //     mindig működik — ezzel készül a fagyasztott pillanatkép és a minimap thumbnail-je.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // Crucial for getBitmap() to work 100% of the time!
        }
    }

    // --- Interaktív zoom-állapotok ---
    // liveZoomRatio = a KAMERA (optikai/hibrid) zoomja; Saveable, mert felhasználói beállítás.
    // minZoom/maxZoom = a kamera által ténylegesen támogatott tartomány; remember, mert a kamera
    // zoomState-observerből frissül binding után (lásd KAMERA-BIND szekció) — nem kézi beállítás.
    var liveZoomRatio by rememberSaveable { mutableStateOf(1.0f) }
    var minZoom by remember { mutableStateOf(1.0f) }
    var maxZoom by remember { mutableStateOf(8.0f) }

    // --- Több-kamerás állapotok ---
    // availableCameras a bindkor derül ki (remember), a kiválasztott index viszont mentendő (Saveable).
    var availableCameras by remember { mutableStateOf<List<androidx.camera.core.CameraInfo>>(emptyList()) }
    var selectedCameraIndex by rememberSaveable { mutableStateOf(0) }

    // --- Kijelző-oldali (szoftveres) digitális zoom és pásztázás (pan) az élő previewn ---
    // Amikor a kamera optikai zoomja már kimaxolt, innen jön a további nagyítás szoftveresen.
    // Az extraDigitalPan egy Offset (x,y eltolás) → egyedi OffsetSaver kell a mentéséhez (lásd fent).
    var extraDigitalZoom by rememberSaveable { mutableStateOf(1.0f) }
    var extraDigitalPan by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    var torchEnabled by rememberSaveable { mutableStateOf(false) } // zseblámpa (vaku) be/ki — mentendő beállítás

    // --- Expozíció (élő fényerő-kompenzáció a kamerán) ---
    // exposureIndex mentendő; a min/max tartomány a kamerától jön binding után (remember).
    var exposureIndex by rememberSaveable { mutableStateOf(0) }
    var minExposureIndex by remember { mutableStateOf(-4) }
    var maxExposureIndex by remember { mutableStateOf(4) }

    // --- Fagyasztás (freeze) és feldolgozás-jelző ---
    // isProcessing = épp fut-e háttérfeldolgozás (spinner overlay-hez) — nem kell túlélnie forgatást.
    // isFrozen = kimerevített módban vagyunk-e; Saveable, hogy forgatás után is fagyasztva maradjon.
    var isProcessing by remember { mutableStateOf(false) }
    var isFrozen by rememberSaveable { mutableStateOf(false) }

    // derivedStateOf: SZÁRMAZTATOTT állapot. Akkor és csak akkor számol újra, ha a benne OLVASOTT
    // állapotok (isFrozen, minZoom) tényleg változnak — és a rá figyelő UI is csak ekkor komponálódik
    // újra, akkor sem, ha az eredmény történetesen ugyanaz maradt. A remember(isFrozen, minZoom) kulcsai
    // gondoskodnak róla, hogy a derivedStateOf blokk maga is frissüljön a függőségek változásakor.
    // Jelentés: fagyasztott módban 0.5x-ig lehet kicsinyíteni, élőben a kamera minZoomja a plafon lefelé.
    val sliderMin by remember(isFrozen, minZoom) {
        derivedStateOf { if (isFrozen) 0.5f else minZoom }
    }
    val sliderMax = 64.0f // a slider felső korlátja (a teljes: optikai + digitális zoom)
    // A gyors-zoom "preset" gombok értékei (pl. 1x, 2x, 4x...). Kezdetben egy ésszerű default lista,
    // amit a lenti LaunchedEffect a mód és a kamera-tartomány szerint felülír.
    var presets by remember { mutableStateOf(listOf(1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f)) }
    // Preset-lista frissítése, amikor a mód vagy a zoom-tartomány változik (a kulcsokról lásd az
    // EFFEKTEK szekciót). Fagyasztott módban fix lista (0.5x-től); élőben a getOpticalSteps a
    // konkrét kamera optikai lencséihez igazított lépcsőket ad. A getOpticalSteps CameraManager-t
    // kérdez (lassabb, I/O-jellegű), ezért withContext(Dispatchers.IO)-val háttérszálra tesszük,
    // hogy ne akassza meg a fő (UI) szálat.
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
    // A computeZoomDistribution (ZoomLogic.kt) logikája: ameddig a kamera optikai/hibrid zoomja
    // elviszi (<= maxZoom), addig CSAK a kamera zoomol (élesebb kép); afölött a maradékot szoftveres
    // digitális szorzóként tesszük rá. A resetPan / digitalZoom<=1 esetén az eltolást is nullázzuk,
    // mert 1x digitális zoomnál nincs mit pásztázni.
    fun applyTotalZoom(target: Float, resetPan: Boolean) {
        val distribution = computeZoomDistribution(target.coerceIn(minZoom, sliderMax), minZoom, maxZoom)
        liveZoomRatio = distribution.cameraZoom
        extraDigitalZoom = distribution.digitalZoom
        if (resetPan || distribution.digitalZoom <= 1.0f) {
            extraDigitalPan = Offset.Zero
        }
    }

    // A NYERS kimerevített képkocka. A "by viewModel::property" szintaxis magát a ViewModel
    // property-jét delegálja: a rawFrozenBitmap írása/olvasása közvetlenül a ViewModel mezőjét
    // éri el (ami maga is mutableStateOf, tehát reaktív). Azért a ViewModel-ben van, mert egy
    // teljes felbontású Bitmap túl nagy a savedInstanceState-hez (lásd MagnifierViewModel.kt).
    var rawFrozenBitmap by magnifierViewModel::rawFrozenBitmap
    // A MEGJELENÍTENDŐ kimerevített kép: a nyers képkocka esetleg élesített (sharpen) változata.
    // remember (nem Saveable), mert a nyersből bármikor újraszámolható, és bitmap úgysem menthető.
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharpenStrength by rememberSaveable { mutableStateOf(0.0f) } // 0.0f (Off) to 2.0f (Very Strong)
    // Az élő digitális zoomhoz készített, háttérben élesített overlay-kép (lásd lentebbi effekt).
    var liveSharpenedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Támogatás (Tip Jar) állapotai és segédosztálya
    val billingHelper = remember { BillingHelper(context, coroutineScope) }
    var showTipJar by remember { mutableStateOf(false) }

    // ========================================================================
    //  2. EFFEKTEK (side effects) — állapotváltozásra reagáló mellékhatások
    // ========================================================================
    //
    // Egy @Composable függvénynek "tisztának" kellene lennie: pusztán a UI-t írja le, nem futtat
    // mellékhatást (kamera-parancs, hálózat, timer...) a törzsében — hisz recompositionkor akárhányszor
    // lefuthat. A mellékhatásokat ezért külön EFFEKT-blokkokba tesszük:
    //
    //   LaunchedEffect(kulcs1, kulcs2, ...) { ... }
    //     - Elindít egy coroutine-t, amikor az effekt először a kompozícióba kerül.
    //     - Ha BÁRMELYIK kulcs megváltozik, a régi coroutine-t MEGSZAKÍTJA (cancel) és ÚJRAINDÍTJA.
    //     - Ha a kulcs nem változik, recompositionkor NEM indul újra.
    //     - LaunchedEffect(Unit): a kulcs konstans → pontosan egyszer fut le (a képernyő élete alatt).
    //
    //   DisposableEffect(kulcs) { ... onDispose { ... } }
    //     - Akkor kell, ha TAKARÍTANI is muszáj (erőforrás felszabadítása), amikor az effekt eltűnik
    //       vagy a kulcs változik. Az onDispose { } blokk a "cleanup".
    //
    //   withContext(Dispatchers.X): SZÁLVÁLTÁS egy coroutine-on belül.
    //     - Dispatchers.Main   : a fő (UI) szál — CSAK ide szabad UI-állapotot írni.
    //     - Dispatchers.Default: CPU-intenzív munka (élesítés, szűrő-számítás) — hogy ne akadjon a UI.
    //     - Dispatchers.IO     : blokkoló I/O (fájl, CameraManager-lekérdezés) — sok várakozó szál.
    //     Az aranyszabály: nehéz munka NE a fő szálon fusson, különben a UI beakad ("jank"/ANR).

    // Process death után a savedInstanceState visszaáll, de a ViewModel-beli bitmap már nincs meg —
    // ilyenkor vissza élő módba. (LaunchedEffect(Unit): egyszer fut, induláskor.)
    LaunchedEffect(Unit) {
        if (isFrozen && rawFrozenBitmap == null) {
            isFrozen = false
        }
    }

    // Ha visszatérünk az élő képhez (isFrozen = false), az élesítés erősségét visszaállítjuk 0-ra.
    LaunchedEffect(isFrozen) {
        if (!isFrozen) {
            sharpenStrength = 0.0f
        }
    }

    // A kimerevített kép aszinkron élesítése. Újrafut, ha a nyers kép VAGY az élesítés-erősség változik.
    // A drága sharpenBitmap Dispatchers.Default-on (háttérszál) fut, majd az eredményt Dispatchers.Main-en
    // (fő szál) írjuk vissza a frozenBitmap állapotba — mert UI-állapotot csak a fő szálról szabad módosítani.
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

    // --- Kép-korrekciós beállítások (elsősorban a kimerevített képen látszanak) ---
    // Mind felhasználói beállítás → rememberSaveable. A kontraszt/fényerő ÉLŐ nézetben szándékosan
    // nem látszik (a natív preview nem tudja élőben alkalmazni), csak fagyasztott képen — ezt a
    // WYSIWYG-logika (lásd combinedColorFilter / processExportBitmap) következetesen kezeli.
    var contrast by rememberSaveable { mutableStateOf(1.0f) } // 1.0f (Normal) to 3.0f (High Contrast)
    var brightness by rememberSaveable { mutableStateOf(0.0f) } // -100f to 100f
    var filterMode by rememberSaveable { mutableStateOf(FilterMode.NORMAL) }

    // --- A kimerevített kép saját digitális zoom/pan állapota ---
    // (Az élő módé az extraDigitalZoom/extraDigitalPan; fagyasztáskor ezekbe másoljuk át — lásd onToggleFreeze.)
    var frozenScale by rememberSaveable { mutableStateOf(1.0f) }
    var frozenOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    // A viewfinder (nézőke) tényleges pixelmérete. Debben a Compose méri be az onSizeChanged-del (lásd UI szekció),
    // és kell a pan-határok (clampPan) és a minimap geometria kiszámításához. remember: layoutfüggő, nem beállítás.
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Ha a zoom CSÖKKEN, a korábbi (nagyobb zoomhoz tartozó) pan-eltolás túlléphetné az új, szűkebb
    // határt, és a kép "elcsúszna" a viewporttól. Ezért zoom-változáskor visszaszorítjuk (clampPan)
    // az eltolást az érvényes tartományba. Külön effekt az élő és a fagyasztott zoomhoz.
    LaunchedEffect(extraDigitalZoom) {
        extraDigitalPan = clampPan(extraDigitalPan, extraDigitalZoom, viewportSize)
    }

    LaunchedEffect(frozenScale) {
        frozenOffset = clampPan(frozenOffset, frozenScale, viewportSize)
    }

    // --- UI-overlay / státusz állapotok ---
    var activeTab by rememberSaveable { mutableStateOf(0) } // 0: Nagyítás, 1: Szűrők és Korrekció, 2: Beállítások
    // Az egyedi (Compose-rajzolt) "toast" értesítés ikonja/alikonja/színe és láthatósága.
    // Nem a rendszeres Android Toast, hanem saját animált kártya (lásd UI szekció legvégén).
    var toastIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector>(Icons.Default.CheckCircle) }
    var toastSubIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var toastColor by remember { mutableStateOf(Color(0xFF10B981)) }
    var showSavedToast by remember { mutableStateOf(false) }
    // A kezelőszervek láthatósága (teljes képernyős mód). Saveable: forgatás után is maradjon rejtve/látszó.
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    // Az élő minimap-hoz periodikusan lekapott thumbnail (kicsinyített pillanatkép a previewból).
    var liveThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Grab thumbnail dynamically and generate sharpened live overlay when digital zoom is active in live mode.
    // Az overlay kizárólag aktív élesítésnél jelenik meg: élesítés nélkül a natív preview
    // élesebb, mint bármilyen leskálázott bitmap-másolat.
    //
    // FONTOS a kulcs: LaunchedEffect(extraDigitalZoom > 1.0f). A kulcs egy Boolean, NEM maga az
    // extraDigitalZoom float. Így a polling-hurok csak akkor indul újra, amikor átbillenünk a
    // "van digitális zoom" / "nincs" határon — nem pedig minden apró zoom-változásnál. A hurokban
    // futó coroutine-t a LaunchedEffect automatikusan megszakítja, amint a feltétel false lesz.
    LaunchedEffect(extraDigitalZoom > 1.0f) {
        if (extraDigitalZoom > 1.0f) {
            // Végtelen ciklus, amíg a coroutine él (a delay(120) alján lélegzik). A megszakításkor
            // dobott CancellationException-t szándékosan NEM nyeljük el (lásd a catch-ben), mert az a
            // coroutine rendes leállásának a jele — el kell engednünk.
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
                    // A CancellationException a coroutine leállításának jele → továbbdobjuk (kötelező).
                    // Minden más (pl. átmeneti getBitmap-hiba) elnyelhető: a következő körben újrapróbáljuk.
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // ignore errors fetching bitmap
                }
                delay(120) // 8-10 FPS is more than enough for live digital zoom overlay and preserves battery/CPU
            }
        } else {
            // Nincs digitális zoom → nincs szükség overlay-re/thumbnail-re; felszabadítjuk a referenciát.
            liveThumbnailBitmap = null
            liveSharpenedBitmap = null
        }
    }

    // Tap-to-focus vizuális visszajelzés: hova koppintottak (focusPoint) és egy "trigger" számláló.
    // A focusTrigger azért kell, mert ugyanarra a pontra ismételt koppintásnál a focusPoint értéke
    // nem változna → a hozzá kötött animációs effekt nem indulna újra. A számláló minden koppintásnál
    // nő, így garantáltan új kulcs, és az elhalványító effekt (lásd lentebb) mindig újraindul.
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTrigger by remember { mutableStateOf(0) }

    // ÉLŐ vs. KIMEREVÍTETT (frozen) MÓD és a WYSIWYG-elv ("what you see is what you get"):
    //   - Kimerevített képen a szűrőt + kontraszt + fényerőt egy ColorFilter (color matrix) alkalmazza
    //     a bitmapre — ez a combinedColorFilter.
    //   - ÉLŐ previewre viszont nem tehetünk ColorFilter-t (a natív kamerakép Surface-én rajzol),
    //     ezért ott a szűrőt Canvas blend-módokkal rakjuk RÁ (lásd a UI szekcióban a Difference/Color/
    //     Modulate rétegeket). A két út MATEMATIKAILAG UGYANAZT az eredményt adja, így amit a
    //     felhasználó lát, pontosan az kerül a mentett/megosztott képre is (WYSIWYG).
    // Mindkét ColorFilter remember(kulcsok)-kal van memoizálva: csak akkor épül újra, ha a benne
    // használt beállítás változik — a color matrix előállítása felesleges munka volna minden frame-en.
    val combinedColorFilter = remember(filterMode, contrast, brightness) {
        ColorFilter.colorMatrix(ColorMatrix(buildFilterMatrixValues(filterMode, contrast, brightness)))
    }
    // Élő módban a kontraszt/fényerő nem látszik a previewn, ezért a minimap is szűrő-only mátrixot kap
    // (contrast=1.0, brightness=0.0), hogy a kicsinyített nézet UGYANAZT mutassa, mint a nagy élő kép.
    val liveColorFilter = remember(filterMode) {
        ColorFilter.colorMatrix(ColorMatrix(buildFilterMatrixValues(filterMode, 1.0f, 0.0f)))
    }

    // Az egyedi toast automatikus eltüntetése 3 másodperc után. A kulcs a showSavedToast: valahányszor
    // true-ra állítjuk, az effekt újraindul, kivárja a 3 mp-et, majd visszakapcsolja false-ra.
    LaunchedEffect(showSavedToast) {
        if (showSavedToast) {
            delay(3000)
            showSavedToast = false
        }
    }

    // A tap-to-focus gyűrű eltüntetése 1,2 mp után. A kulcs a focusTrigger (nem a focusPoint!), így
    // ugyanoda ismételt koppintásnál is újraindul a visszaszámlálás (lásd a focusTrigger magyarázatát fent).
    LaunchedEffect(focusTrigger) {
        if (focusPoint != null) {
            delay(1200)
            focusPoint = null
        }
    }

    // REAKTÍV KAMERA-PARANCSOK. Ez a három effekt köti az állapotot a valódi kamerához:
    // valahányszor a hozzá tartozó állapot (zoom / vaku / expozíció) változik, az effekt lefut és
    // átküldi a parancsot a kamera cameraControl-jának.
    //
    // MIÉRT SZEREPEL A "camera" IS KULCSKÉNT? Mert a camera objektum újra létrejön minden rebindnél
    // (pl. forgatás vagy kameraváltás után az új kamerát bindoljuk). Az új kamera "üres" — nem tudja
    // a korábbi zoom/vaku/expozíció beállítást. Azzal, hogy a camera is kulcs, ezek az effektek a
    // rebind után AUTOMATIKUSAN újrafutnak, és a (Saveable-ből visszaállított) értékeket ismét
    // ráállítják az új kamerára. A try/catch azért kell, mert egyes eszközök nem támogatnak minden
    // vezérlést; hiba esetén csak logolunk, nem omlik össze az app.
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

    // TAKARÍTÁS DisposableEffect-tel. Amikor ez a képernyő végleg elhagyja a kompozíciót (az app
    // bezárul / elnavigálunk), az onDispose { } lefut, és leválasztjuk a kamerát (unbindAll), hogy
    // a kamera-erőforrás felszabaduljon más appok/rendszer számára. A kulcs Unit → egyszer regisztrál,
    // egyszer takarít. Az isDone ellenőrzés megvédi attól, hogy egy még be sem fejezett future-t
    // blokkolva várjunk meg (a .get() különben blokkolna).
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

    // ========================================================================
    //  3. KAMERA-BIND — a CameraX kamera "bekötése" az életciklusba
    // ========================================================================
    //
    // Röviden a CameraX szereplőiről:
    //   - ProcessCameraProvider: a kamera-alrendszer belépési pontja; aszinkron áll elő (ListenableFuture),
    //     ezért awaitListenableFuture-rel várjuk meg (nem blokkoló módon, suspend függvényként).
    //   - Preview            : "use case" az élő kép megjelenítéséhez (a PreviewView Surface-ére köti).
    //   - ImageCapture       : "use case" fotó készítéséhez (a fagyasztás natív felbontású still-je).
    //   - CameraSelector     : megmondja, MELYIK fizikai kamerát szeretnénk (itt egy egyedi szűrővel a
    //                          pontosan kiválasztott CameraInfo-t célozzuk, hogy több hátsó lencse közül is válthassunk).
    //   - bindToLifecycle    : az egészet a lifecycleOwner-höz köti — a CameraX ettől kezdve maga
    //                          indítja/állítja le a kamerát az Activity életciklusa szerint.
    //
    // A kulcs a selectedCameraIndex: kameraváltáskor újra lefut az egész bind-folyamat az új kamerára.
    LaunchedEffect(selectedCameraIndex) {
        // Engedély nélkül nincs mit bindolni: jelezzük, hogy az ellenőrzés kész (a hívó ág ilyenkor
        // a NoCameraScreen-t mutatja), és korán kilépünk (return@LaunchedEffect a címkézett return).
        if (!hasCameraPermission(context)) {
            isCameraCheckingFinished = true
            return@LaunchedEffect
        }
        try {
            val cameraProvider = awaitListenableFuture(ProcessCameraProvider.getInstance(context), context)
            val cameraInfos = cameraProvider.availableCameraInfos
            availableCameras = cameraInfos

            if (cameraInfos.isEmpty()) {
                // Nincs elérhető kamera → hibaállapot, a UI a NoCameraScreen-re vált.
                isCameraBindingFailed = true
                previewUseCase = null
                isCameraCheckingFinished = true
                return@LaunchedEffect
            }

            // A mentett index túlcsúszhat, ha kevesebb kamera van, mint korábban → beszorítjuk a tartományba.
            val index = selectedCameraIndex.coerceIn(0, cameraInfos.lastIndex)
            val selectedCameraInfo = cameraInfos[index]

            val preview = Preview.Builder().build()

            // MINIMIZE_LATENCY: a fotó a lehető leggyorsabban készüljön el (a fagyasztás azonnaliságához),
            // a maximális minőség helyett a késleltetés minimalizálására optimalizál.
            val localImageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = localImageCapture

            // Egyedi szűrő, amely PONTOSAN a kiválasztott fizikai kamerát célozza (nem csak "elülső/hátsó").
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { infos ->
                    infos.filter { it == selectedCameraInfo }
                }
                .build()

            isCameraBindingFailed = false
            // A bind előtt MINDIG unbindAll: egy lifecycle-owner-hez ne kötődjön kétszer ugyanaz a use case,
            // különben ütközés/hiba lehet (pl. kameraváltáskor a régi kötést el kell engedni).
            cameraProvider.unbindAll()
            val cameraInstance = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                localImageCapture
            )
            // Az új camera objektum beállítása → ez indítja el a fenti reaktív parancs-effekteket (camera kulcs),
            // amelyek visszaállítják rá a zoom/vaku/expozíció beállításokat.
            camera = cameraInstance
            previewUseCase = preview

            // OBSERVER a kamera zoom-képességeire. A zoomState egy LiveData: a .observe(lifecycleOwner) { }
            // callback minden változáskor lefut, és a lifecycleOwner-höz van kötve (automatikusan leiratkozik).
            // Innen tudjuk meg a KONKRÉT kamera valódi min/max optikai zoomját.
            cameraInstance.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                // Védekezés a hibás/hiányzó értékek ellen: 0, NaN vagy végtelen esetén ésszerű
                // alapértékre (1.0x / 8.0x) esünk vissza, nehogy a UI érvénytelen zoom-tartományt kapjon.
                val mz = if (zoomState.minZoomRatio <= 0f || zoomState.minZoomRatio.isNaN() || zoomState.minZoomRatio.isInfinite()) 1.0f else zoomState.minZoomRatio
                val xz = if (zoomState.maxZoomRatio <= 0f || zoomState.maxZoomRatio.isNaN() || zoomState.maxZoomRatio.isInfinite()) 8.0f else zoomState.maxZoomRatio
                minZoom = mz
                maxZoom = maxOf(mz, xz) // biztosítjuk, hogy max sose legyen kisebb a minnél
                // Ha a jelenlegi zoom kívül esik az új tartományon, visszahúzzuk a határra.
                if (liveZoomRatio < minZoom) liveZoomRatio = minZoom
                if (liveZoomRatio > maxZoom) liveZoomRatio = maxZoom
            }

            // Az expozíció-kompenzáció tartományának kiolvasása. A mentett exposureIndex-et NEM írjuk
            // felül, csak beszorítjuk (coerceIn) az új kamera által támogatott tartományba — így a
            // beállítás átvihető marad kameraváltás után is, ha a tartomány engedi.
            val exposureState = cameraInstance.cameraInfo.exposureState
            minExposureIndex = exposureState.exposureCompensationRange.lower
            maxExposureIndex = exposureState.exposureCompensationRange.upper
            exposureIndex = exposureIndex.coerceIn(minExposureIndex, maxExposureIndex)
        } catch (exc: Exception) {
            // Bármilyen bind-hiba esetén hibaállapot → a UI a NoCameraScreen-t mutatja.
            Log.e("Magnifier", "Use case binding failed", exc)
            isCameraBindingFailed = true
            previewUseCase = null
        } finally {
            // A finally MINDIG lefut (siker és hiba esetén is): innentől nem a loadert mutatjuk.
            isCameraCheckingFinished = true
        }
    }

    // --- Korai kilépő ágak a UI felépítése ELŐTT ---
    // Amíg a kamera-ellenőrzés fut, csak egy töltés-jelzőt (spinner) mutatunk, és return-nel kilépünk.
    // Egy @Composable-ből a korai return teljesen legális: az adott állapotban ennyi a képernyő.
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

    // Ha nincs kamera vagy a bind meghiúsult, a dedikált hibaképernyőt mutatjuk és kilépünk.
    if (availableCameras.isEmpty() || isCameraBindingFailed) {
        NoCameraScreen()
        return
    }

    // ========================================================================
    //  4. UI — a képernyő felépítése az állapotból + a kiemelt komponensek hívása
    // ========================================================================
    //
    // Scaffold: a Material3 alap-váz. Kezeli a rendszer-insetteket (státuszsáv, navigációs sáv);
    // az innerPadding ezekhez ad biztonságos térközt, amit lentebb a padding()-ekben használunk fel.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF09090B)
    ) { innerPadding ->
        // A legkülső Box a teljes képernyő. Box = rétegelt elrendezés: a gyerekek EGYMÁSRA kerülnek
        // (rajzolási sorrendben), és .align(...)-nal igazíthatók a Box sarkaihoz/széleihez. Így ül a
        // viewfinder alul, az overlay-ek (minimap, vezérlők, toast, spinner) pedig fölötte.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B))
        ) {
            // A viewfinder (nézőke) teljes képernyős, hogy a lehető legnagyobb legyen a nagyított terület.
            // onSizeChanged: a Compose ide adja vissza a Box tényleges pixelméretét → viewportSize.
            // Ezt használja a pan-korlát (clampPan) és a minimap geometria.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { viewportSize = it }
            ) {
                // A fő nézet KÉT állapota: élő kamerakép VAGY kimerevített statikus bitmap.
                if (!isFrozen) {
                    // ---- ÉLŐ MÓD (LIVE) ----
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // AndroidView: klasszikus Android View (a PreviewView) BEÁGYAZÁSA Compose-ba.
                        // A Compose önmagában nem tud kameraképet rajzolni — a CameraX egy hagyományos
                        // View-ba (PreviewView) rajzol, ezt az AndroidView "hídon" keresztül tesszük a fába.
                        //   - factory: egyszer állítja elő a View-t (a stabil previewView-t adjuk).
                        //   - update : recompositionkor fut; ide kötjük a Preview use case-t a View Surface-ére.
                        AndroidView(
                            factory = { previewView },
                            update = {
                                previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
                            },
                            // graphicsLayer: a preview SZOFTVERES (digitális) nagyítása/eltolása. A GPU
                            // egyszerűen skálázza/tolja a már megrajzolt réteget — ez a kamera optikai
                            // zoomján FELÜLI extra digitális zoom. scaleX/scaleY = nagyítás középpontból,
                            // translationX/Y = pásztázás (pan).
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = extraDigitalZoom
                                    scaleY = extraDigitalZoom
                                    translationX = extraDigitalPan.x
                                    translationY = extraDigitalPan.y
                                }
                        )

                        // Az élesített overlay-kép RÁ, ugyanazzal a graphicsLayer transzformációval, hogy
                        // pixelre pontosan fedje a natív previewt. A ?.let { } csak akkor rajzol, ha van
                        // ilyen kép (aktív élesítésnél a fenti polling-effekt állítja elő).
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

                        // ÉLŐ SZŰRŐK Canvas blend-móddal (lásd a WYSIWYG-magyarázatot fentebb). A natív
                        // previewre nem tehető ColorFilter, ezért egy áttetsző Canvas-réteget rajzolunk RÁ,
                        // és a blendMode dönti el, hogyan keveredik a mögötte lévő képpel. Ezek matematikailag
                        // megegyeznek a buildFilterMatrixValues color matrix-ával (ImageProcessing.kt).
                        if (filterMode == FilterMode.INVERTED) {
                            // Difference fehérrel: out = |white - src| = 255 - src → színinvertálás (negatív kép).
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    color = Color.White,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Difference
                                )
                            }
                        } else if (filterMode == FilterMode.MONOCHROME) {
                            // Color blend szürkével: átveszi a szürke telítettségét (0) → deszaturálás (fekete-fehér).
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

                        // ÉRINTÉS-ELKAPÓ ÁTTETSZŐ RÉTEG a preview és a Canvas-ek FÖLÖTT. Azért kell külön,
                        // legfelső Box, mert az AndroidView (natív View) elnyelhetné az érintéseket — így
                        // viszont minden gesztust megbízhatóan itt fogunk el. A pointerInput a gesztus-
                        // felismerés belépője; két külön blokk, hogy a transform- és a tap-gesztusok ne
                        // zavarják egymást. A kulcs Unit → a felismerő a Box élete alatt stabil marad.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // detectTransformGestures: pinch-zoom + pásztázás. A lambda paraméterei:
                                    // (centroid, pan, zoom, rotation) — minket a pan (eltolás-delta) és a
                                    // zoom (relatív nagyítási szorzó, 1f = nincs változás) érdekel.
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (zoom != 1f) {
                                            // Egységes zoom-logika: a JELENLEGI teljes nagyítást (kamera * digitális)
                                            // szorozzuk a gesztus relatív zoomjával, és az applyTotalZoom újra elosztja
                                            // kamera- és digitális részre. Így a pinch folyamatos és zökkenőmentes.
                                            applyTotalZoom(liveZoomRatio * extraDigitalZoom * zoom, resetPan = false)
                                        }

                                        // Pásztázás: az ujj-elmozdulást (pan) hozzáadjuk az eltoláshoz, majd
                                        // clampPan visszaszorítja a megengedett tartományba (ne csússzon le a kép).
                                        extraDigitalPan = clampPan(extraDigitalPan + pan, extraDigitalZoom, viewportSize)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            // Dupla koppintás = zoom-váltó. Ha épp nagyítva vagyunk (a teljes
                                            // zoom > ~1x), visszaállunk 1x-re; különben a tartomány közepére
                                            // ugrunk. A 0.05f küszöb a lebegőpontos pontatlanságot tolerálja.
                                            val currentZoom = liveZoomRatio * extraDigitalZoom
                                            if (Math.abs(currentZoom - 1.0f) > 0.05f) {
                                                applyTotalZoom(1.0f, resetPan = true)
                                            } else {
                                                applyTotalZoom((minZoom + maxZoom) / 2.0f, resetPan = true)
                                            }
                                        },
                                        onTap = {
                                            // A koppintás a kezelőszervek láthatóságát váltja.
                                            controlsVisible = !controlsVisible
                                        }
                                    )
                                }
                        )
                    }
                } else {
                    // ---- KIMEREVÍTETT (FROZEN) MÓD ----
                    // Itt már egy statikus bitmapet mutatunk, saját digitális zoommal (frozenScale) és
                    // eltolással (frozenOffset). Nincs kamera-parancs, minden tisztán kijelző-oldali.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Pinch-zoom a fagyasztott képen: itt egyszerűbb, mert nincs kamera-zoom,
                                // csak a frozenScale-t szorozzuk a gesztus zoomjával (0.5x..64x közé szorítva).
                                detectTransformGestures { _, pan, zoom, _ ->
                                    frozenScale = (frozenScale * zoom).coerceIn(0.5f, sliderMax)
                                    frozenOffset = clampPan(frozenOffset + pan, frozenScale, viewportSize)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    // Dupla koppintás: váltás 1x és 3x között, az eltolás nullázásával.
                                    onDoubleTap = {
                                        frozenScale = if (frozenScale > 1.0f) 1.0f else 3.0f
                                        frozenOffset = Offset.Zero
                                    },
                                    // Fagyasztott módban a koppintás a kezelőszervek láthatóságát váltja.
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        // A kimerevített kép megjelenítése. A combinedColorFilter (szűrő + kontraszt +
                        // fényerő color matrix) ITT, ColorFilter-ként kerül rá — szemben az élő móddal,
                        // ahol Canvas blend-móddal (WYSIWYG: a kétféle út ugyanazt az eredményt adja).
                        // A graphicsLayer itt a frozenScale/frozenOffset szerint nagyít/tol.
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

                // MINIMAP (picture-in-picture) — csak akkor látszik, ha aktív a digitális zoom ÉS
                // a kezelőszervek is láthatók. Megmutatja a teljes képet, benne kiemelve, hogy épp
                // melyik kivágást látjuk nagyítva.
                //
                // AnimatedVisibility: animáltan jeleníti meg/tünteti el a tartalmát a "visible" flag
                // változásakor (itt fadeIn/fadeOut = elhalványítás). Az .align(Alignment.TopEnd) a
                // szülő Box jobb-felső sarkába helyezi; a padding a rendszer-inset fölé tolja.
                // A ZoomMinimap maga a MagnifierComponents.kt-ban van — ide csak a kirajzoláshoz szükséges
                // állapotot adjuk át paraméterként (state hoisting).
                val isDigitalZoomActive = (if (isFrozen) frozenScale > 1.0f else extraDigitalZoom > 1.0f) && controlsVisible
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDigitalZoomActive,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp, end = 16.dp)
                ) {
                    ZoomMinimap(
                        isFrozen = isFrozen,
                        frozenBitmap = frozenBitmap,
                        liveThumbnailBitmap = liveThumbnailBitmap,
                        frozenScale = frozenScale,
                        extraDigitalZoom = extraDigitalZoom,
                        frozenOffset = frozenOffset,
                        extraDigitalPan = extraDigitalPan,
                        viewportSize = viewportSize,
                        combinedColorFilter = combinedColorFilter,
                        liveColorFilter = liveColorFilter,
                        themeColor = themeColor
                    )
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
                    TopLeftControls(
                        themeColor = themeColor,
                        isFrozen = isFrozen,
                        availableCameras = availableCameras,
                        selectedCameraIndex = selectedCameraIndex,
                        onSwapCamera = {
                            if (availableCameras.isNotEmpty()) {
                                // Kameraváltásnál a vaku kikapcsol (forgatásnál viszont megmarad)
                                torchEnabled = false
                                selectedCameraIndex = (selectedCameraIndex + 1) % availableCameras.size
                            }
                        }
                    )
                }
            }

            // Sleek, semi-transparent frosted card container at the bottom with animations
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.40f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                        .background(Color(0xE60D0C11), RoundedCornerShape(28.dp))
                        .border(1.dp, Color(0xFF2E2C33).copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { /* Consumes clicks to prevent toggling controls visibility */ }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Controls content depending on Active Tab (Fills remaining space in the 40% height UI)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .animateContentSize()
                    ) {
                        when (activeTab) {
                            0 -> CombinedZoomFiltersTuneTabContent(
                                appVersion = appVersion,
                                themeColor = themeColor,
                                isFrozen = isFrozen,
                                frozenScale = frozenScale,
                                onFrozenScaleChange = { frozenScale = it },
                                liveZoomRatio = liveZoomRatio,
                                extraDigitalZoom = extraDigitalZoom,
                                maxZoom = maxZoom,
                                sliderMin = sliderMin,
                                sliderMax = sliderMax,
                                presets = presets,
                                onApplyTotalZoom = { target, resetPan -> applyTotalZoom(target, resetPan) },
                                filterMode = filterMode,
                                onFilterModeChange = { filterMode = it },
                                contrast = contrast,
                                onContrastChange = { contrast = it },
                                brightness = brightness,
                                onBrightnessChange = { brightness = it },
                                exposureIndex = exposureIndex,
                                onExposureIndexChange = { exposureIndex = it },
                                minExposureIndex = minExposureIndex,
                                maxExposureIndex = maxExposureIndex,
                                sharpenStrength = sharpenStrength,
                                onSharpenStrengthChange = { sharpenStrength = it }
                            )
                            1 -> SettingsTabContent(
                                appVersion = appVersion,
                                themeColor = themeColor,
                                themeOptions = themeOptions,
                                currentThemeIndex = currentThemeIndex,
                                onThemeIndexChange = { index ->
                                    currentThemeIndex = index
                                    prefs.edit().putInt("theme_index", index).apply()
                                },
                                onRateApp = {
                                    val packageName = context.packageName
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("market://details?id=$packageName")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                        }
                                        context.startActivity(webIntent)
                                    }
                                    prefs.edit().putBoolean("rate_done", true).apply()
                                },
                                onShowTutorial = {
                                    showWalkthrough = true
                                },
                                onShowTipJar = {
                                    showTipJar = true
                                },
                                currentLanguage = currentLanguage,
                                onChangeLanguage = onChangeLanguage
                            )
                        }
                    }

                    // 2. Compact divider line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF2E2C33).copy(alpha = 0.35f))
                    )

                    // 3. Fő akció-gombsor (zseblámpa, mentés, kimerevítés/folytatás, megosztás).
                    // Maga a gombsor a MagnifierComponents.kt-ban van; a tényleges műveleteket
                    // itt, az állapot közelében definiált lambdákként (state hoisting) adjuk át.
                    // Így a gombsor "buta" (csak megjelenít), a logika pedig ott van, ahol az
                    // összes szükséges állapot (previewView, filterMode, contrast, ...) elérhető.
                    ActionButtonsRow(
                        themeColor = themeColor,
                        torchEnabled = torchEnabled,
                        isFrozen = isFrozen,
                        onToggleTorch = { torchEnabled = !torchEnabled },
                        onSave = {
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

                                            // Növeljük a mentések számát és ellenőrizzük az értékelést
                                            val saveCount = prefs.getInt("save_count", 0) + 1
                                            prefs.edit().putInt("save_count", saveCount).apply()

                                            val rateNever = prefs.getBoolean("rate_never", false)
                                            val rateDone = prefs.getBoolean("rate_done", false)
                                            if (saveCount >= 3 && !rateNever && !rateDone) {
                                                showRateDialog = true
                                            }
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
                        },
                        onToggleFreeze = {
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
                        },
                        onShare = {
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
                    )

                    // 4. Compact pill-shaped Segmented Control Navigation Tab Row
                    ControlTabBar(
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it },
                        themeColor = themeColor
                    )


                }
            }
            }

            // Animált tap-to-focus visszajelző gyűrű.
            // A focusPoint a koppintás helye (vagy null, ha épp nincs). A `?.let { point -> }`
            // csak akkor futtatja a belsejét, ha a focusPoint NEM null — ilyenkor kirajzol egy
            // ideiglenes gyűrűt a koppintás köré, hogy a felhasználó lássa: az adott pontra
            // fókuszáltunk. A gyűrűt egy fentebbi LaunchedEffect(focusTrigger) rövid idő után
            // nullázza (focusPoint = null), így magától eltűnik.
            // A LocalDensity + `with(density) { ... }` a képernyő-pixeleket (px) dp-alapú
            // eltolássá váltja; az absoluteOffset a gyűrűt pontosan a koppintás köré helyezi.
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

            // Saját, testreszabott "toast" (felugró visszajelzés) overlay.
            // Nem az Android beépített Toast-ja: az nem stílusozható és nem animálható tetszőlegesen,
            // ezért itt saját megoldás készül. A showSavedToast flag vezérli (mentés/megosztás/hiba
            // után állítjuk true-ra); az AnimatedVisibility be- és kicsúsztatja/elhalványítja.
            // Egy fentebbi LaunchedEffect(showSavedToast) pár másodperc után magától elrejti.
            // A toastIcon a fő ikon, a toastSubIcon (ha van) egy kis kiegészítő állapotjelző
            // (pipa = siker, felkiáltójel = hiba/figyelmeztetés), a toastColor a háttérszín.
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

            // Teljes képernyős töltés-jelző (spinner) overlay.
            // Amíg háttérfeldolgozás zajlik (isProcessing = true — pl. a kimerevített kép
            // élesítése, mentése), egy félig áttetsző fekete réteget és egy pörgő indikátort
            // teszünk MINDEN más fölé. Mivel ez a Box a legkülső Box UTOLSÓ gyereke, a
            // rétegzési (z-) sorrendben legfelülre kerül, és a sötét háttér vizuálisan
            // "letiltja" a mögötte lévő UI-t, amíg a művelet tart.
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

            // Útmutató overlay
            if (showWalkthrough) {
                WalkthroughOverlay(
                    themeColor = themeColor,
                    onDismiss = {
                        showWalkthrough = false
                        prefs.edit().putBoolean("walkthrough_shown", true).apply()
                    }
                )
            }

            // Értékelési párbeszédpanel
            if (showRateDialog) {
                RatePromptDialog(
                    themeColor = themeColor,
                    onRateNow = {
                        showRateDialog = false
                        prefs.edit().putBoolean("rate_done", true).apply()

                        val packageName = context.packageName
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=$packageName")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                            }
                            context.startActivity(webIntent)
                        }
                    },
                    onRateLater = {
                        showRateDialog = false
                    },
                    onRateNever = {
                        showRateDialog = false
                        prefs.edit().putBoolean("rate_never", true).apply()
                    }
                )
            }

            // Támogatás (Tip Jar) párbeszédpanel
            if (showTipJar) {
                TipJarDialog(
                    themeColor = themeColor,
                    onDismiss = { showTipJar = false },
                    onSelectTip = { productId ->
                        showTipJar = false
                        billingHelper.launchBillingFlow(context, productId) {
                            // Mock sikeres fizetés utáni szimulált események (ha szükséges)
                        }
                    }
                )
            }
        }
    }
}

