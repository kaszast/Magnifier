package com.example

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MagnifierLogicTest {

    // --- clampPan ---

    @Test
    fun `clampPan zoom nelkul nullat ad`() {
        val result = clampPan(Offset(100f, 50f), 1.0f, IntSize(1080, 2400))
        assertEquals(Offset.Zero, result)
    }

    @Test
    fun `clampPan hataron beluli erteket nem modosit`() {
        // scale=2, viewport 1080x2400 → maxPan = (540, 1200)
        val result = clampPan(Offset(300f, 900f), 2.0f, IntSize(1080, 2400))
        assertEquals(Offset(300f, 900f), result)
    }

    @Test
    fun `clampPan tulcsordulast a viewport-fuggo hatarra vagja`() {
        val result = clampPan(Offset(9999f, -9999f), 2.0f, IntSize(1080, 2400))
        assertEquals(Offset(540f, -1200f), result)
    }

    @Test
    fun `clampPan nagyobb viewportnal nagyobb hatart enged`() {
        // Korábbi fix 500/800 konstans ezt levágta volna
        val result = clampPan(Offset(700f, 1100f), 3.0f, IntSize(1440, 3120))
        assertEquals(Offset(700f, 1100f), result)
    }

    // --- computeVisibleCropRect ---

    @Test
    fun `crop scale 1 alatt a teljes kepet adja`() {
        val rect = computeVisibleCropRect(1000, 2000, 1.0f, 0f, 0f)
        assertEquals(Rect(0, 0, 1000, 2000), rect)
    }

    @Test
    fun `crop pan nelkul kozepre esik`() {
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 0f, 0f)
        assertEquals(Rect(250, 500, 750, 1500), rect)
    }

    @Test
    fun `crop pozitiv pan a tartalom bal oldalat mutatja`() {
        // graphicsLayer: pozitív translationX jobbra tolja a tartalmat,
        // tehát a látott kivágás középpontja balra kerül: cx = w/2 - pan/scale
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 100f, 0f)
        assertEquals(Rect(200, 500, 700, 1500), rect)
    }

    @Test
    fun `crop a kep szelere szoritva marad`() {
        val rect = computeVisibleCropRect(1000, 2000, 2.0f, 99999f, -99999f)
        assertEquals(Rect(0, 1000, 500, 2000), rect)
    }

    // --- buildFilterMatrixValues ---

    private val lumaR = 0.2126f
    private val lumaG = 0.7152f
    private val lumaB = 0.0722f
    private val delta = 1e-4f

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

    @Test
    fun `voros szuro csak R csatornara kepez`() {
        val v = buildFilterMatrixValues(FilterMode.RED, 1.0f, 0.0f)
        assertEquals(lumaR, v[0], delta)
        assertEquals(lumaG, v[1], delta)
        assertEquals(lumaB, v[2], delta)
        for (i in 5..9) assertEquals("G sor index $i", 0f, v[i], delta)
        for (i in 10..14) assertEquals("B sor index $i", 0f, v[i], delta)
    }

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

    @Test
    fun `fenyero additiv offsetkent jelenik meg`() {
        val v = buildFilterMatrixValues(FilterMode.NORMAL, 1.0f, 40.0f)
        assertEquals(40f, v[4], delta)
        assertEquals(40f, v[9], delta)
        assertEquals(40f, v[14], delta)
    }

    @Test
    fun `kontraszt az offsetet pontosan egyszer szorozza`() {
        // Negatív szűrőnél az offset 255 → kontraszt 2.0 mellett 510 (nem 1020, ami
        // a korábbi dupla szorzás hibája volt)
        val v = buildFilterMatrixValues(FilterMode.INVERTED, 2.0f, 0.0f)
        assertEquals(-2f, v[0], delta)
        assertEquals(510f, v[4], delta)
    }
}
