package com.example

/*
 * ============================================================================
 *  ImageProcessing.kt — a nagyító-app KÉPFELDOLGOZÓ rétege
 * ============================================================================
 *
 * MI EZ A FÁJL?
 * Itt van összegyűjtve minden "kép-matek": a pixelekkel dolgozó, tisztán
 * kiszámítható (side-effect nélküli) függvények. Ide tartozik:
 *
 *   1. SZÍNSZŰRŐK  (buildFilterMatrixValues, applyColorFilterToBitmap):
 *      akadálymentesítési / éjszakai módok — monokróm, invertált, sárga,
 *      vörös — plusz kontraszt és fényerő, mind egyetlen "color matrix"-szal.
 *
 *   2. ÉLESÍTÉS  (sharpenBitmap): saját, kézzel optimalizált konvolúciós
 *      (convolution) élesítő szűrő, ami digitális zoomnál kihozza a részleteket.
 *
 *   3. VÁGÁS GEOMETRIÁJA  (computeVisibleCropRect, computeAspectCropRect):
 *      annak kiszámítása, hogy a kamera-képnek MELYIK téglalap-részét látja
 *      épp a felhasználó (digitális zoom + pásztázás), illetve hogyan vágjuk
 *      a képet a kívánt képarányra (aspect ratio).
 *
 *   4. EXPORT / DEKÓDOLÁS  (decodeCapturedJpeg, computeInSampleSize,
 *      processExportBitmap): a lefotózott JPEG memóriakímélő beolvasása és a
 *      mentendő/megosztandó kép előállítása.
 *
 * MIÉRT KÜLÖN FÁJL?
 * Ezek a függvények NEM tudnak a UI-ról és az Android életciklusról; csak
 * bemenet -> kimenet. Így egységtesztelhetők (unit testable), és a felhasználói
 * felület (Compose) csak meghívja őket. Ez a "separation of concerns" elv.
 *
 * VISSZATÉRŐ ANDROID-FOGALMAK (részletek lentebb, az adott függvénynél):
 *   - Bitmap: memóriában tárolt raszteres (pixeltömb) kép.
 *   - ColorMatrix: 4x5-ös mátrix, amivel a színcsatornákat transzformáljuk.
 *   - BitmapFactory: JPEG/PNG bájtokból Bitmap-et dekódoló segédosztály.
 */

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.roundToInt

/**
 * A választható színszűrő-módok felsorolása.
 *
 * ENUM CLASS — RÖVIDEN:
 * Az `enum class` egy zárt, előre rögzített értékkészlet. Itt pontosan öt
 * lehetőség van (NORMAL, MONOCHROME, ...), és a kód sehol máshol nem hozhat
 * létre újat. Minden enum-tag egyetlen, alkalmazás-szintű példány (singleton):
 * amikor `FilterMode.RED`-et írsz, mindig ugyanaz az objektum. A when-ág
 * (lásd buildFilterMatrixValues) így teljesnek is tekinthető: ha új tagot
 * veszel fel, a fordító figyelmeztet a hiányzó ágra.
 *
 * MIÉRT VAN A TAGOKNAK PARAMÉTERE?
 * A Kotlinban az enum-tag "hordozhat" adatot. Itt minden mód két számot kap:
 * `labelRes` (a felület gombfelirata) és `descriptionRes` (a hosszabb leírás).
 * A `R.string.filter_...` egy fordításkor generált egész szám (resource ID),
 * ami a res/values/strings.xml egy sorára mutat.
 *
 * MIÉRT ERŐFORRÁS-AZONOSÍTÓ (@StringRes Int) ÉS NEM SIMA String?
 * A `@StringRes` annotáció az androidx.annotation csomagból csak jelzés a
 * lint/IDE felé: "ez az Int NEM akármilyen szám, hanem egy string-erőforrás
 * azonosítója" — így fordítás előtt kiszúrja, ha véletlenül más resource ID-t
 * (pl. R.drawable.*) adnál át. A tényleges szöveget majd futásidőben oldjuk
 * fel: context.getString(labelRes). Ennek két oka van:
 *   1) LOKALIZÁCIÓ: ugyanaz az ID a készülék nyelvétől függően magyar vagy
 *      angol szöveget ad vissza (values-hu/strings.xml stb.). Ha ide magát a
 *      String-et írnánk, egyetlen nyelvre égetnénk be a feliratot.
 *   2) A felsorolás az app indulásakor létrejön, amikor még nincs Context;
 *      egy Int viszont bármikor tárolható, a szöveg csak akkor kell, amikor
 *      tényleg kirajzoljuk.
 */
enum class FilterMode(@param:StringRes val labelRes: Int, @param:StringRes val descriptionRes: Int) {
    NORMAL(R.string.filter_normal, R.string.filter_normal_desc),
    MONOCHROME(R.string.filter_monochrome, R.string.filter_monochrome_desc),
    INVERTED(R.string.filter_inverted, R.string.filter_inverted_desc),
    YELLOW(R.string.filter_yellow, R.string.filter_yellow_desc),
    RED(R.string.filter_red, R.string.filter_red_desc),
    DEUTERANOPIA(R.string.filter_deuteranopia, R.string.filter_deuteranopia_desc),
    PROTANOPIA(R.string.filter_protanopia, R.string.filter_protanopia_desc),
    TRITANOPIA(R.string.filter_tritanopia, R.string.filter_tritanopia_desc)
}

/**
 * Kiszámítja a kiválasztott szűrőhöz + kontraszthoz + fényerőhöz tartozó
 * "color matrix" 20 elemű számtömbjét.
 *
 * PARAMÉTEREK:
 *   - filterMode: melyik akadálymentesítési/éjszakai szűrő (lásd FilterMode).
 *   - contrast:   kontraszt-szorzó (1.0 = változatlan, >1 erősebb kontraszt).
 *   - brightness: fényerő-eltolás, közvetlenül a csatornákhoz adva (0 = változatlan).
 * VISSZATÉRÉSI ÉRTÉK: 20 elemű FloatArray, egy 4x5-ös mátrix sorfolytonosan
 *   (row-major). Ezt közvetlenül átadhatjuk egy ColorMatrixColorFilter-nek.
 *
 * ------------------------------------------------------------------------
 * MI AZ A ColorMatrix (COLOR MATRIX)?
 * ------------------------------------------------------------------------
 * Egy 4x5-ös mátrix, amivel EGYETLEN lépésben átszínezhetünk minden pixelt.
 * A pixel négy csatornája (R, G, B, A = piros, zöld, kék, alfa/átlátszóság)
 * egy [R, G, B, A, 1] oszlopvektorként megy be — az ötödik "1" teszi lehetővé
 * a konstans hozzáadását (offset). A mátrixszorzás a kimenetet így adja:
 *
 *     R' = m0*R  + m1*G  + m2*B  + m3*A  + m4      <- 0. sor (indexek 0..4)
 *     G' = m5*R  + m6*G  + m7*B  + m8*A  + m9      <- 1. sor (indexek 5..9)
 *     B' = m10*R + m11*G + m12*B + m13*A + m14     <- 2. sor (indexek 10..14)
 *     A' = m15*R + m16*G + m17*B + m18*A + m19     <- 3. sor (indexek 15..19)
 *
 * Az EGYÜTTHATÓK jelentése:
 *   - Az átló (m0, m6, m12) az adott csatorna "önmagára" ható erőssége.
 *   - Az átlón kívüli tagok keverik a csatornákat (pl. zöld ->  pirosba).
 *   - Az ÖTÖDIK OSZLOP (m4, m9, m14, m19) a konstans eltolás: ezt adjuk
 *     hozzá fixen, mert az input vektor ötödik eleme mindig 1.
 *   Példa: az identitás-mátrix (átlóban 1, minden más 0) mindent változatlanul
 *   hagy — pontosan ez a NORMAL mód (a friss ColorNormal() alapból ilyen).
 *
 * ------------------------------------------------------------------------
 * LUMINANCIA (LUMA) ÉS A 0.2126 / 0.7152 / 0.0722 SÚLYOK
 * ------------------------------------------------------------------------
 * A "luma" a pixel érzékelt világossága egyetlen számban. NEM az (R+G+B)/3
 * egyszerű átlag, mert a szem a három alapszínt eltérő erősséggel érzékeli:
 * a zöldre a legérzékenyebb, a kékre a legkevésbé. A Rec.709 / sRGB szabvány
 * súlyai ezt tükrözik:
 *     luma = 0.2126*R + 0.7152*G + 0.0722*B     (összegük ~ 1.0)
 * A zöld ~72%-ot, a piros ~21%-ot, a kék csak ~7%-ot nyom. Ezért néz ki
 * "helyesnek" a szürkeárnyalatos kép, és nem lesz a kék részlet túl sötét.
 * A YELLOW/RED szűrőben minden kimeneti csatorna EZT a lumát számolja
 * (a sorok első három együtthatója pont ez a három súly), majd a színt
 * úgy állítjuk elő, hogy csak bizonyos csatornákat töltünk fel vele.
 *
 * ------------------------------------------------------------------------
 * MIÉRT WYSIWYG (What You See Is What You Get)?
 * ------------------------------------------------------------------------
 * Ez a függvény a szűrő EGYETLEN, kanonikus forrása. Ugyanezt a mátrixot
 * használja az élő képernyős előnézet (Compose overlay) ÉS a mentett/megosztott
 * kép renderelése is (applyColorFilterToBitmap). Mivel a matek bitre azonos,
 * a lementett kép pontosan az lesz, amit a felhasználó a képernyőn látott —
 * nincs "a szűrő máshogy néz ki mentés után" meglepetés.
 */
// A szűrő + kontraszt + fényerő kanonikus color matrix-a. A képernyős megjelenítés
// (combinedColorFilter, élő overlay) és a mentett/megosztott kép ugyanebből épül,
// hogy a kimenet pontosan az legyen, amit a felhasználó lát (WYSIWYG).
fun buildFilterMatrixValues(filterMode: FilterMode, contrast: Float, brightness: Float): FloatArray {
    // Friss ColorMatrix = identitás (átlóban 1-esek): alapból nem változtat semmit.
    val matrix = ColorMatrix()

    // 1. Apply base accessibility/night filter
    when (filterMode) {
        // NORMAL: marad az identitás, azaz eredeti színek.
        FilterMode.NORMAL -> { /* Keep identity */ }
        // MONOCHROME: a telítettség (saturation) 0-ra állítása -> szürkeárnyalat.
        // A setToSaturation() magától a fenti luma-súlyokkal képzi a szürkét.
        FilterMode.MONOCHROME -> {
            matrix.setToSaturation(0f)
        }
        // INVERTED: színnegatív. Minden csatorna: R' = -1*R + 255 (offset a 4/9/14
        // indexen), az alfa (utolsó sor) érintetlen marad -> sötét<->világos csere.
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
            // R' és G' egyaránt a luma (piros+zöld = sárga), B' = 0 -> monokróm sárga kép.
            matrix.set(ColorMatrix(floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.RED -> {
            // luma → (l, 0, 0): megegyezik az élő nézet deszaturálás + vörös modulálás blendjével
            // Csak R' kap luma-értéket, G' = B' = 0 -> monokróm vörös kép (éjszakai mód).
            matrix.set(ColorMatrix(floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.DEUTERANOPIA -> {
            // Zöld-gyengeség korrekciós mátrix (Daltonization)
            matrix.set(ColorMatrix(floatArrayOf(
                0.367f, 0.861f, -0.228f, 0f, 0f,
                0.280f, 0.673f, 0.047f, 0f, 0f,
                -0.012f, 0.043f, 0.969f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.PROTANOPIA -> {
            // Piros-gyengeség korrekciós mátrix (Daltonization)
            matrix.set(ColorMatrix(floatArrayOf(
                0.152f, 1.052f, -0.205f, 0f, 0f,
                0.115f, 0.786f, 0.099f, 0f, 0f,
                -0.004f, -0.048f, 1.053f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterMode.TRITANOPIA -> {
            // Kék-gyengeség korrekciós mátrix (Daltonization)
            matrix.set(ColorMatrix(floatArrayOf(
                1.013f, -0.016f, 0.003f, 0f, 0f,
                0.008f, 1.011f, -0.019f, 0f, 0f,
                0.008f, 0.252f, 0.741f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
    }

    // 2. Adjust contrast & brightness directly in color matrix values
    // A .values egy 20 elemű FloatArray-t ad vissza (a fenti 4x5 mátrix sorfolytonosan).
    val values = matrix.values
    // KONTRASZT: a szín-sorok (0..14 index, azaz R', G', B' sorai) minden együtthatóját
    // beszorozzuk a contrast szorzóval. Az alfa-sort (15..19) szándékosan nem bántjuk,
    // hogy az átlátszóság ne torzuljon. Megjegyzés: ez egyszerű szorzásos kontraszt,
    // nem a középszürke (128) körül forgató változat.
    for (i in 0..14) {
        values[i] = values[i] * contrast
    }
    // FÉNYERŐ: a három szín-sor konstans oszlopához (offset) adjuk hozzá a brightness-t.
    // Index 4 = R offset, 9 = G offset, 14 = B offset (lásd a fenti indextérképet).
    values[4] = values[4] + brightness
    values[9] = values[9] + brightness
    values[14] = values[14] + brightness

    return values
}

/**
 * Kiszámítja, hogy a digitális zoom + pásztázás mellett a forráskép MELYIK
 * téglalap-részét látja épp a felhasználó — a FORRÁSKÉP pixelkoordinátáiban.
 *
 * PARAMÉTEREK:
 *   - width, height: a teljes forráskép mérete pixelben.
 *   - scale: a digitális nagyítás (1.0 = nincs zoom, 2.0 = kétszeres).
 *   - panX, panY: a felhasználó eltolása (pásztázás) képernyő-pixelben.
 * VISSZATÉRÉSI ÉRTÉK: android.graphics.Rect a látható kivágással (left, top,
 *   right, bottom). Zoom nélkül (vagy hibás méretnél) a teljes kép.
 *
 * A geometria szándékosan azonos az élő nézet graphicsLayer-transzformációjával
 * (középpontból skálázó zoom + eltolás) és a minimap-számítással, hogy a mentett
 * kivágás pontosan az legyen, amit a képernyőn látni.
 */
// A digitális zoom által képernyőn látott kivágás forrás-koordinátákban; a geometria
// megegyezik a minimap-kalkulációval (graphicsLayer középpontos skálázás + eltolás).
fun computeVisibleCropRect(width: Int, height: Int, scale: Float, panX: Float, panY: Float): android.graphics.Rect {
    // Nincs mit vágni: 1x (vagy kisebb) zoomnál, illetve érvénytelen méretnél
    // a teljes képet adjuk vissza.
    if (scale <= 1.0f || width <= 0 || height <= 0) {
        return android.graphics.Rect(0, 0, width, height)
    }
    // A kivágás annyival kisebb a teljes képnél, amennyire nagyítunk: 2x zoom ->
    // fele akkora látható terület. coerceIn(1, width): legalább 1px, legfeljebb a teljes szélesség.
    val cropW = (width / scale).roundToInt().coerceIn(1, width)
    val cropH = (height / scale).roundToInt().coerceIn(1, height)
    // A kivágás bal-felső sarka. A pan képernyő-pixelben van, ezért /scale-lel
    // váltjuk forrás-pixelre. width/2 a kép közepe; ebből kivonva a pásztázást
    // megkapjuk a látható terület KÖZEPÉT, majd -cropW/2-vel a bal szélét.
    // coerceIn(0, width-cropW): a téglalap ne lógjon ki a képből.
    val left = ((width / 2f - panX / scale) - cropW / 2f).roundToInt().coerceIn(0, width - cropW)
    val top = ((height / 2f - panY / scale) - cropH / 2f).roundToInt().coerceIn(0, height - cropH)
    return android.graphics.Rect(left, top, left + cropW, top + cropH)
}

/**
 * Középre igazított ("center crop") vágás egy cél-KÉPARÁNYRA (aspect ratio).
 *
 * A képarány a szélesség/magasság hányadosa (pl. 16:9 = 1.777..., 4:3 = 1.333).
 * Ha a forráskép aránya nem egyezik a céllal, a felesleget a hosszabbik tengely
 * KÉT SZÉLÉBŐL vágjuk le egyenlően, így a lényeg középen marad. Ez ugyanaz,
 * amit a kamera-előnézet FILL_CENTER módban tesz a képernyőn.
 *
 * PARAMÉTEREK:
 *   - width, height: a forráskép mérete pixelben.
 *   - targetAspect: a kívánt szélesség/magasság arány.
 * VISSZATÉRÉSI ÉRTÉK: a bevágott terület Rect-je (érvénytelen bemenetnél a teljes kép).
 */
// Középre igazított vágás a cél-képarányra (a preview FILL_CENTER kivágásának megfelelően)
fun computeAspectCropRect(width: Int, height: Int, targetAspect: Float): android.graphics.Rect {
    if (width <= 0 || height <= 0 || targetAspect <= 0f) {
        return android.graphics.Rect(0, 0, width, height)
    }
    // A forráskép saját képaránya.
    val srcAspect = width.toFloat() / height.toFloat()
    return if (srcAspect > targetAspect) {
        // A forrás SZÉLESEBB a kelleténél -> oldalt vágunk. A magasság marad, az
        // új szélességet a célarányból számoljuk: cropW = height * targetAspect.
        val cropW = (height * targetAspect).roundToInt().coerceIn(1, width)
        // A maradék szélességet elosztjuk kétfelé -> vízszintes középre igazítás.
        val left = (width - cropW) / 2
        android.graphics.Rect(left, 0, left + cropW, height)
    } else {
        // A forrás MAGASABB (vagy pont jó) -> fent/lent vágunk. A szélesség marad,
        // az új magasság: cropH = width / targetAspect.
        val cropH = (width / targetAspect).roundToInt().coerceIn(1, height)
        // Függőleges középre igazítás.
        val top = (height - cropH) / 2
        android.graphics.Rect(0, top, width, top + cropH)
    }
}

/**
 * Kiszámítja a BitmapFactory `inSampleSize` értékét: hányad részére skálázzuk le
 * a képet MÁR DEKÓDOLÁSKOR, hogy beleférjen a memóriába.
 *
 * MI AZ AZ inSampleSize?
 * A BitmapFactory ezzel az egész számmal ritkítja a beolvasott pixeleket:
 *   1 = teljes felbontás, 2 = fele szélesség+magasság (negyed pixelszám),
 *   4 = negyed méret, ... Az Android CSAK 2-HATVÁNYT (1, 2, 4, 8, ...) fogad el;
 *   más értéket a legközelebbi 2-hatványra kerekít. Ezért lépegetünk *2-vel.
 *
 * MIÉRT KELL? (OOM-védelem)
 * Egy kép memóriaigénye szélesség*magasság*4 bájt (ARGB_8888). Egy 12 MP-es fotó
 * ~48 MB. Ha teljes felbontásban dekódolnánk, könnyen OutOfMemoryError (OOM) lenne.
 * A leskálázás MÁR a dekódoláskor történik, tehát a nagy verzió sosem foglal helyet.
 *
 * PARAMÉTEREK:
 *   - width, height: a kép EREDETI mérete (az inJustDecodeBounds fázisból).
 *   - maxDim: a megengedett leghosszabb oldal pixelben.
 * VISSZATÉRÉSI ÉRTÉK: a legkisebb 2-hatvány, amivel a hosszabbik oldal <= maxDim.
 */
// Legkisebb 2-hatvány mintavételezés, amellyel a leghosszabb oldal maxDim alá kerül (OOM-védelem)
fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (maxDim <= 0) return 1
    var sampleSize = 1
    // Duplázzuk a mintavételt (1 -> 2 -> 4 -> ...), amíg a hosszabbik oldal
    // adott mintavétellel a maxDim korlát ALÁ nem kerül.
    while (maxOf(width, height) / sampleSize > maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * A lefotózott (still capture) JPEG-bájtokat Bitmap-pé dekódolja: memóriakímélő
 * leskálázással, a nézet képarányára vágva, végül a kijelző tájolására forgatva.
 *
 * PARAMÉTEREK:
 *   - bytes: a nyers JPEG bájttömb (a kamerából).
 *   - rotationDegrees: hány fokkal kell forgatni, hogy állva jelenjen meg (0/90/180/270).
 *   - targetAspect: a viewport (megjelenített nézet) kívánt képaránya.
 *   - maxDim: a leghosszabb oldal felső korlátja (alap 4096) — OOM-védelem.
 * VISSZATÉRÉSI ÉRTÉK: a kész Bitmap, vagy null, ha a dekódolás nem sikerül
 *   (a `?` a `Bitmap?`-ben azt jelenti: nullable, tehát lehet null a válasz).
 *
 * ------------------------------------------------------------------------
 * A BitmapFactory KÉTLÉPCSŐS DEKÓDOLÁSA (inJustDecodeBounds)
 * ------------------------------------------------------------------------
 * Ahhoz, hogy tudjuk MEKKORA leskálázás kell (inSampleSize), előbb ismernünk kell
 * a kép méretét — DE nem akarjuk emiatt az egész (nagy) képet a memóriába tölteni.
 * Ezért két menetben dekódolunk:
 *   1. menet: inJustDecodeBounds = true -> a BitmapFactory csak a FEJLÉCET olvassa,
 *      kitölti az outWidth/outHeight mezőket, és NEM foglal pixelmemóriát
 *      (a visszatérő Bitmap null, ez itt szándékos, ezért nem is használjuk).
 *   2. menet: a kiszámolt inSampleSize-zal ténylegesen beolvassuk a (leskálázott) képet.
 *
 * VÁGÁS A FORGATÁS ELŐTT:
 * Előbb vágunk képarányra, csak utána forgatunk — így a (drágább) forgatás már a
 * kisebb bitmapen fut. 90/270 foknál a szélesség és a magasság felcserélődik, ezért
 * a forgatás ELŐTTI vágáshoz az INVERTÁLT célarányt (1/targetAspect) használjuk.
 */
// A still capture JPEG dekódolása memória-plafonnal, a viewport képarányára vágva, majd a
// kijelző tájolására forgatva. A vágás a forgatás ELŐTT történik (90/270 foknál invertált
// cél-aspecttel), így a forgatandó bitmap kisebb.
fun decodeCapturedJpeg(bytes: ByteArray, rotationDegrees: Int, targetAspect: Float, maxDim: Int = 4096): Bitmap? {
    // 1. menet: csak a méreteket olvassuk ki, pixelmemória nélkül.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    // Ha nem sikerült valós méretet kiolvasni, a JPEG sérült/érvénytelen -> null.
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // 2. menet: a méretekből kiszámolt 2-hatvány leskálázással dekódolunk ténylegesen.
    val options = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
    }
    // A ?: (Elvis operátor) itt "ha null, akkor return null" — biztonságos kilépés.
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    // 90/270 foknál a forgatás után W és H helyet cserél, ezért a forgatás előtti
    // vágáshoz a célarány reciprokát használjuk. (% 180 != 0 -> 90 vagy 270 fok.)
    val bufferAspect = if (rotationDegrees % 180 != 0 && targetAspect > 0f) 1f / targetAspect else targetAspect
    val crop = computeAspectCropRect(bitmap.width, bitmap.height, bufferAspect)
    // Csak akkor vágunk (új bitmapet allokálva), ha tényleg van mit levágni.
    if (crop.width() < bitmap.width || crop.height() < bitmap.height) {
        bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
    }

    // A tájolás korrekciója: egy forgató Matrix-szal új, elforgatott bitmapet készítünk.
    if (rotationDegrees != 0) {
        // postRotate: forgatás fokban; a createBitmap utolsó true paramétere a
        // simítás (bilineáris szűrés) bekapcsolása a forgatott pixeleknél.
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    return bitmap
}

/**
 * A mentés/megosztás előtti teljes képfeldolgozó folyamat (pipeline). Előállítja
 * azt a végleges Bitmap-et, ami a felhasználó által látott állapotot tükrözi.
 *
 * Két üzemmódot kezel:
 *   - FAGYASZTOTT (isFrozen == true): a kép már ki van merevítve, rajta a
 *     kontraszt/fényerő is látszik, ezért azokat is beleszámoljuk a kimenetbe.
 *   - ÉLŐ mód: a képernyőn látható kivágást (digitális zoom + pan) és élesítést
 *     reprodukáljuk. Élő nézetben a kontraszt/fényerő NEM látszik, ezért itt
 *     szándékosan 1.0/0.0 értékkel hívjuk a szűrőt (nem kerül a kimenetre) — így
 *     a mentett kép megegyezik a látottal (WYSIWYG).
 *
 * PARAMÉTEREK:
 *   - raw: a nyers forrás-bitmap.
 *   - isFrozen: ki van-e merevítve a kép.
 *   - digitalZoom, digitalPan: a digitális nagyítás és eltolás (Offset = x/y pár).
 *   - sharpenStrength: az élesítés erőssége (0 = kikapcsolva).
 *   - filterMode, contrast, brightness: a színszűrő paraméterei.
 * VISSZATÉRÉSI ÉRTÉK: a feldolgozott Bitmap.
 */
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
    // Fagyasztott kép: a kontraszt/fényerő is látszik, tehát azokat is alkalmazzuk.
    if (isFrozen) {
        return applyColorFilterToBitmap(raw, filterMode, contrast, brightness)
    }
    var result = raw
    // Élő mód: ha van digitális zoom, előbb a látható részt vágjuk ki...
    if (digitalZoom > 1.0f) {
        val rect = computeVisibleCropRect(result.width, result.height, digitalZoom, digitalPan.x, digitalPan.y)
        result = Bitmap.createBitmap(result, rect.left, rect.top, rect.width(), rect.height())
        // ...majd a felnagyított kivágást élesítjük (csak zoomnál van értelme).
        if (sharpenStrength > 0.0f) {
            result = sharpenBitmap(result, strength = sharpenStrength)
        }
    }
    // A szűrőt 1.0 kontraszttal és 0.0 fényerővel hívjuk: azok élő nézetben nem
    // látszanak, így nem is kerülnek a mentett képre.
    return applyColorFilterToBitmap(result, filterMode, 1.0f, 0.0f)
}

/**
 * Egy Bitmap-re alkalmazza a color matrix szűrőt, ÚJ Bitmap-et adva vissza.
 * Ezt háttérszálon hívjuk (mentés/megosztás), a UI-tól függetlenül.
 *
 * PARAMÉTEREK: source (forráskép), filterMode, contrast, brightness.
 * VISSZATÉRÉSI ÉRTÉK: a szűrt kép; ha nincs tényleges módosítás, maga a forrás.
 *
 * ------------------------------------------------------------------------
 * MI AZ A Bitmap ÉS A Bitmap.Config.ARGB_8888?
 * ------------------------------------------------------------------------
 * A Bitmap egy raszteres kép: a pixelek egy táblázata a memóriában. A "config"
 * mondja meg, hogy EGY PIXELT hány bájton tárolunk. Az ARGB_8888 a leggyakoribb:
 * pixelenként 4 csatorna, MINDEGYIK 8 bit (a "8888" négy nyolcast jelöl), azaz
 * összesen 4 BÁJT / pixel. A négy csatorna:
 *     A = alfa (átlátszóság), R = piros, G = zöld, B = kék.
 * Minden csatorna 0..255 közötti érték. Egy pixel egyetlen 32 bites int-ben
 * 0xAARRGGBB elrendezésben tárolódik (a legfelső bájt az alfa). Emiatt egy
 * W*H méretű kép memóriaigénye W*H*4 bájt.
 * ------------------------------------------------------------------------
 */
// Helper to manually render combined color matrices to a saved or shared bitmap in background threads
fun applyColorFilterToBitmap(source: Bitmap, filterMode: FilterMode, contrast: Float, brightness: Float): Bitmap {
    // Gyorsítás: ha nincs valódi változtatás (alap szűrő, semleges kontraszt/fényerő),
    // felesleges új bitmapet gyártani -> visszaadjuk az eredetit.
    if (filterMode == FilterMode.NORMAL && contrast == 1.0f && brightness == 0.0f) {
        return source
    }
    // Üres cél-bitmap a forrással azonos méretben, 4 bájt/pixel formátumban.
    val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    // Canvas = "rajzvászon" a cél-bitmap fölött; amit rá rajzolunk, abba kerül.
    val canvas = android.graphics.Canvas(resultBitmap)
    // Paint = "ecset": itt beállítjuk rá a színszűrőt, ami rajzolás közben átszínez.
    val paint = android.graphics.Paint()
    // A buildFilterMatrixValues 20 elemű tömbjéből ColorMatrixColorFilter-t készítünk,
    // ez alkalmazza pixelenként a fentebb részletezett 4x5-ös mátrixot.
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(buildFilterMatrixValues(filterMode, contrast, brightness))
    // A forrást a (0,0) pontba rajzoljuk az ecsettel -> a szűrő a rajzoláskor lép életbe.
    canvas.drawBitmap(source, 0f, 0f, paint)
    return resultBitmap
}

/**
 * Saját, kézzel optimalizált ÉLESÍTŐ (sharpening) szűrő él-adaptív védelemmel.
 *
 * PARAMÉTEREK:
 *   - src: az élesítendő forrás-bitmap.
 *   - strength: az élesítés erőssége (alap 0.8). Nagyobb = markánsabb élek.
 * VISSZATÉRÉSI ÉRTÉK: új, élesített Bitmap (a nagyon kicsi képet változatlanul adja vissza).
 *
 * ------------------------------------------------------------------------
 * MI AZ A KONVOLÚCIÓ (CONVOLUTION) ÉS A LAPLACE-KERNEL?
 * ------------------------------------------------------------------------
 * A konvolúció a képfeldolgozás egyik alapművelete: minden pixel új értékét a
 * SZOMSZÉDAIVAL együtt, egy kis súlytáblázat (a "kernel") szerint számoljuk ki.
 * Itt egy 3x3-as kernelt csúsztatunk végig a képen; a most használt élesítő
 * kernel a Laplace-operátoron alapul (a Laplace a lokális "görbületet", azaz az
 * intenzitás gyors változását — az éleket — emeli ki):
 *
 *        0      -s       0
 *       -s    1+4s      -s          ( s = strength )
 * A középső pixel súlya 1+4s, a négy szomszédé -s. A súlyok összege 1+4s-4s = 1,
 * ezért EGYENLETES (sík) felületen a fényerő nem változik; ahol viszont él van,
 * ott a különbséget felerősíti -> a kép "élesebbnek" tűnik.
 * ------------------------------------------------------------------------
 */
fun sharpenBitmap(src: android.graphics.Bitmap, strength: Float = 0.8f): android.graphics.Bitmap {
    val width = src.width
    val height = src.height
    // 3x3 kernelhez minden belső pixelnek kell szomszédja; 2-nél kisebb oldalnál
    // nincs mit feldolgozni -> változatlanul visszaadjuk.
    if (width <= 2 || height <= 2) return src

    // A képet egyetlen lineáris int-tömbbe olvassuk a nagy sebességért
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(width * height)

    // A széleket fallbackként átmásoljuk
    System.arraycopy(pixels, 0, outPixels, 0, pixels.size)

    // Laplace-kernel súlyai a megadott erősségből: közép = 1 + 4*strength, szomszédok = -strength
    val centerWeight = 1f + 4f * strength
    val neighborWeight = -strength

    // Fixpontos átváltás (10 bites pontosság)
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

            // Csatornák kinyerése
            val cA = (center ushr 24) and 0xFF
            val cR = (center ushr 16) and 0xFF
            val cG = (center ushr 8) and 0xFF
            val cB = center and 0xFF

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

            // Konvolúció kiszámítása
            var rSharp = (centerWeightInt * cR + neighborWeightInt * (tR + bR + lR + rR)) shr 10
            var gSharp = (centerWeightInt * cG + neighborWeightInt * (tG + bG + lG + rG)) shr 10
            var bSharp = (centerWeightInt * cB + neighborWeightInt * (tB + bB + lB + rB)) shr 10

            // Értéktartományba szorítás (clamping)
            if (rSharp < 0) rSharp = 0 else if (rSharp > 255) rSharp = 255
            if (gSharp < 0) gSharp = 0 else if (gSharp > 255) gSharp = 255
            if (bSharp < 0) bSharp = 0 else if (bSharp > 255) bSharp = 255

            // Új pixel összerakása
            outPixels[idx] = (cA shl 24) or (rSharp shl 16) or (gSharp shl 8) or bSharp
        }
    }

    // A feldolgozott int-tömbből új Bitmap-et építünk: létrehozzuk üresen, majd a
    // setPixels egyben visszaírja az összes pixelt (gyorsabb, mint egyesével).
    val result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    result.setPixels(outPixels, 0, width, 0, 0, width, height)
    return result
}

