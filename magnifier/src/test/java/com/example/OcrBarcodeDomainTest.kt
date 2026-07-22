package com.example

import com.example.domain.barcode.BarcodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clean Code domain osztályok egységtesztjei.
 */
class OcrBarcodeDomainTest {

    @Test
    fun barcodeResult_dataStructure_correctlyStoresUrlInfo() {
        val result = BarcodeResult(
            text = "https://example.com",
            typeLabel = "Webcím (URL)",
            isUrl = true
        )

        assertEquals("https://example.com", result.text)
        assertEquals("Webcím (URL)", result.typeLabel)
        assertTrue(result.isUrl)
    }

    @Test
    fun barcodeResult_dataStructure_correctlyStoresTextInfo() {
        val result = BarcodeResult(
            text = "1234567890",
            typeLabel = "Termékkód",
            isUrl = false
        )

        assertEquals("1234567890", result.text)
        assertEquals("Termékkód", result.typeLabel)
        assertEquals(false, result.isUrl)
    }
}
