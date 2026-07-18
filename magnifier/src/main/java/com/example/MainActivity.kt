/*
 * ============================================================================
 *  MainActivity.kt — az alkalmazás belépési pontja (entry point)
 * ============================================================================
 *
 * Ez a fájl az Android-app "főkapuja". Két feladata van:
 *
 *   1) Definiálja a MainActivity osztályt. Egy Activity az Androidban egyetlen
 *      képernyőt (screen) és belépési pontot jelent — a rendszer ezt indítja el,
 *      amikor a felhasználó megnyitja az appot (ezt az AndroidManifest.xml-ben
 *      a LAUNCHER intent-filter köti a MainActivity-hez).
 *
 *   2) Kezeli a kamera-jogosultságot (camera permission). A nagyító a kamera
 *      élőképéből dolgozik, ezért futásidőben (runtime permission) engedélyt kell
 *      kérnie a felhasználótól. Amíg nincs engedély, egy magyarázó képernyő
 *      (PermissionRequiredScreen) látszik; ha megvan, a tényleges nagyító UI
 *      (MagnifierMainScreen) töltődik be.
 *
 * A felhasználói felületet végig Jetpack Compose írja le, NEM a klasszikus
 * XML-layout: a képernyőt Kotlin-függvényekkel, deklaratívan építjük fel
 * (megmondjuk, MI látszódjon, nem azt, hogyan rajzoljuk ki lépésről lépésre).
 *
 * Kapcsolódó, de MÁS fájlban lévő elemek, amikre ez a fájl hivatkozik:
 *   - hasCameraPermission(context): segédfüggvény a jogosultság lekérdezésére
 *     (CameraCapture.kt).
 *   - MagnifierMainScreen(): maga a nagyító UI (MagnifierScreen.kt).
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
import com.google.android.gms.ads.MobileAds
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

/**
 * A MainActivity az app egyetlen Activity-je (single-activity architecture).
 *
 * Mi az az Activity? Egy Activity az Android egyik alap-építőköve: egy
 * "képernyő" és egyben belépési pont, aminek saját életciklusa (lifecycle) van
 * (létrejön → látható lesz → háttérbe kerül → megsemmisül). A rendszer NEM a mi
 * kódunkból, hanem magától példányosítja és hívja meg a metódusait.
 *
 * A ComponentActivity a Google által adott ős-osztály (base class), amiből
 * öröklünk (a `:` jelenti az öröklést Kotlinban). Ez adja azt a felszerelést,
 * ami a Compose-hoz és a modern jetpack-komponensekhez kell.
 */
class MainActivity : ComponentActivity() {
    /**
     * Az onCreate() az Activity életciklusának ELSŐ metódusa: a rendszer akkor
     * hívja meg, amikor létrehozza a képernyőt. Ide tesszük az egyszeri
     * inicializálást (setup). Az `override` azt jelzi, hogy felüldefiniáljuk az
     * ős-osztály metódusát.
     *
     * A savedInstanceState (Bundle?) a korábban elmentett állapotot tartalmazza,
     * ha az Activity újra létrejön (pl. képernyőforgatás után a rendszer
     * "eldobja" és újraépíti a képernyőt). A `?` azt jelenti, hogy null is lehet
     * — az app első indításakor nincs mit visszatölteni, ezért null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Az ős onCreate-jét KÖTELEZŐ először meghívni: az végzi el az Activity
        // alap-inicializálását. Enélkül futásidejű hibát (exception) kapnánk.
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        // enableEdgeToEdge(): az app a teljes kijelzőt használhatja, a rendszer-
        // sávok (status bar fent, navigation bar lent) mögé is kirajzolódik a
        // tartalom — modern, "kerettelen" megjelenés.
        enableEdgeToEdge()
        // setContent { ... }: INNENTŐL Compose. A blokkban lévő composable
        // függvényhívások írják le a képernyő tartalmát. Ez váltja ki a régi
        // setContentView(R.layout.xxx) hívást, ami XML-layoutot töltött be.
        setContent {
            // MyApplicationTheme: a projekt Compose-témája (színek, tipográfia,
            // formák egységes csomagja) — a ui/theme csomagban definiálva.
            // darkTheme = true: erőltetett sötét mód, hogy a nagyító kevésbé
            // vakítson gyenge fényviszonyok között.
            MyApplicationTheme(darkTheme = true) { // Force Dark Mode for superior low-glare magnifier experience
                // Surface: alap-felület/háttér-réteg a Material Designból. Itt a
                // teljes képernyőt kitölti (fillMaxSize) és feketére színezi —
                // ez a kamera-előnézet alatti "vászon".
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    // A tényleges app-tartalom belépő composable-je.
                    MagnifierApp()
                }
            }
        }
    }
}

/**
 * MagnifierApp — a felső szintű composable, ami eldönti, MELYIK képernyő
 * látszódjon: a jogosultság-kérő vagy maga a nagyító.
 *
 * Mi az a @Composable függvény? Egy UI-t LEÍRÓ függvény. Nem "rajzol"
 * imperatívan (nincs benne "húzz ide egy vonalat"), hanem deklarálja, milyen
 * legyen a felület az adott állapot (state) mellett. A Compose futtatókörnyezete
 * ezt a leírást fordítja képpé. Ezért is NAGYBETŰVEL kezdődik a neve
 * (PascalCase) — konvenció, ami vizuálisan elkülöníti a "UI-elem" függvényeket a
 * hétköznapi (camelCase) függvényektől.
 *
 * @OptIn(ExperimentalPermissionsApi::class): kifejezetten "vállaljuk", hogy egy
 * még kísérleti (experimental) API-t használunk — az Accompanist permission
 * könyvtárét. Enélkül a fordító figyelmeztetne.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MagnifierApp() {
    // LocalContext.current: a Context egy "kulcs" az Android-rendszerhez
    // (erőforrások, rendszer-szolgáltatások eléréséhez). A LocalContext egy
    // CompositionLocal: olyan érték, amit nem paraméterként adunk át, hanem a
    // composition fájából "olvasunk ki" az aktuális ponton.
    val context = LocalContext.current
    // rememberPermissionState (Accompanist könyvtár): egy állapotobjektum, ami
    // követi, hogy a CAMERA engedély meg van-e adva, és metódust ad a kéréshez.
    // A "remember" előtag itt is azt jelenti: a composition újrafutásakor
    // (recomposition) NEM jön létre újra, megőrzi az értékét.
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Compose state (állapot). A remember { } megőrzi az értéket a recompositionök
    // között, a mutableStateOf pedig FIGYELHETŐ dobozba csomagolja: amikor az
    // értéke megváltozik, a Compose automatikusan újrafuttatja (recompose) azokat
    // a composable-öket, amelyek ezt az értéket olvassák — így frissül a UI.
    // A `by` a Kotlin property-delegation: emiatt közvetlenül a Boolean értékkel
    // dolgozhatunk (isPermissionGranted), nem a .value-val kell bajlódni.
    var isPermissionGranted by remember {
        mutableStateOf(hasCameraPermission(context))
    }

    // LaunchedEffect: MELLÉKHATÁS (side effect), amit a composition életciklusához
    // kötünk. A blokkja akkor fut le, amikor a composable először megjelenik, ÉS
    // minden olyankor újra, amikor a "kulcsa" (a zárójelben átadott érték)
    // megváltozik. Itt a kulcs az engedély megadottsága: amint az Accompanist azt
    // jelzi, hogy változott (isGranted), frissítjük a saját állapotunkat a
    // rendszertől kérdezett, mérvadó értékkel.
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        isPermissionGranted = hasCameraPermission(context)
    }

    // Második LaunchedEffect: a képernyő első megjelenésekor (a kulcs itt maga a
    // permissionState objektum, ami nem változik) automatikusan feldobja a
    // rendszer engedélykérő párbeszédét, HA még nincs megadva az engedély.
    LaunchedEffect(cameraPermissionState) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // LocalLifecycleOwner: szintén CompositionLocal — az aktuális életciklus-
    // gazdát (jellemzően az Activity-t) adja vissza, akire életciklus-eseményekre
    // fel lehet iratkozni.
    val lifecycleOwner = LocalLifecycleOwner.current
    // DisposableEffect: olyan mellékhatás, amihez TAKARÍTÁS is tartozik. Akkor
    // használjuk, ha valamire fel kell iratkozni (register), és később KÖTELEZŐ
    // leiratkozni (unregister), különben memóriaszivárgás (memory leak) lehet.
    DisposableEffect(lifecycleOwner) {
        // Figyelő (observer), ami minden életciklus-eseményt megkap. Minket az
        // ON_RESUME érdekel: az akkor tüzel, amikor a képernyő ismét előtérbe
        // kerül. Ez azért fontos, mert a felhasználó elhagyhatja az appot a
        // rendszer Beállítások (Settings) képernyőjére, ott KÉZZEL is megadhatja
        // (vagy visszavonhatja) a kamera-engedélyt, majd visszatérhet. Ilyenkor a
        // rendszer engedélykérő dialógusa nem fut újra, ezért visszatéréskor mi
        // magunk kérdezzük le újra a tényleges állapotot.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = hasCameraPermission(context)
            }
        }
        // Feliratkozás a figyelővel.
        lifecycleOwner.lifecycle.addObserver(observer)
        // onDispose: a takarító blokk. Akkor fut le, amikor a composable eltűnik a
        // composicióból (vagy a kulcs változik). Itt leiratkozunk, hogy ne
        // maradjon "lógó" figyelő az eldobott képernyőre mutatva.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // A tényleges elágazás: az állapot alapján vagy a nagyítót, vagy az
    // engedélykérő képernyőt mutatjuk. Mivel isPermissionGranted egy Compose
    // state, ennek változásakor a Compose automatikusan a másik ágra vált.
    if (isPermissionGranted) {
        MagnifierMainScreen()
    } else {
        // Az engedélykérő képernyő gombja ugyanazt a rendszer-dialógust indítja.
        // A gomb-eseményt lambda-ként (callback) adjuk át lefelé.
        PermissionRequiredScreen(onRequestPermission = {
            cameraPermissionState.launchPermissionRequest()
        })
    }
}

/**
 * PermissionRequiredScreen — az a képernyő, amit akkor mutatunk, ha még NINCS
 * kamera-engedély. Egy kamera-ikont (rajta egy piros lakat), és egy zöld
 * "engedélyezés" gombot jelenít meg.
 *
 * A paraméter egy lambda: onRequestPermission: () -> Unit. Ez egy visszahívás
 * (callback) — a képernyő nem tudja, mi történjen a gombnyomáskor, csak jelzi
 * "felfelé" a hívónak (MagnifierApp), aki a tényleges engedélykérést elindítja.
 * Ez a Compose "state hoist" (állapot felfelé emelése) mintája.
 */
@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    // Box: egymásra rétegző konténer (a gyerekei egymás fölé kerülhetnek).
    // A Modifier a UI-elem "beállítás-lánca": itt kitölti a képernyőt
    // (fillMaxSize), sötét háttérszínt kap, és 24 dp belső margót (padding).
    // A contentAlignment = Center a tartalmat középre igazítja.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Slate 900
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Column: a gyerekeit FÜGGŐLEGESEN, egymás alá pakolja. Itt vízszintesen
        // középre igazítva és függőlegesen középre rendezve.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Kör alakú (CircleShape) háttérdoboz a kamera-ikonnak. A .size fix
            // 120 dp-s méretet ad. A dp (density-independent pixel) a kijelző-
            // sűrűségtől független mértékegység, hogy minden készüléken hasonló
            // fizikai méret jöjjön ki.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color(0xFF1E293B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Icon: egy vektoros ikon a beépített Material-készletből
                // (Icons.Default.CameraAlt). A contentDescription a képernyő-
                // olvasók (accessibility, pl. TalkBack) számára mondja el, mi ez
                // az ikon; a stringResource(...) a szöveget a strings.xml-ből
                // olvassa ki (nem "beégetett" szöveg). A tint a színe.
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.permission_camera_icon),
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
                        contentDescription = stringResource(R.string.permission_locked),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Spacer: üres kitöltő elem, itt 48 dp függőleges térköz az ikon és a
            // gomb között.
            Spacer(modifier = Modifier.height(48.dp))

            // Button: kattintható gomb. Az onClick a fentebb kapott callback —
            // megnyomáskor ez jelez vissza a hívónak, hogy indítsa az
            // engedélykérést. A testTag egy láthatatlan azonosító, amivel az
            // automatizált (UI) tesztek megtalálják ezt a gombot.
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
                    contentDescription = stringResource(R.string.permission_grant),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

