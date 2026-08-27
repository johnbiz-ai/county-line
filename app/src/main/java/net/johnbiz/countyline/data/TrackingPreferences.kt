package net.johnbiz.countyline.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CrossingState
import net.johnbiz.countyline.core.UsStates

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "county_line_prefs")

/**
 * Persisted tracking state. Survives process death and reboot so the
 * [net.johnbiz.countyline.core.CrossingDetector] can pick up exactly where it
 * left off.
 */
class TrackingPreferences(private val context: Context) {

    private object Keys {
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val CURRENT_FIPS = stringPreferencesKey("current_fips")
        val CURRENT_NAME = stringPreferencesKey("current_name")
        val CURRENT_STATE_FIPS = stringPreferencesKey("current_state_fips")
        val CANDIDATE_FIPS = stringPreferencesKey("candidate_fips")
        val CANDIDATE_NAME = stringPreferencesKey("candidate_name")
        val CANDIDATE_STATE_FIPS = stringPreferencesKey("candidate_state_fips")
        val CANDIDATE_STREAK = intPreferencesKey("candidate_streak")
    }

    val trackingEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.TRACKING_ENABLED] ?: false }

    /** The last confirmed county, for display on the status screen. */
    val currentCounty: Flow<County?> = context.dataStore.data.map { prefs ->
        prefs.readCounty(Keys.CURRENT_FIPS, Keys.CURRENT_NAME, Keys.CURRENT_STATE_FIPS)
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKING_ENABLED] = enabled }
    }

    suspend fun isTrackingEnabled(): Boolean = trackingEnabled.first()

    suspend fun loadCrossingState(): CrossingState {
        val prefs = context.dataStore.data.first()
        return CrossingState(
            current = prefs.readCounty(Keys.CURRENT_FIPS, Keys.CURRENT_NAME, Keys.CURRENT_STATE_FIPS),
            candidate = prefs.readCounty(Keys.CANDIDATE_FIPS, Keys.CANDIDATE_NAME, Keys.CANDIDATE_STATE_FIPS),
            candidateStreak = prefs[Keys.CANDIDATE_STREAK] ?: 0,
        )
    }

    suspend fun saveCrossingState(state: CrossingState) {
        context.dataStore.edit { prefs ->
            prefs.writeCounty(state.current, Keys.CURRENT_FIPS, Keys.CURRENT_NAME, Keys.CURRENT_STATE_FIPS)
            prefs.writeCounty(state.candidate, Keys.CANDIDATE_FIPS, Keys.CANDIDATE_NAME, Keys.CANDIDATE_STATE_FIPS)
            prefs[Keys.CANDIDATE_STREAK] = state.candidateStreak
        }
    }

    private fun Preferences.readCounty(
        fipsKey: Preferences.Key<String>,
        nameKey: Preferences.Key<String>,
        stateFipsKey: Preferences.Key<String>,
    ): County? {
        val fips = this[fipsKey] ?: return null
        val name = this[nameKey] ?: return null
        val stateFips = this[stateFipsKey] ?: return null
        return County(
            fips = fips,
            name = name,
            stateFips = stateFips,
            stateName = UsStates.name(stateFips),
            stateAbbr = UsStates.abbr(stateFips),
        )
    }

    private fun MutablePreferences.writeCounty(
        county: County?,
        fipsKey: Preferences.Key<String>,
        nameKey: Preferences.Key<String>,
        stateFipsKey: Preferences.Key<String>,
    ) {
        if (county == null) {
            remove(fipsKey); remove(nameKey); remove(stateFipsKey)
        } else {
            this[fipsKey] = county.fips
            this[nameKey] = county.name
            this[stateFipsKey] = county.stateFips
        }
    }
}
