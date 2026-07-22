@file:Suppress("REDUNDANT_ELSE_IN_WHEN")
package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.ln
import kotlin.math.exp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt
import androidx.compose.material.icons.automirrored.filled.Help
import java.util.Locale

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
 *  @param onApplyTotalZoom   (target, resetPan) -> Unit: állítsd a TELJES zoomot
 *                            a `target` célértékre; a szülő szétosztja hardveres
 *                            és digitális részre. Ha resetPan=true, a pásztázás
 *                            (pan) is nullázódjon — preset-választásnál true,
 *                            a csúszka húzásánál false.
 */
@Composable
fun SettingsTabContent(
    themeColor: Color,
    themeOptions: List<AppThemeColor>,
    currentThemeIndex: Int,
    onThemeIndexChange: (Int) -> Unit,
    onRateApp: () -> Unit,
    onShowTutorial: () -> Unit,
    onShowTipJar: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    isHdrEnabled: Boolean,
    onHdrEnabledChange: (Boolean) -> Unit,
    isNightEnabled: Boolean,
    onNightEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Fejléc beállítások ikonnal
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = themeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Téma színek rácsa
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.action_show_tutorial),
                    tint = themeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Kamera Módok (HDR / Éjszakai képjavítás)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color(0xFF2E2C33)
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HdrOn,
                    contentDescription = stringResource(R.string.setting_hdr),
                    tint = if (isHdrEnabled) themeColor else Color(0xFF5E5C64),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isHdrEnabled,
                    onCheckedChange = onHdrEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = themeColor,
                        uncheckedThumbColor = Color(0xFF5E5C64),
                        uncheckedTrackColor = Color(0xFF111115)
                    )
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Nightlight,
                    contentDescription = stringResource(R.string.setting_night),
                    tint = if (isNightEnabled) themeColor else Color(0xFF5E5C64),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isNightEnabled,
                    onCheckedChange = onNightEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = themeColor,
                        uncheckedThumbColor = Color(0xFF5E5C64),
                        uncheckedTrackColor = Color(0xFF111115)
                    )
                )
            }
        }
    }
}

@Composable
fun CombinedZoomFiltersTuneTabContent(
    themeColor: Color,
    isFrozen: Boolean,
    frozenScale: Float,
    onFrozenScaleChange: (Float) -> Unit,
    liveZoomRatio: Float,
    extraDigitalZoom: Float,
    sliderMin: Float,
    sliderMax: Float,
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
    focusMode: String,
    onFocusModeChange: (String) -> Unit,
    manualFocusDistance: Float,
    onManualFocusDistanceChange: (Float) -> Unit,
    minFocusDistance: Float,
    onSliderDraggingChange: (Boolean) -> Unit = {}
) {
    val zoomInteractionSource = remember { MutableInteractionSource() }
    val isZoomDragged by zoomInteractionSource.collectIsDraggedAsState()

    val brightnessInteractionSource = remember { MutableInteractionSource() }
    val isBrightnessDragged by brightnessInteractionSource.collectIsDraggedAsState()

    val contrastInteractionSource = remember { MutableInteractionSource() }
    val isContrastDragged by contrastInteractionSource.collectIsDraggedAsState()

    val sharpenInteractionSource = remember { MutableInteractionSource() }
    val isSharpenDragged by sharpenInteractionSource.collectIsDraggedAsState()

    val focusInteractionSource = remember { MutableInteractionSource() }
    val isFocusDragged by focusInteractionSource.collectIsDraggedAsState()

    val isAnySliderDragging = isZoomDragged || isBrightnessDragged || isContrastDragged || isSharpenDragged || isFocusDragged

    LaunchedEffect(isAnySliderDragging) {
        onSliderDraggingChange(isAnySliderDragging)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val currentTotalZoom = if (isFrozen) frozenScale else (liveZoomRatio * extraDigitalZoom)

        // 1. Sor: Zoom csúszka (logaritmikus, teljes hosszban, Presetek nélkül) (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(20.dp)
            )
            val sliderMinLog = ln(sliderMin.toDouble()).toFloat()
            val sliderMaxLog = ln(sliderMax.toDouble()).toFloat()
            val currentZoomLog = ln(currentTotalZoom.toDouble().coerceIn(sliderMin.toDouble(), sliderMax.toDouble())).toFloat()
            Slider(
                value = currentZoomLog,
                onValueChange = { newValue ->
                    val actualZoom = exp(newValue.toDouble()).toFloat()
                    if (isFrozen) {
                        onFrozenScaleChange(actualZoom)
                    } else {
                        onApplyTotalZoom(actualZoom, false)
                    }
                },
                valueRange = sliderMinLog..sliderMaxLog,
                interactionSource = zoomInteractionSource,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("zoom_slider")
            )
            // Kattintásra a nagyítás visszaáll az alapértelmezett 1.0x-es értékre
            Text(
                text = String.format(Locale.US, "%.1fx", currentTotalZoom),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .clickable {
                        if (isFrozen) {
                            onFrozenScaleChange(1.0f)
                        } else {
                            onApplyTotalZoom(1.0f, true)
                        }
                    },
                textAlign = TextAlign.End
            )
        }

        // 2. Sor: Fényerő csúszka (teljes hosszban, de élő kép esetén letiltva) (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = null,
                tint = if (isFrozen) themeColor else Color(0xFF5E5C64),
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = brightness,
                onValueChange = { onBrightnessChange(it) },
                valueRange = -80f..80f,
                enabled = isFrozen,
                interactionSource = brightnessInteractionSource,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21),
                    disabledActiveTrackColor = Color(0xFF3E3D45),
                    disabledThumbColor = Color(0xFF3E3D45),
                    disabledInactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier.weight(1f)
            )
            // Kattintásra a fényerő visszaáll az alapértelmezett 0-ra
            Text(
                text = String.format(Locale.US, "%.0f", brightness),
                color = if (isFrozen) Color.White else Color(0xFF5E5C64),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .widthIn(min = 32.dp)
                    .clickable(enabled = isFrozen) { onBrightnessChange(0.0f) },
                textAlign = TextAlign.End
            )
        }

        // 3. Sor: Szűrők választósávja (vízszintesen egyenletesen elosztva, 8 szűrőgombbal) (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterMode.entries.forEach { mode ->
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
                                        FilterMode.DEUTERANOPIA -> {
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF81C784), Color.Black)
                                            )
                                        }
                                        FilterMode.PROTANOPIA -> {
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFFE57373), Color.Black)
                                            )
                                        }
                                        FilterMode.TRITANOPIA -> {
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF64B5F6), Color.Black)
                                            )
                                        }
                                        else -> {
                                            Brush.linearGradient(
                                                colors = listOf(Color.Gray, Color.Black)
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
        }

        // 4. Sor: Expozíció (élő) vagy Kontraszt (fagyasztott) teljes szélességben (magasság: 40.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically
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
                interactionSource = contrastInteractionSource,
                colors = SliderDefaults.colors(
                    activeTrackColor = themeColor,
                    thumbColor = themeColor,
                    inactiveTrackColor = Color(0xFF1B1A21)
                ),
                modifier = Modifier.weight(1f)
            )
            // Kattintásra az expozíció vagy a kontraszt visszaáll a kiinduló alapértelmezett értékre (0 vagy 1.0x)
            Text(
                text = if (isFrozen) String.format(Locale.US, "%.1fx", contrast) else String.format(Locale.US, "%+d", exposureIndex),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .widthIn(min = 35.dp)
                    .clickable {
                        if (isFrozen) {
                            onContrastChange(1.0f)
                        } else {
                            onExposureIndexChange(0)
                        }
                    },
                textAlign = TextAlign.End
            )
        }

        // 5. Sor: Fókusz vezérlők (élő) vagy Sharpen csúszka (fagyasztott) teljes szélességben (magasság: 40.dp)
        if (isFrozen) {
            // Fagyasztott mód: Élesítés (Sharpen) teljes szélességben
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(
                    modifier = Modifier.size(16.dp)
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                var draggingValue by remember(sharpenStrength) { mutableFloatStateOf(sharpenStrength) }
                Slider(
                    value = draggingValue,
                    onValueChange = { draggingValue = it },
                    onValueChangeFinished = { onSharpenStrengthChange(draggingValue) },
                    valueRange = 0.0f..10.0f,
                    interactionSource = sharpenInteractionSource,
                    colors = SliderDefaults.colors(
                        activeTrackColor = themeColor,
                        thumbColor = themeColor,
                        inactiveTrackColor = Color(0xFF1B1A21)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sharpen_strength_slider")
                )
                // Kattintásra az élesítés erőssége visszaáll az alapértelmezett 0.0-s értékre
                Text(
                    text = String.format(Locale.US, "%.1f", sharpenStrength),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .clickable { onSharpenStrengthChange(0.0f) },
                    textAlign = TextAlign.End
                )
            }
        } else {
            // Élő mód: Fókusz mód választó (Auto, Locked, Manual) és Manuális Fókusz csúszka
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bal oldal: Fókusz Mód Választó (Auto / Locked / Manual)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("auto", "manual").forEach { mode ->
                        val selected = focusMode == mode
                        val icon = when (mode) {
                            "auto" -> Icons.Default.CenterFocusStrong
                            else -> Icons.Default.Tune
                        }
                        val label = when (mode) {
                            "auto" -> stringResource(R.string.focus_auto)
                            else -> stringResource(R.string.focus_manual)
                        }
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
                                .clickable { onFocusModeChange(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) themeColor else Color(0xFF5E5C64),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Jobb oldal: Fókusztávolság csúszka (csak ha manual módban van és támogatott)
                val isManualEnabled = focusMode == "manual" && minFocusDistance > 0f
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = null,
                        tint = if (isManualEnabled) themeColor else Color(0xFF5E5C64),
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = manualFocusDistance,
                        onValueChange = onManualFocusDistanceChange,
                        valueRange = 0f..minFocusDistance.coerceAtLeast(0.1f),
                        enabled = isManualEnabled,
                        interactionSource = focusInteractionSource,
                        colors = SliderDefaults.colors(
                            activeTrackColor = themeColor,
                            thumbColor = themeColor,
                            inactiveTrackColor = Color(0xFF1B1A21),
                            disabledActiveTrackColor = Color(0xFF3E3D45),
                            disabledThumbColor = Color(0xFF3E3D45),
                            disabledInactiveTrackColor = Color(0xFF1B1A21)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    // Kattintásra a manuális fókusz visszaáll automatikus fókuszmódba (0.0 távolság)
                    Text(
                        text = if (isManualEnabled) String.format(Locale.US, "%.1f", manualFocusDistance) else "Auto",
                        color = if (isManualEnabled) Color.White else Color(0xFF5E5C64),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .widthIn(min = 32.dp)
                            .clickable(enabled = isManualEnabled) { onManualFocusDistanceChange(0.0f) },
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0C11)
@Composable
fun CombinedZoomFiltersTuneTabContentPreviewLive() {
    Box(modifier = Modifier.padding(16.dp)) {
        CombinedZoomFiltersTuneTabContent(

            themeColor = Color(0xFF8B5CF6),
            isFrozen = false,
            frozenScale = 1.0f,
            onFrozenScaleChange = {},
            liveZoomRatio = 1.0f,
            extraDigitalZoom = 1.0f,
            sliderMin = 1.0f,
            sliderMax = 8.0f,

            onApplyTotalZoom = { _, _ -> },
            contrast = 1.0f,
            onContrastChange = {},
            brightness = 0f,
            onBrightnessChange = {},
            filterMode = FilterMode.NORMAL,
            onFilterModeChange = {},
            exposureIndex = 0,
            onExposureIndexChange = {},
            minExposureIndex = -4,
            maxExposureIndex = 4,
            sharpenStrength = 0.0f,
            onSharpenStrengthChange = {},
            focusMode = "auto",
            onFocusModeChange = {},
            manualFocusDistance = 0f,
            onManualFocusDistanceChange = {},
            minFocusDistance = 10f
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0C11)
@Composable
fun CombinedZoomFiltersTuneTabContentPreviewFrozen() {
    Box(modifier = Modifier.padding(16.dp)) {
        CombinedZoomFiltersTuneTabContent(

            themeColor = Color(0xFF8B5CF6),
            isFrozen = true,
            frozenScale = 2.0f,
            onFrozenScaleChange = {},
            liveZoomRatio = 1.0f,
            extraDigitalZoom = 1.0f,
            sliderMin = 1.0f,
            sliderMax = 8.0f,

            onApplyTotalZoom = { _, _ -> },
            contrast = 1.5f,
            onContrastChange = {},
            brightness = 20f,
            onBrightnessChange = {},
            filterMode = FilterMode.YELLOW,
            onFilterModeChange = {},
            exposureIndex = 0,
            onExposureIndexChange = {},
            minExposureIndex = -4,
            maxExposureIndex = 4,
            sharpenStrength = 5.0f,
            onSharpenStrengthChange = {},
            focusMode = "manual",
            onFocusModeChange = {},
            manualFocusDistance = 5.0f,
            onManualFocusDistanceChange = {},
            minFocusDistance = 10f
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0C11)
@Composable
fun SettingsTabContentPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        SettingsTabContent(
            themeColor = Color(0xFF8B5CF6),
            themeOptions = listOf(
                AppThemeColor(R.string.theme_purple, Color(0xFFB180FF)),
                AppThemeColor(R.string.theme_green, Color(0xFF00FF87)),
                AppThemeColor(R.string.theme_gold, Color(0xFFFFB300)),
                AppThemeColor(R.string.theme_blue, Color(0xFF00D2FF)),
                AppThemeColor(R.string.theme_orange, Color(0xFFFF6B00))
            ),
            currentThemeIndex = 0,
            onThemeIndexChange = {},
            onRateApp = {},
            onShowTutorial = {},
            onShowTipJar = {},
            onChangeLanguage = {},
            isHdrEnabled = false,
            onHdrEnabledChange = {},
            isNightEnabled = true,
            onNightEnabledChange = {}
        )
    }
}

