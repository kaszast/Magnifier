/*
 * CameraCapture.kt — a kamera-kezelés segédfüggvényei a nagyító-alkalmazáshoz.
 *
 * Mit tartalmaz ez a fájl:
 *  - Kamera-jogosultság (runtime permission) ellenőrzése: hasCameraPermission.
 *  - Aszinkron kamera-műveletek CameraX-szel: callback- és Future-alapú API-k
 *    átalakítása Kotlin coroutine-ná (suspend függvényekké) — awaitListenableFuture,
 *    awaitCapturedJpeg.
 *  - Natív (teljes) felbontású still capture, azaz fényképkészítés, és a JPEG byte-ok
 *    kiolvasása — CapturedJpeg, awaitCapturedJpeg.
 *  - Az adott eszközhöz illő zoom-lépcsők (a UI preset-gombjai) becslése — getOpticalSteps,
 *    roundZoomStep, finalizeZoomSteps.
 *
 * Android-alapfogalmak, amelyek itt előkerülnek (kezdő Android-fejlesztőnek):
 *  - CameraX: a Google modern, magas szintű kamera-könyvtára, amely a nyers, alacsony
 *    szintű camera2 API fölé épül és leegyszerűsíti annak használatát. A képkészítés
 *    aszinkron: nem blokkoljuk a hívó szálat, hanem az eredményt később, callback vagy
 *    Future formájában kapjuk meg, amikor elkészült.
 *  - coroutine / suspend: a Kotlin nyelvi eszköze, amellyel aszinkron kódot szinkron
 *    (fentről lefelé olvasható) stílusban lehet írni. A callback-/Future-alapú API-kat
 *    itt suspend függvényekbe csomagoljuk, hogy a hívó egyszerűen `val x = awaitXxx(...)`
 *    formában megvárhassa az eredményt, felfüggesztés (suspend) közben a szálat elengedve.
 *  - Context: az Android "belépési pontja" a rendszerszolgáltatásokhoz (jogosultságok,
 *    kamera-service, executorok). Szinte minden Android-API igényli.
 */
package com.example

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Megvizsgálja, hogy a felhasználó megadta-e az alkalmazásnak a CAMERA jogosultságot.
//
// Android runtime permission modell: az Android 6.0 (API 23) óta a "veszélyes"
// (dangerous) jogosultságokat — amilyen a kamerához való hozzáférés is — NEM elég az
// AndroidManifest.xml-ben deklarálni. A felhasználónak FUTÁSIDŐBEN, egy felugró
// rendszer-párbeszédablakban kell rábólintania, és utóbb bármikor vissza is vonhatja a
// beállításokban. Ezért nem lehet a jogosultságot fordítási időben "beépíteni": minden
// kamerát használó művelet előtt le kell kérdezni az aktuális állapotot.
//
// checkSelfPermission: visszaadja, hogy az adott jogosultság épp PERMISSION_GRANTED
// (megadva) vagy PERMISSION_DENIED (megtagadva) állapotú. A ContextCompat ennek az
// AndroidX/support-változata, amely régebbi Android-verziókon is helyesen viselkedik
// (backward compatibility) — API 23 alatt pl. mindig GRANTED-et ad, mert ott még a
// telepítéskori engedélymodell élt.
fun hasCameraPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

// Egy ListenableFuture-t (később elkészülő, aszinkron eredményt) alakít át egy
// coroutine-ban közvetlenül várható suspend függvénnyé.
//
// Mi az a Future? Olyan objektum, amely egy még folyamatban lévő számítás JÖVŐBELI
// eredményét képviseli — most kapod meg a "jegyet", az érték pedig majd később lesz kész.
// A ListenableFuture (a Google Guava könyvtárából; a CameraX ezt használja) annyival
// több egy sima Future-nél, hogy egy listener-t (figyelőt) lehet rá aggatni, amelyet
// automatikusan meghív, amint az eredmény elkészült — így nem kell aktívan pollingolni
// (ismételten kérdezgetni) vagy blokkolva várni egy szálon.
//
// suspendCoroutine: felfüggeszti (suspend) az aktuális coroutine-t, és ad egy
// `continuation` objektumot, ami a "hogyan folytatódjon innen" kézzelfogható formája.
// A coroutine addig "áll" (a szálat közben elengedi), amíg meg nem hívjuk a continuation
// valamelyik befejező metódusát:
//   - continuation.resume(érték)          -> folytatás az eredménnyel; ezt adja vissza a suspend fv.,
//   - continuation.resumeWithException(e) -> folytatás hibával; a kivétel a hívónál dobódik.
//
// A logika: felaggatunk egy listener-t a Future-re; amikor a Future kész, kiolvassuk az
// eredményt (future.get(), ami itt már nem blokkol, mert biztosan kész) és resume-mal
// visszaadjuk. Ha közben hiba történt, azt a resumeWithException viszi tovább a hívóhoz.
//
// Main executor (getMainExecutor): az addListener második paramétere adja meg, MELYIK
// szálon fusson a listener. A "main executor" a fő (UI) szálat jelöli ki. Ez azért
// fontos, mert a CameraX callback-jei és a UI-frissítések jellemzően a main threaden
// futtathatók/várhatók biztonságosan.
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

// Egy elkészült fénykép nyers adatait fogja össze: a kódolt JPEG byte-ok (bytes) és a
// helyes tájoláshoz szükséges elforgatás foka (rotationDegrees, lásd lentebb). Egyszerű
// adat-hordozó osztály — csak azért van, hogy a takePicture eredményét egyben, egyetlen
// visszatérési értékként adhassuk vissza a hívónak.
class CapturedJpeg(val bytes: ByteArray, val rotationDegrees: Int)

// Fényképet készít a kamerával natív (teljes) felbontáson, és a JPEG byte-okat adja
// vissza. A CameraX ImageCapture.takePicture API-ja callback-alapú (nem ad vissza
// azonnal eredményt), ezt itt suspendCoroutine-nal alakítjuk várható suspend függvénnyé
// — ugyanaz az elv, mint az awaitListenableFuture-nél, csak most egy callback-objektumot
// adunk át Future helyett.
//
// A takePicture két dolgot kap: egy executort (main thread, lásd fentebb) és egy
// OnImageCapturedCallback-et, amelynek két kimenete van:
//   - onCaptureSuccess(image): sikeres kép -> az eredményt resume-mal visszaadjuk,
//   - onError(exception):      hiba        -> resume(null), azaz null-lal térünk vissza.
//
// ImageProxy: a CameraX ebbe csomagolja az elkészült képet. Fontos: az ImageProxy egy
// korlátozott számban rendelkezésre álló, natív képpuffert (image buffer) tart életben,
// ezért a feldolgozás UTÁN KÖTELEZŐ lezárni (image.close()). Ha nem tesszük, a puffer
// nem szabadul fel, és néhány kép után a kamera "beragad" (a capture-pipeline elfogy a
// szabad bufferekből, és nem tud több képet adni). Ezért van a close() a finally ágban:
// akkor is lefut, ha közben kivétel keletkezett a byte-ok olvasása során.
//
// A JPEG kiolvasása: a JPEG egysíkú formátum, ezért a kép egyetlen plane-ből áll
// (planes[0]). A plane bufferéből (egy java.nio.ByteBuffer) kiolvassuk a hátralévő
// byte-okat egy ByteArray-be: remaining() = hány byte van még a bufferben, majd
// buffer.get(bytes) = ezek átmásolása a saját tömbünkbe.
//
// imageInfo.rotationDegrees: hány fokkal kell elforgatni a képet, hogy "felfelé" álljon.
// A szenzor fizikai beépítési tájolása és az eszköz aktuális orientációja miatt a nyers
// kép gyakran el van forgatva (pl. 90°). Ezt az értéket a hívó használja fel a kép
// helyes megjelenítéséhez/mentéséhez — a JPEG byte-okat magukat nem forgatjuk itt át.
//
// Natív felbontású still capture JPEG byte-okként; hibánál null — a hívó a
// preview-snapshotra esik vissza, a felhasználó felé nincs külön hibaút.
suspend fun awaitCapturedJpeg(imageCapture: ImageCapture, context: Context): CapturedJpeg? =
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

// Megbecsüli, milyen zoom-lépcsőket (a UI preset-gombjait) érdemes felkínálni az adott
// eszközön, a [minZoom, maxZoom] tartományon belül. A cél, hogy a gombok az eszköz
// tényleges (jellemzően optikai) kamera-váltásaihoz igazodjanak, ne csak önkényes számok
// legyenek.
//
// Miért kell eszközfüggő heurisztika? A telefonokban több hátsó kamera van (ultra-wide,
// fő, tele...), különböző fókusztávolsággal, és nincs egységes, megbízható Android-API,
// ami "szép" zoom-lépcsőket adna. Ezért itt több stratégiát próbálunk végig sorban, és az
// első alkalmasnál visszatérünk:
//   1. Emulátor esetén fix, biztonságos lépcsők (a camera2 lekérdezés emulátoron
//      megbízhatatlan, akár lefagyást is okozhat).
//   2. Ismert eszköz (Xiaomi 15 Ultra) esetén kézzel bemért, pontos lépcsők.
//   3. Egyébként best-effort becslés a camera2 CameraCharacteristics-ből.
// A gyűjtésre Set-et használunk, hogy a duplikált értékek automatikusan kiessenek; a
// végén rendezve adjuk vissza. Fontos: ez az egész best-effort közelítés — nem garantált,
// hogy minden eszközön tökéletes lesz, csak "jó eséllyel értelmes" lépcsőket ad.
fun getOpticalSteps(context: Context, minZoom: Float, maxZoom: Float): List<Float> {
    val steps = mutableSetOf<Float>()
    
    // A minZoom (a legkisebb elérhető zoom, jellemzően az ultra-wide kamera) és az 1.0x
    // (a fő kamera "alapértelmezett" nézete) mindig legyen a lépcsők között, ha egyáltalán
    // beleesik az elérhető tartományba. A `1.0f in minZoom..maxZoom` egy Kotlin
    // range-ellenőrzés: igaz, ha minZoom <= 1.0f <= maxZoom.
    // Always include minZoom and 1.0f (if within range)
    steps.add(minZoom)
    if (1.0f in minZoom..maxZoom) {
        steps.add(1.0f)
    }
    
    // 1. lépés — emulátor-detektálás. Az Android-emulátor egy szoftveres/virtuális
    // kamerát ad, amelyen a camera2 jellemző-lekérdezés (CameraCharacteristics) hibás
    // értékeket adhat, vagy akár be is fagyaszthatja a camera2 service-t. Ezt elkerülendő
    // emulátoron egyszerű, fix lépcsőket (2x, 4x, 8x) adunk vissza. A felismerés több
    // Build-mező tapasztalati vizsgálatával történik (ezek a stringek valódi eszközökön
    // mások):
    //   - Build.FINGERPRINT: a build egyedi "ujjlenyomata"; emulátoron generic/unknown/sdk_gphone kezdetű,
    //   - Build.MODEL / MANUFACTURER / BRAND / DEVICE / PRODUCT: emulátoron árulkodó nevek,
    //   - Build.HARDWARE: "ranchu" és "goldfish" az Android-emulátorok belső hardver-nevei.
    // A `||` láncban bármelyik feltétel elég az emulátor gyanújához (rövidzár kiértékelés).
    // 1. Check for emulator to avoid any camera2 service locks/crashes
    val isEmulator = Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.FINGERPRINT.contains("sdk_gphone")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("sdk_gphone")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HARDWARE == "ranchu"
            || Build.HARDWARE == "goldfish"
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk" == Build.PRODUCT

    if (isEmulator) {
        listOf(2.0f, 4.0f, 8.0f).forEach {
            if (it in minZoom..maxZoom) steps.add(it)
        }
        return steps.toList().sorted()
    }

    // 2. lépés — ismert eszköz kézi "táblázata". A Xiaomi 15 Ultra kameráit bemérték,
    // ezért erre az eszközre a valós optikai/hibrid zoom-váltásokhoz igazított, fix
    // lépcsőket adunk (0.6x ultra-wide, 1x fő, 2x/4.3x tele, majd hibrid/digitális
    // 10x, 30x, 120x). A felismerés kisbetűsített modell-név és a belső kódnév alapján
    // történik (a "25010pn30" a Xiaomi 15 Ultra egyik gyári kódneve).
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

    // 3. lépés — általános, best-effort becslés a camera2 CameraCharacteristics-ből.
    // A CameraManager egy rendszerszolgáltatás (system service), amelytől lekérhető az
    // eszköz kameráinak listája (cameraIdList) és minden kamera "jellemzői"
    // (CameraCharacteristics: fókusztávolság, szenzorméret, iránytartás, stb.).
    // Minden kamera-id-t végignézünk, és csak a HÁTSÓ (LENS_FACING_BACK) kamerákkal
    // foglalkozunk. A beágyazott try/catch-ek védőháló: egyes gyártók bizonyos
    // lekérdezésekre kivételt dobhatnak — ilyenkor csak logolunk és lépünk a következő
    // kamerára, nem dől el az egész funkció.
    // 3. Fallback to generic safe CameraCharacteristics query for other actual devices
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cameraManager.cameraIdList) {
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // A zoom-faktort a 35 mm-es film ekvivalens fókusztávolság (35mm
                    // equivalent focal length) alapján becsüljük — ezzel a mérőszámmal
                    // fejezi ki a fényképészet a látószöget/nagyítást, eszköztől függetlenül.
                    // Ehhez két fizikai adat kell a kameráról:
                    //   LENS_INFO_AVAILABLE_FOCAL_LENGTHS: a kamera fizikai fókusztávolsága(i)
                    //     milliméterben (egy objektívnél több is lehet).
                    //   SENSOR_INFO_PHYSICAL_SIZE: a képérzékelő fizikai mérete (szélesség x
                    //     magasság) milliméterben.
                    // A get(...) null-t adhat, ha az eszköz nem közli az adott jellemzőt,
                    // ezért lentebb null-ellenőrzés következik.
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    
                    if (focalLengths != null && sensorSize != null) {
                        // A szenzor átlója (diagonal) Pitagorasz-tétellel: sqrt(w^2 + h^2).
                        // Erre a crop factor kiszámításához lesz szükség: a crop factor azt
                        // fejezi ki, mennyivel "vág be" ez a kis szenzor a 35mm-es kerethez
                        // képest — a 35mm-es keret átlójának és a szenzor átlójának hányadosa.
                        val diagonal = Math.sqrt((sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()).toFloat()
                        if (diagonal > 0f) {
                            for (f in focalLengths) {
                                // 43.27 (mm): a 35 mm-es ("full frame") filmkocka átlója —
                                //   tapasztalati/szabvány érték. A (43.27 / szenzorátló) épp a
                                //   crop factor; ezzel szorozva a fizikai fókusztávolságot (f)
                                //   megkapjuk a 35mm-ekvivalens fókusztávolságot.
                                val eqFocal = f * (43.27f / diagonal)
                                // 26.0 (mm): a tipikus telefonos "fő kamera" 35mm-ekvivalens
                                //   fókusztávolsága, amit ~1.0x zoomnak veszünk — szintén
                                //   tapasztalati érték. Ehhez viszonyítva (osztva) adódik a
                                //   nyers zoom-faktor: pl. egy 52mm-ekvivalens tele-kamera ~2.0x.
                                val rawZoom = eqFocal / 26.0f
                                
                                // A nyers faktort bevett preset-lépcsőre kerekítjük (lásd
                                // roundZoomStep), és csak akkor vesszük fel a jelöltek közé,
                                // ha a valós [minZoom, maxZoom] tartományba esik.
                                val rounded = roundZoomStep(rawZoom, minZoom)
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
    
    return finalizeZoomSteps(steps, minZoom, maxZoom)
}

// A nyers, 35mm-ekvivalensből becsült zoom-faktort a fényképezőgépeknél/telefonoknál
// bevett, "szép" lépcsőre kerekíti (0.5x, 0.6x, 1x, 2x, 3x, 4.3x, 5x ...). Nyers 0.83x
// helyett tehát pl. 1.0x-et kapunk vissza, hogy a preset-gombok kerek értékeket mutassanak.
// A when-ágak sávokra bontják a nyers értéket (a felső határig terjedő tartományra):
//   - < 0.8x : ultra-wide sáv; ha közel esik a minZoom-hoz (< 0.15 különbség), azt adjuk,
//              különben 0.5x vagy 0.6x,
//   - < 1.3x : 1.0x (fő kamera),
//   - < 2.5x : 2.0x, majd 3.0x / 4.3x / 5.0x a tipikus tele-lépcsőkre,
//   - egyébként: fél-egész értékre kerekítés (roundToInt fél-lépésekkel: *2, kerekít, /2).
// A küszöbök (0.8, 1.3, 2.5, 3.5, 4.8, 6.0) és a cél-lépcsők tapasztalati (empirikus)
// értékek, nem elméleti konstansok — a való eszközök szokásos zoom-fokozataihoz igazítva.
// Nyers, 35 mm-ekvivalensből számolt zoom-érték kerekítése bevett preset-lépcsőre
fun roundZoomStep(rawZoom: Float, minZoom: Float): Float = when {
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

// Összeállítja a végleges preset-listát a 3. stratégia (CameraCharacteristics) jelöltjeiből.
// Lépések:
//   1. Biztosítja, hogy a minZoom és az 1.0x mindig benne legyen (ha a tartományba esik).
//   2. Ha túl kevés a jelölt (<= 2, azaz gyakorlatilag semmi hasznos becslés nem született),
//      feltölti egy alapértelmezett 2/4/8/16/32/64x sorral.
//   3. Nagy zoom-tartománynál (maxZoom >= 30x) beszúr néhány "mérföldkő" lépcsőt, hogy a
//      nagy nagyításoknál is legyen mit választani; >= 100x-nál még 50x/100x és maga a
//      maxZoom is bekerül.
//   4. Rendezés után, ha 7-nél több lépcső jönne ki, RITKÍTJA a listát: mindig megtartja a
//      legkisebbet, a legnagyobbat és az 1.0x-et, a maradékból pedig közel egyenletesen
//      mintavételez (lásd stepSize lentebb), amíg el nem éri a 7-et. A 7-es korlát a UI
//      zsúfoltságát kerüli el (ne legyen túl sok gomb).
// A jelölt lépcsők kiegészítése és szűkítése: default sor kevés jelöltnél, mérföldkövek nagy
// zoom-tartománynál, rendezés és legfeljebb 7 preset a UI zsúfoltságának elkerülésére.
fun finalizeZoomSteps(candidates: Set<Float>, minZoom: Float, maxZoom: Float): List<Float> {
    val steps = candidates.toMutableSet()
    steps.add(minZoom)
    if (1.0f in minZoom..maxZoom) {
        steps.add(1.0f)
    }

    // 2. lépés: ha csak a minZoom (és esetleg az 1.0x) van meg, alapértelmezett lépcsők.
    // Add default fallbacks if list is too small
    if (steps.size <= 2) {
        listOf(2.0f, 4.0f, 8.0f, 16.0f, 32.0f, 64.0f).forEach {
            if (it in minZoom..maxZoom) steps.add(it)
        }
    }

    // 3. lépés: nagy zoom-tartománynál "mérföldkő" lépcsők (digitális/hibrid zoom).
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

    // 4. lépés: rendezés, majd 7 fölött ritkítás (lásd a függvény fejlécében részletesen).
    // Sort and return as a nice list, max 7 presets to prevent cluttering the UI
    val sorted = steps.toList().sorted()
    return if (sorted.size > 7) {
        val result = mutableSetOf<Float>()
        result.add(sorted.first())
        result.add(sorted.last())
        if (1.0f in sorted) result.add(1.0f)

        val remaining = sorted.filter { it != sorted.first() && it != sorted.last() && it != 1.0f }
        // stepSize: milyen "lépésközzel" mintavételezzük a maradék (remaining) lépcsőket,
        // hogy közel egyenletesen legyenek elosztva, és pont kitöltsék a hátralévő helyeket
        // 7-ig. A max(1, ...) véd a 0-val való osztás és a végtelen ciklus ellen (ha kevés a
        // maradék elem). A while-ciklus a maradékból minden stepSize-adik elemet vesz be.
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

