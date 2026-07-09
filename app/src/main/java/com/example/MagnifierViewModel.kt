package com.example

/*
 * MagnifierViewModel.kt
 * ---------------------
 * Ez a fájl egyetlen apró, de fontos szerepű osztályt tartalmaz: egy ViewModel-t, amely
 * a "kimerevített" (freeze) képkockát tárolja a képernyő-újraépítéseken (pl. forgatás) át.
 *
 * Mi az a ViewModel (Android/Jetpack fogalom)?
 *  - Az Androidban egy Activity vagy Compose-képernyő NEM stabil: bizonyos eseményeknél a
 *    rendszer elpusztítja és újra létrehozza (configuration change), tipikusan képernyő-
 *    forgatáskor, sötét/világos téma váltáskor vagy nyelvváltáskor. Ilyenkor minden, ami
 *    csak az Activity-ben (mezőben) élt, elveszik.
 *  - A ViewModel ezt éli túl: az Android Jetpack keretrendszer külön életciklushoz köti,
 *    amely FÜGGETLEN az Activity újra-létrehozásától. Ezért a ViewModelben tárolt adat
 *    megmarad forgatás után is, és az újra létrejövő UI ugyanazt a ViewModel-példányt kapja
 *    vissza. (A ViewModel csak akkor semmisül meg, amikor a képernyő végleg elhagyásra kerül.)
 *  - A ViewModel emiatt az UI-állapot (state) természetes tárolóhelye — pont ezt használjuk ki.
 */

// A Bitmap az Android osztálya egy nyers, pixel-alapú képre (a memóriában lévő képkocka).
import android.graphics.Bitmap
// A getValue és setValue import teszi lehetővé a lenti `by` (property delegation) szintaxist:
// ezek nélkül a fordító nem tudná, hogyan olvasson/írjon a delegált property-n keresztül.
import androidx.compose.runtime.getValue
// A mutableStateOf a Compose "megfigyelhető" (observable) állapot-tárolója: ha az értéke
// megváltozik, a Compose automatikusan újrarajzolja (recomposition) azokat a UI-részeket,
// amelyek ezt az értéket olvassák.
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
// A ViewModel az androidx.lifecycle csomagból jön; ebből származtatjuk lentebb a saját osztályt.
import androidx.lifecycle.ViewModel

// A kimerevített nyers képkocka nem fér el a savedInstanceState-ben (TransactionTooLargeException),
// ezért konfigurációváltásnál (pl. forgatás) ViewModel őrzi meg.
//
// Miért nem a szokásos módon (savedInstanceState) mentjük?
//  - Az Android a "kis" UI-állapotot egy Bundle-be (savedInstanceState) menti forgatáskor.
//    Ezt a Bundle-t a rendszer egy IPC-tranzakción (Binder) keresztül viszi át, aminek szigorú
//    méretkorlátja van (~1 MB az egész folyamatra). Egy teljes képernyős Bitmap ennél sokszorta
//    nagyobb, ezért a Bundle-be tömése TransactionTooLargeException-t dobna (crash).
//  - Megoldás: a nagy képet a ViewModel őrzi (ami memóriában marad, nem megy IPC-n át), nem a Bundle.
class MagnifierViewModel : ViewModel() {
    // A kimerevített nyers képkocka. Típusa `Bitmap?`, azaz nullable: null = épp nincs kép
    // kimerevítve (a nagyító élő képet mutat), nem-null = a felhasználó "befagyasztott" egy kockát.
    //
    // A `by mutableStateOf(...)` a Compose "state delegate" mintája:
    //   - a `mutableStateOf(null)` egy megfigyelhető állapot-tartót hoz létre kezdőértékkel;
    //   - a `by` kulcsszó (property delegation) elrejti a boilerplate-et: kívülről úgy használjuk
    //     a `rawFrozenBitmap`-et, mint egy sima `var` mezőt (olvasás/írás `=`-lel), de a háttérben
    //     minden olvasás "feliratkozik" az értékre, minden írás pedig recomposition-t vált ki.
    //   - Emiatt, ha ezt a mezőt átállítjuk, a Compose UI automatikusan frissül — nincs szükség
    //     manuális "értesítsd a nézetet" hívásra.
    var rawFrozenBitmap by mutableStateOf<Bitmap?>(null)
}

