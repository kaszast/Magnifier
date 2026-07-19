/*
 * ============================================================================
 *  MagnifierLogicTest — a nagyító-app fő logikai tesztje (unit teszt)
 * ============================================================================
 *
 *  MI EZ A FÁJL?
 *  Ez a projekt legfontosabb, "tartalmas" tesztje. A nagyító tiszta, matematikai
 *  logikáját fedi le: a pan (eltolás) korlátozását, a digitális zoom kivágásait,
 *  a szín-szűrők (color matrix) helyességét, a mentett kép dekódolását és a
 *  zoom-lépcsők számítását. A UI-t (Compose) NEM teszteli — csak a mögötte lévő
 *  függvényeket. Így gyors és determinisztikus.
 *
 *  MIÉRT UNIT TESZT (src/test), MÉGIS ANDROID-OSZTÁLYOKKAL?
 *  A tesztelt függvények android.graphics.Bitmap, .Rect, .Color, .Matrix
 *  típusokat használnak. Ezek éles Androidon léteznének, a JVM-en viszont nem.
 *  Hogy emulátor nélkül, gyorsan futtathassuk, Robolectric-et használunk (lásd
 *  az ExampleRobolectricTest.kt bővebb magyarázatát):
 *    - @RunWith(RobolectricTestRunner::class) : a JVM-en szimulálja az Android
 *      keretrendszert.
 *    - @GraphicsMode(GraphicsMode.Mode.NATIVE) : bekapcsolja a VALÓDI grafikát.
 *      Ez itt kulcsfontosságú: a decodeCapturedJpeg tesztek ténylegesen JPEG-et
 *      kódolnak/dekódolnak és Bitmap-eket vágnak/forgatnak — ehhez natív
 *      képműveletek kellenek, nem üres stub-ok.
 *    - @Config(sdk = [36]) : SDK 36 API-szintet szimulál (determinisztikus).
 *
 *  HASZNÁLT JUNIT ÁLLÍTÁSOK (asserts):
 *    - assertEquals(várt, kapott)         : egyenlőség (elsőként mindig a VÁRT).
 *    - assertEquals(várt, kapott, delta)  : float-oknál a delta a megengedett
 *      hibahatár — lebegőpontos számoknál (float) sosem "==", mindig tűréssel.
 *    - assertTrue(feltétel)               : a feltételnek igaznak kell lennie.
 *    - assertNull / assertNotNull         : null-e a visszatérési érték.
 *    - assertEquals("index $i", v, k, d)  : az első String egy üzenet, ami
 *      bukáskor megjelenik — így tudni, MELYIK ciklus-elemnél hasalt el.
 *
 *  A tesztek "backtick-es" (` `) magyar nevei szándékosan mondatszerűek, hogy a
 *  teszt-riport önmagában is olvasható legyen. A metódusok fölötti kommentek
 *  ezért főleg a MIÉRT-et (a védett viselkedést) magyarázzák.
 * ============================================================================
 */
package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MagnifierLogicTest {

    // ========================= clampPan =========================
    // A pan (ujjal húzott eltolás) korlátozása. Nagyításkor a felhasználó odébb
    // tolhatja a képet, de nem húzhatja "a semmibe": a nagyított tartalom széle
    // nem szakadhat el a viewport szélétől. A megengedett maximum a nagyítás
    // mértékétől ÉS a viewport méretétől függ (maxPan = (scale-1) * oldal / 2).
    // Miért fontos: rossz korlát esetén fekete csíkok jelennének meg a kép szélén.
    // --- clampPan ---

    // scale <= 1 esetén nincs mit tologatni, ezért a pan mindig nullára esik vissza.
    @Test
    fun `clampPan zoom nelkul nullat ad`() {
        val result = clampPan(Offset(100f, 50f), 1.0f, IntSize(1080, 2400))
        assertEquals(Offset.Zero, result)
    }

    // A határon belüli eltolást érintetlenül hagyja (nincs fölösleges módosítás).
    @Test
    fun `clampPan hataron beluli erteket nem modosit`() {
        // scale=2, viewport 1080x2400 → maxPan = (540, 1200)
        val result = clampPan(Offset(300f, 900f), 2.0f, IntSize(1080, 2400))
        assertEquals(Offset(300f, 900f), result)
    }

    // Túl nagy (és negatív) eltolást a számított határra vág, mindkét tengelyen.
    @Test
    fun `clampPan tulcsordulast a viewport-fuggo hatarra vagja`() {
        val result = clampPan(Offset(9999f, -9999f), 2.0f, IntSize(1080, 2400))
        assertEquals(Offset(540f, -1200f), result)
    }

    // Bizonyítja, hogy a határ a viewportból SZÁMÍTOTT, nem fix konstans: nagyobb
    // kijelzőn nagyobb eltolás is elfér. (Regressziós teszt a régi 500/800 fix
    // korlátra, amely ezt hibásan levágta volna.)
    @Test
    fun `clampPan nagyobb viewportnal nagyobb hatart enged`() {
        // Korábbi fix 500/800 konstans ezt levágta volna
        val result = clampPan(Offset(700f, 1100f), 3.0f, IntSize(1440, 3120))
        assertEquals(Offset(700f, 1100f), result)
    }

    // ==================== computeVisibleCropRect ====================
    // A digitális zoom közben a képernyőn LÁTOTT részt adja vissza a forrás-kép
    // pixel-koordinátáiban. Ugyanaz a geometria, amit a graphicsLayer (középpontos
    // skálázás + eltolás) és a minimap is használ. Miért fontos: mentéskor pontosan
    // ezt a kivágást kell kimenteni, hogy a fájl azt tartalmazza, amit a felhasználó
    // a képernyőn látott (WYSIWYG).
    // --- computeVisibleCropRect ---

    // Nagyítás nélkül (scale <= 1) nincs kivágás → a teljes kép a látott terület.
    @Test
    fun `crop scale 1 alatt a teljes kepet adja`() {
        val rect = computeVisibleCropRect(1000, 2000, 1.0f, 0f, 0f)
        assertEquals(Rect(0, 0, 1000, 2000), rect)
    }

    // 2x zoom, eltolás nélkül → a kép közepéből a fele akkora rész látszik.
    @Test
    fun `crop pan nelkul kozepre esik`() {
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 0f, 0f)
        assertEquals(Rect(250, 500, 750, 1500), rect)
    }

    // A pan előjel-konvencióját rögzíti (könnyű elrontani): a pozitív eltolás a
    // tartalmat jobbra tolja, így a látott kivágás BALRA csúszik a forrásban.
    @Test
    fun `crop pozitiv pan a tartalom bal oldalat mutatja`() {
        // graphicsLayer: pozitív translationX jobbra tolja a tartalmat,
        // tehát a látott kivágás középpontja balra kerül: cx = w/2 - pan/scale
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 100f, 0f)
        assertEquals(Rect(200, 500, 700, 1500), rect)
    }

    // Extrém eltolásnál a kivágás a kép szélére szorul, nem lóg túl rajta
    // (coerceIn a [0, meret-crop] tartományra).
    @Test
    fun `crop a kep szelere szoritva marad`() {
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 99999f, -99999f)
        assertEquals(Rect(0, 1000, 500, 2000), rect)
    }

    // =================== buildFilterMatrixValues ===================
    // Az akadálymentesítő szín-szűrők (normál, negatív, sárga, vörös) + a kontraszt
    // és a fényerő egyetlen 4x5-ös color matrix-szá összegyúrva. UGYANEZ a mátrix
    // hajtja meg az élő képernyős nézetet ÉS a mentett/megosztott képet, ezért ha
    // itt eltér egy együttható, a kimenet nem az lenne, amit a felhasználó lát
    // (WYSIWYG-sérülés). A 20 float soronként az alábbi képletet adja:
    //   Rout = a*R + b*G + c*B + d*A + e   → 5 szám (a,b,c,d,e) soronként,
    //   4 sor sorrendben: R, G, B, A. Az "e" (offset) oszlop indexei: 4, 9, 14, 19.
    //   A luma-együtthatók (0.2126, 0.7152, 0.0722) a szabványos szürkeárnyalat-
    //   súlyok (ITU-R BT.709), amelyekkel a szín világosságát számoljuk.
    // --- buildFilterMatrixValues ---

    // A luma-súlyok és a float-összehasonlítás tűréshatára (delta) a lenti
    // ellenőrzésekhez. delta = 1e-4: ennyi eltérést még "egyenlőnek" fogadunk el,
    // mert két float ritkán bitre pontosan azonos.
    private val lumaR = 0.2126f
    private val lumaG = 0.7152f
    private val lumaB = 0.0722f
    private val delta = 1e-4f

    // NORMÁL szűrő, semleges kontraszt/fényerő → identitás-mátrix, azaz a színt
    // semmi nem változtatja. A ciklus mind a 20 együtthatót ellenőrzi.
    @Test
    fun `normal szuro identitas matrix`() {
        val v = buildFilterMatrixValues(FilterMode.NORMAL, 1.0f, 0.0f)
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        for (i in 0..19) assertEquals("index $i", identity[i], v[i], delta)
    }

    // SÁRGA szűrő: az R és G kimenet is a luma (szürkeérték), a kék (B) sor nulla
    // → az eredmény (l, l, 0), ami sárgás árnyalat. A luma-modell megegyezik az élő
    // nézet deszaturálás + sárga blendjével.
    @Test
    fun `sarga szuro luma sorok es nulla kek csatorna`() {
        val v = buildFilterMatrixValues(FilterMode.YELLOW, 1.0f, 0.0f)
        // R és G sor: luma együtthatók; B sor: nulla → (l, l, 0)
        assertEquals(lumaR, v[0], delta)
        assertEquals(lumaG, v[1], delta)
        assertEquals(lumaB, v[2], delta)
        assertEquals(lumaR, v[5], delta)
        assertEquals(lumaG, v[6], delta)
        assertEquals(lumaB, v[7], delta)
        for (i in 10..14) assertEquals("B sor index $i", 0f, v[i], delta)
    }

    // VÖRÖS szűrő: csak az R kimenet kap luma-értéket, a G és B sor teljesen nulla
    // → az eredmény (l, 0, 0), azaz vörös árnyalat.
    @Test
    fun `voros szuro csak R csatornara kepez`() {
        val v = buildFilterMatrixValues(FilterMode.RED, 1.0f, 0.0f)
        assertEquals(lumaR, v[0], delta)
        assertEquals(lumaG, v[1], delta)
        assertEquals(lumaB, v[2], delta)
        for (i in 5..9) assertEquals("G sor index $i", 0f, v[i], delta)
        for (i in 10..14) assertEquals("B sor index $i", 0f, v[i], delta)
    }

    // NEGATÍV (invertált) szűrő: minden csatorna -1 szorzót és +255 offsetet kap
    // (kimenet = 255 - bemenet), ez a klasszikus színinvertálás. Az indexek: a -1
    // szorzók a 0/6/12, a 255 offsetek a 4/9/14 pozíción vannak.
    @Test
    fun `negativ szuro invertal`() {
        val v = buildFilterMatrixValues(FilterMode.INVERTED, 1.0f, 0.0f)
        assertEquals(-1f, v[0], delta)
        assertEquals(255f, v[4], delta)
        assertEquals(-1f, v[6], delta)
        assertEquals(255f, v[9], delta)
        assertEquals(-1f, v[12], delta)
        assertEquals(255f, v[14], delta)
    }

    // A fényerő (brightness) additív: az offset-oszlop mindhárom szín-sorához
    // (index 4, 9, 14) hozzáadódik, így minden pixel egyformán világosabb lesz.
    @Test
    fun `fenyero additiv offsetkent jelenik meg`() {
        val v = buildFilterMatrixValues(FilterMode.NORMAL, 1.0f, 40.0f)
        assertEquals(40f, v[4], delta)
        assertEquals(40f, v[9], delta)
        assertEquals(40f, v[14], delta)
    }

    // Regressziós teszt: a kontraszt a beépített offsetet (pl. az invertálás 255-ét)
    // PONTOSAN EGYSZER szorozza. Korábban kétszer szorzódott (1020 lett 510 helyett),
    // ami elrontotta a negatív szűrő világosságát.
    @Test
    fun `kontraszt az offsetet pontosan egyszer szorozza`() {
        // Negatív szűrőnél az offset 255 → kontraszt 2.0 mellett 510 (nem 1020, ami
        // a korábbi dupla szorzás hibája volt)
        val v = buildFilterMatrixValues(FilterMode.INVERTED, 2.0f, 0.0f)
        assertEquals(-2f, v[0], delta)
        assertEquals(510f, v[4], delta)
    }


    // ================== computeZoomDistribution ==================
    // Egy kívánt teljes nagyítás szétosztása KAMERA zoom (optikai/hibrid, éles) és
    // DIGITÁLIS zoom (kijelző-oldali, pixel-nyújtás) között. Amíg a kamera
    // zoom-tartománya elviszi, addig csak a kamerát húzza — ez adja a legjobb
    // képminőséget; csak fölötte kapcsol digitális szorzóra. Miért fontos: rossz
    // felosztásnál fölöslegesen romlana a képminőség.
    // --- computeZoomDistribution ---

    // A kamera-tartományon belüli cél (5x < 8x max) teljes egészében kamera-zoom,
    // digitális szorzó nélkül (1.0).
    @Test
    fun `zoom eloszlas kamera-tartomanyon belul csak kamera`() {
        assertEquals(ZoomDistribution(5.0f, 1.0f), computeZoomDistribution(5.0f, 1.0f, 8.0f))
    }

    // A kamera-max fölötti cél (16x): a kamera a maxra (8x) áll, a maradékot
    // digitális szorzó viszi (16 / 8 = 2.0x).
    @Test
    fun `zoom eloszlas a kamera-max folott digitalis szorzot ad`() {
        assertEquals(ZoomDistribution(8.0f, 2.0f), computeZoomDistribution(16.0f, 1.0f, 8.0f))
    }

    // A minZoom (0.6) alatti célt felhúzza a minimumra — nincs értelmetlen "0.1x".
    @Test
    fun `zoom eloszlas minimum ala nem megy`() {
        assertEquals(ZoomDistribution(0.6f, 1.0f), computeZoomDistribution(0.1f, 0.6f, 8.0f))
    }

    // Határeset: pontosan a kamera-maxon (8x == max) még NINCS digitális szorzó,
    // mert a feltétel "<= maxZoom" (nem "<"). A digitális rész csak fölötte indul.
    @Test
    fun `zoom eloszlas pontosan a kamera-maxon meg nem digitalis`() {
        assertEquals(ZoomDistribution(8.0f, 1.0f), computeZoomDistribution(8.0f, 1.0f, 8.0f))
    }

    // ===================== computeInSampleSize =====================
    // A JPEG dekódolás memória-védelme. Nagy képnél a BitmapFactory-nak megmondjuk,
    // hányad részére mintavételezzen (inSampleSize, mindig 2-hatvány), hogy a
    // leghosszabb oldal a maxDim plafon alá kerüljön. Miért fontos: egy 108 MP-es
    // fotó teljes felbontású dekódolása OutOfMemoryError-t (OOM) okozna.
    // --- computeInSampleSize ---

    // A plafon alatti kép nem igényel kicsinyítést → 1 (nincs mintavételezés).
    @Test
    fun `inSampleSize plafon alatt egy`() {
        assertEquals(1, computeInSampleSize(1920, 1080, 4096))
    }

    // Határeset: pontosan a plafonnal egyező oldal még belefér, nincs kicsinyítés.
    @Test
    fun `inSampleSize pontosan a plafonon egy`() {
        assertEquals(1, computeInSampleSize(4096, 3072, 4096))
    }

    // A visszaadott érték mindig 2-hatvány (2, majd 4), soha nem tetszőleges szám —
    // ezt várja el a BitmapFactory a leggyorsabb dekódoláshoz.
    @Test
    fun `inSampleSize nagy kepnel ketto hatvanya`() {
        assertEquals(2, computeInSampleSize(8000, 6000, 4096))
        assertEquals(4, computeInSampleSize(16000, 12000, 4096))
    }

    // Érvénytelen plafon (0) esetén biztonságos 1-et ad — így nem lép be a while
    // ciklusba (elkerüli a 0-val osztást / a végtelen ciklust).
    @Test
    fun `inSampleSize ervenytelen plafonnal egy`() {
        assertEquals(1, computeInSampleSize(8000, 6000, 0))
    }

    // ==================== computeAspectCropRect ====================
    // Középre igazított vágás egy cél-képarányra (targetAspect = szélesség/magasság).
    // A kamera buffere és a képernyő aránya eltérhet; a preview FILL_CENTER módban
    // levágja a lelógó részt, és a mentett képnek is pont ezt kell tennie, hogy a
    // fájl a preview-val egyezzen.
    // --- computeAspectCropRect ---

    // A forrás szélesebb a célnál (4000x3000, cél 0.5 = álló) → a bal/jobb szélből
    // vág, középen egy függőleges sáv marad.
    @Test
    fun `aspect crop szelesebb forrasbol fuggoleges savot vag kozepen`() {
        assertEquals(Rect(1250, 0, 2750, 3000), computeAspectCropRect(4000, 3000, 0.5f))
    }

    // A forrás magasabb a célnál (1000x3000, cél 1.0 = négyzet) → a tetejéből/aljából
    // vág, középen egy vízszintes sáv marad.
    @Test
    fun `aspect crop magasabb forrasbol vizszintes savot vag kozepen`() {
        assertEquals(Rect(0, 1000, 1000, 2000), computeAspectCropRect(1000, 3000, 1.0f))
    }

    // Ha a forrás aránya már egyezik a céllal, nincs mit vágni → a teljes kép.
    @Test
    fun `aspect crop egyezo aranynal teljes kep`() {
        assertEquals(Rect(0, 0, 1000, 2000), computeAspectCropRect(1000, 2000, 0.5f))
    }

    // Érvénytelen cél-arány (0) esetén biztonságos viselkedés: teljes kép, nincs
    // 0-val osztás.
    @Test
    fun `aspect crop ervenytelen cel-aspectnel teljes kep`() {
        assertEquals(Rect(0, 0, 1000, 2000), computeAspectCropRect(1000, 2000, 0f))
    }

    // ===================== decodeCapturedJpeg =====================
    // A kamera által készített still (JPEG bájttömb) teljes feldolgozási láncát
    // teszteli: (1) dekódolás memória-plafonnal, (2) vágás a viewport képarányára,
    // (3) forgatás a kijelző tájolására. Kulcs-részlet: a vágás a FORGATÁS ELŐTT
    // történik, 90/270 foknál INVERTÁLT cél-aránnyal — így a forgatandó bitmap eleve
    // kisebb (kevesebb memória, gyorsabb).
    // --- decodeCapturedJpeg ---

    // Segéd: egyszínű bitmapet gyárt, valódi JPEG-gé tömöríti, és annak bájtjait adja
    // vissza — így a tesztek "igazi" kamera-kimenetet kapnak bemenetként. (Ehhez kell
    // a @GraphicsMode(NATIVE): valódi kép-kódolás/-dekódolás fut a JVM-en.)
    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(120, 80, 40))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    // Forgatás nélkül (0°), cél-arány 1.0 (négyzet): a 400x200-as forrásból középen
    // 200x200-at vág. Az assertNotNull azért kell, mert a függvény Bitmap?-et (null
    // is lehet) ad; a "!!" utána már a nem-null értéken dolgozik.
    @Test
    fun `decode forgatas nelkul a cel-aspectre vag`() {
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 0, 1.0f)
        assertNotNull(bmp)
        assertEquals(200, bmp!!.width)
        assertEquals(200, bmp.height)
    }

    // A trükkös eset: 90°-os forgatásnál a cél-arány csak a FORGATÁS UTÁN kell hogy
    // teljesüljön. Ezért a vágást a buffer-en fordított aránnyal (1/0.5 = 2.0) végzi;
    // itt a 400x200 pont 2.0 arányú, így nem is kell vágni, és a forgatás után áll a
    // kép: 200x400 (a kért 0.5 arány).
    @Test
    fun `decode 90 fokos forgatasnal a cel-aspect a forgatas utan ervenyesul`() {
        // 400x200 buffer, 90° forgatás, cél 0.5 (álló) → bufferben invertált cél (2.0),
        // vágás nem kell, forgatás után 200x400
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 90, 0.5f)
        assertNotNull(bmp)
        assertEquals(200, bmp!!.width)
        assertEquals(400, bmp.height)
    }

    // A memória-plafon a teljes láncon átér: maxDim = 100 mellett a 400x200-as kép
    // 1/4-ére mintavételeződik (inSampleSize = 4) → 100x50. A cél-arány (2.0) itt már
    // egyezik, ezért nincs külön vágás.
    @Test
    fun `decode maxDim ala mintavetelez`() {
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 0, 2.0f, maxDim = 100)
        assertNotNull(bmp)
        assertEquals(100, bmp!!.width)
        assertEquals(50, bmp.height)
    }

    // Robusztusság: hibás/nem JPEG bájtokra a függvény null-t ad (assertNull), nem
    // dob kivételt — így egy sérült felvétel nem viszi el az appot.
    @Test
    fun `decode ervenytelen bajtokra nullt ad`() {
        assertNull(decodeCapturedJpeg(byteArrayOf(1, 2, 3), 0, 1.0f))
    }
}
