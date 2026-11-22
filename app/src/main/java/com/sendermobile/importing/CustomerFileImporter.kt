package com.sendermobile.importing

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import jxl.Workbook

data class ImportPreview(
    val headers: List<String>,
    val rows: List<List<String>>
)

object CustomerFileImporter {

    fun parse(input: InputStream, fileName: String): ImportPreview {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "csv", "txt" -> parseCsv(input)
            "xls" -> parseXls(input)
            "xlsx" -> parseXlsx(input)
            "vcf", "vcard" -> parseVcf(input)
            else -> throw IllegalArgumentException("نوع الملف غير مدعوم: .$extension")
        }
    }

    private fun parseCsv(input: InputStream): ImportPreview {
        val text = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            .removePrefix("\uFEFF")
        if (text.isBlank()) return ImportPreview(emptyList(), emptyList())

        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { firstLine.count { ch -> ch == it } } ?: ','
        val records = parseDelimited(text, delimiter)
            .map { row -> row.map(String::trim) }
            .filter { row -> row.any(String::isNotBlank) }
        if (records.isEmpty()) return ImportPreview(emptyList(), emptyList())

        val width = records.maxOf { it.size }
        val normalized = records.map { row -> row + List(width - row.size) { "" } }
        return ImportPreview(normalized.first(), normalized.drop(1))
    }

    private fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"'); index++
                }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> {
                    row += cell.toString(); cell.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    row += cell.toString(); cell.setLength(0); records += row.toList(); row.clear()
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> cell.append(ch)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            records += row.toList()
        }
        return records
    }

    /** Legacy .xls reader. JExcelAPI is a small BIFF reader without Apache POI's AWT/StAX stack. */
    private fun parseXls(input: InputStream): ImportPreview {
        val workbook = Workbook.getWorkbook(input)
        try {
            val sheet = workbook.sheets.firstOrNull { it.rows > 0 && it.columns > 0 }
                ?: return ImportPreview(emptyList(), emptyList())
            val records = (0 until sheet.rows).map { row ->
                (0 until sheet.columns).map { column -> sheet.getCell(column, row).contents.trim() }
            }.filter { row -> row.any(String::isNotBlank) }
            if (records.isEmpty()) return ImportPreview(emptyList(), emptyList())
            return ImportPreview(records.first(), records.drop(1))
        } finally {
            workbook.close()
        }
    }

    /** Reads the first worksheet in an XLSX package using Android's SAX XML parser. */
    private fun parseXlsx(input: InputStream): ImportPreview {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" ||
                        entry.name == "xl/workbook.xml" ||
                        entry.name == "xl/_rels/workbook.xml.rels" ||
                        entry.name.startsWith("xl/worksheets/"))) {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val firstSheetPath = readFirstWorksheetPath(
            entries["xl/workbook.xml"],
            entries["xl/_rels/workbook.xml.rels"]
        ) ?: "xl/worksheets/sheet1.xml"
        val sheetBytes = entries[firstSheetPath]
            ?: throw IllegalArgumentException("ملف XLSX لا يحتوي على ورقة العمل الأولى قابلة للقراءة")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val sheetRows = readWorksheet(sheetBytes, sharedStrings)
            .filter { row -> row.any(String::isNotBlank) }
        if (sheetRows.isEmpty()) return ImportPreview(emptyList(), emptyList())
        val width = sheetRows.maxOf { it.size }
        val rectangular = sheetRows.map { it + List(width - it.size) { "" } }
        return ImportPreview(rectangular.first(), rectangular.drop(1))
    }


    private fun readFirstWorksheetPath(workbookXml: ByteArray?, relationshipsXml: ByteArray?): String? {
        if (workbookXml == null || relationshipsXml == null) return null
        var firstRelationshipId: String? = null
        val workbookHandler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                if (qName.substringAfter(':') == "sheet" && firstRelationshipId == null) {
                    firstRelationshipId = attrs.getValue("r:id") ?: attrs.getValue("id")
                }
            }
        }
        saxFactory().newSAXParser().parse(ByteArrayInputStream(workbookXml), workbookHandler)
        val wantedId = firstRelationshipId ?: return null
        var target: String? = null
        val relationshipsHandler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                if (qName.substringAfter(':') == "Relationship" && attrs.getValue("Id") == wantedId) {
                    target = attrs.getValue("Target")
                }
            }
        }
        saxFactory().newSAXParser().parse(ByteArrayInputStream(relationshipsXml), relationshipsHandler)
        val resolved = target ?: return null
        return when {
            resolved.startsWith("/") -> resolved.removePrefix("/")
            resolved.startsWith("xl/") -> resolved
            else -> "xl/$resolved"
        }
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val values = mutableListOf<String>()
        val handler = object : DefaultHandler() {
            private var inItem = false
            private var inText = false
            private val current = StringBuilder()
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (qName.substringAfter(':')) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> if (inItem) inText = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inItem && inText) current.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName.substringAfter(':')) {
                    "t" -> inText = false
                    "si" -> { values += current.toString(); inItem = false }
                }
            }
        }
        saxFactory().newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        return values
    }

    private fun readWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableMapOf<Int, String>()
        var currentColumn = 0
        var cellType = ""
        var inValue = false
        var inInlineText = false
        val value = StringBuilder()

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (qName.substringAfter(':')) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        currentColumn = columnIndex(attrs.getValue("r") ?: "")
                        cellType = attrs.getValue("t").orEmpty()
                        value.setLength(0)
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) value.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName.substringAfter(':')) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = value.toString()
                        row[currentColumn] = if (cellType == "s") {
                            sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        } else raw
                    }
                    "row" -> {
                        val width = (row.keys.maxOrNull() ?: -1) + 1
                        rows += (0 until width).map { row[it].orEmpty() }
                    }
                }
            }
        }
        saxFactory().newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        return rows
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }
        var result = 0
        letters.forEach { char -> result = result * 26 + (char.uppercaseChar() - 'A' + 1) }
        return (result - 1).coerceAtLeast(0)
    }

    private fun saxFactory(): SAXParserFactory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) { }
        try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) { }
        try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) { }
    }

    private fun parseVcf(input: InputStream): ImportPreview {
        val lines = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readLines() }
        val unfolded = mutableListOf<String>()
        lines.forEach { line ->
            if (unfolded.isNotEmpty() && unfolded.last().endsWith("=")) {
                unfolded[unfolded.lastIndex] = unfolded.last().dropLast(1) + line.trimStart()
            } else if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + line.drop(1)
            } else unfolded += line
        }

        val rows = mutableListOf<List<String>>()
        var name = ""
        val phones = mutableListOf<String>()
        fun flushCard() {
            phones.filter(String::isNotBlank).forEach { phone -> rows += listOf(name.ifBlank { phone }, phone) }
            name = ""
            phones.clear()
        }
        unfolded.forEach { line ->
            val property = line.substringBefore(':').substringBefore(';').uppercase(Locale.ROOT)
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> { name = ""; phones.clear() }
                line.equals("END:VCARD", ignoreCase = true) -> flushCard()
                property == "FN" -> name = decodeVCardValue(line.substringAfter(':', "").trim(), vCardCharset(line))
                property == "N" && name.isBlank() -> {
                    val parts = line.substringAfter(':').split(';')
                    name = decodeVCardValue(parts.take(2).reversed().filter(String::isNotBlank).joinToString(" ").trim(), vCardCharset(line))
                }
                property == "TEL" && line.contains(':') -> phones += decodeVCardValue(line.substringAfter(':').trim(), vCardCharset(line))
            }
        }
        if (name.isNotBlank() || phones.isNotEmpty()) flushCard()
        return ImportPreview(listOf("الاسم", "رقم الهاتف"), rows)
    }

    private fun vCardCharset(line: String): java.nio.charset.Charset {
        val header = line.substringBefore(':')
        val charsetName = Regex("(?i)(?:^|;)CHARSET=([^;:]+)")
            .find(header)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
        return try {
            if (charsetName.isNullOrBlank()) StandardCharsets.UTF_8
            else java.nio.charset.Charset.forName(charsetName)
        } catch (_: Exception) {
            StandardCharsets.UTF_8
        }
    }

    /** Decodes vCard escaped text and RFC 2045 quoted-printable bytes used by Android exports. */
    private fun decodeVCardValue(value: String, charset: java.nio.charset.Charset = StandardCharsets.UTF_8): String {
        val unescaped = value.replace("\\n", " ", ignoreCase = true)
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
        if (!Regex("=[0-9A-Fa-f]{2}").containsMatchIn(unescaped)) return unescaped

        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < unescaped.length) {
            if (unescaped[index] == '=' && index + 2 < unescaped.length) {
                val decoded = unescaped.substring(index + 1, index + 3).toIntOrNull(16)
                if (decoded != null) {
                    bytes.write(decoded)
                    index += 3
                    continue
                }
            }
            val codePoint = unescaped.codePointAt(index)
            bytes.write(String(Character.toChars(codePoint)).toByteArray(charset))
            index += Character.charCount(codePoint)
        }
        return bytes.toByteArray().toString(charset).trim()
    }
}
