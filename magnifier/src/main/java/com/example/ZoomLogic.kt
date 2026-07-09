package com.example

/*
 * ZoomLogic.kt
 * ------------
 * Ez a fájl a nagyító-app "matematikai magja": tiszta (pure) segédfüggvényeket
 * tartalmaz a zoom (nagyítás) és a pan (pásztázás, azaz a nagyított kép eltolása)
 * kiszámításához. Szándékosan NINCS benne Android UI- vagy kamera-kód: nem hivatkozik
 * Activity-re, ViewModel-re vagy hardverre. Ennek az az előnye, hogy ezek a függvények
 * önmagukban, emulator/telefon nélkül is unit-tesztelhetők.
 *
 * Két központi fogalom, amit érdemes előre tisztázni:
 *
 *  - Kamera-zoom (optikai / hibrid zoom): maga a kamera hardvere/driver-e nagyít.
 *    Ez lehet valódi optikai zoom (teleobjektív, mozgó lencsék) vagy "hibrid" zoom,
 *    ahol több lencse képét és okos algoritmusokat kombinál a rendszer. A lényeg:
 *    a részletgazdagság megmarad, mert a szenzor/optika tényleg közelebb "megy".
 *    Minden kamerának van egy megengedett tartománya: minZoom..maxZoom.
 *
 *  - Digitális zoom (szoftveres nagyítás): amikor a kamera már nem tud tovább nagyítani,
 *    a kész képkockát egyszerűen "felfújjuk" (a pixeleket kinagyítjuk) a kijelzőn.
 *    Ez nem hoz új részletet, csak nagyobbra skálázza a meglévő pixeleket, ezért
 *    a kép idővel pixeles/homályos lesz. Cserébe korlátlanul nagyítható.
 *
 * A computeZoomDistribution() épp ezt a két világot osztja szét egy kért összesített
 * nagyításból; a clampPan() pedig azt biztosítja, hogy pásztázáskor ne csússzon üres
 * (fekete) sáv a kép mellé.
 */

// Az Offset a Compose 2D koordináta/vektor típusa (x, y Float értékekkel);
// itt a pan-eltolás (elmozdulás) reprezentálására használjuk.
import androidx.compose.ui.geometry.Offset
// Az IntSize egész (pixel) szélesség/magasság pár; itt a viewport (a látható
// kijelző-terület) méretét adja meg.
import androidx.compose.ui.unit.IntSize

// Egy cél teljes nagyítás felosztása a kamera (optikai/hibrid) és a kijelző-oldali digitális
// zoom között: ameddig a kamera zoom-tartománya elviszi, ott marad; fölötte digitális szorzó.
//
// A `data class` a Kotlin egyik nagy kényelmi eszköze: egyszerű adathordozó (value object),
// amelyhez a fordító automatikusan legenerálja az equals()/hashCode()/toString() és copy()
// metódusokat, valamint a "destructuring"-ot (pl. `val (c, d) = distribution`). Ideális arra,
// hogy egy függvény EGYSZERRE két értéket adjon vissza rendezett módon, egy saját típus nélkül,
// amit külön kellene karbantartani. Itt két mezője van:
//   - cameraZoom:  mekkora nagyítást állítson be maga a kamera (a min..max tartományon belül).
//   - digitalZoom: mekkora további szoftveres szorzót tegyünk rá a kijelzőn (1.0f = nincs plusz).
data class ZoomDistribution(val cameraZoom: Float, val digitalZoom: Float)

// Kiszámítja, hogyan bontsuk fel a felhasználó által kért ÖSSZESÍTETT nagyítást (`target`)
// kamera-zoomra és digitális zoomra.
//
// Paraméterek:
//   - target:  a kívánt teljes nagyítás (pl. 1.0f = nincs nagyítás, 8.0f = nyolcszoros).
//   - minZoom: a kamera által megengedett legkisebb zoom (általában 1.0f).
//   - maxZoom: a kamera által megengedett legnagyobb (optikai/hibrid) zoom.
//
// Visszatérési érték: egy ZoomDistribution, amelyben a cameraZoom * digitalZoom szorzat
// (nagyjából) a kért `target`-et adja ki.
//
// Stratégia: "amíg a kamera bírja, addig kamerával nagyítunk" — mert a kamera-zoom
// megőrzi a részleteket, míg a digitális zoom csak felfújja a pixeleket. A digitális
// szorzót csak akkor kapcsoljuk be, ha már túllépnénk a kamera maxZoom-ját.
fun computeZoomDistribution(target: Float, minZoom: Float, maxZoom: Float): ZoomDistribution {
    // Nem engedjük a kért értéket a kamera alsó határa alá: a coerceAtLeast() a `minZoom`-nál
    // kisebb értékeket felhúzza minZoom-ra (a nagyobbakat változatlanul hagyja).
    val clamped = target.coerceAtLeast(minZoom)
    return if (clamped <= maxZoom) {
        // A kért nagyítás belefér a kamera tartományába -> mindent a kamera intéz,
        // nincs szükség szoftveres nagyításra (digitalZoom = 1.0f, azaz "nincs plusz szorzó").
        ZoomDistribution(clamped, 1.0f)
    } else {
        // Túllépnénk a kamera határát -> a kamerát a maximumára állítjuk, a maradék nagyítást
        // pedig digitálisan tesszük rá. A `clamped / maxZoom` az a szorzó, ami a maxZoom-ról
        // felviszi a képet a kért `clamped` szintre.
        // A `if (maxZoom > 0f) ... else 1.0f` egy nullával-osztás elleni védelem: ha maxZoom
        // valamiért 0 (vagy negatív) lenne, ne osszunk vele, hanem essünk vissza 1.0f-re.
        ZoomDistribution(maxZoom, if (maxZoom > 0f) clamped / maxZoom else 1.0f)
    }
}

// Pan-eltolás korlátozása úgy, hogy a nagyított tartalom széle ne szakadjon el a viewport szélétől
//
// "Pan" = pásztázás: amikor a nagyított képet ujjal ide-oda húzzuk, hogy a kép más-más
// részét lássuk. Ha ezt nem korlátoznánk, a felhasználó annyira eltolhatná a képet, hogy
// a szélén üres (fekete) sáv jelenne meg — ez a függvény pontosan ezt akadályozza meg:
// a kért `pan` eltolást beszorítja a még megengedett tartományba.
//
// Paraméterek:
//   - pan:      a kért eltolás vektora (x, y pixelben), tipikusan a húzó gesztusból.
//   - scale:    az aktuális nagyítási arány (1.0f = eredeti méret, nincs mit pásztázni).
//   - viewport: a látható kijelző-terület mérete pixelben (szélesség, magasság).
//
// Visszatérési érték: a "megvágott" (clamped) eltolás — sosem lóg túl a képen.
fun clampPan(pan: Offset, scale: Float, viewport: IntSize): Offset {
    // Ha nincs valódi nagyítás (scale <= 1.0f), a kép pont kitölti a viewportot, tehát nincs
    // "többlet" tartalom, amit pásztázni lehetne. Ilyenkor az eltolás mindig nulla legyen.
    // Az Offset.Zero a (0f, 0f) vektort jelenti.
    if (scale <= 1.0f) return Offset.Zero
    // A nagyítás miatt a kép (scale - 1.0f)-szer szélesebb/magasabb, mint a viewport.
    // Ez a "többlet" fele lóghat ki az egyik, fele a másik oldalon, ezért osztunk 2f-fel:
    // ennyi a maximálisan megengedett eltolás vízszintesen (maxPanX) és függőlegesen (maxPanY).
    val maxPanX = (scale - 1.0f) * viewport.width / 2f
    val maxPanY = (scale - 1.0f) * viewport.height / 2f
    // A coerceIn(-max, +max) a kért eltolást a [-max, +max] tartományba szorítja: ami kilógna,
    // azt visszahúzza a határra. Így a kép széle legfeljebb a viewport széléig tolható, tovább nem.
    return Offset(
        x = pan.x.coerceIn(-maxPanX, maxPanX),
        y = pan.y.coerceIn(-maxPanY, maxPanY)
    )
}

