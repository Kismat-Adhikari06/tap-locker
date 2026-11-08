package com.taplock

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "taplock")

data class LockState(
    val cardUid: String? = null,
    val locked: Boolean = false,
    val lockUntilMillis: Long? = null,
    val durationMinutes: Int = 30,
    val selected: Set<String> = emptySet(),
    val events: List<PlannerEvent> = emptyList()
) {
    val isLocked: Boolean get() = locked && (lockUntilMillis == null || lockUntilMillis > System.currentTimeMillis())
}

data class PlannerEvent(
    val id: String,
    val dateEpochDay: Long,
    val minuteOfDay: Int,
    val title: String,
    val note: String
)

class LockStore(private val context: Context) {
    private object Keys {
        val card = stringPreferencesKey("card_uid")
        val locked = booleanPreferencesKey("locked")
        val lockUntil = longPreferencesKey("lock_until")
        val duration = androidx.datastore.preferences.core.intPreferencesKey("duration_minutes")
        val selected = stringSetPreferencesKey("selected_apps")
        val events = stringPreferencesKey("planner_events")
    }

    val state = context.dataStore.data.map { p ->
        LockState(
            p[Keys.card], p[Keys.locked] ?: false, p[Keys.lockUntil], p[Keys.duration] ?: 30,
            p[Keys.selected] ?: LockState().selected, decodeEvents(p[Keys.events])
        )
    }

    suspend fun register(uid: String) = context.dataStore.edit { it[Keys.card] = uid; it[Keys.locked] = false; it.remove(Keys.lockUntil) }
    suspend fun clearCard() = context.dataStore.edit { it.remove(Keys.card); it[Keys.locked] = false; it.remove(Keys.lockUntil) }
    suspend fun toggle(durationMinutes: Int?) = context.dataStore.edit { prefs ->
        val until = prefs[Keys.lockUntil]
        val currentlyLocked = (prefs[Keys.locked] ?: false) && (until == null || until > System.currentTimeMillis())
        if (currentlyLocked) {
            prefs[Keys.locked] = false
            prefs.remove(Keys.lockUntil)
        } else {
            prefs[Keys.locked] = true
            if (durationMinutes == null) prefs.remove(Keys.lockUntil)
            else prefs[Keys.lockUntil] = System.currentTimeMillis() + durationMinutes * 60_000L
        }
    }
    suspend fun setSelected(packages: Set<String>) = context.dataStore.edit { it[Keys.selected] = packages }
    suspend fun setDuration(minutes: Int) = context.dataStore.edit { it[Keys.duration] = minutes }
    suspend fun saveEvent(event: PlannerEvent) = context.dataStore.edit { prefs ->
        val events = decodeEvents(prefs[Keys.events]).filterNot { it.id == event.id } + event
        prefs[Keys.events] = encodeEvents(events)
    }
    suspend fun deleteEvent(id: String) = context.dataStore.edit { prefs ->
        prefs[Keys.events] = encodeEvents(decodeEvents(prefs[Keys.events]).filterNot { it.id == id })
    }

    private fun decodeEvents(raw: String?): List<PlannerEvent> = runCatching {
        val array = JSONArray(raw ?: "[]")
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            PlannerEvent(
                id = item.getString("id"),
                dateEpochDay = item.getLong("date"),
                minuteOfDay = item.getInt("time"),
                title = item.getString("title"),
                note = item.optString("note")
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeEvents(events: List<PlannerEvent>): String = JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("id", event.id)
                put("date", event.dateEpochDay)
                put("time", event.minuteOfDay)
                put("title", event.title)
                put("note", event.note)
            })
        }
    }.toString()
}
