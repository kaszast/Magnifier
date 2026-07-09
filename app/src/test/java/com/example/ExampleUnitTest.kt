/*
 * ============================================================================
 *  ExampleUnitTest — a legegyszerűbb "local unit test" (JVM-teszt)
 * ============================================================================
 *
 *  MI EZ A FÁJL?
 *  Az Android Studio által automatikusan generált minta-teszt. Semmi
 *  Android-specifikusat nem ellenőriz, csak azt demonstrálja, hogyan néz ki egy
 *  működő teszt. Jó kiindulópont a fogalmak megértéséhez.
 *
 *  UNIT TESZT vs. INSTRUMENTED TESZT — a két teszt-fajta Androidon:
 *
 *  1) UNIT TESZT  (src/test/ könyvtár — ez a fájl is ide tartozik)
 *     - A fejlesztő gépének JVM-jén (Java Virtual Machine) fut, NEM telefonon.
 *     - Nagyon gyors: nincs emulátor-indítás, nincs APK-telepítés.
 *     - Cserébe alapból NEM éri el a valódi Android keretrendszert (framework);
 *       az android.* osztályok itt csak üres "stub"-ok — hívásuk kivételt dob,
 *       hacsak nem használunk Robolectric-et (lásd ExampleRobolectricTest.kt).
 *     - Tiszta logika (matek, algoritmusok) tesztelésére ideális.
 *
 *  2) INSTRUMENTED TESZT  (src/androidTest/ könyvtár)
 *     - Valódi eszközön VAGY emulátoron fut, teljes Android keretrendszerrel.
 *     - Lassabb, de "igazi" környezet. Lásd: ExampleInstrumentedTest.kt.
 *
 *  JUNIT ALAPFOGALMAK (a tesztkeretrendszer, amit itt használunk):
 *  - @Test         : megjelöli, hogy az adott metódus egy futtatandó teszt-eset.
 *  - assertEquals(várt, kapott) : elbukik a teszt, ha a két érték nem egyenlő.
 *    (Fontos a sorrend: az ELSŐ paraméter a VÁRT, a második a TÉNYLEGES érték.)
 *  - Az `import org.junit.Assert.*` a csillaggal az összes assert-függvényt
 *    behozza (assertTrue, assertNull, assertNotNull, ...).
 * ============================================================================
 */
package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    // A legegyszerűbb lehetséges állítás: 2 + 2 valóban 4-e. Ha valaki elrontaná
    // a JUnit/Gradle beállítást, már ez a triviális teszt is jelezné a hibát.
    assertEquals(4, 2 + 2)
  }
}
