package com.wakecapture.app.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.wakecapture.app.data.CaptureSource
import com.wakecapture.app.data.PersistenceState
import com.wakecapture.app.data.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CaptureRepository,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val mutex = Mutex()

    private val _state = MutableStateFlow(CaptureState.DISARMED)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _error = MutableStateFlow<CaptureError?>(null)
    val error: StateFlow<CaptureError?> = _error.asStateFlow()

    private val _recordingStartTime = MutableStateFlow<Long?>(null)
    val recordingStartTime: StateFlow<Long?> = _recordingStartTime.asStateFlow()

    var currentCaptureId: String? = null
        private set
    var currentFilePath: String? = null
        private set
    var currentSource: CaptureSource? = null
        private set

    val silenceTimeoutSeconds: Int
        get() = PreferencesManager.DEFAULT_SILENCE_TIMEOUT_SECONDS
    val maxRecordingDurationMinutes: Int
        get() = PreferencesManager.DEFAULT_MAX_RECORDING_MINUTES

    init {
        scope.launch { restoreState() }
    }

    private suspend fun restoreState() {
        if (preferencesManager.isAutoDisarmExpired()) {
            preferencesManager.disarm()
            return
        }
        val armed = preferencesManager.isArmed.first()
        if (armed) {
            _state.value = CaptureState.ARMED
        }
    }

    suspend fun arm(): Result<Unit> = mutex.withLock {
        if (!hasRecordAudioPermission()) {
            _state.value = CaptureState.PERMISSION_DENIED
            _error.value = CaptureError.PermissionDenied
            return Result.failure(SecurityException(CaptureError.PermissionDenied.message))
        }

        if (!_state.value.canTransitionTo(CaptureState.ARMED)) {
            return Result.failure(IllegalStateException("Cannot arm from ${_state.value}"))
        }

        preferencesManager.arm()
        _state.value = CaptureState.ARMED
        _error.value = null
        Result.success(Unit)
    }

    suspend fun disarm(): Result<Unit> = mutex.withLock {
        if (!_state.value.canTransitionTo(CaptureState.DISARMED)) {
            return Result.failure(IllegalStateException("Cannot disarm from ${_state.value}"))
        }

        preferencesManager.disarm()
        _state.value = CaptureState.DISARMED
        _error.value = null
        Result.success(Unit)
    }

    suspend fun requestStartCapture(source: CaptureSource): Result<Unit> = mutex.withLock {
        if (preferencesManager.isAutoDisarmExpired()) {
            preferencesManager.disarm()
            _state.value = CaptureState.DISARMED
            _error.value = CaptureError.NotArmed
            return Result.failure(IllegalStateException(CaptureError.NotArmed.message))
        }

        if (_state.value != CaptureState.ARMED) {
            _error.value = CaptureError.NotArmed
            return Result.failure(IllegalStateException(CaptureError.NotArmed.message))
        }

        if (!hasRecordAudioPermission()) {
            _state.value = CaptureState.PERMISSION_DENIED
            _error.value = CaptureError.PermissionDenied
            return Result.failure(SecurityException(CaptureError.PermissionDenied.message))
        }

        if (!repository.hasAvailableStorage()) {
            _state.value = CaptureState.FAILED
            _error.value = CaptureError.InsufficientStorage
            return Result.failure(IllegalStateException(CaptureError.InsufficientStorage.message))
        }

        _state.value = CaptureState.STARTING
        currentCaptureId = UUID.randomUUID().toString()
        currentFilePath = repository.createFilePath()
        currentSource = source
        _error.value = null
        Result.success(Unit)
    }

    suspend fun onRecordingStarted() = mutex.withLock {
        if (_state.value != CaptureState.STARTING) {
            Log.w(TAG, "onRecordingStarted called in unexpected state: ${_state.value}")
            return
        }
        _state.value = CaptureState.RECORDING
        _recordingStartTime.value = System.currentTimeMillis()
    }

    suspend fun onRecordingFailed(error: CaptureError) = mutex.withLock {
        _state.value = CaptureState.FAILED
        _error.value = error
        currentCaptureId = null
        currentFilePath = null
        currentSource = null
        _recordingStartTime.value = null
    }

    suspend fun requestStopCapture(): Result<Unit> = mutex.withLock {
        if (_state.value != CaptureState.RECORDING) {
            return Result.failure(IllegalStateException("Not recording"))
        }
        _state.value = CaptureState.STOPPING
        Result.success(Unit)
    }

    suspend fun onCaptureSaved(durationMs: Long): Result<Unit> = mutex.withLock {
        val path = currentFilePath ?: return Result.failure(IllegalStateException("No file path"))
        val source = currentSource ?: CaptureSource.APP
        val id = currentCaptureId ?: UUID.randomUUID().toString()

        try {
            repository.saveCapture(
                id = id,
                filePath = path,
                durationMs = durationMs,
                source = source,
                state = PersistenceState.SAVED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save capture", e)
            _state.value = CaptureState.FAILED
            _error.value = CaptureError.FileWriteFailed
            return Result.failure(e)
        }

        currentCaptureId = null
        currentFilePath = null
        currentSource = null
        _recordingStartTime.value = null
        _state.value = CaptureState.ARMED
        Result.success(Unit)
    }

    suspend fun onCaptureInterrupted(durationMs: Long) = mutex.withLock {
        val path = currentFilePath
        val source = currentSource ?: CaptureSource.APP
        val id = currentCaptureId ?: UUID.randomUUID().toString()

        if (path != null) {
            try {
                repository.markInterrupted(id, path, durationMs, source)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save interrupted capture", e)
            }
        }

        currentCaptureId = null
        currentFilePath = null
        currentSource = null
        _recordingStartTime.value = null
        _state.value = CaptureState.INTERRUPTED
    }

    suspend fun onPermissionRestored() = mutex.withLock {
        if (_state.value == CaptureState.PERMISSION_DENIED && hasRecordAudioPermission()) {
            _state.value = CaptureState.ARMED
            _error.value = null
        }
    }

    suspend fun rearmAfterTerminal(): Result<Unit> = mutex.withLock {
        if (_state.value in setOf(CaptureState.INTERRUPTED, CaptureState.FAILED)) {
            if (!hasRecordAudioPermission()) {
                _state.value = CaptureState.PERMISSION_DENIED
                _error.value = CaptureError.PermissionDenied
                return Result.failure(SecurityException(CaptureError.PermissionDenied.message))
            }
            _state.value = CaptureState.ARMED
            _error.value = null
            return Result.success(Unit)
        }
        Result.failure(IllegalStateException("Cannot re-arm from ${_state.value}"))
    }

    suspend fun checkAndHandleExpiry() {
        if (preferencesManager.isAutoDisarmExpired()) {
            mutex.withLock {
                preferencesManager.disarm()
                if (_state.value == CaptureState.ARMED) {
                    _state.value = CaptureState.DISARMED
                }
            }
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CaptureCoordinator"
    }
}
