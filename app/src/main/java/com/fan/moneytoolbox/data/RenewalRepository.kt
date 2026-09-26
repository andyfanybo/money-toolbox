package com.fan.moneytoolbox.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.renewalDataStore by preferencesDataStore(name = "renewal_items")

class RenewalRepository(private val context: Context) {
    private val itemsKey = stringPreferencesKey("items")

    val itemsFlow: Flow<List<RenewalItem>> = context.renewalDataStore.data.map { prefs ->
        decode(prefs[itemsKey])
    }

    fun itemsBlocking(): List<RenewalItem> = kotlinx.coroutines.runBlocking { itemsFlow.first() }

    suspend fun upsert(item: RenewalItem): RenewalItem? {
        var previous: RenewalItem? = null
        context.renewalDataStore.edit { prefs ->
            val items = decode(prefs[itemsKey]).toMutableList()
            previous = items.firstOrNull { it.id == item.id }
            items.removeAll { it.id == item.id }
            items += item
            prefs[itemsKey] = encode(items)
        }
        return previous
    }

    suspend fun delete(id: String): RenewalItem? {
        var deleted: RenewalItem? = null
        context.renewalDataStore.edit { prefs ->
            val items = decode(prefs[itemsKey])
            deleted = items.firstOrNull { it.id == id }
            prefs[itemsKey] = encode(items.filterNot { it.id == id })
        }
        return deleted
    }

    suspend fun markPaid(id: String, today: LocalDate): Pair<RenewalItem, RenewalItem>? {
        var change: Pair<RenewalItem, RenewalItem>? = null
        context.renewalDataStore.edit { prefs ->
            val items = decode(prefs[itemsKey]).map { item ->
                if (item.id == id && !item.completed) {
                    RenewalMath.advanceAfterPaid(item, today).also { change = item to it }
                } else item
            }
            prefs[itemsKey] = encode(items)
        }
        return change
    }

    private fun decode(raw: String?): List<RenewalItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                RenewalItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    category = RenewalCategory.valueOf(obj.getString("category")),
                    amountCents = if (obj.isNull("amountCents")) null else obj.getLong("amountCents"),
                    firstDueEpochDay = obj.getLong("firstDueEpochDay"),
                    cycle = RenewalCycle.valueOf(obj.getString("cycle")),
                    periodIndex = obj.optInt("periodIndex", 0),
                    remindDaysBefore = obj.optInt("remindDaysBefore", 3),
                    completed = obj.optBoolean("completed", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(items: List<RenewalItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category.name)
                put("amountCents", item.amountCents ?: JSONObject.NULL)
                put("firstDueEpochDay", item.firstDueEpochDay)
                put("cycle", item.cycle.name)
                put("periodIndex", item.periodIndex)
                put("remindDaysBefore", item.remindDaysBefore)
                put("completed", item.completed)
            })
        }
    }.toString()
}
