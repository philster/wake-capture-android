package com.wakecapture.app.capture

sealed class CaptureError(val message: String) {
    data object PermissionDenied : CaptureError("Microphone permission is required")
    data object NotArmed : CaptureError("Wake Capture must be armed first")
    data object AlreadyRecording : CaptureError("A recording is already in progress")
    data object ServiceStartFailed : CaptureError("Failed to start recording service")
    data object RecorderInitFailed : CaptureError("Failed to initialize audio recorder")
    data object InsufficientStorage : CaptureError("Not enough storage space (50 MB required)")
    data object FileWriteFailed : CaptureError("Failed to write audio file")
    data class Unexpected(val cause: Throwable) : CaptureError(cause.message ?: "Unexpected error")

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
