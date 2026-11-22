package com.sendermobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerSearchTest {
    @Test
    fun normalizesArabicAndWesternDigitsForSearch() {
        assertEquals("01027616172", phoneSearchDigits("٠١٠٢٧٦١٦١٧٢"))
        assertEquals("01027616172", phoneSearchDigits("010-2761-6172"))
    }

    @Test
    fun localEgyptianPrefixMatchesOriginalAndInternationalNumbers() {
        assertTrue(matchesPhonePrefix("01027616172", "+201027616172", "+20", "010"))
        assertTrue(matchesPhonePrefix("01027616172", "+201027616172", "+20", "0102"))
        assertTrue(matchesPhonePrefix("01027616172", "+201027616172", "+20", "01027616172"))
        assertTrue(matchesPhonePrefix("+201027616172", "+201027616172", "+20", "010"))
        assertTrue(matchesPhonePrefix("+201027616172", "+201027616172", "+20", "0102"))
    }

    @Test
    fun internationalPrefixMatchesNormalizedNumber() {
        assertTrue(matchesPhonePrefix("+965 5001 2345", "+96550012345", "+965", "9655"))
        assertTrue(matchesPhonePrefix("+966501234567", "+966501234567", "+966", "501"))
    }

    @Test
    fun supportsInternationalAndMiddleDigitSearch() {
        assertTrue(matchesPhonePrefix("01027616172", "+201027616172", "+20", "002010"))
        assertTrue(matchesPhonePrefix("01027616172", "+201027616172", "+20", "2761"))
        assertTrue(matchesPhonePrefix("+965 5001 2345", "+96550012345", "+965", "5001"))
    }

    @Test
    fun unrelatedPrefixDoesNotMatch() {
        assertFalse(matchesPhonePrefix("01027616172", "+201027616172", "+20", "011"))
    }
}
