package com.sendermobile.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerImportMapperTest {

    @Test
    fun mapperNormalizesAndDeduplicatesEquivalentPhoneFormats() {
        val preview = ImportPreview(
            headers = listOf("الاسم", "الهاتف"),
            rows = listOf(
                listOf("أحمد", "01027616172"),
                listOf("أحمد مكرر", "+20 102 761 6172"),
                listOf("سالم", "+965 5001 2345"),
                listOf("رقم غير صالح", "123")
            )
        )

        val result = CustomerImportMapper.map(preview, nameColumn = 0, phoneColumn = 1)

        assertEquals(2, result.customers.size)
        assertEquals(1, result.duplicateRows)
        assertEquals(1, result.invalidRows)
        assertEquals("+201027616172", result.customers[0].normalizedPhone)
        assertEquals("+96550012345", result.customers[1].normalizedPhone)
        assertEquals("الكويت", result.customers[1].countryName)
    }

    @Test
    fun mapperUsesPhoneAsFallbackWhenNameIsBlank() {
        val preview = ImportPreview(
            headers = listOf("الاسم", "الهاتف"),
            rows = listOf(listOf("", "01027616172"))
        )

        val result = CustomerImportMapper.map(preview, nameColumn = 0, phoneColumn = 1)

        assertEquals("01027616172", result.customers.single().name)
    }
}
