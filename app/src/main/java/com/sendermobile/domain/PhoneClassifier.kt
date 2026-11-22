package com.sendermobile.domain

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.sendermobile.data.CountryNames
import com.sendermobile.data.Customer

data class PhoneClassification(
    val normalizedNumber: String,
    val countryCode: String,
    val countryName: String,
    val isEgyptian: Boolean
)

class PhoneClassifier {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * Accepts international numbers (+...), and national numbers using the
     * supplied default region. Egyptian numbers are the default for this app.
     * Every region supported by libphonenumber receives its localized country name.
     */
    fun classify(raw: String, defaultRegion: String = "EG"): PhoneClassification? {
        val cleaned = raw.trim().replace(Regex("[\\s().-]"), "")
        if (cleaned.isBlank()) return null
        return try {
            val parsed = phoneUtil.parse(cleaned, defaultRegion.uppercase())
            if (!phoneUtil.isPossibleNumber(parsed) || !phoneUtil.isValidNumber(parsed)) return null
            val region = phoneUtil.getRegionCodeForNumber(parsed) ?: return null
            PhoneClassification(
                normalizedNumber = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                countryCode = "+${parsed.countryCode}",
                countryName = CountryNames.displayName(region),
                isEgyptian = region == "EG"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun toCustomer(name: String, phone: String, defaultRegion: String = "EG"): Customer? {
        val result = classify(phone, defaultRegion) ?: return null
        return Customer(
            name = name.trim().ifBlank { phone },
            originalPhone = phone,
            normalizedPhone = result.normalizedNumber,
            countryCode = result.countryCode,
            countryName = result.countryName,
            isEgyptian = result.isEgyptian
        )
    }
}
