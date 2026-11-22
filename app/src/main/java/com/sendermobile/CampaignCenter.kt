package com.sendermobile

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.sendermobile.data.CampaignStore
import com.sendermobile.data.Customer
import com.sendermobile.data.SavedCampaign

@Composable
internal fun CampaignCenter(customers: List<Customer>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { CampaignStore(context) }
    var campaignName by remember { mutableStateOf("حملة جديدة") }
    var message by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val selectedAttachments = remember { mutableStateListOf<String>() }
    val templates = remember { mutableStateListOf<String>().apply { addAll(store.loadTemplates()) } }
    val history = remember { mutableStateListOf<SavedCampaign>().apply { addAll(store.loadCampaigns()) } }
    var activeCampaign by remember {
        mutableStateOf(history.firstOrNull { it.status == "قيد التنفيذ" && it.nextIndex < it.recipientIds.size })
    }
    var status by remember { mutableStateOf("اختر العملاء واكتب الرسالة لبدء الحملة.") }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            if (uri.toString() !in selectedAttachments) selectedAttachments.add(uri.toString())
        }
        if (uris.isNotEmpty()) status = "تمت إضافة " + uris.size + " مرفقًا إلى مسودة الحملة."
    }

    fun persist(campaign: SavedCampaign) {
        store.saveCampaign(campaign)
        val oldIndex = history.indexOfFirst { it.id == campaign.id }
        if (oldIndex >= 0) history[oldIndex] = campaign else history.add(0, campaign)
        activeCampaign = campaign
    }

    fun personalizedText(customer: Customer, source: String): String =
        source.replace("{name}", customer.name, ignoreCase = true)

    fun openCustomer(customer: Customer, messageText: String) {
        val phone = customer.normalizedPhone.filter { it.isDigit() }
        if (phone.isBlank()) {
            status = "رقم " + customer.name + " غير صالح لفتح واتساب."
            return
        }
        val url = "https://wa.me/" + phone + "?text=" + Uri.encode(personalizedText(customer, messageText))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            status = "تم فتح محادثة " + customer.name + ". راجع الرسالة واضغط إرسال داخل واتساب."
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب أو المتصفح.", Toast.LENGTH_LONG).show()
            status = "تعذر فتح المحادثة. تأكد من وجود واتساب أو متصفح."
        }
    }

    fun shareAttachments(customer: Customer, campaign: SavedCampaign) {
        val uris = campaign.attachmentUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        if (uris.isEmpty()) { status = "لا توجد مرفقات في هذه الحملة."; return }
        try {
            val types = uris.mapNotNull { context.contentResolver.getType(it) }
            val mime = if (types.isNotEmpty() && types.all { it.startsWith("image/") }) "image/*" else "*/*"
            val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND) else Intent(Intent.ACTION_SEND_MULTIPLE)
            intent.type = mime
            intent.putExtra(Intent.EXTRA_TEXT, personalizedText(customer, campaign.message))
            if (uris.size == 1) {
                intent.putExtra(Intent.EXTRA_STREAM, uris.first())
                intent.clipData = ClipData.newUri(context.contentResolver, "Sender Mobile attachment", uris.first())
            } else {
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                val clip = ClipData.newUri(context.contentResolver, "Sender Mobile attachments", uris.first())
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                intent.clipData = clip
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "مشاركة مرفقات " + customer.name))
            status = "اختر واتساب ثم اختر محادثة العميل " + customer.name + " يدويًا. راجع النص والمرفقات قبل الإرسال."
        } catch (_: Exception) {
            status = "تعذرت مشاركة المرفقات. تحقق من صلاحية الوصول إلى الملفات."
        }
    }

    fun resumeCampaign(campaign: SavedCampaign) {
        campaignName = campaign.name
        message = campaign.message
        selectedAttachments.clear()
        selectedAttachments.addAll(campaign.attachmentUris)
        activeCampaign = campaign
        val recipient = customers.firstOrNull { it.id == campaign.recipientIds.getOrNull(campaign.nextIndex) }
        if (recipient != null) openCustomer(recipient, campaign.message)
        else status = "تعذر العثور على العميل التالي. ربما تم حذف جهة الاتصال."
    }

    val currentRecipient = activeCampaign?.let { campaign ->
        customers.firstOrNull { it.id == campaign.recipientIds.getOrNull(campaign.nextIndex) }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("مركز الحملات والرسائل", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = campaignName, onValueChange = { campaignName = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم الحملة") })
        OutlinedTextField(value = message, onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("نص الرسالة") },
            supportingText = { Text("اكتب {name} لإدراج اسم العميل تلقائيًا.") })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val template = message.trim()
                if (template.isNotBlank() && template !in templates) {
                    store.saveTemplate(template); templates.add(template); status = "تم حفظ قالب الرسالة على الجهاز."
                }
            }, enabled = message.isNotBlank()) { Text("حفظ كقالب") }
            OutlinedButton(onClick = { selectedIds.clear(); selectedIds.addAll(customers.map { it.id }) },
                enabled = customers.isNotEmpty()) { Text("تحديد الكل") }
            OutlinedButton(onClick = { selectedIds.clear() }) { Text("إلغاء التحديد") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) { Text("إضافة صور / ملفات") }
            Text("المرفقات: " + selectedAttachments.size, style = MaterialTheme.typography.bodySmall)
        }
        selectedAttachments.toList().forEachIndexed { index, uri ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("مرفق " + (index + 1) + ": " + (Uri.parse(uri).lastPathSegment ?: "ملف"),
                    modifier = Modifier.weight(1f), maxLines = 1)
                OutlinedButton(onClick = { selectedAttachments.remove(uri) }) { Text("إزالة") }
            }
        }

        if (templates.isNotEmpty()) {
            Text("قوالب محفوظة", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.weight(0.55f, fill = false)) {
                items(templates) { template ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(template, modifier = Modifier.weight(1f), maxLines = 2)
                        OutlinedButton(onClick = { message = template }) { Text("استخدام") }
                    }
                }
            }
        }

        Text("المستلمون: " + selectedIds.size + " من " + customers.size, style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(customers, key = { it.id }) { customer ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = customer.id in selectedIds, onCheckedChange = { checked ->
                        if (checked && customer.id !in selectedIds) selectedIds.add(customer.id)
                        if (!checked) selectedIds.remove(customer.id)
                    })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customer.name, style = MaterialTheme.typography.bodyMedium)
                        Text(customer.normalizedPhone, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (activeCampaign != null) {
            val campaign = activeCampaign!!
            val total = campaign.recipientIds.size
            val pending = (total - campaign.nextIndex).coerceAtLeast(0)
            Text("الحملة الحالية: " + campaign.name, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) {
                    Text("تم الإرسال", style = MaterialTheme.typography.labelSmall)
                    Text(campaign.sentCount.toString(), style = MaterialTheme.typography.titleLarge)
                } }
                Card(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) {
                    Text("متعثر", style = MaterialTheme.typography.labelSmall)
                    Text(campaign.failedCount.toString(), style = MaterialTheme.typography.titleLarge)
                } }
                Card(modifier = Modifier.weight(1f)) { Column(Modifier.padding(8.dp)) {
                    Text("متبقٍ", style = MaterialTheme.typography.labelSmall)
                    Text(pending.toString(), style = MaterialTheme.typography.titleLarge)
                } }
            }
            Text("التقدم: " + campaign.nextIndex + " / " + total)
            if (currentRecipient != null) Text("العميل التالي: " + currentRecipient.name + " (" + currentRecipient.normalizedPhone + ")")
            else Text("اكتملت قائمة العملاء أو لم يعد العميل التالي موجودًا.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { currentRecipient?.let { openCustomer(it, campaign.message) } },
                    enabled = currentRecipient != null, modifier = Modifier.weight(1f)) { Text("فتح المحادثة") }
                OutlinedButton(onClick = { currentRecipient?.let { shareAttachments(it, campaign) } },
                    enabled = currentRecipient != null && campaign.attachmentUris.isNotEmpty(),
                    modifier = Modifier.weight(1f)) { Text("مشاركة المرفقات") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val next = campaign.nextIndex + 1
                    val updated = campaign.copy(nextIndex = next, sentCount = campaign.sentCount + 1,
                        status = if (next >= total) "مكتملة" else "قيد التنفيذ")
                    persist(updated)
                    if (next >= total) { activeCampaign = null; status = "اكتملت الحملة. تم حفظ إحصائيات الإرسال." }
                    else customers.firstOrNull { it.id == updated.recipientIds[next] }?.let {
                        openCustomer(it, updated.message)
                    } ?: run { status = "العميل التالي غير موجود في قائمة العملاء." }
                }, enabled = currentRecipient != null, modifier = Modifier.weight(1f)) { Text("تم الإرسال / التالي") }
                OutlinedButton(onClick = {
                    val next = campaign.nextIndex + 1
                    val updated = campaign.copy(nextIndex = next, failedCount = campaign.failedCount + 1,
                        status = if (next >= total) "مكتملة مع تعثر" else "قيد التنفيذ")
                    persist(updated)
                    if (next >= total) { activeCampaign = null; status = "انتهت قائمة الحملة مع تسجيل حالات التعثر." }
                    else customers.firstOrNull { it.id == updated.recipientIds[next] }?.let {
                        openCustomer(it, updated.message)
                    } ?: run { status = "العميل التالي غير موجود في قائمة العملاء." }
                }, enabled = currentRecipient != null, modifier = Modifier.weight(1f)) { Text("تعثر / التالي") }
            }
            OutlinedButton(onClick = {
                persist(campaign.copy(status = "متوقفة")); activeCampaign = null
                status = "تم حفظ الحملة كمتوقفة ويمكن استكمالها من السجل."
            }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف مؤقت وحفظ التقدم") }
        } else {
            Button(onClick = {
                val recipients = customers.filter { it.id in selectedIds }
                when {
                    recipients.isEmpty() -> status = "حدد عميلًا واحدًا على الأقل."
                    message.isBlank() -> status = "اكتب نص الرسالة أولًا."
                    else -> {
                        val campaign = SavedCampaign(name = campaignName.ifBlank { "حملة جديدة" },
                            message = message, recipientIds = recipients.map { it.id },
                            attachmentUris = selectedAttachments.toList())
                        persist(campaign); openCustomer(recipients.first(), campaign.message)
                        status = "بدأت الحملة وحُفظت. أكد الإرسال يدويًا داخل واتساب."
                    }
                }
            }, enabled = customers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("بدء الحملة وحفظها") }
        }

        Text("سجل الحملات", style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty()) Text("لا توجد حملات محفوظة حتى الآن.", style = MaterialTheme.typography.bodySmall)
        else LazyColumn(modifier = Modifier.weight(0.7f, fill = false)) {
            items(history.toList(), key = { it.id }) { campaign ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(campaign.name, style = MaterialTheme.typography.titleSmall)
                        Text(campaign.status + " — " + campaign.nextIndex + " / " + campaign.recipientIds.size +
                            " | نجاح " + campaign.sentCount + " | تعثر " + campaign.failedCount +
                            " | مرفقات " + campaign.attachmentUris.size, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { resumeCampaign(campaign) },
                                enabled = campaign.nextIndex < campaign.recipientIds.size) { Text("استكمال") }
                            OutlinedButton(onClick = {
                                store.deleteCampaign(campaign.id); history.removeAll { it.id == campaign.id }
                                if (activeCampaign?.id == campaign.id) activeCampaign = null
                                status = "تم حذف سجل الحملة."
                            }) { Text("حذف السجل") }
                        }
                    }
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("الإرسال لا يتم تلقائيًا. بعد الإرسال داخل واتساب اضغط «تم الإرسال / التالي». مشاركة المرفقات تفتح شاشة المشاركة لتختار واتساب ثم محادثة العميل يدويًا.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
