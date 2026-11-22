package com.sendermobile.importing

import com.sendermobile.data.Customer
import com.sendermobile.data.PhoneNormalizer

data class ImportMappingResult(
    val customers: List<Customer>,
    val invalidRows: Int
)

object CustomerImportMapper {
    fun map(
        preview: ImportPreview,
        nameColumn: Int,
        phoneColumn: Int
    ): ImportMappingResult {
        val unique = LinkedHashMap<String, Customer>()
        var invalid = 0

        preview.rows.forEach { row ->
            val rawPhone = row.getOrNull(phoneColumn).orEmpty().trim()
            val normalized = PhoneNormalizer.normalize(rawPhone)
            if (normalized == null) {
                invalid++
            } else {
                val name = row.getOrNull(nameColumn).orEmpty().trim().ifBlank { rawPhone }
                unique.putIfAbsent(
                    normalized.e164,
                    Customer(
                        name = name,
                        originalPhone = rawPhone,
                        normalizedPhone = normalized.e164,
                        countryCode = normalized.countryCode,
                        countryName = normalized.countryName,
                        isEgyptian = normalized.isEgyptian
                    )
                )
            }
        }
        return ImportMappingResult(unique.values.toList(), invalid)
    }
}
