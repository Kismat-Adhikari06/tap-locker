package com.taplock

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "taplock")

data class LockState(
    val cardUid: String? = null,
    val locked: Boolean = false,
    val lockUntilMillis: Long? = null,
    val durationMinutes: Int = 30,
    val selected: Set<String> = emptySet()
) {
    val isLocked: Boolean get() = locked && (lockUntilMillis == null || lockUntilMillis > System.currentTimeMillis())
}

class LockStore(private val context: Context) {
    private object Keys {
        val card = stringPreferencesKey("card_uid")
        val locked = booleanPreferencesKey("locked")
        val lockUntil = longPreferencesKey("lock_until")
        val duration = androidx.datastore.preferences.core.intPreferencesKey("duration_minutes")
        val selected = stringSetPreferencesKey("selected_apps")
    }

    val state = context.dataStore.data.map { p ->
        LockState(p[Keys.card], p[Keys.locked] ?: false, p[Keys.lockUntil], p[Keys.duration] ?: 30, p[Keys.selected] ?: LockState().selected)
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
}
