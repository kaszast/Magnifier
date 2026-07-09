/*
 * ============================================================================
 *  ExampleInstrumentedTest — minta "instrumented" teszt (valódi eszközön fut)
 * ============================================================================
 *
 *  MI EZ A FÁJL?
 *  Az Android Studio által generált minta. Azt ellenőrzi, hogy az alkalmazás
 *  package-neve tényleg "com.example". Nem a mi saját logikánkat teszteli, csak
 *  azt, hogy a teszt-infrastruktúra egyáltalán "él-e".
 *
 *  MIÉRT VAN KÜLÖN KÖNYVTÁRBAN (src/androidTest/)?
 *  Ez egy INSTRUMENTED teszt. A unit tesztekkel (src/test/) ellentétben ez NEM a
 *  fejlesztői gép JVM-jén fut, hanem egy valódi Android eszközön vagy emulátoron.
 *  A build-rendszer becsomagolja egy tesztelő APK-ba, feltelepíti, és ott
 *  futtatja — így a teljes, ÉLES Android keretrendszer rendelkezésre áll (valódi
 *  Context, valódi erőforrások, valódi rendszerszolgáltatások).
 *
 *  ÁR: sokkal lassabb, mint egy unit teszt (emulátor kell hozzá). Ezért csak arra
 *  használjuk, amit tényleg valódi eszközön kell igazolni; a tiszta logikát
 *  inkább gyors unit teszttel (lásd MagnifierLogicTest.kt) fedjük le.
 *
 *  KULCSFOGALMAK:
 *  - @RunWith(AndroidJUnit4::class) : ez a "test runner" köti össze a JUnit-ot az
 *    Android instrumentation-nal — enélkül a teszt nem tudná, hogy eszközön kell
 *    futnia.
 *  - InstrumentationRegistry.getInstrumentation().targetContext : a TESZTELT
 *    alkalmazás valódi Context-je (nem a tesztelő APK-é).
 * ============================================================================
 */
package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    // A tesztelt alkalmazás valódi Context-jét kérjük le, majd ellenőrizzük, hogy
    // a package-neve az elvárt "com.example". Ha az assert elbukik, alapvető
    // konfigurációs hiba van (pl. rossz applicationId).
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.example", appContext.packageName)
  }
}
