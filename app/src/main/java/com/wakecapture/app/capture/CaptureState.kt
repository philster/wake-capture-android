package com.wakecapture.app.capture

enum class CaptureState {
    DISARMED,
    ARMED,
    STARTING,
    RECORDING,
    STOPPING,
    INTERRUPTED,
    PERMISSION_DENIED,
    FAILED;

    fun canTransitionTo(target: CaptureState): Boolean = when (this) {
        DISARMED -> target == ARMED
        ARMED -> target in setOf(STARTING, DISARMED, FAILED)
        STARTING -> target in setOf(RECORDING, PERMISSION_DENIED, FAILED)
        RECORDING -> target in setOf(STOPPING, INTERRUPTED, FAILED)
        STOPPING -> target in setOf(ARMED, FAILED)
        INTERRUPTED -> target in setOf(ARMED, DISARMED, FAILED)
        PERMISSION_DENIED -> target in setOf(ARMED, DISARMED)
        FAILED -> target in setOf(ARMED, DISARMED)
    }
}
