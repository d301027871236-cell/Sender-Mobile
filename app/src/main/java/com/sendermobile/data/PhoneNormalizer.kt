package com.sendermobile.data

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

data class NormalizedPhone(
    val e164: String,
    val countryCode: String,
    val countryName: String,
    val isEgyptian: Boolean
)

object PhoneNormalizer {
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Local numbers are interpreted as Egyptian by default.
     * International numbers are classified for every region supported by libphonenumber.
     */
    fun normalize(rawPhone: String): NormalizedPhone? {
        val cleaned = normalizeDigits(rawPhone).trim()
        if (cleaned.isBlank()) return null

        return try {
            val parsed = phoneUtil.parse(cleaned, "EG")
            if (!phoneUtil.isPossibleNumber(parsed) || !phoneUtil.isValidNumber(parsed)) {
                return null
            }

            val region = phoneUtil.getRegionCodeForNumber(parsed) ?: return null
            val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            NormalizedPhone(
                e164 = e164,
                countryCode = "+${parsed.countryCode}",
                countryName = CountryNames.displayName(region),
                isEgyptian = region == "EG"
            )
        } catch (_: NumberParseException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun normalizeDigits(value: String): String = buildString {
        value.forEach { char ->
            val digit = Character.digit(char, 10)
            when {
                digit >= 0 -> append(('0'.code + digit).toChar())
                char == '+' -> append(char)
                char.isWhitespace() || char == '-' || char == '(' || char == ')' ||
                    char == '.' -> Unit
                else -> append(char)
            }
        }
    }
}
