package com.alazhartex.stock.overrides

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manual Dashboard overrides, persisted on this device in DataStore (Preferences) — NOT in
 * Room, NOT in SharedPreferences, by product decision (see Settings → Dashboard Settings).
 *
 * Semantics:
 *  - [DashboardOverrideValues.enabled] gates everything: when false the Dashboard renders
 *    live SQL sums from the database, when true it renders these manual values.
 *  - Every numeric field is independently optional; a blank field falls back to its live
 *    SQL value even in override mode, so a partially-filled form never yields "0" KPIs.
 *  - Utilisation is stored as a percentage (e.g. 42.5 means 42.5%), not a fraction.
 */
data class DashboardOverrideValues(
    val enabled: Boolean,
    val totalRolls: Long?,
    val activeRolls: Long?,
    val inactiveRolls: Long?,
    val capacityRolls: Long?,
    val utilisationPercent: Double?,
) {
    companion object {
        val NONE = DashboardOverrideValues(
            enabled = false,
            totalRolls = null,
            activeRolls = null,
            inactiveRolls = null,
            capacityRolls = null,
            utilisationPercent = null,
        )
    }
}

private val Context.dashboardOverridesDataStore by preferencesDataStore(name = "dashboard_overrides")

class DashboardOverrideStore(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val TOTAL_ROLLS = longPreferencesKey("total_rolls")
        val ACTIVE_ROLLS = longPreferencesKey("active_rolls")
        val INACTIVE_ROLLS = longPreferencesKey("inactive_rolls")
        val CAPACITY_ROLLS = longPreferencesKey("capacity_rolls")
        val UTILISATION_PERCENT = doublePreferencesKey("utilisation_percent")
    }

    /** Hot, conflated stream of the current overrides — first emission is cached by DataStore. */
    val overrides: Flow<DashboardOverrideValues> =
        context.dashboardOverridesDataStore.data.map { prefs: Preferences ->
            DashboardOverrideValues(
                enabled = prefs[Keys.ENABLED] ?: false,
                totalRolls = prefs[Keys.TOTAL_ROLLS],
                activeRolls = prefs[Keys.ACTIVE_ROLLS],
                inactiveRolls = prefs[Keys.INACTIVE_ROLLS],
                capacityRolls = prefs[Keys.CAPACITY_ROLLS],
                utilisationPercent = prefs[Keys.UTILISATION_PERCENT],
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.dashboardOverridesDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    /** Persist all manual values atomically. Pass null to leave a field on its live SQL value. */
    suspend fun setValues(
        totalRolls: Long?,
        activeRolls: Long?,
        inactiveRolls: Long?,
        capacityRolls: Long?,
        utilisationPercent: Double?,
    ) {
        context.dashboardOverridesDataStore.edit { prefs ->
            putOrRemove(prefs, Keys.TOTAL_ROLLS, totalRolls)
            putOrRemove(prefs, Keys.ACTIVE_ROLLS, activeRolls)
            putOrRemove(prefs, Keys.INACTIVE_ROLLS, inactiveRolls)
            putOrRemove(prefs, Keys.CAPACITY_ROLLS, capacityRolls)
            if (utilisationPercent == null) {
                prefs.remove(Keys.UTILISATION_PERCENT)
            } else {
                prefs[Keys.UTILISATION_PERCENT] = utilisationPercent
            }
        }
    }

    /** Clear every manual value and disable overrides (back to pure SQL totals). */
    suspend fun clearAll() {
        context.dashboardOverridesDataStore.edit { it.clear() }
    }

    private fun putOrRemove(prefs: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<Long>, value: Long?) {
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = value
        }
    }
}
