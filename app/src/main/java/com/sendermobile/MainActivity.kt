package com.sendermobile

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sendermobile.data.Customer
import com.sendermobile.data.CustomerDatabase
import com.sendermobile.importing.CustomerFileImporter
import com.sendermobile.importing.CustomerImportMapper
import com.sendermobile.importing.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal fun phoneSearchDigits(value: String): String = buildString {
    value.forEach { character ->
        val digit = Character.digit(character, 10)
        if (digit >= 0) append(('0'.code + digit).toChar())
    }
}

internal fun matchesPhonePrefix(
    originalPhone: String,
    normalizedPhone: String,
    countryCode: String,
    queryDigits: String
): Boolean {
    if (queryDigits.isBlank()) return false

    val originalDigits = phoneSearchDigits(originalPhone)
    val normalizedDigits = phoneSearchDigits(normalizedPhone)
    val callingCode = phoneSearchDigits(countryCode)

    // Build equivalent forms so local, international, and 00-prefixed input
    // can find the same saved contact.
    val phoneForms = linkedSetOf(originalDigits, normalizedDigits)
    if (callingCode.isNotBlank() && normalizedDigits.startsWith(callingCode)) {
        val national = normalizedDigits.removePrefix(callingCode)
        phoneForms += national
        if (callingCode == "20") phoneForms += "0$national"
    }

    val queryForms = linkedSetOf(queryDigits)
    if (queryDigits.startsWith("00") && queryDigits.length > 2) {
        queryForms += queryDigits.drop(2)
    }
    if (callingCode == "20") {
        if (queryDigits.startsWith("0") && queryDigits.length > 1) {
            queryForms += queryDigits.drop(1)
        } else if (queryDigits.startsWith("20") && queryDigits.length > 2) {
            queryForms += queryDigits.drop(2)
        }
    }

    // Prefix search works while typing; contains also supports searching
    // by a middle section of a phone number (after at least 4 digits).
    return phoneForms.any { phone ->
        queryForms.any { q ->
            phone.startsWith(q) || (q.length >= 4 && phone.contains(q))
        }
    }
}

class MainActivity : ComponentActivity() {
    private val customerDao by lazy { CustomerDatabase.getInstance(applicationContext).customerDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val customers by customerDao.observeAll().collectAsState(initial = emptyList())
            SenderMobileApp(
                customers = customers,
                onReadFile = ::readImportFile,
                onImport = ::saveImportedCustomers
            )
        }
    }

    private fun readImportFile(uri: Uri, onResult: (ImportPreview?, String) -> Unit) {
        lifecycleScope.launch {
            try {
                val name = queryDisplayName(uri)
                val parsed = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        CustomerFileImporter.parse(stream, name)
                    } ?: throw IllegalStateException("تعذر فتح الملف")
                }
                onResult(parsed, name)
            } catch (error: Exception) {
                onResult(null, error.localizedMessage ?: "تعذر قراءة الملف")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: "contacts.csv"
            }
        }
        return uri.lastPathSegment ?: "contacts.csv"
    }

    private fun saveImportedCustomers(
        preview: ImportPreview,
        nameColumn: Int,
        phoneColumn: Int,
        onResult: (String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val mapped = withContext(Dispatchers.Default) {
                    CustomerImportMapper.map(preview, nameColumn, phoneColumn)
                }
                val insertResults = withContext(Dispatchers.IO) {
                    if (mapped.customers.isEmpty()) emptyList()
                    else customerDao.insertAll(mapped.customers)
                }
                val inserted = insertResults.count { it != -1L }
                val duplicates = mapped.duplicateRows + (mapped.customers.size - inserted)
                onResult("تمت إضافة $inserted عميل. المكرر: $duplicates، والأرقام غير الصالحة: ${mapped.invalidRows}.")
            } catch (error: Exception) {
                onResult("تعذر حفظ العملاء: ${error.localizedMessage ?: "خطأ غير معروف"}")
            }
        }
    }
}

@Composable
private fun SenderMobileApp(
    customers: List<Customer>,
    onReadFile: (Uri, (ImportPreview?, String) -> Unit) -> Unit,
    onImport: (ImportPreview, Int, Int, (String) -> Unit) -> Unit
) {
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var nameColumn by remember { mutableStateOf(0) }
    var phoneColumn by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf("اختر ملف CSV أو Excel أو VCF لبدء الاستيراد.") }
    var busy by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var countryFilter by remember { mutableStateOf("الكل") }
    val query = searchQuery.trim()
    val queryDigits = phoneSearchDigits(query)
    val isPhoneQuery = query.isNotBlank() && query.all {
        Character.digit(it, 10) >= 0 || it.isWhitespace() || it in "+-()."
    } && queryDigits.isNotEmpty()
    val filteredCustomers = customers.filter { customer ->
        val matchesCountry = when (countryFilter) {
            "الكل" -> true
            "مصر" -> customer.isEgyptian
            "غير مصر" -> !customer.isEgyptian
            else -> customer.countryName == countryFilter
        }
        val matchesName = query.isBlank() || customer.name.contains(query, ignoreCase = true)
        val matchesPhone = isPhoneQuery && matchesPhonePrefix(
            customer.originalPhone, customer.normalizedPhone, customer.countryCode, queryDigits
        )
        matchesCountry && (query.isBlank() || matchesName || matchesPhone)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            onReadFile(uri) { result, fileName ->
                preview = result
                selectedFile = if (result == null) "" else fileName
                nameColumn = 0
                phoneColumn = if ((result?.headers?.size ?: 0) > 1) 1 else 0
                status = if (result == null) fileName else "تمت قراءة ${result.rows.size} صفًا من $fileName. راجع الأعمدة قبل الحفظ."
                busy = false
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sender Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("إدارة العملاء", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Card(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("إجمالي العملاء", style = MaterialTheme.typography.labelMedium)
                            Text(customers.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Card(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("مصريون", style = MaterialTheme.typography.labelMedium)
                            Text(customers.count { it.isEgyptian }.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }

                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "جارٍ قراءة الملف..." else "استيراد CSV / Excel / VCF")
                }
                Text(status, style = MaterialTheme.typography.bodySmall)

                preview?.let { data ->
                    Text("معاينة وتحديد الأعمدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("الملف: $selectedFile")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("عمود الاسم", style = MaterialTheme.typography.labelMedium)
                            ColumnPicker(data.headers, nameColumn, { nameColumn = it })
                        }
                        Column(Modifier.weight(1f)) {
                            Text("عمود الهاتف", style = MaterialTheme.typography.labelMedium)
                            ColumnPicker(data.headers, phoneColumn, { phoneColumn = it })
                        }
                    }
                    Text("أول الصفوف", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(data.rows.take(12)) { index, row ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("صف ${index + 1}", style = MaterialTheme.typography.labelSmall)
                                    Text(row.joinToString("  |  ").ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (data.headers.isNotEmpty() && nameColumn != phoneColumn) {
                                onImport(data, nameColumn, phoneColumn) { status = it; preview = null }
                            } else {
                                status = "اختر عمودين مختلفين للاسم ورقم الهاتف."
                            }
                        },
                        enabled = !busy && data.rows.isNotEmpty() && data.headers.size > 1,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ العملاء في قاعدة البيانات")
                    }
                } ?: run {
                    Spacer(Modifier.height(4.dp))
                    Text("العملاء المحفوظون", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("بحث بالاسم أو رقم الهاتف") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("الكل", "مصر", "غير مصر", "الكويت", "السعودية").forEach { country ->
                            FilterChip(
                                selected = countryFilter == country,
                                onClick = {
                                    countryFilter = country
                                    if (country == "الكل") searchQuery = ""
                                },
                                label = { Text(country) }
                            )
                        }
                    }
                    Text(
                        "المعروض: ${filteredCustomers.size} من ${customers.size} | الكويت: ${customers.count { it.countryName == "الكويت" }} | السعودية: ${customers.count { it.countryName == "السعودية" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (filteredCustomers.isEmpty()) {
                        Text(
                            if (searchQuery.isBlank()) "لا يوجد عملاء في هذا التصنيف."
                            else "لا توجد نتائج مطابقة للبحث.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(filteredCustomers) { _, customer -> CustomerCard(customer) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnPicker(headers: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(headers.getOrNull(selected)?.ifBlank { "عمود ${selected + 1}" } ?: "اختر عمودًا", maxLines = 1)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        headers.forEachIndexed { index, header ->
            DropdownMenuItem(
                text = { Text(header.ifBlank { "عمود ${index + 1}" }) },
                onClick = { onSelect(index); expanded = false }
            )
        }
    }
}

@Composable
private fun CustomerCard(customer: Customer) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.SemiBold)
                Text(customer.normalizedPhone, style = MaterialTheme.typography.bodySmall)
            }
            Text(customer.countryName, style = MaterialTheme.typography.labelMedium)
        }
    }
}
