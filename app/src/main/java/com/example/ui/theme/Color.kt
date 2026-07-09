/*
 * Color.kt — a téma nyers színpalettája (color palette).
 *
 * Mi ez a fájl és mi a szerepe a Material 3 témázásban?
 * -----------------------------------------------------
 * Ez a fájl NEM dönti el, hogy melyik szín hol jelenik meg a felületen. Csak
 * "névvel ellátott festékesdobozokat" definiál: néhány konkrét színt, amelyekre
 * később névvel hivatkozhatunk. A tényleges hozzárendelés (pl. "ez a szín legyen
 * a `primary` szín") a Theme.kt-ben történik, ahol ezekből a színekből épül fel a
 * `ColorScheme`. A Material 3 témázás így három rétegre bomlik:
 *   1. Color.kt  -> a nyers színek (ez a fájl).
 *   2. Type.kt   -> a betűstílusok (Typography).
 *   3. Theme.kt  -> ezeket összefogja egyetlen `MaterialTheme`-be.
 *
 * A "80" és "40" végződés a Material Design tonális palettájának (tonal palette)
 * világosságát jelöli 0 (fekete) és 100 (fehér) között. A ~80-as, világos árnyalatok
 * dark módban jól olvashatók sötét háttéren; a ~40-es, sötétebb árnyalatok light
 * módban működnek jól világos háttéren. Ezért van minden színből egy világos és egy
 * sötét változat.
 */
package com.example.ui.theme

// A Compose saját `Color` típusa (nem az android.graphics.Color!). Ez egy egyszerű,
// érték-szemantikájú (value class) színreprezentáció, amit a Compose UI használ.
import androidx.compose.ui.graphics.Color

/*
 * A `Color(0xFF...)` formátum megértése — ARGB hexadecimális szám.
 * ----------------------------------------------------------------
 * A `0x` előtag hexadecimális (16-os számrendszerű) literált jelöl Kotlinban.
 * A 8 hexa jegy négy darab, egyenként 2 jegyű (0x00..0xFF = 0..255) csatornára bomlik,
 * ebben a sorrendben:  A R G B
 *
 *   0x  FF   D0   BC   FF
 *       └�top┘ │    │    └── B (Blue,  kék)      = 0xFF = 255
 *       (A)   │    └─────── G (Green, zöld)     = 0xBC = 188
 *   Alpha ────┘  R (Red, piros)                 = 0xD0 = 208
 *
 *   A = Alpha (átlátszatlanság/opacity): 0xFF = teljesen átlátszatlan,
 *       0x00 = teljesen átlátszó. Színeknél szinte mindig 0xFF a helyes,
 *       ezért kezdődik itt minden érték `0xFF`-fel.
 *
 * Miért `val` (és nem `var`)?
 *   A `val` konstans, egyszer felvett, később nem módosítható hivatkozás
 *   (immutable). Egy színpaletta soha nem változik futás közben, ezért `val`.
 *   Ezek top-level (osztályon kívüli) deklarációk, így az egész modulból
 *   elérhetők pusztán a nevükkel (import után), példányosítás nélkül.
 *
 * A nevek nagybetűvel kezdődnek (Purple80), mert a Compose konvenció szerint a
 * fordítási idejű, konstans-jellegű színeket PascalCase-szel írjuk.
 */

// Világos (light-on-dark) árnyalatok — jellemzően DARK témában használatosak.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// Sötétebb árnyalatok — jellemzően LIGHT témában használatosak.
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
