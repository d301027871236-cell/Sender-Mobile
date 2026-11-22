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
    @Test
    fun otherInternationalCountriesAreRecognizedAndNamed() {
        val unitedStates = PhoneNormalizer.normalize("+1 202 555 0123")
        val unitedKingdom = PhoneNormalizer.normalize("+44 20 7031 3000")

        assertEquals("+12025550123", unitedStates?.e164)
        assertEquals("+1", unitedStates?.countryCode)
        assertTrue(!unitedStates?.countryName.isNullOrBlank())
        assertFalse(unitedStates?.countryName == "US")
        assertFalse(unitedStates?.isEgyptian ?: true)

        assertEquals("+442070313000", unitedKingdom?.e164)
        assertEquals("+44", unitedKingdom?.countryCode)
        assertTrue(!unitedKingdom?.countryName.isNullOrBlank())
        assertFalse(unitedKingdom?.countryName == "GB")
        assertFalse(unitedKingdom?.isEgyptian ?: true)
    }

    @Test
    fun legacyStoredIsoCountryCodesAreShownAsArabicNames() {
        assertEquals("الولايات المتحدة", CountryNames.displayStoredName("US"))
        assertEquals("مصر", CountryNames.displayStoredName("مصر"))
    }

}
