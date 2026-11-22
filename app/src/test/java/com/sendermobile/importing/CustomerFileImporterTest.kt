package com.sendermobile.importing

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
    fun xlsxReadsSharedStringsAndSparseColumns() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Contacts" sheetId="2" r:id="rId7"/></sheets></workbook>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>الاسم</t></si><si><t>الهاتف</t></si><si><t>أحمد</t></si></sst>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row><row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="inlineStr"><is><t>01027616172</t></is></c></row></sheetData></worksheet>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val preview = CustomerFileImporter.parse(
            ByteArrayInputStream(output.toByteArray()),
            "customers.xlsx"
        )

        assertEquals(listOf("الاسم", "الهاتف"), preview.headers)
        assertEquals(listOf("أحمد", "01027616172"), preview.rows.single())
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
