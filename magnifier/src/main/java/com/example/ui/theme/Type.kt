/*
 * Type.kt — a téma tipográfiája (szövegstílusok).
 *
 * Mi ez a fájl és mi a szerepe a Material 3 témázásban?
 * -----------------------------------------------------
 * A Color.kt a színeket, ez a fájl pedig a SZÖVEG megjelenését definiálja: milyen
 * betűtípussal, mekkora mérettel, milyen vastagsággal jelenjenek meg a különböző
 * szövegfajták (törzsszöveg, cím, felirat stb.). A Theme.kt ezt az itt definiált
 * `Typography` objektumot adja át a `MaterialTheme`-nek, így az egész alkalmazásban
 * egységes, központilag szabályozott lesz a betűkép.
 *
 * Fogalmak:
 *   - `Typography`  : egy "stíluskészlet" — a Material 3 elnevezett szövegszerepeit
 *                     (bodyLarge, titleLarge, labelSmall, headlineMedium, ...) fogja
 *                     össze egyetlen objektumba. Egy komponens (pl. Text) ezekre
 *                     szerep szerint hivatkozik, nem konkrét pixelméretre.
 *   - `TextStyle`   : EGY konkrét szövegszerep részletes leírása (betűcsalád, méret,
 *                     vastagság, sortávolság, betűköz stb.).
 *   - `FontFamily`  : a betűcsalád (pl. serif, sans-serif, monospace, vagy egyéni
 *                     betűtípus). A `FontFamily.Default` a rendszer alap betűtípusa.
 *   - `FontWeight`  : a betűvastagság (Thin, Light, Normal, Medium, Bold ...).
 *
 * Miért `sp` és nem `dp` a betűméreteknél?
 *   - `dp` (density-independent pixels): a képernyő fizikai sűrűségétől független
 *      méret — elrendezéshez, méretekhez, paddinghez használjuk.
 *   - `sp` (scale-independent pixels): olyan, mint a `dp`, DE ráadásul követi a
 *      felhasználó rendszerszintű betűméret-beállítását is (Beállítások > Kijelző >
 *      Betűméret). Ha valaki nagyobb rendszerbetűt állít be (pl. látássérülés miatt),
 *      az `sp`-ben megadott szöveg vele együtt nő. Ezért a betű MÉRETÉT mindig `sp`-ben
 *      adjuk meg (akadálymentesség / accessibility), az elrendezést pedig `dp`-ben.
 *      A `16.sp`, `0.5.sp` írásmód a `sp` kiterjesztés-property (extension property)
 *      használata: egy számból csinál `TextUnit`-ot.
 */
package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Az alkalmazás tipográfiai készlete. Top-level `val` (immutable), így az egész
// modul eléri, és a Theme.kt átadja a `MaterialTheme`-nek.
// (Eredeti komment: "Set of Material typography styles to start with" — vagyis a
//  Material alap stíluskészletéből indulunk ki.)
val Typography =
  // A `Typography(...)` konstruktornak minden szövegszerepnek van alapértéke, ezért
  // csak azt kell megadni, amit felül akarunk bírálni. Itt egyedül a `bodyLarge`-t
  // állítjuk be, a többi szerep a Material 3 gyári értékét kapja.
  Typography(
    // `bodyLarge`: a leggyakoribb törzsszöveg-stílus (folyó szöveg, hosszabb
    // bekezdések). Egy `TextStyle` írja le a részleteit:
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default, // rendszer alap betűtípusa
        fontWeight = FontWeight.Normal, // normál (nem félkövér) vastagság
        fontSize = 16.sp, // maga a betűméret — `sp`, hogy a rendszerbeállítást kövesse
        lineHeight = 24.sp, // sormagasság (egy sor függőleges helyigénye)
        letterSpacing = 0.5.sp, // betűköz (a karakterek közti extra térköz)
      )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
  )
