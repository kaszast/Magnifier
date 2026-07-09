/*
 * ============================================================================
 *  ExampleRobolectricTest — Android-osztályok tesztje a JVM-en, Robolectric-kel
 * ============================================================================
 *
 *  MI EZ A FÁJL?
 *  Azt ellenőrzi, hogy az app_name string-erőforrás értéke "Nagyító". Ehhez
 *  valódi Android Context és erőforrás-feloldás (resource resolution) kell — ami
 *  sima unit tesztben (src/test) nem elérhető.
 *
 *  A DILEMMA:
 *  - A tiszta unit teszt gyors, de nincs Android keretrendszere.
 *  - Az instrumented teszt valódi, de lassú (emulátor kell).
 *  Sok esetben viszont csak egy-két Android-osztályra van szükségünk (Context,
 *  Bitmap, Rect, string-erőforrások). Emulátort indítani ezért túlzás lenne.
 *
 *  A MEGOLDÁS: ROBOLECTRIC
 *  A Robolectric egy könyvtár, amely a JVM-en, emulátor NÉLKÜL szimulálja az
 *  Android keretrendszert. Így a teszt gyors marad (unit tesztként fut a
 *  src/test-ben), mégis hívhatunk valódinak látszó Android API-kat.
 *
 *  KULCS-ANNOTÁCIÓK:
 *  - @RunWith(RobolectricTestRunner::class) : ez a runner tölti be a szimulált
 *    Android környezetet a teszt köré. Enélkül az android.* hívások elszállnának.
 *  - @Config(sdk = [36]) : melyik Android API-szintet (itt SDK 36) szimulálja a
 *    Robolectric. Ettől determinisztikus, hogy melyik platform-viselkedést kapjuk.
 *
 *  ApplicationProvider.getApplicationContext<Context>() :
 *    A teszt-környezet Application-szintű Context-je. Ezen keresztül érjük el a
 *    getString(...) hívást és a többi erőforrást — pont úgy, mint éles appban.
 * ============================================================================
 */
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    // A szimulált Context-től lekérjük az app_name erőforrást, és igazoljuk, hogy
    // a lokalizált érték "Nagyító". Ez egyben azt is bizonyítja, hogy a Robolectric
    // helyesen oldja fel az R.string.* erőforrásokat a JVM-en.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nagyító", appName)
  }
}
