package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =============================================================================
//  ControlPanels.kt — Az alsó vezérlőkártya négy fülének (tab) tartalma
// =============================================================================
//
// MI EZ A FÁJL?
// A nagyító képernyő alján egy kártya (card) ül, amelyen négy fül (tab)
// váltogatható. Mindegyik fül tartalmát egy-egy külön @Composable függvény
// írja le ebben a fájlban:
//   1. ZoomTabContent    — nagyítás: zoom csúszka, +/- gombok, gyors preset-ek
//   2. FiltersTabContent — színszűrők: normál, monokróm, invertált, sárga, piros
//   3. TuneTabContent    — korrekció: EV/kontraszt, fényerő és élesítés csúszkák
//   4. ThemeTabContent   — téma: az app akcentusszínének kiválasztása
//
// --- ALAPFOGALOM: @Composable függvény ---
// A @Composable annotáció jelöli, hogy a függvény nem hagyományos "hívd meg,
// térj vissza egy értékkel" függvény, hanem UI-t ír le. A Compose futásidőben
// hívja meg, és ha egy bemenő érték megváltozik, ÚJRA lefuttatja (ezt hívják
// recomposition-nek), hogy a képernyő mindig kövesse az állapotot. Emiatt egy
// Composable-nek gyorsnak és mellékhatás-mentesnek kell lennie.
//
// --- ALAPFOGALOM: state hoisting (állapot-felemelés) ---
// Figyeld meg: EGYETLEN itteni composable sem tárol saját belső állapotot
// (nincs bennük remember { mutableStateOf(...) }). Minden érték PARAMÉTERKÉNT
// érkezik lefelé (pl. `frozenScale: Float`), a módosítás pedig egy
// `on...Change: (T) -> Unit` lambdán keresztül megy VISSZAFELÉ a hívóhoz
// (pl. `onFrozenScaleChange`). Ezt hívják "state hoisting"-nak: az állapotot
// feljebb, a közös szülőbe (MagnifierMainScreen) "emeljük". Előnyök:
//   * Single source of truth — az állapot egy helyen él, nincs kettőződés.
//   * Tesztelhetőség — a composable így tiszta függvény: adott bemenetre adott
//     UI; a teszt könnyen ellenőrzi, hogy kattintásra meghívódik-e a lambda.
//   * Újrafelhasználhatóság — a komponens nem kötődik konkrét adatforráshoz.
// Ez a "unidirectional data flow" (egyirányú adatáramlás): adat LE (paraméter),
// esemény FEL (lambda).
//
// --- ALAPFOGALOM: Modifier és a láncolható (chainable) modifier-ek ---
// A Modifier a Compose "díszítő szalagja": méret, háttér, keret, kattintás,
// térköz stb. egyetlen láncba fűzve adható egy composable-nek. A SORREND
// SZÁMÍT, mert minden modifier az ELŐZŐEK eredményére épül. Példa (lentebb így
// szerepel a kód):
//   Modifier.size(48.dp).background(...).border(...).clickable{}.padding(...)
//   -> előbb 48dp-s négyzet, ARRA háttér, ARRA keret, a keretezett terület
//      lesz kattintható, végül BELÜL padding. Ha a padding a background ELÉ
//      kerülne, a háttér a padding-gel csökkentett kisebb területre festődne.
// Gyakori tagok, amiket itt látsz:
//   * .size(dp) / .width / .height — fix méret
//   * .weight(1f) (csak Row/Column gyerekén) — arányos helykitöltés a
//     testvérekkel osztozva (a fennmaradó helyet súly szerint osztja szét)
//   * .background(color/brush, shape) — kitöltés adott alakzattal
//   * .border(width, color, shape) — keret
//   * .clickable { } — kattintás-kezelő (a { } a kattintás eseménye)
//   * .padding(...) — belső térköz
//
// --- ALAPFOGALOM: layout-composable-ök ---
//   * Column — a gyermekeket FÜGGŐLEGESEN pakolja egymás alá.
//   * Row    — a gyermekeket VÍZSZINTESEN pakolja egymás mellé.
//   * Box    — a gyermekeket EGYMÁSRA rétegezi (z-tengely); a pozíciót a
//              contentAlignment adja meg.
//   * BoxWithConstraints — mint a Box, de a tartalma lekérdezheti a
//              rendelkezésre álló méretet (maxWidth/maxHeight), így reszponzív
//              döntést hozhatunk (lásd a preset-gombokat lentebb).
//   * Arrangement — a FŐ tengelyen osztja el a helyet/térközt
//              (pl. Arrangement.spacedBy(8.dp), Arrangement.SpaceBetween).
//   * Alignment — a KERESZT-tengelyen igazít (pl. Alignment.CenterVertically,
//              Alignment.CenterHorizontally, Alignment.Center).
//
// --- ALAPFOGALOM: stringResource(...) és @StringRes ---
// A felhasználónak látszó szövegek NEM stringliterálként élnek a kódban, hanem
// a res/values/strings.xml-ben, és R.string.* azonosítóval hivatkozunk rájuk.
// A stringResource(R.string.xxx) futásidőben feloldja az aktuális nyelvre ->
// ez teszi lehetővé a lokalizációt (localization) és a szövegek egy helyen
// tartását. A @StringRes annotáció egy Int paraméterről jelzi a fordítónak,
// hogy az string-erőforrás azonosító (típusbiztonság).
//
// --- ALAPFOGALOM: .testTag("...") ---
// Stabil, általunk megadott azonosító egy UI-elemen, amire az automata
// (instrumented) UI-teszt hivatkozhat (pl. onNodeWithTag("zoom_slider")). A
// felhasználó nem látja; kizárólag a teszteknek szól.
//
// Színek: a paletta többnyire hardcode-olt hex-kód (pl. Color(0xFF1B1A21) a
// sötét háttér), a `themeColor` pedig a felhasználó által választott
// akcentusszín, amit a szülő ad át minden fülnek.
// =============================================================================



/**
 * NAGYÍTÁS FÜL tartalma. Megjeleníti (fentről lefelé):
 *  - egy fejlécsort: zoom-ikon + verziószám + (ha digitális zoom-tartományban
 *    vagyunk) egy kis chip/jelvény "Memory" ikonnal;
 *  - egy vezérlősort: [-] gomb, zoom-csúszka, [+] gomb, jobbra az aktuális
 *    "x.x" nagyítás felirat;
 *  - egy sor gyors preset-gombot (pl. 1x, 2x, 5x...), amennyi kifér.
 *
 * A teljes nagyítás forrása kétféle lehet: befagyasztott képnél a `frozenScale`,
 * élő kameránál a `liveZoomRatio * extraDigitalZoom` szorzat.
 *
 * Paraméterek (mind hoistolt — az állapotot a szülő tartja, lásd a fájl fejlécét):
 *  @param appVersion         a kiírt verziószám (pl. "1.2.3")
 *  @param themeColor         aktuális akcentusszín (ikonok, csúszka, kijelölés)
 *  @param isFrozen           igaz, ha a kép be van fagyasztva (állókép); ilyenkor
 *                            a zoom a rögzített képre nagyít, nem a kamerára
 *  @param frozenScale        a befagyasztott kép aktuális nagyítása
 *  @param onFrozenScaleChange visszahívás a frozenScale megváltoztatására
 *  @param liveZoomRatio      az élő kamera hardveres zoom-aránya (setZoomRatio)
 *  @param extraDigitalZoom   az ezen felüli digitális (szoftveres) ráközelítés szorzója
 *  @param maxZoom            a hardver által még natívan támogatott max. zoom
 *                            (efölött már digitális tartományban vagyunk)
 *  @param sliderMin          a csúszka minimuma
 *  @param sliderMax          a csúszka maximuma
 *  @param presets            a felkínált gyors-zoom értékek listája
 *  @param onApplyTotalZoom   (target, resetPan) -> Unit: állítsd a TELJES zoomot
 *                            a `target` célértékre; a szülő szétosztja hardveres
 *                            és digitális részre. Ha resetPan=true, a pásztázás
 *                            (pan) is nullázódjon — preset-választásnál true,
 *                            a csúszka húzásánál false.
 */
@Composable
fun ZoomTabContent(
    appVersion: String,
    themeColor: Color,
    isFrozen: Boolean,
    frozenScale: Float,
    onFrozenScaleChange: (Float) -> Unit,
    liveZoomRatio: Float,
    extraDigitalZoom: Float,
    maxZoom: Float,
    sliderMin: Float,
    sliderMax: Float,
    presets: List<Float>,
    onApplyTotalZoom: (Float, Boolean) -> Unit,
) {
    // A fül tartalma egy függőleges oszlop, amely a teljes szélességet kitölti
    // (fillMaxWidth), és a gyermeksorok közé egységes 8dp térközt tesz.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // A megjelenített "aktuális teljes nagyítás": befagyasztott képnél a
        // frozenScale, élő kameránál a hardveres és digitális rész szorzata.
        val currentTotalZoom = if (isFrozen) frozenScale else (liveZoomRatio * extraDigitalZoom)
        // Digitális tartományban vagyunk-e? Csak élő módban értelmes, és akkor,
        // ha a teljes zoom túllépi a hardver max. zoomját -> a plusz nagyítás már
        // szoftveres (digitális). Ez kapcsolja be lentebb a kis "Memory" jelvényt.
        val isDigitalRange = !isFrozen && (liveZoomRatio * extraDigitalZoom > maxZoom)

        // Fejlécsor. A külső Row a teljes szélességen SpaceBetween-nel osztaná el
        // a gyermekeit (bal/jobb szélre), itt csak egyetlen bal oldali csoport van.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bal csoport: zoom-ikon + verzió + (feltételes) digitális-zoom jelvény,
            // egymás mellett 6dp térközzel, függőlegesen középre igazítva.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Icon: vektoros piktogram. Az imageVector a rajz, a tint a
                // színezés, a contentDescription pedig a képernyőolvasónak
                // (akadálymentesítés) szóló szöveg — erőforrásból, lokalizálva.
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = stringResource(R.string.tab_zoom),
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
                // Text: egyszerű feliratkomponens. Itt a verziószám kis, félkövér.
                Text(
                    text = "v$appVersion",
                    color = themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                // Csak digitális tartományban jelenik meg: halvány lila hátterű,
                // lekerekített kis "chip", benne a memóriachip (digitális) ikon.
                if (isDigitalRange) {
                    // Modifier-sorrend: előbb a lekerekített háttér (RoundedCornerShape
                    // = lekerekített sarkú téglalap), UTÁNA padding. Így a padding a
                    // háttéren BELÜL képez térközt az ikon körül. Fordított sorrendben
                    // a háttér a padding mögé, kisebb területre festődne.
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

        // Fő vezérlősor: [-] kör-gomb | csúszka | [+] kör-gomb | érték-felirat.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // [-] gomb: 48dp-s kör (CircleShape = teljes kör alakzat), sötét
            // háttérrel és vékony kerettel. A .clickable a Box egészét kattinthatóvá
            // teszi; kattintásra 0.5x-zel csökkenti a nagyítást. Befagyasztott képnél
            // közvetlenül a frozenScale-t állítja (min. 0.5x), élőben a teljes zoom
            // célértékét adja tovább a szülőnek (pásztázás nem nullázódik: false).
            // A contentAlignment.Center a benti ikont a kör közepére helyezi.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1B1A21), CircleShape)
                    .border(1.dp, Color(0xFF2E2C33), CircleShape)
                    .clickable {
                        if (isFrozen) {
                            onFrozenScaleChange(max(0.5f, frozenScale - 0.5f))
                        } else {
                            onApplyTotalZoom(liveZoomRatio * extraDigitalZoom - 0.5f, false)
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

            // Slider: húzható csúszka. `value` az aktuális pozíció (coerceIn a
            // tartományba szorítja, hogy sose fusson ki a sávból), `valueRange` a
            // min..max, `onValueChange` pedig húzáskor hívódik az új értékkel — ez
            // a hoistolt esemény "felfelé". A colors a téma szerinti sávszínek.
            // .weight(1f): a csúszka kapja a sorban a gombok utáni MARADÉK helyet.
            // .testTag("zoom_slider"): fogódzó az automata UI-teszteknek.
            Slider(
                value = currentTotalZoom.coerceIn(sliderMin, sliderMax),
                onValueChange = { newValue ->
                    if (isFrozen) {
                        onFrozenScaleChange(newValue)
                    } else {
                        onApplyTotalZoom(newValue, false)
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

            // [+] gomb: a [-] tükörképe, 0.5x-zel NÖVELI a nagyítást (min()-nel a
            // csúszka maximumára korlátozva befagyasztott módban).
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1B1A21), CircleShape)
                    .border(1.dp, Color(0xFF2E2C33), CircleShape)
                    .clickable {
                        if (isFrozen) {
                            onFrozenScaleChange(min(sliderMax, frozenScale + 0.5f))
                        } else {
                            onApplyTotalZoom(liveZoomRatio * extraDigitalZoom + 0.5f, false)
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
            // Aktuális nagyítás szövege, pl. "2.5x". A widthIn(min = 55.dp) fix
            // minimális szélességet ad, hogy a szám változásakor ne ugráljon a
            // layout; a TextAlign.End jobbra igazít ezen a sávon belül.
            Text(
                text = String.format("%.1fx", currentTotalZoom),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.widthIn(min = 55.dp),
                textAlign = TextAlign.End
            )
        }

        // Gyors preset-ek — NEM görgethető, hanem RESZPONZÍVAN annyi fér ki,
        // amennyi. Ehhez BoxWithConstraints kell: ez a Box tudja megmondani a
        // benti kódnak a rendelkezésre álló szélességet (maxWidth), amiből
        // kiszámoljuk, hány gomb fér el, és csak annyit rajzolunk ki.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val itemWidth = 52.dp
            val spacing = 6.dp
            // Hány gomb fér ki hiánytalanul? A képlet a
            //   k * itemWidth + (k - 1) * spacing <= availableWidth
            // egyenlőtlenség átrendezése egész osztással; coerceAtLeast(1) miatt
            // legalább egy gomb mindig látszik.
            val maxFit = ((availableWidth + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)
            // Csak az első `maxFit` preset-et vesszük — a többi egyszerűen kimarad.
            val visiblePresets = presets.take(maxFit)

            // A látható preset-ek egy sorban, a szélek közt egyenletesen elosztva.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePresets.forEach { preset ->
                    // Ki van-e éppen választva ez a preset? Nem szigorú egyenlőség,
                    // hanem 0.15x tűréssel: ha az aktuális nagyítás elég közel van a
                    // preset értékéhez, kiemeltnek (selected) számít.
                    val isSelected = if (isFrozen) {
                        abs(frozenScale - preset) < 0.15f
                    } else {
                        abs((liveZoomRatio * extraDigitalZoom) - preset) < 0.15f
                    }

                    // Élő módban csak azok a preset-ek elérhetők, amelyek beleférnek a
                    // csúszka max-ába; a nem lehetségeseket egyszerűen ki sem rajzoljuk.
                    val isPresetPossible = isFrozen || preset <= sliderMax
                    if (isPresetPossible) {
                        // Egy preset-gomb. A KIVÁLASZTOTT állapot vizuális jelzése a
                        // színcsere: ha isSelected, a háttér és a keret a themeColor
                        // (kiemelt), különben sötét háttér + halvány keret.
                        // Kattintáskor preset-re ugrik: befagyasztva a frozenScale-t
                        // állítja, élőben az onApplyTotalZoom(preset, true) — a true
                        // itt azt jelenti, a pásztázás (pan) is nullázódjon.
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
                                        onFrozenScaleChange(preset)
                                    } else {
                                        onApplyTotalZoom(preset, true)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                         ) {
                            // Felirat: egész értéknél "2x", törtnél "2.5x". A szöveg
                            // színe kiemelt gombon fekete (a világos háttéren olvasható),
                            // különben fehér.
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

@Composable
fun FiltersAndTuneTabContent(
    appVersion: String,
    themeColor: Color,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    isFrozen: Boolean,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    exposureIndex: Int,
    onExposureIndexChange: (Int) -> Unit,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    sharpenStrength: Float,
    onSharpenStrengthChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Fejlécsor: beállítások ikon + verziószám
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
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.tab_filters_tune),
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
        }

        // 1. Szűrők választó sora
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterMode.values().forEach { mode ->
                val selected = filterMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .background(
                            if (selected) Color(0xFF231D30) else Color(0xFF111115),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) themeColor else Color(0xFF2E2C33),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onFilterModeChange(mode) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Csúszkasorok (expozíció/kontraszt, fényerő, élesítés)
        // ELSŐ csúszkasor — "kettős célú": fagyasztva a KONTRASZTOT, élőben az EXPOZÍCIÓT (EV) állítja.
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
                        onContrastChange(newValue)
                    } else {
                        onExposureIndexChange(newValue.roundToInt())
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

        // FÉNYERŐ csúszka — CSAK befagyasztott képnél jelenik meg
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
                    onValueChange = { onBrightnessChange(it) },
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

        // ÉLESÍTÉS csúszka — csak megállított kép (isFrozen = true) esetén érhető el.
        if (isFrozen) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Canvas(
                    modifier = Modifier.size(18.dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = themeColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                var draggingValue by remember(sharpenStrength) { mutableFloatStateOf(sharpenStrength) }

                Slider(
                    value = draggingValue,
                    onValueChange = { draggingValue = it },
                    onValueChangeFinished = { onSharpenStrengthChange(draggingValue) },
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
            }
        }
    }
}

@Composable
fun SettingsTabContent(
    appVersion: String,
    themeColor: Color,
    themeOptions: List<AppThemeColor>,
    currentThemeIndex: Int,
    onThemeIndexChange: (Int) -> Unit,
    onRateApp: () -> Unit,
    onShowTutorial: () -> Unit,
    onShowTipJar: () -> Unit,
    currentLanguage: String,
    onChangeLanguage: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fejléc beállítások ikonnal
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.tab_settings),
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

        // Téma színek rácsa
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            themeOptions.forEachIndexed { index, option ->
                val selected = currentThemeIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            if (selected) option.color.copy(alpha = 0.15f) else Color(0xFF111115),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) option.color else Color(0xFF2E2C33),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onThemeIndexChange(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(option.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Értékelés, Nyelvválasztó és Súgó gombok
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Értékelés gomb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFF1F1E26), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                    .clickable { onRateApp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.action_rate_app),
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Nyelvválasztó gomb (a kért zászló ikonnal a rate és help között)
            var isMenuExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFF1F1E26), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                    .clickable { isMenuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = stringResource(R.string.tab_theme),
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF1F1E26))
                        .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Magyar", color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            isMenuExpanded = false
                            onChangeLanguage("hu")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("English", color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            isMenuExpanded = false
                            onChangeLanguage("en")
                        }
                    )
                }
            }

            // Támogatás (Tip Jar) gomb a nyelvválasztó és a súgó között
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFF1F1E26), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                    .clickable { onShowTipJar() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Támogatás",
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Súgó gomb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(Color(0xFF1F1E26), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2E2C33), RoundedCornerShape(12.dp))
                    .clickable { onShowTutorial() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = stringResource(R.string.action_show_tutorial),
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CombinedZoomFiltersTuneTabContent(
    appVersion: String,
    themeColor: Color,
    isFrozen: Boolean,
    frozenScale: Float,
    onFrozenScaleChange: (Float) -> Unit,
    liveZoomRatio: Float,
    extraDigitalZoom: Float,
    maxZoom: Float,
    sliderMin: Float,
    sliderMax: Float,
    presets: List<Float>,
    onApplyTotalZoom: (Float, Boolean) -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    exposureIndex: Int,
    onExposureIndexChange: (Int) -> Unit,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    sharpenStrength: Float,
    onSharpenStrengthChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val currentTotalZoom = if (isFrozen) frozenScale else (liveZoomRatio * extraDigitalZoom)

        // 1. Csúszkasor 1: Zoom (Balra) és Expozíció/Kontraszt (Jobbra) side-by-side (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bal oldal: Zoom
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = currentTotalZoom.coerceIn(sliderMin, sliderMax),
                    onValueChange = { newValue ->
                        if (isFrozen) {
                            onFrozenScaleChange(newValue)
                        } else {
                            onApplyTotalZoom(newValue, false)
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
                Text(
                    text = String.format("%.1fx", currentTotalZoom),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.End
                )
            }

            // Jobb oldal: Expozíció/Kontraszt
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isFrozen) Icons.Default.Contrast else Icons.Default.Exposure,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = if (isFrozen) contrast else exposureIndex.toFloat(),
                    onValueChange = { newValue ->
                        if (isFrozen) {
                            onContrastChange(newValue)
                        } else {
                            onExposureIndexChange(newValue.roundToInt())
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
                    text = if (isFrozen) String.format("%.1fx", contrast) else String.format("%+d", exposureIndex),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 35.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        // 2. Szűrők (Balra) és Gyors Presetek (Jobbra) side-by-side (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bal oldal: 5 szűrő gomb (méret: 34x40.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterMode.values().forEach { mode ->
                    val selected = filterMode == mode
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 40.dp)
                            .background(
                                if (selected) Color(0xFF231D30) else Color(0xFF111115),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) themeColor else Color(0xFF2E2C33),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onFilterModeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
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
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Jobb oldal: 4 gyors-zoom preset (1x, 4x, 16x, 64x) (méret: 26x40.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val compactPresets = listOf(1.0f, 4.0f, 16.0f, 64.0f)
                compactPresets.forEach { preset ->
                    val isSelected = if (isFrozen) {
                        abs(frozenScale - preset) < 0.15f
                    } else {
                        abs((liveZoomRatio * extraDigitalZoom) - preset) < 0.15f
                    }
                    val isPresetPossible = isFrozen || preset <= sliderMax
                    if (isPresetPossible) {
                        Box(
                            modifier = Modifier
                                .size(width = 26.dp, height = 40.dp)
                                .background(
                                    if (isSelected) themeColor else Color(0xFF1B1A21),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(1.dp, if (isSelected) themeColor else Color(0xFF2E2C33), RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isFrozen) {
                                        onFrozenScaleChange(preset)
                                    } else {
                                        onApplyTotalZoom(preset, true)
                                    }
                                },
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

        // 3. Csúszkasor 2: Fényerő (Balra) és Élesítés (Jobbra) side-by-side (csak kimerevített képen) (magasság: 40.dp)
        if (isFrozen) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bal oldal: Fényerő
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { onBrightnessChange(it) },
                        valueRange = -80f..80f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = themeColor,
                            thumbColor = themeColor,
                            inactiveTrackColor = Color(0xFF1B1A21)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.0f", brightness),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 32.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Jobb oldal: Élesítés
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Canvas(
                        modifier = Modifier.size(20.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = themeColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    var draggingValue by remember(sharpenStrength) { mutableFloatStateOf(sharpenStrength) }
                    Slider(
                        value = draggingValue,
                        onValueChange = { draggingValue = it },
                        onValueChangeFinished = { onSharpenStrengthChange(draggingValue) },
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
                        text = String.format("%.1f", sharpenStrength),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 28.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
