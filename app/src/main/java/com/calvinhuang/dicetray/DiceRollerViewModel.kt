package com.calvinhuang.dicetray

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiceRollerViewModel(
    private val settingsRepository: DiceSettingsRepository,
    private val application: Application,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiceUiState())
    val uiState: StateFlow<DiceUiState> = _uiState.asStateFlow()
    private val pendingSettingsWrites = Channel<DiceSettings>(Channel.CONFLATED)
    private var pendingSettings: DiceSettings? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val pending = pendingSettings
                if (pending == null || pending == settings) {
                    if (pending == settings) {
                        pendingSettings = null
                    }
                    _uiState.update { current ->
                        current.copy(settings = settings)
                    }
                }
            }
        }
        viewModelScope.launch {
            for (settings in pendingSettingsWrites) {
                settingsRepository.save(settings)
            }
        }
    }

    fun roll() {
        val current = _uiState.value
        val settings = current.settings
        val roll = DiceRoll(
            type = settings.type,
            results = DiceRules.roll(settings.type, settings.count),
        )
        _uiState.update {
            it.copy(
                lastRoll = roll,
                isRolling = true,
                animationRequestId = it.animationRequestId + 1L,
                rendererError = false,
            )
        }
        vibrateIfEnabled(settings.vibrationEnabled)
    }

    fun clear() {
        _uiState.update {
            it.copy(
                lastRoll = null,
                isRolling = false,
                clearRequestId = it.clearRequestId + 1L,
                rendererError = false,
            )
        }
    }

    fun onRendererReady() {
        _uiState.update { it.copy(rendererReady = true, rendererError = false) }
    }

    fun onRendererError() {
        _uiState.update { it.copy(rendererError = true, rendererReady = false) }
    }

    fun retryRenderer() {
        _uiState.update {
            it.copy(
                rendererReady = false,
                rendererError = false,
                rendererReloadRequestId = it.rendererReloadRequestId + 1L,
            )
        }
    }

    fun onAnimationFinished(requestId: Long) {
        if (requestId == _uiState.value.animationRequestId) {
            _uiState.update { it.copy(isRolling = false) }
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun setCount(count: Int) {
        updateSettings { it.copy(count = DiceRules.sanitizeCount(count)) }
    }

    fun setType(type: DiceType) {
        updateSettings { it.copy(type = type) }
    }

    fun setColor(color: DiceColor) {
        updateSettings { it.copy(color = color) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        updateSettings { it.copy(soundEnabled = enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        updateSettings { it.copy(vibrationEnabled = enabled) }
    }

    fun setMotionTrailsEnabled(enabled: Boolean) {
        updateSettings { it.copy(motionTrailsEnabled = enabled) }
    }

    fun setPhoneTiltEnabled(enabled: Boolean) {
        updateSettings { it.copy(phoneTiltEnabled = enabled) }
    }

    fun setMotionSensitivity(value: Float) {
        updateSettings { it.copy(motionSensitivity = DiceRules.sanitizeMotionSensitivity(value)) }
    }

    fun setResultChipEnabled(enabled: Boolean) {
        updateSettings { it.copy(resultChipEnabled = enabled) }
    }

    private fun updateSettings(transform: (DiceSettings) -> DiceSettings) {
        val current = _uiState.value.settings
        val next = transform(current).sanitized()
        if (next == current) return

        pendingSettings = next
        _uiState.update { it.copy(settings = next) }
        pendingSettingsWrites.trySend(next)
    }

    override fun onCleared() {
        pendingSettingsWrites.close()
        super.onCleared()
    }

    private fun vibrateIfEnabled(enabled: Boolean) {
        if (!enabled) return

        val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)
            ?: return
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                65L,
                VibrationEffect.DEFAULT_AMPLITUDE,
            ),
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiceRollerViewModel(
                settingsRepository = DiceSettingsRepository(application),
                application = application,
            ) as T
        }
    }
}
