package com.wakecapture.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureStateTest {

    @Test
    fun `DISARMED can only transition to ARMED`() {
        val state = CaptureState.DISARMED
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.STARTING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.STOPPING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.INTERRUPTED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.PERMISSION_DENIED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isFalse()
    }

    @Test
    fun `ARMED can transition to STARTING or DISARMED or FAILED`() {
        val state = CaptureState.ARMED
        assertThat(state.canTransitionTo(CaptureState.STARTING)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isFalse()
    }

    @Test
    fun `STARTING can transition to RECORDING or PERMISSION_DENIED or FAILED`() {
        val state = CaptureState.STARTING
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.PERMISSION_DENIED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isFalse()
    }

    @Test
    fun `RECORDING can transition to STOPPING or INTERRUPTED or FAILED`() {
        val state = CaptureState.RECORDING
        assertThat(state.canTransitionTo(CaptureState.STOPPING)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.INTERRUPTED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isFalse()
    }

    @Test
    fun `STOPPING can transition to ARMED or FAILED`() {
        val state = CaptureState.STOPPING
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
    }

    @Test
    fun `INTERRUPTED can transition to ARMED or DISARMED or FAILED`() {
        val state = CaptureState.INTERRUPTED
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
    }

    @Test
    fun `PERMISSION_DENIED can transition to ARMED or DISARMED`() {
        val state = CaptureState.PERMISSION_DENIED
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.FAILED)).isFalse()
    }

    @Test
    fun `FAILED can transition to ARMED or DISARMED`() {
        val state = CaptureState.FAILED
        assertThat(state.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.DISARMED)).isTrue()
        assertThat(state.canTransitionTo(CaptureState.RECORDING)).isFalse()
        assertThat(state.canTransitionTo(CaptureState.STARTING)).isFalse()
    }

    @Test
    fun `ARMED to RECORDING without STARTING is not allowed`() {
        assertThat(CaptureState.ARMED.canTransitionTo(CaptureState.RECORDING)).isFalse()
    }

    @Test
    fun `complete happy path transitions are valid`() {
        assertThat(CaptureState.DISARMED.canTransitionTo(CaptureState.ARMED)).isTrue()
        assertThat(CaptureState.ARMED.canTransitionTo(CaptureState.STARTING)).isTrue()
        assertThat(CaptureState.STARTING.canTransitionTo(CaptureState.RECORDING)).isTrue()
        assertThat(CaptureState.RECORDING.canTransitionTo(CaptureState.STOPPING)).isTrue()
        assertThat(CaptureState.STOPPING.canTransitionTo(CaptureState.ARMED)).isTrue()
    }
}
