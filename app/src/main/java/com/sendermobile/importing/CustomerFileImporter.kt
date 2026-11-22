package com.sendermobile.importing

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ImportPreview(
    val headers: List<String>,
    val rows: List<List<String>>
)

object CustomerFileImporter {

    fun parse(input: InputStream, fileName: String): ImportPreview {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "csv", "txt" -> parseCsv(input)
            "xls", "xlsx" -> parseExcel(input)
            "vcf", "vcard" -> parseVcf(input)
            else -> throw IllegalArgumentException("نوع الملف غير مدعوم: .$extension")
        }
    }

    private fun parseCsv(input: InputStream): ImportPreview {
        val text = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            .removePrefix("\uFEFF")
        if (text.isBlank()) return ImportPreview(emptyList(), emptyList())

        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\\t').maxByOrNull { firstLine.count { ch -> ch == it } } ?: ','
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
                    cell.append('"')
                    index++
                }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                (ch == '\\n' || ch == '\\r') && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                    records += row.toList()
                    row.clear()
                    if (ch == '\\r' && index + 1 < text.length && text[index + 1] == '\\n') index++
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

    private fun parseExcel(input: InputStream): ImportPreview {
        val workbook = WorkbookFactory.create(input)
        try {
            val sheet = (0 until workbook.numberOfSheets)
                .asSequence()
                .map { workbook.getSheetAt(it) }
                .firstOrNull { it.physicalNumberOfRows > 0 }
                ?: return ImportPreview(emptyList(), emptyList())

            val formatter = DataFormatter(Locale.ROOT)
            val firstRow = sheet.firstRowNum
            val lastRow = sheet.lastRowNum
            var width = 0
            for (rowIndex in firstRow..lastRow) {
                val row = sheet.getRow(rowIndex) ?: continue
                width = maxOf(width, row.lastCellNum.toInt().coerceAtLeast(0))
            }
            if (width == 0) return ImportPreview(emptyList(), emptyList())

            val records = (firstRow..lastRow).mapNotNull { rowIndex ->
                val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
                (0 until width).map { column ->
                    formatter.formatCellValue(row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim()
                }.takeIf { values -> values.any(String::isNotBlank) }
            }
            if (records.isEmpty()) return ImportPreview(emptyList(), emptyList())
            return ImportPreview(records.first(), records.drop(1))
        } finally {
            workbook.close()
        }
    }

    private fun parseVcf(input: InputStream): ImportPreview {
        val lines = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readLines() }
        val unfolded = mutableListOf<String>()
        lines.forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + line.drop(1)
            } else {
                unfolded += line
            }
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
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    name = ""
                    phones.clear()
                }
                line.equals("END:VCARD", ignoreCase = true) -> flushCard()
                line.startsWith("FN", ignoreCase = true) && line.substringBefore(':').contains("FN", true) -> {
                    name = decodeVCardValue(line.substringAfter(':', "").trim())
                }
                line.startsWith("N:", ignoreCase = true) && name.isBlank() -> {
                    val parts = line.substringAfter(':').split(';').asReversed()
                    name = decodeVCardValue(parts.filter(String::isNotBlank).joinToString(" ").trim())
                }
                line.startsWith("TEL", ignoreCase = true) && line.contains(':') -> {
                    phones += decodeVCardValue(line.substringAfter(':').trim())
                }
            }
        }
        if (name.isNotBlank() || phones.isNotEmpty()) flushCard()
        return ImportPreview(listOf("الاسم", "رقم الهاتف"), rows)
    }

    private fun decodeVCardValue(value: String): String =
        value.replace("\\n", " ", ignoreCase = true)
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
}
