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

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
fun applyFocusSettings(
    camera: androidx.camera.core.Camera,
    focusMode: String,
    focusDistance: Float
) {
    val cameraControl = camera.cameraControl
    val camera2CameraControl = androidx.camera.camera2.interop.Camera2CameraControl.from(cameraControl)
    when (focusMode) {
        "auto" -> {
            camera2CameraControl.clearCaptureRequestOptions()
        }
        "locked" -> {
            camera2CameraControl.captureRequestOptions = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_AUTO)
                .build()
        }
        "manual" -> {
            camera2CameraControl.captureRequestOptions = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                .build()
        }
    }
}



