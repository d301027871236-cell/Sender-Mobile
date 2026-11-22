package com.sendermobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedCampaign(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val message: String,
    val recipientIds: List<Long>,
    val nextIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "قيد التنفيذ",
    val attachmentUris: List<String> = emptyList(),
    val sentCount: Int = 0,
    val failedCount: Int = 0
)

class CampaignStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sender_mobile_campaigns", Context.MODE_PRIVATE)

    fun loadCampaigns(): List<SavedCampaign> {
        val source = preferences.getString(KEY_CAMPAIGNS, "[]") ?: "[]"
        return try {
            val array = JSONArray(source)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val ids = item.optJSONArray("recipientIds") ?: JSONArray()
                val attachments = item.optJSONArray("attachmentUris") ?: JSONArray()
                val next = item.optInt("nextIndex", 0)
                SavedCampaign(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    message = item.optString("message"),
                    recipientIds = (0 until ids.length()).map { ids.optLong(it) },
                    nextIndex = next,
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    status = item.optString("status", "قيد التنفيذ"),
                    attachmentUris = (0 until attachments.length()).map { attachments.optString(it) },
                    sentCount = item.optInt("sentCount", next),
                    failedCount = item.optInt("failedCount", 0)
                )
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCampaign(campaign: SavedCampaign) {
        writeCampaigns(loadCampaigns().filterNot { it.id == campaign.id } + campaign)
    }

    fun deleteCampaign(id: String) {
        writeCampaigns(loadCampaigns().filterNot { it.id == id })
    }

    private fun writeCampaigns(campaigns: List<SavedCampaign>) {
        val array = JSONArray()
        campaigns.sortedByDescending { it.createdAt }.forEach { saved ->
            val ids = JSONArray().also { values -> saved.recipientIds.forEach(values::put) }
            val attachments = JSONArray().also { values -> saved.attachmentUris.forEach(values::put) }
            array.put(JSONObject()
                .put("id", saved.id).put("name", saved.name).put("message", saved.message)
                .put("recipientIds", ids).put("nextIndex", saved.nextIndex)
                .put("createdAt", saved.createdAt).put("status", saved.status)
                .put("attachmentUris", attachments).put("sentCount", saved.sentCount)
                .put("failedCount", saved.failedCount))
        }
        preferences.edit().putString(KEY_CAMPAIGNS, array.toString()).apply()
    }

    fun loadTemplates(): List<String> {
        val source = preferences.getString(KEY_TEMPLATES, "[]") ?: "[]"
        return try {
            val array = JSONArray(source)
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    fun saveTemplate(template: String) {
        val templates = (loadTemplates() + template.trim()).filter { it.isNotBlank() }.distinct()
        val array = JSONArray()
        templates.forEach(array::put)
        preferences.edit().putString(KEY_TEMPLATES, array.toString()).apply()
    }

    companion object {
        private const val KEY_CAMPAIGNS = "campaign_history"
        private const val KEY_TEMPLATES = "message_templates"
    }
}
