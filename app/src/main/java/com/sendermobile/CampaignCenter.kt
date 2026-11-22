package com.sendermobile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    val templates = remember { mutableStateListOf<String>().apply { addAll(store.loadTemplates()) } }
    val history = remember { mutableStateListOf<SavedCampaign>().apply { addAll(store.loadCampaigns()) } }
    var activeCampaign by remember {
        mutableStateOf(history.firstOrNull { it.status == "قيد التنفيذ" && it.nextIndex < it.recipientIds.size })
    }
    var status by remember { mutableStateOf("اختر العملاء واكتب الرسالة لبدء الحملة.") }

    fun persist(campaign: SavedCampaign) {
        store.saveCampaign(campaign)
        val oldIndex = history.indexOfFirst { it.id == campaign.id }
        if (oldIndex >= 0) history[oldIndex] = campaign else history.add(0, campaign)
        activeCampaign = campaign
    }

    fun openCustomer(customer: Customer, messageText: String) {
        val phone = customer.normalizedPhone.filter { it.isDigit() }
        if (phone.isBlank()) {
            status = "رقم " + customer.name + " غير صالح لفتح واتساب."
            return
        }
        val personalized = messageText.replace("{name}", customer.name, ignoreCase = true)
        val url = "https://wa.me/" + phone + "?text=" + Uri.encode(personalized)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            status = "تم فتح محادثة " + customer.name + ". راجع الرسالة واضغط إرسال داخل واتساب."
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب أو المتصفح.", Toast.LENGTH_LONG).show()
            status = "تعذر فتح المحادثة. تأكد من وجود واتساب أو متصفح."
        }
    }

    fun resumeCampaign(campaign: SavedCampaign) {
        campaignName = campaign.name
        message = campaign.message
        activeCampaign = campaign
        val recipient = customers.firstOrNull { it.id == campaign.recipientIds.getOrNull(campaign.nextIndex) }
        if (recipient != null) {
            openCustomer(recipient, campaign.message)
        } else {
            status = "تعذر العثور على العميل التالي. ربما تم حذف جهة الاتصال."
        }
    }

    val activeRecipients = activeCampaign?.recipientIds?.mapNotNull { id ->
        customers.firstOrNull { it.id == id }
    }.orEmpty()
    val currentRecipient = activeCampaign?.let { campaign ->
        customers.firstOrNull { it.id == campaign.recipientIds.getOrNull(campaign.nextIndex) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
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
            minLines = 2,
            label = { Text("نص الرسالة") },
            supportingText = { Text("اكتب {name} لإدراج اسم العميل تلقائيًا.") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val template = message.trim()
                    if (template.isNotBlank() && template !in templates) {
                        store.saveTemplate(template)
                        templates.add(template)
                        status = "تم حفظ قالب الرسالة على الجهاز."
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
            Text("قوالب محفوظة", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.weight(0.65f, fill = false)) {
                items(templates) { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(template, modifier = Modifier.weight(1f), maxLines = 2)
                        OutlinedButton(onClick = { message = template }) { Text("استخدام") }
                    }
                }
            }
        }
        Text("المستلمون: " + selectedIds.size + " من " + customers.size, style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(customers, key = { it.id }) { customer ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

        if (activeCampaign != null) {
            val campaign = activeCampaign!!
            Text(
                "الحملة الحالية: " + campaign.name + " — " +
                    campaign.nextIndex + " من " + campaign.recipientIds.size,
                style = MaterialTheme.typography.titleSmall
            )
            if (currentRecipient != null) {
                Text("العميل التالي: " + currentRecipient.name + " (" + currentRecipient.normalizedPhone + ")")
            } else {
                Text("اكتملت قائمة العملاء أو لم يعد العميل التالي موجودًا.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val recipient = currentRecipient
                        if (recipient != null) openCustomer(recipient, campaign.message)
                        else status = "لا يوجد عميل تالٍ لفتحه."
                    },
                    enabled = currentRecipient != null,
                    modifier = Modifier.weight(1f)
                ) { Text("فتح المحادثة") }
                OutlinedButton(
                    onClick = {
                        val next = campaign.nextIndex + 1
                        val updated = campaign.copy(
                            nextIndex = next,
                            status = if (next >= campaign.recipientIds.size) "مكتملة" else "قيد التنفيذ"
                        )
                        persist(updated)
                        if (next >= campaign.recipientIds.size) {
                            activeCampaign = null
                            status = "اكتملت قائمة الحملة. تم حفظها في السجل."
                        } else {
                            val nextCustomer = customers.firstOrNull { it.id == updated.recipientIds[next] }
                            if (nextCustomer != null) openCustomer(nextCustomer, updated.message)
                            else status = "العميل التالي غير موجود في قائمة العملاء."
                        }
                    },
                    enabled = currentRecipient != null
                ) { Text("تم الإرسال / التالي") }
            }
            OutlinedButton(
                onClick = {
                    val paused = campaign.copy(status = "متوقفة")
                    persist(paused)
                    activeCampaign = null
                    status = "تم حفظ الحملة كمتوقفة ويمكن استكمالها من السجل."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إيقاف مؤقت وحفظ التقدم") }
        } else {
            Button(
                onClick = {
                    val recipients = customers.filter { it.id in selectedIds }
                    if (recipients.isEmpty()) {
                        status = "حدد عميلًا واحدًا على الأقل."
                    } else if (message.isBlank()) {
                        status = "اكتب نص الرسالة أولًا."
                    } else {
                        val campaign = SavedCampaign(
                            name = campaignName.ifBlank { "حملة جديدة" },
                            message = message,
                            recipientIds = recipients.map { it.id }
                        )
                        persist(campaign)
                        openCustomer(recipients.first(), campaign.message)
                        status = "بدأت الحملة وحُفظت على الجهاز. الإرسال النهائي يتم بموافقتك داخل واتساب."
                    }
                },
                enabled = customers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("بدء الحملة وحفظها") }
        }

        Text("سجل الحملات", style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty()) {
            Text("لا توجد حملات محفوظة حتى الآن.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(modifier = Modifier.weight(0.75f, fill = false)) {
                items(history.toList(), key = { it.id }) { campaign ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(campaign.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                campaign.status + " — " + campaign.nextIndex + " / " + campaign.recipientIds.size,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { resumeCampaign(campaign) },
                                    enabled = campaign.nextIndex < campaign.recipientIds.size
                                ) { Text("استكمال") }
                                OutlinedButton(
                                    onClick = {
                                        store.deleteCampaign(campaign.id)
                                        history.removeAll { it.id == campaign.id }
                                        if (activeCampaign?.id == campaign.id) activeCampaign = null
                                        status = "تم حذف سجل الحملة."
                                    }
                                ) { Text("حذف السجل") }
                            }
                        }
                    }
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            "التطبيق يجهز المحادثة فقط ولا يرسل تلقائيًا أو في الخلفية. بعد الإرسال داخل واتساب ارجع واضغط «تم الإرسال / التالي».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
