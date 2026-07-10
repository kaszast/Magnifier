package com.example

import android.graphics.Bitmap
import androidx.camera.core.CameraInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// MagnifierComponents.kt
// -----------------------------------------------------------------------------
// Ez a fájl a nagyító UI "buta" (stateless / presentational) Compose-komponenseit
// gyűjti össze. Ezek részben az élő kamerakép, illetve a kimerevített (frozen) kép
// FÖLÖTT jelennek meg overlay-ként, részben az alsó vezérlőkártya sorait alkotják:
//
//   - ZoomMinimap        -> Picture-in-Picture bélyegkép, amely a digitális zoom
//                           aktuális kivágását (a látható "viewport"-ot) mutatja.
//   - TopLeftControls    -> bal-felső lebegő gombok: kezelőszervek elrejtése +
//                           kamera-indikátor és -váltó.
//   - ActionButtonsRow   -> fő akciógombsor: zseblámpa, mentés, kimerevítés, megosztás.
//   - ControlTabBar      -> alsó szegmentált navigáció: zoom / szűrők / korrekció / téma.
//
// FONTOS ARCHITEKTÚRÁLIS ELV — "state hoisting":
// Ezek a komponensek NEM tárolnak állapotot (state) és NEM tartalmaznak üzleti
// logikát. Minden szükséges adatot KÉSZEN kapnak paraméterként, a felhasználói
// interakciókat pedig felfelé emelt (hoistolt) lambdákon keresztül jelentik vissza
// a hívó MagnifierMainScreen-nek, ahol az összes állapot egy helyen él. A "state
// hoisting" azt jelenti, hogy az állapotot a lehető legfeljebb toljuk a hívási fában,
// a gyerek komponens pedig csak megjelenít + eseményt jelez. Előnye: a komponens
// könnyen tesztelhető és újrafelhasználható, mert önmagában nincs mellékhatása.
// =============================================================================

// Az élő nézet és a kimerevített kép fölötti overlay-komponensek és az alsó kártya vezérlősorai.
// Az állapotot a MagnifierMainScreen tartja; ezek csak megjelenítenek és hoistolt lambdákkal jeleznek.

// Picture-in-Picture kicsinyített nézet a digitális zoom kivágásának mutatásához
//
// MIT JELENÍT MEG:
// Egy kicsi (110x170 dp) kártya a képernyő sarkában, benne a teljes (nem-zoomolt)
// forráskép + egy kiemelt téglalap, amely megmutatja, hogy a nagyítás/pásztázás
// után épp MELYIK részletet látja a felhasználó a fő nézetben. A kereten kívüli
// terület elsötétítve — így azonnal látszik, "hol járunk" a képen belül.
//
// @Composable: ez a Compose UI alapköve. A @Composable-lel jelölt függvény nem
// "visszaad" egy View-objektumot, hanem LEÍRJA az UI-t az aktuális paraméterek
// alapján. Amikor egy bemenő paraméter (state) megváltozik, a Compose automatikusan
// újra lefuttatja a függvényt (ez a "recomposition"), és csak a ténylegesen
// megváltozott részt rajzolja újra. Composable-t csak másik Composable hívhat.
//
// PARAMÉTEREK:
//   isFrozen            - true, ha a kimerevített képet mutatjuk (nem az élő kamerát)
//   frozenBitmap        - a kimerevített teljes felbontású kép (null, amíg nincs)
//   liveThumbnailBitmap - az élő előnézet periodikusan frissülő bélyegképe
//   frozenScale         - a kimerevített kép nagyítási faktora (pl. 2f = 2x)
//   extraDigitalZoom    - az élő nézet extra digitális zoom faktora
//   frozenOffset        - a kimerevített kép pásztázási eltolása pixelben (Offset x/y)
//   extraDigitalPan     - az élő nézet pásztázási eltolása pixelben
//   viewportSize        - a fő nézet mérete pixelben (a vetítési arányokhoz kell)
//   combinedColorFilter - a kimerevített képre alkalmazott színszűrő (pl. kontraszt)
//   liveColorFilter     - az élő bélyegképre alkalmazott színszűrő
//   themeColor          - az akcentszín (a keret és a neon viewport-téglalap színe)
@Composable
fun ZoomMinimap(
    isFrozen: Boolean,
    frozenBitmap: Bitmap?,
    liveThumbnailBitmap: Bitmap?,
    frozenScale: Float,
    extraDigitalZoom: Float,
    frozenOffset: Offset,
    extraDigitalPan: Offset,
    viewportSize: IntSize,
    combinedColorFilter: ColorFilter,
    liveColorFilter: ColorFilter,
    themeColor: Color,
) {
    // Card: Material3 "kártya" konténer — enyhén kiemelt, lekerekített felület.
    // containerColor = Color(0xE60D0C11): ARGB hexa szín, ahol az első bájt (0xE6)
    // az alpha (~90% átlátszatlan), a maradék a sötét háttérszín. Így a minimap
    // félig áttetsző "üveg" hatású.
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE60D0C11)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        // Modifier-lánc: a Compose-ban a méretezést, kinézetet és viselkedést nem
        // attribútumokkal, hanem egymás után fűzött (chained) Modifier-hívásokkal
        // állítjuk be. A SORREND SZÁMÍT — a láncot fentről lefelé alkalmazza a rendszer.
        // Itt: fix 110 dp széles, 170 dp magas, majd egy 1.5 dp vastag, a témaszín
        // 80%-os változatával rajzolt lekerekített keret. (dp = density-independent
        // pixel: kijelző-sűrűségtől független mértékegység, hogy minden eszközön
        // ugyanakkorának tűnjön.)
        modifier = Modifier
            .width(110.dp)
            .height(170.dp)
            .border(1.5.dp, themeColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
    ) {
        // Box: olyan elrendezés (layout), amely a gyerekeit EGYMÁSRA rétegzi (z-order),
        // a beszúrás sorrendjében. Emiatt tökéletes overlay-ekhez: itt előbb a
        // forráskép (Image) kerül alulra, majd FÖLÉ a rajzolt viewport-téglalap
        // (Canvas). A fillMaxSize() a Box-ot a szülő (Card) teljes belső méretére húzza.
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. réteg: a NEM-zoomolt forráskép kirajzolása (kimerevített bitmap, vagy
            //    élő módban a periodikusan frissülő élő bélyegkép).
            if (isFrozen) {
                // frozenBitmap?.let { ... }: null-biztos hívás — a blokk csak akkor fut le,
                // ha a bitmap nem null (különben a minimap kép nélkül marad).
                frozenBitmap?.let { bitmap ->
                    // Image: bittérkép megjelenítő Composable.
                    // - asImageBitmap(): az Android natív android.graphics.Bitmap típusát
                    //   alakítja át Compose-kompatibilis ImageBitmap-pé (a Compose nem a
                    //   klasszikus Bitmap-et, hanem a saját ImageBitmap típusát rajzolja).
                    // - contentDescription: akadálymentesítési (accessibility) felirat,
                    //   amit a képernyőolvasó (TalkBack) felolvas.
                    // - stringResource(...): a felhasználónak szánt szöveget NEM beégetve,
                    //   hanem az res/values/strings.xml-ből, az eszköz nyelvén tölti be.
                    // - ContentScale.FillBounds: a képet a rendelkezésre álló területre
                    //   FESZÍTI (torzíthat is), hogy pontosan kitöltse a minimapot — így a
                    //   téglalap-számítás arányai egyszerűek maradnak.
                    // - colorFilter: a fő nézetével AZONOS színszűrő, hogy a minimap a
                    //   valós, szűrt képet mutassa (lásd combinedColorFilter).
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_minimap_frozen),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = combinedColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Élő módban ugyanaz, csak az élő bélyegkép + az élő nézet szűrője.
                liveThumbnailBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_minimap_live),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = liveColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Az Elvis-operátor (?:): ha a liveThumbnailBitmap null, akkor a
                    // ?.let egésze null-t ad vissza (a blokk le sem fut), és ilyenkor a
                    // jobb oldali Box lép működésbe — egy középre igazított töltésjelző
                    // (spinner), amíg meg nem érkezik az első élő bélyegkép.
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

            // 2. réteg: a látható kivágást jelző téglalap SAJÁT kézzel rajzolva.
            //
            // Canvas: az a Composable, amellyel közvetlenül, primitívekből (téglalap,
            // kör, vonal, útvonal...) rajzolhatunk — nem kész UI-elemeket helyezünk el.
            // A Canvas lambdája egy DrawScope-ban fut; ezen belül elérhető a `size`
            // (a Canvas aktuális mérete pixelben), és olyan rajzoló-függvények, mint a
            // drawRect. Ezt akkor használjuk, ha nincs kész komponens az adott vizuális
            // elemre — itt épp a dinamikusan mozgó/méreteződő viewport-téglalapra.
            Canvas(modifier = Modifier.fillMaxSize()) {
                // A Canvas pixel-méretei (a size a DrawScope-ból jön).
                val canvasW = size.width
                val canvasH = size.height

                // A minimap kétféle forrást szolgál ki, ezért a megfelelő zoom/pan
                // értékpárt választjuk ki: kimerevített módban a frozen*, élő módban
                // az extraDigital* értékeket.
                val scale = if (isFrozen) frozenScale else extraDigitalZoom
                val pan = if (isFrozen) frozenOffset else extraDigitalPan

                // A fő nézet (viewport) mérete pixelben. A coerceAtLeast(1f) véd a
                // nullával osztás ellen, ha a méret még nincs beállítva (0).
                val wWidth = viewportSize.width.toFloat().coerceAtLeast(1f)
                val wHeight = viewportSize.height.toFloat().coerceAtLeast(1f)

                // --- A látható kivágás téglalapjának kiszámítása ---
                //
                // A kivágás MÉRETE a nagyítás reciproka: ha 2x-re zoomolunk, akkor a
                // teljes képnek csak az 1/2 részét látjuk mindkét irányban; 4x-nél 1/4-et,
                // stb. Ezért a téglalap oldalait a teljes kép arányában 1/scale adja.
                val boxWidthFraction = 1f / scale
                val boxHeightFraction = 1f / scale

                // A kivágás KÖZÉPPONTJA arányban (0..1). Pásztázás nélkül a középpont 0.5
                // (a kép közepe). A pan pixelben, a MÁR NAGYÍTOTT koordinátarendszerben
                // mért eltolás, ezért vissza kell alakítani forrás-arányra: elosztjuk
                // (scale * viewport-méret)-tel. A kivonás iránya azért fordított, mert a
                // képet jobbra tolni (pozitív pan) annyi, mint a kivágást balra vinni.
                val centerXFraction = 0.5f - (pan.x / (scale * wWidth))
                val centerYFraction = 0.5f - (pan.y / (scale * wHeight))

                // Az arányokat átváltjuk a Canvas konkrét pixelméreteire.
                // Ha a nagyítás 1x alatti (pl. 0.5x), a kivágás nagyobb lenne, mint a kép,
                // ezért ilyenkor a teljes minimap-et (canvasW/H) tekintjük aktívnak.
                val rectW = (canvasW * boxWidthFraction).coerceAtMost(canvasW)
                val rectH = (canvasH * boxHeightFraction).coerceAtMost(canvasH)

                // A téglalap bal-felső sarka = középpont mínusz a fél oldalhossz.
                // A coerceIn(0f, canvas - rect) beszorítja az értéket az érvényes
                // tartományba, hogy a téglalap sose lógjon ki a minimap széléből
                // (a szélek elérésekor "megáll", nem csúszik tovább).
                // A .coerceAtLeast(0f) véd az IllegalArgumentException ellen, ha a
                // számítási pontatlanság vagy 1x alatti zoom miatt a max < 0 lenne.
                val rectX = ((canvasW * centerXFraction) - (rectW / 2f)).coerceIn(0f, (canvasW - rectW).coerceAtLeast(0f))
                val rectY = ((canvasH * centerYFraction) - (rectH / 2f)).coerceIn(0f, (canvasH - rectH).coerceAtLeast(0f))

                // A kívül eső (nem látható) rész elsötétítése.
                // MIÉRT: a fő nézetben csak a viewport-téglalapon belüli részt látja a
                // felhasználó; a téglalapon KÍVÜLI tartalom jelen van a képen, de épp
                // "kilóg" a nagyításból. Ezt félig áttetsző fekete réteggel tompítjuk, így
                // a kiemelt (világos) téglalap egyértelműen mutatja az aktuális kivágást.
                //
                // A drawRect a DrawScope alap-rajzolófüggvénye: kitölt egy téglalapot a
                // megadott színnel. Paraméterei: color (itt 50%-ban átlátszó fekete),
                // topLeft (a bal-felső sarok Offset-je) és size (Size szélesség/magasság).
                // Négy külön téglalapot rajzolunk a viewport köré — fent / lent / bal / jobb —
                // amelyek együtt lefedik a teljes "keret" területet, a közepét szabadon hagyva.
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

                // Végül a viewport téglalapjának KERETE a témaszínnel (neon hatás).
                // A style = Stroke(...) miatt itt a drawRect nem kitölt, hanem csak a
                // körvonalat húzza meg (a Stroke alapértelmezett alternatívája a Fill).
                // A 1.5.dp.toPx() a dp-t pixelre váltja, mert a Canvas rajzolás pixelben
                // dolgozik — a dp önmagában nem pixel, hanem sűrűségfüggetlen egység.
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

// Bal-felső lebegő vezérlők: teljes képernyő (kezelőszervek) váltó + kamera-indikátor/váltó
//
// MIT JELENÍT MEG: két kicsi lebegő gomb egymás mellett — (1) egy "szem" ikon a
// kezelőszervek elrejtéséhez/megjelenítéséhez (tiszta, teljes képernyős nézethez),
// és (2) egy kamera-jelző + -váltó, amely mutatja az aktív kamerát és tabbal vált.
//
// STATE HOISTING: a komponens NEM dönti el, mi történjen a kattintásra — csak
// paraméterként kapja az AKTUÁLIS állapotot (controlsVisible, isFrozen, a kamerák
// listája, a kiválasztott index), a kattintást pedig továbbítja a hoistolt
// lambdáknak. Ezért lambda az onToggleControls és az onSwapCamera: a tényleges
// állapotváltást a hívó MagnifierMainScreen végzi el, ahol az állapot valóban él.
// Így ez a komponens tiszta és mellékhatás-mentes marad.
//
// PARAMÉTEREK:
//   themeColor          - akcentszín (ikonok színe, keretek)
//   controlsVisible     - jelenleg láthatók-e a kezelőszervek (ikonválasztáshoz)
//   onToggleControls    - kattintás-esemény: kezelőszervek ki/be (hoistolt lambda)
//   isFrozen            - kimerevített módban a kamera-váltó rejtve marad
//   availableCameras    - az eszközön elérhető kamerák listája
//   selectedCameraIndex - az aktív kamera indexe a fenti listában
//   onSwapCamera        - kattintás-esemény: következő kamerára váltás (hoistolt lambda)
@Composable
fun TopLeftControls(
    themeColor: Color,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    isFrozen: Boolean,
    availableCameras: List<CameraInfo>,
    selectedCameraIndex: Int,
    onSwapCamera: () -> Unit,
) {
    // Row: vízszintes elrendezés — a gyerekeit balról jobbra sorba rendezi.
    // horizontalArrangement = spacedBy(12.dp): 12 dp rés a gyerekek KÖZÖTT.
    // verticalAlignment = CenterVertically: függőlegesen középre igazítja őket.
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Szem" gomb: a kezelőszervek elrejtése/megjelenítése. A Box itt gombként
        // viselkedik: kör alakú (CircleShape) félig áttetsző háttér + keret, a
        // .clickable { onToggleControls() } teszi kattinthatóvá. Az ikon aszerint vált
        // (VisibilityOff / Visibility), hogy a kezelőszervek épp láthatók-e.
        // Floating Controls Visibility Toggle Button (Full Screen)
        Box(
            modifier = Modifier
                .background(Color(0xFF09090B).copy(alpha = 0.75f), CircleShape)
                .border(1.5.dp, themeColor.copy(alpha = 0.6f), CircleShape)
                .clickable { onToggleControls() }
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

        // Kamera-jelző és -váltó. Feltételes megjelenítés: csak akkor rajzoljuk ki,
        // ha NEM vagyunk kimerevített módban (élő kép van) ÉS van legalább egy elérhető
        // kamera. Compose-ban a feltételes UI egyszerű if — ami nem fut le, meg sem jelenik.
        // Floating Camera Indicator and Swap Button (in a separated box)
        if (!isFrozen && availableCameras.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF09090B).copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .border(1.5.dp, themeColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable { onSwapCamera() }
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

                    // Az aktív kamera adatai. getOrNull(): biztonságos indexelés — ha az
                    // index érvénytelen, null-t ad (nem dob kivételt, mint a sima []).
                    val activeCameraInfo = availableCameras.getOrNull(selectedCameraIndex)
                    // Ikonválasztás a kamera "lensFacing" (a lencse iránya) értéke alapján.
                    // A CameraX/Camera2 konvenciója szerint:
                    //   0 = elülső (front / selfie)  -> "Person" ikon
                    //   1 = hátsó (back)             -> "PhotoCamera" ikon
                    //   egyéb = külső/USB kamera      -> "Videocam" ikon
                    // Ha valamiért nincs aktív kamera-info, a hátsó ikon a biztonságos alap.
                    val cameraIcon = if (activeCameraInfo != null) {
                        when (activeCameraInfo.lensFacing) {
                            0 -> Icons.Default.Person // selfie/front
                            1 -> Icons.Default.PhotoCamera // back
                            else -> Icons.Default.Videocam // external
                        }
                    } else {
                        Icons.Default.PhotoCamera
                    }

                    // Feliratválasztás ugyanazon lensFacing logika szerint. Extra eset a
                    // hátsó kameráknál: ha az eszköznek TÖBB hátsó kamerája van (pl.
                    // fő + ultraszéles + tele), akkor sorszámozzuk őket ("Hátsó 1/2/...").
                    // A camera_back_n egy paraméteres string-erőforrás: a stringResource
                    // második argumentuma (idx + 1) illesztődik be a szöveg %d helyére.
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

// Fő akciógombsor: zseblámpa, mentés, kimerevítés/folytatás (hero), megosztás.
// A tényleges műveletek a MagnifierMainScreen lambdáiban élnek, ahol az összes állapot elérhető.
//
// === STATE HOISTING — ITT A LEGSZEMLÉLETESEBB ===
// Ez a komponens szándékosan "buta": CSAK kirajzolja a négy gombot, és kattintáskor
// meghívja a hozzájuk tartozó lambdát. A mentés, megosztás és kimerevítés VALÓS
// logikája NINCS itt — nincs itt a bitmap, a MediaStore-mentés, a share Intent, sem
// a kimerevítés állapotgépe. Mindez a hívó MagnifierMainScreen-ben van, ahol az összes
// szükséges állapot (élő kamerakép, kimerevített kép, szűrők, zoom stb.) egy helyen
// elérhető. A hívó oda adja át a kész műveleteket lambdaként (onSave / onShare /
// onToggleFreeze / onToggleTorch), ez a sor pedig csak "elsüti" őket a megfelelő
// gombnál. MIÉRT JÓ: a gombsor önmagában, valódi kamera nélkül is tesztelhető
// (a lambdák helyére tesztben tetszőleges "kém"-függvény tehető), és bárhol
// újrahasználható, mert nem kötődik konkrét állapothoz.
//
// PARAMÉTEREK:
//   themeColor     - akcentszín (aktív zseblámpa és a hero gomb színe)
//   torchEnabled   - be van-e kapcsolva a zseblámpa (ikon/szín választáshoz)
//   isFrozen       - kimerevített módban vagyunk-e (a hero gomb megjelenése ettől függ)
//   onToggleTorch  - zseblámpa ki/be kattintás-esemény (hoistolt lambda)
//   onSave         - mentés kattintás-esemény (hoistolt lambda)
//   onToggleFreeze - kimerevítés/folytatás kattintás-esemény (hoistolt lambda)
//   onShare        - megosztás kattintás-esemény (hoistolt lambda)
@Composable
fun ActionButtonsRow(
    themeColor: Color,
    torchEnabled: Boolean,
    isFrozen: Boolean,
    onToggleTorch: () -> Unit,
    onSave: () -> Unit,
    onToggleFreeze: () -> Unit,
    onShare: () -> Unit,
) {
    // A gombokat vízszintes Row-ba tesszük, teljes szélességben (fillMaxWidth).
    // Arrangement.SpaceAround: a szabad helyet egyenletesen osztja el a gombok között
    // ÉS a két szélén is (a szélső rés a belsők fele) — így légző, kiegyensúlyozott sor.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Zseblámpa gomb. Bekapcsolt állapotban (torchEnabled) a háttér és a keret a
        // témaszínre vált, az ikon FlashOn/FlashOff-ra, jelezve az aktív állapotot.
        //
        // .testTag("torch_button"): teszt-azonosító. Ez a Modifier semmit nem változtat
        // a kinézeten, de "címkét" ad az elemnek, amivel az UI-tesztek (pl. Compose
        // testing / Espresso) egyértelműen megtalálják és rákattintanak — anélkül, hogy
        // a felhasználónak látható szövegre kellene támaszkodniuk.
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
                .clickable { onToggleTorch() }
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
                .clickable { onSave() }
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

        // A kiemelt (hero) kimerevítés/folytatás gomb — a fényképezőgépek zár-gombjának
        // (shutter) klasszikus DUPLA GYŰRŰS kialakítása. Két egymásba ágyazott Box adja ki:
        //   - KÜLSŐ Box (72 dp): csak a vékony gyűrűt rajzolja a .border-rel, majd egy
        //     4 dp .padding-gal "hézagot" hagy befelé — ez a két gyűrű közti rés.
        //   - BELSŐ Box (fillMaxSize): a tömör, kitöltött korong; EZ a kattintható felület
        //     (.clickable { onToggleFreeze() }).
        // A szín állapotfüggő: élő módban a témaszín, kimerevítve piros (0xFFEF4444),
        // az ikon pedig Pause <-> PlayArrow között vált (kimerevítés vs. folytatás).
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
                    .clickable { onToggleFreeze() }
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
                .clickable { onShare() }
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
}

// Alsó szegmentált navigációs tab-sor (nagyítás / szűrők / korrekció / téma)
//
// MIT JELENÍT MEG: egy "szegmentált vezérlő" (segmented control) — négy egyenlő
// szélességű fül egyetlen keretben, mindegyik ikonnal + felirattal. A kiválasztott
// fül kiemelt háttérrel és a témaszínnel jelenik meg; a többi halványabb.
//
// STATE HOISTING: a komponens csak megkapja, MELYIK fül aktív (activeTab), és
// kattintáskor visszajelzi a választott indexet az onTabSelected(index) lambdán
// keresztül. Azt, hogy a fülváltás mit csinál (melyik vezérlőpanelt mutatja), a hívó
// MagnifierMainScreen dönti el.
//
// PARAMÉTEREK:
//   activeTab     - a jelenleg kiválasztott fül indexe (0..3)
//   onTabSelected - fülre kattintás esemény, átadja a kattintott index-et (hoistolt lambda)
//   themeColor    - akcentszín (a kiválasztott fül ikonja és felirata)
@Composable
fun ControlTabBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    themeColor: Color,
) {
    // A teljes sávot egy Row tartja: teljes szélesség, fix 48 dp magasság, sötét
    // kitöltés és keret lekerekített sarokkal (a "kapszula" külső formája).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF131217), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF23222A), RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A fülek adatai (ikon + felirat) párokként, listában. A stringResource itt is
        // az strings.xml-ből tölti a feliratokat, így fordíthatóak maradnak.
        val tabs = listOf(
            Pair(Icons.Default.ZoomIn, stringResource(R.string.tab_zoom)),
            Pair(Icons.Default.ColorLens, stringResource(R.string.tab_filters)),
            Pair(Icons.Default.Tune, stringResource(R.string.tab_tune)),
            Pair(Icons.Default.Palette, stringResource(R.string.tab_theme))
        )

        // forEachIndexed: végigmegy a listán, és a lambdának ODAADJA az elem INDEXÉT is
        // (index) az elem mellett. Az index kell, mert (a) össze tudjuk hasonlítani az
        // activeTab-bal a kiválasztottság eldöntéséhez, és (b) ezt az indexet adjuk vissza
        // kattintáskor. A "(icon, label)" a Pair azonnali szétszedése (destructuring).
        tabs.forEachIndexed { index, (icon, label) ->
            // Ez a fül van-e kiválasztva? Ettől függ a háttér és a színezés.
            val selected = activeTab == index
            Box(
                // A fül Modifier-lánca:
                //   .weight(1f)   - a Row-on belül minden fül EGYENLŐ arányban osztozik a
                //                   szélességen (mind 1f súlyú -> mind ugyanolyan széles).
                //   .clip(...)    - a fül tartalmát/hátterét lekerekített sarokra vágja.
                //   .background   - kiválasztva kiemelő háttérszín, egyébként átlátszó.
                //   .clickable    - kattintáskor visszajelzi a fül indexét a hívónak.
                //   .testTag      - "tab_0".."tab_3" teszt-azonosító az UI-tesztekhez.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF25212E) else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .testTag("tab_$index"),
                contentAlignment = Alignment.Center
            ) {
                // Column: FÜGGŐLEGES elrendezés — a gyerekeket egymás ALÁ teszi.
                // Itt az ikon kerül felülre, alá 2 dp réssel a felirat, vízszintesen középre.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // A kiválasztott fül ikonja/felirata a témaszínnel, a többié halvány
                    // (50%-os fehér) — így vizuálisan egyértelmű, melyik fül aktív.
                    //
                    // contentDescription = null: az ikon SZÁNDÉKOSAN "dekoratív" az
                    // akadálymentesítés (accessibility) szempontjából. Mivel közvetlenül
                    // alatta ott az OLVASHATÓ szöveges felirat (Text), az hordozza a
                    // jelentést a képernyőolvasónak. Ha az ikon is kapna leírást, a
                    // TalkBack ugyanazt kétszer mondaná ki — a null épp ezt kerüli el.
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) themeColor else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    /*
                    Text(
                        text = label,
                        color = if (selected) themeColor else Color(0xFFE6E1E5).copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    */
                }
            }
        }
    }
}
