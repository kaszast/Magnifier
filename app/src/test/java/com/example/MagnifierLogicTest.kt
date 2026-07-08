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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    // --- computeZoomDistribution ---

    @Test
    fun `zoom eloszlas kamera-tartomanyon belul csak kamera`() {
        assertEquals(ZoomDistribution(5.0f, 1.0f), computeZoomDistribution(5.0f, 1.0f, 8.0f))
    }

    @Test
    fun `zoom eloszlas a kamera-max folott digitalis szorzot ad`() {
        assertEquals(ZoomDistribution(8.0f, 2.0f), computeZoomDistribution(16.0f, 1.0f, 8.0f))
    }

    @Test
    fun `zoom eloszlas minimum ala nem megy`() {
        assertEquals(ZoomDistribution(0.6f, 1.0f), computeZoomDistribution(0.1f, 0.6f, 8.0f))
    }

    @Test
    fun `zoom eloszlas pontosan a kamera-maxon meg nem digitalis`() {
        assertEquals(ZoomDistribution(8.0f, 1.0f), computeZoomDistribution(8.0f, 1.0f, 8.0f))
    }

    // --- computeInSampleSize ---

    @Test
    fun `inSampleSize plafon alatt egy`() {
        assertEquals(1, computeInSampleSize(1920, 1080, 4096))
    }

    @Test
    fun `inSampleSize pontosan a plafonon egy`() {
        assertEquals(1, computeInSampleSize(4096, 3072, 4096))
    }

    @Test
    fun `inSampleSize nagy kepnel ketto hatvanya`() {
        assertEquals(2, computeInSampleSize(8000, 6000, 4096))
        assertEquals(4, computeInSampleSize(16000, 12000, 4096))
    }

    @Test
    fun `inSampleSize ervenytelen plafonnal egy`() {
        assertEquals(1, computeInSampleSize(8000, 6000, 0))
    }

    // --- computeAspectCropRect ---

    @Test
    fun `aspect crop szelesebb forrasbol fuggoleges savot vag kozepen`() {
        assertEquals(Rect(1250, 0, 2750, 3000), computeAspectCropRect(4000, 3000, 0.5f))
    }

    @Test
    fun `aspect crop magasabb forrasbol vizszintes savot vag kozepen`() {
        assertEquals(Rect(0, 1000, 1000, 2000), computeAspectCropRect(1000, 3000, 1.0f))
    }

    @Test
    fun `aspect crop egyezo aranynal teljes kep`() {
        assertEquals(Rect(0, 0, 1000, 2000), computeAspectCropRect(1000, 2000, 0.5f))
    }

    @Test
    fun `aspect crop ervenytelen cel-aspectnel teljes kep`() {
        assertEquals(Rect(0, 0, 1000, 2000), computeAspectCropRect(1000, 2000, 0f))
    }

    // --- decodeCapturedJpeg ---

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(120, 80, 40))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    @Test
    fun `decode forgatas nelkul a cel-aspectre vag`() {
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 0, 1.0f)
        assertNotNull(bmp)
        assertEquals(200, bmp!!.width)
        assertEquals(200, bmp.height)
    }

    @Test
    fun `decode 90 fokos forgatasnal a cel-aspect a forgatas utan ervenyesul`() {
        // 400x200 buffer, 90° forgatás, cél 0.5 (álló) → bufferben invertált cél (2.0),
        // vágás nem kell, forgatás után 200x400
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 90, 0.5f)
        assertNotNull(bmp)
        assertEquals(200, bmp!!.width)
        assertEquals(400, bmp.height)
    }

    @Test
    fun `decode maxDim ala mintavetelez`() {
        val bmp = decodeCapturedJpeg(jpegBytes(400, 200), 0, 2.0f, maxDim = 100)
        assertNotNull(bmp)
        assertEquals(100, bmp!!.width)
        assertEquals(50, bmp.height)
    }

    @Test
    fun `decode ervenytelen bajtokra nullt ad`() {
        assertNull(decodeCapturedJpeg(byteArrayOf(1, 2, 3), 0, 1.0f))
    }
}
