package com.shavlegi.brawlarena

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brawl_arena")

/** Local persistence: total trophies, selected brawler, UI language. */
class TrophyStore(private val context: Context) {

    companion object {
        private val KEY_TROPHIES = intPreferencesKey("trophies")
        private val KEY_BRAWLER = stringPreferencesKey("brawler_id")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
    }

    val trophies: Flow<Int> = context.dataStore.data.map { it[KEY_TROPHIES] ?: 0 }

    val brawlerId: Flow<String> =
        context.dataStore.data.map { it[KEY_BRAWLER] ?: "grom" }

    val language: Flow<String> =
        context.dataStore.data.map { it[KEY_LANGUAGE] ?: "ka" }

    suspend fun addTrophies(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_TROPHIES] ?: 0
            prefs[KEY_TROPHIES] = (current + delta).coerceAtLeast(0)
        }
    }

    suspend fun setBrawler(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BRAWLER] = id }
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = code }
    }
}
