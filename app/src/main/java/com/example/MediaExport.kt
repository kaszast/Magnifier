/*
 * MediaExport.kt
 * ==============
 *
 * Ez a fájl a nagyító-alkalmazás által készített képek (Bitmap) EXPORTÁLÁSÁÉRT felel.
 * Két, egymástól független feladatot lát el, mindkettőt egy-egy top-level (osztályon
 * kívüli, önálló) függvényben:
 *
 *   1) saveBitmapToGallery(...) — a képet TARTÓSAN elmenti a készülék galériájába
 *      (a rendszer médiatárába), így a felhasználó később a Fotók/Galéria appban
 *      megtalálja.
 *
 *   2) shareBitmap(...) — a képet MEGOSZTJA más alkalmazásokkal (pl. e-mail, chat,
 *      közösségi média) az Android rendszer-szintű megosztó felületén keresztül.
 *
 * Android-alap: a `Bitmap` a memóriában élő, dekódolt (pixelekre bontott) kép.
 * Az itteni függvények ezt a memóriabeli képet írják ki JPEG fájlként a megfelelő
 * helyre, és intézik a rendszerrel való kommunikációt.
 *
 * A `Context` egy központi Android-fogalom: ez a "belépési pont" a rendszer
 * szolgáltatásaihoz és erőforrásaihoz (fájlrendszer, médiatár, más appok indítása,
 * stringek olvasása stb.). Szinte minden rendszerhívásnak szüksége van rá, ezért
 * kapja meg mindkét függvény az első paraméterében.
 */
package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

// Helper to save Bitmap to the Device's Public/Scoped Gallery
//
// CÉL: a memóriában lévő `bitmap` képet elmenti a készülék galériájába, a
//      Képek/Nagyító almappába, JPEG formátumban.
//
// PARAMÉTEREK:
//   - context: belépési pont a rendszer médiatárához (ezen keresztül érjük el a
//              ContentResolver-t, lásd lentebb).
//   - bitmap:  a mentendő, memóriában élő kép.
//
// VISSZATÉRÉS: a mentett kép `Uri`-ja (a rendszerbeli "címe"), ha a mentés sikerült;
//              `null`, ha bármi hiba történt (a `?` a `Uri?`-ben jelzi, hogy a
//              visszatérési érték lehet null — ez a Kotlin nullable típusa).
//
// ANDROID-HÁTTÉR — MediaStore és ContentResolver:
//   A `MediaStore` a rendszer központi MÉDIA-ADATBÁZISA: itt tartja nyilván az összes
//   képet, videót, hangot a készüléken. Nem közvetlenül fájlokat írunk tehát, hanem
//   a médiatárnak jelezzük, hogy új képet szeretnénk létrehozni.
//   A `ContentResolver` az a "közvetítő" objektum, amin keresztül ezzel az adatbázissal
//   (és más appok megosztott adataival) kommunikálunk: rekordot szúrunk be (insert),
//   frissítünk (update), törlünk (delete), vagy megnyitunk írásra egy stream-et.
//
// ANDROID-HÁTTÉR — scoped storage (Android 10 / Q felett):
//   Régen az appok szabadon írhattak a közös tárhelyre. Android 10-től (API 29, "Q")
//   bevezették a "scoped storage"-ot: az app alapból csak a SAJÁT területéhez és a
//   médiatáron keresztül a médiafájlokhoz fér hozzá, külön tárhely-engedély nélkül.
//   Ezért a lenti kód a Q feletti verziókon MÁSKÉNT viselkedik (lásd a SDK_INT >= Q
//   ágakat): a médiatárra bízza a fájl fizikai elhelyezését.
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    // Egyedi fájlnév: az aktuális idő ezredmásodpercben (epoch millis) garantálja,
    // hogy két egymás utáni mentés ne ütközzön ugyanazon a néven.
    val filename = "Nagyito_${System.currentTimeMillis()}.jpg"

    // A `ContentValues` egy kulcs-érték párokat tároló "adatlap": itt írjuk le a
    // médiatárnak az új kép METAADATAIT (neve, típusa, hova kerüljön). Az `apply { }`
    // egy Kotlin-idióma: a blokkon belül közvetlenül a most létrehozott objektumon
    // hívjuk a metódusokat (a `put` mind ugyanarra a ContentValues-ra vonatkozik).
    val contentValues = ContentValues().apply {
        // DISPLAY_NAME: a galériában megjelenő fájlnév.
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        // MIME_TYPE: a tartalom típusa; az "image/jpeg" mondja meg a rendszernek,
        // hogy JPEG képről van szó.
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // RELATIVE_PATH (csak Q+): a médiatáron belüli CÉLMAPPA relatív útvonala.
            // Itt a szabványos "Pictures" mappán belül egy "Nagyító" almappát kérünk.
            // Q alatt ez a mechanizmus nem létezik, ott a fájl a médiatár által
            // választott alapértelmezett helyre kerül.
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nagyító")
            // IS_PENDING = 1 (csak Q+): a képet "függő/befejezetlen" állapotúra
            // állítjuk. Amíg IS_PENDING = 1, a kép NEM látszik a galériában és más
            // appok sem férnek hozzá — így nem látszik félig kiírt, sérült kép.
            // A tényleges pixelek kiírása után állítjuk majd vissza 0-ra (lásd lentebb).
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    // A `Context`-ből lekérjük a ContentResolver-t — ez a médiatárral kommunikáló objektum.
    val resolver = context.contentResolver
    // insert(...): bejegyezzük az új kép metaadatait a médiatár képek-táblájába
    // (EXTERNAL_CONTENT_URI = a "külső" tár képei). A rendszer ekkor lefoglal egy
    // helyet a fájlnak, és visszaad egy `Uri`-t. Az `Uri` (Uniform Resource Identifier)
    // egy rendszerbeli "cím", amivel a képre hivatkozni tudunk — NEM közvetlen fájlútvonal,
    // hanem egy content://... alakú azonosító. `null`-t ad vissza, ha a beszúrás nem sikerült.
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (imageUri != null) {
        try {
            // openOutputStream(uri): a lefoglalt helyhez nyitunk egy ÍRÁSI stream-et,
            // amibe a kép bájtjait ki tudjuk küldeni.
            // A `.use { }` a Kotlin megfelelője a Java try-with-resources-nak: a blokk
            // végén (akár normál lefutás, akár kivétel esetén) AUTOMATIKUSAN lezárja a
            // stream-et, így nem szivárog erőforrás. Kézzel nem kell close()-t hívni.
            resolver.openOutputStream(imageUri).use { outputStream ->
                // A stream elvileg lehet null; csak akkor írunk, ha valóban megnyílt.
                if (outputStream != null) {
                    // compress(...): a memóriabeli Bitmap-et JPEG-be tömöríti, és
                    // egyenesen a stream-be írja. A 95 a JPEG MINŐSÉG (0–100): a magas
                    // érték jó képminőséget, de nagyobb fájlt jelent. A JPEG veszteséges
                    // tömörítés, ezért érdemes magas minőséget használni.
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // A pixelek immár kiírva: a képet "készre" jelentjük.
                // clear(): kiürítjük a korábbi metaadatokat a ContentValues-ból, hogy
                // az update csak az alább beállított mezőt frissítse.
                contentValues.clear()
                // IS_PENDING = 0: megszűnik a "függő" állapot, a kép mostantól LÁTHATÓ
                // a galériában és elérhető más appok számára is.
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                // update(...): a médiatárban lévő rekordot frissítjük az új értékkel.
                resolver.update(imageUri, contentValues, null, null)
            }
            // Siker: visszaadjuk a kép Uri-ját (pl. hogy a hívó megjeleníthesse vagy
            // hivatkozhasson rá).
            return imageUri
        } catch (e: Exception) {
            // Bármilyen hiba (pl. lemez megtelt, stream-hiba) esetén logolunk...
            Log.e("Magnifier", "Sikertelen kép mentés", e)
            // ...és TÖRÖLJÜK a már lefoglalt, de hiányosan kiírt rekordot, hogy ne
            // maradjon a médiatárban félkész/sérült bejegyzés.
            resolver.delete(imageUri, null, null)
        }
    }
    // Ide akkor jutunk, ha az insert null-t adott, vagy hiba történt a mentés közben.
    return null
}

// Helper to share Captured Bitmap via standard Android ACTION_SEND Intent and FileProvider URI
//
// CÉL: a `bitmap` képet ideiglenes fájlba menti, majd felkínálja MEGOSZTÁSRA más
//      alkalmazásoknak (e-mail, chat, közösségi média stb.) az Android beépített
//      megosztó felületén keresztül.
//
// PARAMÉTEREK:
//   - context: kell a cache-könyvtár eléréséhez, a FileProvider-hez, és a megosztó
//              felület (chooser) elindításához.
//   - bitmap:  a megosztandó, memóriában élő kép.
//
// VISSZATÉRÉS: nincs (Unit). A függvény "mellékhatásként" megnyitja a rendszer
//              megosztó dialógusát; hiba esetén csak logol, nem dob kivételt a hívónak.
//
// ANDROID-HÁTTÉR — Intent és ACTION_SEND:
//   Az `Intent` egy "szándéknyilatkozat": leírja, MIT szeretnénk (pl. tartalmat
//   megosztani), és a rendszer megkeresi, MELYIK app tudja ezt teljesíteni. Az
//   ACTION_SEND a szabványos "küldés/megosztás" művelet — erre reagál minden olyan
//   app, amely tud tartalmat fogadni (Gmail, Messenger, Drive stb.).
//
// ANDROID-HÁTTÉR — miért kell FileProvider (content:// és nem file://):
//   Biztonsági okból egy app NEM adhat át más appnak nyers `file://` útvonalat a saját
//   privát fájljaira — modern Android (API 24+) ezt a FileUriExposedException kivétellel
//   meg is tiltja. Helyette a `FileProvider` egy IDEIGLENES, engedélyezett `content://`
//   URI-t generál a fájlhoz, amit a fogadó app biztonságosan (és csak korlátozott ideig)
//   olvashat. Így nem szivárog ki az app privát fájlrendszerének szerkezete.
fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        // A megosztandó képet az app SAJÁT cache-könyvtárában (`cacheDir`) helyezzük el,
        // egy "shared_images" almappában. A cache átmeneti tár: a rendszer szükség esetén
        // magától is üríti, ide való az ilyen ideiglenes, megosztásra szánt fájl.
        val cachePath = File(context.cacheDir, "shared_images")
        // mkdirs(): létrehozza a mappát (a hiányzó szülőmappákkal együtt), ha még nincs.
        cachePath.mkdirs()
        
        // Clean old cached shared files to save user disk space
        // Kitakarítjuk a korábbi megosztásokból ottmaradt fájlokat, hogy ne halmozódjanak
        // fel és ne foglaljanak feleslegesen helyet. A `?.` (safe call) miatt akkor sem
        // hasal el, ha a listFiles() null-t adna vissza.
        cachePath.listFiles()?.forEach { it.delete() }
        
        // Egyedi nevű ideiglenes fájl a mostani képnek (időbélyeg a névben — ütközésmentes).
        val file = File(cachePath, "magnifier_share_${System.currentTimeMillis()}.jpg")
        // A képet JPEG-ként kiírjuk a fájlba. A `.use { }` itt is automatikusan lezárja a
        // FileOutputStream-et a blokk végén (lásd a mentés-függvénynél leírt magyarázatot);
        // a 95 ismét a JPEG-minőség (0–100).
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        // A privát fájlból biztonságos, megosztható `content://` URI-t készítünk.
        // A 2. paraméter az "authority": egyedi azonosító, amit a FileProvider a
        // manifestben regisztrál. Itt a csomagnévből képezzük (packageName + ".fileprovider"),
        // és pontosan ennek egyeznie kell az AndroidManifest.xml provider-bejegyzésével.
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (contentUri != null) {
            // Összeállítjuk a megosztási Intent-et (`apply { }` — a blokkon belül közvetlenül
            // a most létrehozott Intent tulajdonságait állítjuk).
            val shareIntent = Intent().apply {
                // A művelet: tartalom küldése/megosztása.
                action = Intent.ACTION_SEND
                // FLAG_GRANT_READ_URI_PERMISSION: IDEIGLENES olvasási jogot ad a fogadó
                // appnak a content:// URI-hoz. Enélkül a másik app nem tudná megnyitni a
                // fájlt (nincs joga hozzá) — ez a FileProvider biztonsági modelljének része.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // EXTRA_STREAM: maga a megosztandó tartalom (a kép content:// URI-ja).
                // ACTION_SEND esetén ebbe a "borítékba" tesszük a csatolmányt.
                putExtra(Intent.EXTRA_STREAM, contentUri)
                // A megosztott adat MIME-típusa; ez alapján szűri a rendszer, mely appok
                // ajánlhatók fel (csak azok, amelyek képet tudnak fogadni).
                type = "image/jpeg"
            }
            // Intent.createChooser(...): a rendszer "Megosztás ezzel..." választó
            // dialógusát nyitja meg, ahol a felhasználó kiválaszthatja a célalkalmazást.
            // A második argumentum a dialógus címe (a strings.xml-ből, lokalizálva).
            // A startActivity indítja el ténylegesen a felületet.
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_chooser_title)))
        }
    } catch (e: Exception) {
        // Bármilyen hiba (fájlírás, FileProvider-konfiguráció, nincs megosztásra alkalmas
        // app stb.) esetén logolunk — a felhasználót nem akasztjuk meg kivétellel.
        Log.e("Magnifier", "Sikertelen kép megosztás", e)
    }
}
