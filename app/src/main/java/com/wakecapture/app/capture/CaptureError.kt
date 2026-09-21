package com.wakecapture.app.capture

import androidx.annotation.StringRes
import com.wakecapture.app.R

sealed class CaptureError(val message: String, @StringRes val messageResId: Int) {
    data object PermissionDenied : CaptureError("Microphone permission is required", R.string.error_permission_denied)
    data object NotArmed : CaptureError("Wake Capture must be armed first", R.string.error_not_armed)
    data object AlreadyRecording : CaptureError("A recording is already in progress", R.string.error_already_recording)
    data object ServiceStartFailed : CaptureError("Failed to start recording service", R.string.error_service_start_failed)
    data object RecorderInitFailed : CaptureError("Failed to initialize audio recorder", R.string.error_recorder_init_failed)
    data object InsufficientStorage : CaptureError("Not enough storage space (50 MB required)", R.string.error_insufficient_storage)
    data object FileWriteFailed : CaptureError("Failed to write audio file", R.string.error_file_write_failed)
    data class Unexpected(val cause: Throwable) : CaptureError(cause.message ?: "Unexpected error", R.string.error_unexpected)

    val recoveryAction: RecoveryAction?
        get() = when (this) {
            is PermissionDenied -> RecoveryAction.OPEN_APP_SETTINGS
            is InsufficientStorage -> RecoveryAction.OPEN_STORAGE_SETTINGS
            else -> null
        }
}

enum class RecoveryAction {
    OPEN_APP_SETTINGS,
    OPEN_STORAGE_SETTINGS
}
