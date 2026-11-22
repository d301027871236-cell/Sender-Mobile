package com.sendermobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun localEgyptianNumberUsesEgyptAsDefaultRegion() {
        val result = PhoneNormalizer.normalize("01027616172")

        assertEquals("+201027616172", result?.e164)
        assertEquals("+20", result?.countryCode)
        assertEquals("مصر", result?.countryName)
        assertTrue(result?.isEgyptian == true)
    }

    @Test
    fun kuwaitNumberIsClassifiedByInternationalCallingCode() {
        val result = PhoneNormalizer.normalize("+965 5001 2345")

        assertEquals("+96550012345", result?.e164)
        assertEquals("+965", result?.countryCode)
        assertEquals("الكويت", result?.countryName)
        assertFalse(result?.isEgyptian ?: true)
    }

    @Test
    fun saudiNumberIsClassifiedByInternationalCallingCode() {
        val result = PhoneNormalizer.normalize("+966 50 123 4567")

        assertEquals("+966501234567", result?.e164)
        assertEquals("+966", result?.countryCode)
        assertEquals("السعودية", result?.countryName)
        assertFalse(result?.isEgyptian ?: true)
    }

    @Test
    fun arabicIndicDigitsAreNormalized() {
        val result = PhoneNormalizer.normalize("٠١٠٢٧٦١٦١٧٢")

        assertEquals("+201027616172", result?.e164)
    }

    @Test
    fun invalidNumberIsRejected() {
        assertNull(PhoneNormalizer.normalize("123"))
    }
}
