package com.wakecapture.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wake_capture_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val IS_ARMED = booleanPreferencesKey("is_armed")
        val ARM_TIMESTAMP = longPreferencesKey("arm_timestamp")
        val AUTO_DISARM_DURATION_MS = longPreferencesKey("auto_disarm_duration_ms")
        val SILENCE_TIMEOUT_SECONDS = intPreferencesKey("silence_timeout_seconds")
        val MAX_RECORDING_DURATION_MINUTES = intPreferencesKey("max_recording_duration_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val isArmed: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_ARMED] ?: false }

    val armTimestamp: Flow<Long?> = context.dataStore.data.map { it[Keys.ARM_TIMESTAMP] }

    val autoDisarmDurationMs: Flow<Long> = context.dataStore.data.map {
        it[Keys.AUTO_DISARM_DURATION_MS] ?: DEFAULT_AUTO_DISARM_MS
    }

    val silenceTimeoutSeconds: Flow<Int> = context.dataStore.data.map {
        it[Keys.SILENCE_TIMEOUT_SECONDS] ?: DEFAULT_SILENCE_TIMEOUT_SECONDS
    }

    val maxRecordingDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.MAX_RECORDING_DURATION_MINUTES] ?: DEFAULT_MAX_RECORDING_MINUTES
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun arm() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ARMED] = true
            prefs[Keys.ARM_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun disarm() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ARMED] = false
            prefs.remove(Keys.ARM_TIMESTAMP)
        }
    }

    suspend fun setAutoDisarmDuration(durationMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_DISARM_DURATION_MS] = durationMs
        }
    }

    suspend fun setSilenceTimeout(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SILENCE_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun setMaxRecordingDuration(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_RECORDING_DURATION_MINUTES] = minutes
        }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun isAutoDisarmExpired(): Boolean {
        val prefs = context.dataStore.data.first()
        val armed = prefs[Keys.IS_ARMED] ?: false
        val timestamp = prefs[Keys.ARM_TIMESTAMP]
        val duration = prefs[Keys.AUTO_DISARM_DURATION_MS] ?: DEFAULT_AUTO_DISARM_MS
        return armed && timestamp != null && duration != DURATION_UNTIL_DISARM &&
                System.currentTimeMillis() > timestamp + duration
    }

    companion object {
        const val DEFAULT_SILENCE_TIMEOUT_SECONDS = 30
        const val DEFAULT_MAX_RECORDING_MINUTES = 5
        const val DEFAULT_AUTO_DISARM_MS = 8L * 60 * 60 * 1000 // 8 hours
        const val DURATION_UNTIL_DISARM = -1L

        val SILENCE_TIMEOUT_OPTIONS = listOf(10, 15, 30, 45, 60, 90, 120)
        val MAX_RECORDING_OPTIONS = listOf(1, 2, 5, 10, 15, 30)
        val AUTO_DISARM_OPTIONS = mapOf(
            "8 hours" to 8L * 60 * 60 * 1000,
            "12 hours" to 12L * 60 * 60 * 1000,
            "Until I disarm" to DURATION_UNTIL_DISARM
        )
    }
}
