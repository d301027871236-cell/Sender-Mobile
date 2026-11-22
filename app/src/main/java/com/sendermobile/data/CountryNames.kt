package com.sendermobile.data

import java.util.Locale

/**
 * Converts libphonenumber region identifiers (and older stored ISO codes)
 * into readable Arabic country names.
 */
object CountryNames {
    private val overrides = mapOf(
        "EG" to "مصر",
        "KW" to "الكويت",
        "SA" to "السعودية"
    )

    fun displayName(regionCode: String): String {
        val code = regionCode.trim().uppercase(Locale.ROOT)
        if (code.isBlank()) return "غير معروف"
        return overrides[code]
            ?: Locale("", code).getDisplayCountry(Locale("ar")).ifBlank { code }
    }

    fun displayStoredName(value: String): String {
        val stored = value.trim()
        return if (stored.length == 2 && stored.all { it.isLetter() }) {
            displayName(stored)
        } else {
            stored.ifBlank { "غير معروف" }
        }
    }
}
