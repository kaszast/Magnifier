/*
 * Theme.kt — a téma "összeszerelő" rétege.
 *
 * Mi ez a fájl és mi a szerepe a Material 3 témázásban?
 * -----------------------------------------------------
 * Ez a fájl köti össze a másik kettőt: fogja a Color.kt színeit és a Type.kt
 * tipográfiáját, és egyetlen, az egész appot körülölelő téma-wrapper composable
 * függvénybe (`MyApplicationTheme`) csomagolja őket. Ezt a függvényt jellemzően a
 * legfelső szinten hívjuk (pl. a MainActivity `setContent { ... }` blokkjában), és
 * a teljes UI-t beletesszük — így minden lentebbi komponens ebből a témából örökli
 * a színeit és betűstílusait.
 *
 * Kulcsfogalmak:
 *   - `MaterialTheme`  : a Compose beépített composable-je, amely a témát (színek,
 *                        tipográfia, formák) "lefelé" elérhetővé teszi a benne lévő
 *                        összes komponens számára. Egy komponens így nem konkrét
 *                        színt kér, hanem pl. `MaterialTheme.colorScheme.primary`-t.
 *   - `ColorScheme`    : a Material 3 SZEREP-alapú színkészlete (primary, secondary,
 *                        background, surface, onPrimary, error ...). Nem "ez a kék",
 *                        hanem "ez a `primary` szerep színe". Kétféleképp gyártható:
 *                        `lightColorScheme(...)` és `darkColorScheme(...)` — mindkettő
 *                        értelmes alapértékeket ad, csak a felülírandókat kell megadni.
 *   - dynamic color / Material You: Android 12+ (API 31, "S") esetén a rendszer a
 *                        felhasználó háttérképéből generál egy egyedi palettát; ezt a
 *                        `dynamicLightColorScheme` / `dynamicDarkColorScheme` adja.
 */
package com.example.ui.theme

// `Build`: a futó Android-verzió lekérdezéséhez (SDK_INT), a dynamic color
// verzióellenőrzéséhez kell.
import android.os.Build
// `isSystemInDarkTheme()`: composable függvény, ami megmondja, hogy a rendszer épp
// sötét módban van-e.
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
// `@Composable` annotáció importja — ezzel jelöljük a UI-t leíró függvényeket.
import androidx.compose.runtime.Composable
// `LocalContext`: a Compose-on belül elérhető Android `Context` (a dynamic color
// pl. ezen keresztül fér hozzá a rendszer színeihez).
import androidx.compose.ui.platform.LocalContext

// A DARK (sötét) témához tartozó színkészlet. `private`, mert csak ezen a fájlon
// belül használjuk. A `darkColorScheme(...)` sötét háttérhez hangolt alapértékeket
// ad; itt a három fő szerephez a Color.kt VILÁGOS ("80"-as) árnyalatait rendeljük,
// mert azok olvashatók sötét háttéren.
private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

// A LIGHT (világos) témához tartozó színkészlet. A `lightColorScheme(...)` világos
// háttérhez hangolt alapértékeket ad; ide a Color.kt SÖTÉTEBB ("40"-es) árnyalatai
// kerülnek. Az alul kommentben hagyott sorok mutatják, mely további szerepeket
// lehetne még felülbírálni (background, surface, onPrimary ...).
private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

/*
 * A téma-wrapper composable függvény.
 * -----------------------------------
 * A `@Composable` annotáció jelzi, hogy ez a függvény a Compose UI-fába illeszkedik
 * (nem hagyományos függvény: a Compose futásidőben kezeli, és pl. re-composition-nel
 * automatikusan újrafuttatja, ha a bemenetei változnak).
 *
 * Paraméterek (mind alapértékkel, így a leggyakoribb esetben paraméter nélkül hívható):
 *   - `darkTheme`   : sötét témát használjunk-e. Alapértéke `isSystemInDarkTheme()`,
 *                     tehát alapból a rendszer beállítását követi — de a hívó felül is
 *                     bírálhatja (pl. az app saját téma-kapcsolójával).
 *   - `dynamicColor`: engedélyezzük-e a Material You dinamikus színeket. Csak Android
 *                     12+ (API 31 / "S") esetén van hatása; régebbi rendszeren a kód
 *                     amúgy is a saját palettára esik vissza.
 *   - `content`     : a `@Composable () -> Unit` egy composable lambda — vagyis maga a
 *                     "becsomagolandó" UI. Ezt a `MaterialTheme` a saját belsejében
 *                     hívja meg, így minden benne rajzolt komponens EBBEN a témában
 *                     fut, és onnan örökli a színeit/betűit. Így "kapja meg" a content
 *                     a témát: nem paraméterként adjuk át neki, hanem a `MaterialTheme`
 *                     által megnyitott hatókörön (scope) belül jelenik meg.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  // (A dynamic color / Material You csak Android 12-től érhető el.)
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  // A tényleges színkészlet kiválasztása három tényező alapján: kértünk-e dynamic
  // color-t, elég új-e az Android, és sötét vagy világos mód aktív-e. A `when`
  // ág-kiértékelése fentről lefelé történik, az első illeszkedő ág nyer.
  val colorScheme =
    when {
      // 1. eset: dynamic color kérve ÉS a készülék Android 12+ (a `Build.VERSION_CODES.S`
      //    = API 31). Ekkor a rendszer a háttérképből generált palettát adjuk.
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current // a dinamikus palettához kell egy Context
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      // 2. eset: nincs dynamic color (vagy régi Android) + sötét mód -> saját dark paletta.
      darkTheme -> DarkColorScheme
      // 3. eset: minden más -> saját light paletta.
      else -> LightColorScheme
    }

  // A `MaterialTheme` a kiválasztott színkészletet és a Type.kt tipográfiáját teszi
  // elérhetővé a `content` számára. A `content`-et a Material 3 alapértelmezett
  // formáival (shapes) együtt "lefelé" adja tovább; innentől bármely lentebbi
  // composable a `MaterialTheme.colorScheme` / `MaterialTheme.typography` értékeit látja.
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
