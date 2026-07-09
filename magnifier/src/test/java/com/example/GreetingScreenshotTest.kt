/*
 * ============================================================================
 *  GreetingScreenshotTest — vizuális regressziós (screenshot) teszt Roborazzi-val
 * ============================================================================
 *
 *  MI AZ A SCREENSHOT- / VIZUÁLIS REGRESSZIÓS TESZT?
 *  A hagyományos teszt értékeket hasonlít össze (pl. "2+2 == 4"). Egy UI-nál
 *  viszont az a kérdés: "ugyanúgy NÉZ-e ki, mint korábban?". A screenshot-teszt
 *  lefényképezi a megjelenített felületet egy PNG-be, és összeveti egy korábban
 *  jóváhagyott "arany" (golden) képpel. Ha eltér (pl. valaki véletlenül elrontja a
 *  színt vagy az elrendezést), a teszt elbukik. Ezt hívják vizuális regressziónak.
 *
 *  ROBORAZZI
 *  A Roborazzi az a könyvtár, amely Robolectric felett képes screenshotot
 *  készíteni a JVM-en — emulátor nélkül. A captureRoboImage(...) hívás rendereli és
 *  fájlba menti a képet.
 *  - Első futáskor legenerálja az "arany" képet (record mód).
 *  - Később ehhez hasonlít (verify mód); eltérésnél diff-képet is ad.
 *
 *  KULCS-ANNOTÁCIÓK:
 *  - @RunWith(RobolectricTestRunner::class) : szimulált Android a JVM-en.
 *  - @GraphicsMode(GraphicsMode.Mode.NATIVE) : bekapcsolja a VALÓDI grafikus
 *    renderelést (natív rajzolás). Enélkül a Robolectric nem festene ki tényleges
 *    pixeleket, így nem lenne mit lefényképezni.
 *  - @Config(qualifiers = ...Pixel8, sdk = [36]) : a szimulált eszköz paraméterei
 *    (Pixel 8 kijelző-méret/sűrűség, SDK 36). Ez teszi determinisztikussá a
 *    képet — más eszköz-profil más felbontású screenshotot adna.
 *
 *  createComposeRule() : JUnit @Rule, amely felállít egy Compose tesztkörnyezetet.
 *    A setContent { ... } blokkban adjuk meg a renderelendő UI-t.
 * ============================================================================
 */
package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    // A tényleges alkalmazás-témába (MyApplicationTheme) ágyazva kirajzolunk egy
    // egyszerű "Nagyító" szöveget, majd a teljes gyökér-node-ról (onRoot) képet
    // készítünk. A kép a megadott útvonalra kerül; a Roborazzi ehhez hasonlítja a
    // későbbi futásokat.
    composeTestRule.setContent {
      MyApplicationTheme {
        androidx.compose.material3.Text("Nagyító")
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
