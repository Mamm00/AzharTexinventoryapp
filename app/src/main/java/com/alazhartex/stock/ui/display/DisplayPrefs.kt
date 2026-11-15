package com.alazhartex.stock.ui.display

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings → Display preferences (v1.8.0) — persisted in DataStore (Preferences),
 * never in Room and never in the exportable database, mirroring the dashboard overrides.
 */
private val Context.displayPrefsDataStore by preferencesDataStore(name = "display_prefs")

class DisplayPrefs(private val context: Context) {

    private object Keys {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val WHATS_NEW_DISMISSED = stringPreferencesKey("whats_new_dismissed_version")
    }

    val reduceMotion: Flow<Boolean> =
        context.displayPrefsDataStore.data.map { prefs: Preferences -> prefs[Keys.REDUCE_MOTION] ?: false }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.displayPrefsDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    val dismissedWhatsNewVersion: Flow<String?> =
        context.displayPrefsDataStore.data.map { prefs: Preferences -> prefs[Keys.WHATS_NEW_DISMISSED] }

    suspend fun setWhatsNewDismissedVersion(version: String) {
        context.displayPrefsDataStore.edit { it[Keys.WHATS_NEW_DISMISSED] = version }
    }
}
