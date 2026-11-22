package com.sendermobile.importing

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerFileImporterTest {

    @Test
    fun csvSupportsSemicolonAndQuotedValues() {
        val csv = "الاسم;الهاتف;ملاحظة\n\"أحمد؛ محمد\";01027616172;\"عميل مهم\""
        val preview = CustomerFileImporter.parse(
            ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)),
            "customers.csv"
        )

        assertEquals(listOf("الاسم", "الهاتف", "ملاحظة"), preview.headers)
        assertEquals("أحمد؛ محمد", preview.rows.single()[0])
        assertEquals("01027616172", preview.rows.single()[1])
        assertEquals("عميل مهم", preview.rows.single()[2])
    }

    @Test
    fun vcfCreatesOneRowForEveryPhoneNumber() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:أحمد محمد
            TEL;TYPE=CELL:+201027616172
            TEL;TYPE=WORK:+201112223333
            END:VCARD
        """.trimIndent()

        val preview = CustomerFileImporter.parse(
            ByteArrayInputStream(vcf.toByteArray(StandardCharsets.UTF_8)),
            "contacts.vcf"
        )

        assertEquals(2, preview.rows.size)
        assertEquals("أحمد محمد", preview.rows[0][0])
        assertEquals("+201027616172", preview.rows[0][1])
        assertEquals("+201112223333", preview.rows[1][1])
    }

    @Test
    fun emptyCsvReturnsEmptyPreview() {
        val preview = CustomerFileImporter.parse(
            ByteArrayInputStream(ByteArray(0)),
            "empty.csv"
        )
        assertTrue(preview.headers.isEmpty())
        assertTrue(preview.rows.isEmpty())
    }
}
