package com.sendermobile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendermobile.data.Customer

@Composable
internal fun CampaignCenter(customers: List<Customer>) {
    val context = LocalContext.current
    var campaignName by remember { mutableStateOf("حملة جديدة") }
    var message by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val templates = remember { mutableStateListOf<String>() }
    var queue by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(-1) }
    var status by remember { mutableStateOf("اختر العملاء واكتب الرسالة لبدء الحملة.") }

    fun openCustomer(customer: Customer) {
        val phone = customer.normalizedPhone.filter { it.isDigit() }
        if (phone.isBlank()) {
            status = "رقم " + customer.name + " غير صالح لفتح واتساب."
            return
        }
        val personalized = message.replace("{name}", customer.name, ignoreCase = true)
        val url = "https://wa.me/" + phone + "?text=" + Uri.encode(personalized)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            status = "تم فتح محادثة " + customer.name + ". راجع الرسالة واضغط إرسال من واتساب."
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب أو المتصفح.", Toast.LENGTH_LONG).show()
            status = "تعذر فتح المحادثة. تأكد من وجود واتساب أو متصفح."
        }
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("مركز الحملات والرسائل", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = campaignName,
            onValueChange = { campaignName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("اسم الحملة") }
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("نص الرسالة") },
            supportingText = { Text("اكتب {name} لإدراج اسم العميل تلقائيًا.") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (message.isNotBlank() && templates.none { it == message.trim() }) {
                        templates.add(message.trim())
                        status = "تم حفظ نص الرسالة ضمن القوالب على هذه الشاشة."
                    }
                },
                enabled = message.isNotBlank()
            ) { Text("حفظ كقالب") }
            OutlinedButton(
                onClick = { selectedIds.clear(); selectedIds.addAll(customers.map { it.id }) },
                enabled = customers.isNotEmpty()
            ) { Text("تحديد الكل") }
            OutlinedButton(onClick = { selectedIds.clear() }) { Text("إلغاء التحديد") }
        }
        if (templates.isNotEmpty()) {
            Text("قوالب الرسائل", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.weight(0.8f, fill = false)) {
                items(templates) { template ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(template, modifier = Modifier.weight(1f), maxLines = 2)
                            OutlinedButton(onClick = { message = template }) { Text("استخدام") }
                        }
                    }
                }
            }
        }
        Text("اختر المستلمين: " + selectedIds.size + " من " + customers.size, style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(customers, key = { it.id }) { customer ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = customer.id in selectedIds,
                        onCheckedChange = { checked ->
                            if (checked && customer.id !in selectedIds) selectedIds.add(customer.id)
                            if (!checked) selectedIds.remove(customer.id)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customer.name, style = MaterialTheme.typography.bodyMedium)
                        Text(customer.normalizedPhone, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (currentIndex >= 0 && currentIndex < queue.size) {
            Text("تقدم الحملة: " + (currentIndex + 1) + " من " + queue.size + " — " + queue[currentIndex].name)
            Button(
                onClick = {
                    currentIndex++
                    if (currentIndex < queue.size) openCustomer(queue[currentIndex])
                    else status = "انتهت قائمة الحملة. تأكد من إرسال كل رسالة يدويًا داخل واتساب."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (currentIndex + 1 < queue.size) "فتح العميل التالي" else "إنهاء الحملة") }
        } else {
            Button(
                onClick = {
                    val recipients = customers.filter { it.id in selectedIds }
                    if (recipients.isEmpty()) {
                        status = "حدد عميلًا واحدًا على الأقل."
                    } else if (message.isBlank()) {
                        status = "اكتب نص الرسالة أولًا."
                    } else {
                        queue = recipients
                        currentIndex = 0
                        status = "بدأت " + campaignName.ifBlank { "الحملة" } + ". الإرسال النهائي يتم بموافقتك داخل واتساب."
                        openCustomer(queue.first())
                    }
                },
                enabled = customers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("بدء الحملة وفتح أول محادثة") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "ملاحظة: التطبيق يجهز المحادثة فقط؛ لا يرسل الرسائل تلقائيًا ولا يعمل في الخلفية. اضغط إرسال داخل واتساب، ثم ارجع واضغط العميل التالي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
