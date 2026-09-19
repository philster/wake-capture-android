package com.wakecapture.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakecapture.app.capture.CaptureCoordinator
import com.wakecapture.app.capture.CaptureState
import com.wakecapture.app.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val captureState: CaptureState = CaptureState.DISARMED,
    val autoDisarmDurationMs: Long = PreferencesManager.DEFAULT_AUTO_DISARM_MS,
    val armTimestamp: Long? = null,
    val silenceTimeoutSeconds: Int = PreferencesManager.DEFAULT_SILENCE_TIMEOUT_SECONDS,
    val maxRecordingMinutes: Int = PreferencesManager.DEFAULT_MAX_RECORDING_MINUTES,
    val errorMessage: String? = null,
    val recordingStartTime: Long? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val coordinator: CaptureCoordinator,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        coordinator.state,
        coordinator.error,
        coordinator.recordingStartTime,
        preferencesManager.autoDisarmDurationMs,
        preferencesManager.armTimestamp,
        preferencesManager.silenceTimeoutSeconds,
        preferencesManager.maxRecordingDurationMinutes
    ) { values ->
        val state = values[0] as CaptureState
        val error = values[1] as? com.wakecapture.app.capture.CaptureError
        val recordingStart = values[2] as? Long
        @Suppress("UNCHECKED_CAST")
        val autoDisarm = values[3] as Long
        val armTs = values[4] as? Long
        val silence = values[5] as Int
        val maxRec = values[6] as Int

        HomeUiState(
            captureState = state,
            autoDisarmDurationMs = autoDisarm,
            armTimestamp = armTs,
            silenceTimeoutSeconds = silence,
            maxRecordingMinutes = maxRec,
            errorMessage = error?.message,
            recordingStartTime = recordingStart
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch { coordinator.checkAndHandleExpiry() }
    }

    fun toggleArm() {
        viewModelScope.launch {
            val state = coordinator.state.value
            val result = when (state) {
                CaptureState.DISARMED -> coordinator.arm()
                CaptureState.ARMED -> coordinator.disarm()
                CaptureState.INTERRUPTED, CaptureState.FAILED -> coordinator.rearmAfterTerminal()
                CaptureState.PERMISSION_DENIED -> coordinator.disarm()
                else -> Result.failure(IllegalStateException("Cannot toggle from $state"))
            }
            if (result.isFailure) {
                _snackbarMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun setAutoDisarmDuration(durationMs: Long) {
        viewModelScope.launch { preferencesManager.setAutoDisarmDuration(durationMs) }
    }

    fun setSilenceTimeout(seconds: Int) {
        viewModelScope.launch { preferencesManager.setSilenceTimeout(seconds) }
    }

    fun setMaxRecordingDuration(minutes: Int) {
        viewModelScope.launch { preferencesManager.setMaxRecordingDuration(minutes) }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun onPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                coordinator.onPermissionRestored()
            }
        }
    }
}
