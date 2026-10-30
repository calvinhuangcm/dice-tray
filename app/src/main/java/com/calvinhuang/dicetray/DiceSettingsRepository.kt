package com.calvinhuang.dicetray

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.diceSettingsDataStore by preferencesDataStore(name = "dice_settings")

class DiceSettingsRepository(private val context: Context) {
    private val defaultSettings = DiceSettings()

    private object Keys {
        val count = intPreferencesKey("count")
        val type = stringPreferencesKey("type")
        val color = stringPreferencesKey("color")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val trails = booleanPreferencesKey("trails")
        val tilt = booleanPreferencesKey("tilt")
        val motionSensitivity = floatPreferencesKey("motion_sensitivity")
        val resultChip = booleanPreferencesKey("result_chip")
    }

    val settings: Flow<DiceSettings> = context.diceSettingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            defaultSettings.copy(
                count = preferences[Keys.count] ?: defaultSettings.count,
                type = preferences[Keys.type]?.let(::parseDiceType) ?: defaultSettings.type,
                color = preferences[Keys.color]?.let(::parseDiceColor) ?: defaultSettings.color,
                soundEnabled = preferences[Keys.sound] ?: defaultSettings.soundEnabled,
                vibrationEnabled = preferences[Keys.vibration] ?: defaultSettings.vibrationEnabled,
                motionTrailsEnabled = preferences[Keys.trails] ?: defaultSettings.motionTrailsEnabled,
                phoneTiltEnabled = preferences[Keys.tilt] ?: defaultSettings.phoneTiltEnabled,
                motionSensitivity = preferences[Keys.motionSensitivity]
                    ?: defaultSettings.motionSensitivity,
                resultChipEnabled = preferences[Keys.resultChip] ?: defaultSettings.resultChipEnabled,
            ).sanitized()
        }

    suspend fun save(settings: DiceSettings) {
        val safeSettings = settings.sanitized()
        context.diceSettingsDataStore.edit { preferences ->
            preferences[Keys.count] = safeSettings.count
            preferences[Keys.type] = safeSettings.type.name
            preferences[Keys.color] = safeSettings.color.name
            preferences[Keys.sound] = safeSettings.soundEnabled
            preferences[Keys.vibration] = safeSettings.vibrationEnabled
            preferences[Keys.trails] = safeSettings.motionTrailsEnabled
            preferences[Keys.tilt] = safeSettings.phoneTiltEnabled
            preferences[Keys.motionSensitivity] = safeSettings.motionSensitivity
            preferences[Keys.resultChip] = safeSettings.resultChipEnabled
        }
    }

    private fun parseDiceType(value: String): DiceType =
        DiceType.entries.firstOrNull { it.name == value } ?: DiceType.D6

    private fun parseDiceColor(value: String): DiceColor =
        DiceColor.entries.firstOrNull { it.name == value } ?: DiceColor.IVORY
}
