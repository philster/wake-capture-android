package com.wakecapture.app.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.wakecapture.app.data.CaptureSource
import com.wakecapture.app.data.PreferencesManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class CaptureCoordinatorTest {

    private lateinit var context: Context
    private lateinit var repository: CaptureRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var coordinator: CaptureCoordinator

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)

        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED

        coEvery { preferencesManager.isArmed } returns flowOf(false)
        coEvery { preferencesManager.isAutoDisarmExpired() } returns false
        coEvery { preferencesManager.autoDisarmDurationMs } returns flowOf(PreferencesManager.DEFAULT_AUTO_DISARM_MS)
        coEvery { preferencesManager.armTimestamp } returns flowOf(null)
        coEvery { preferencesManager.silenceTimeoutSeconds } returns flowOf(30)
        coEvery { preferencesManager.maxRecordingDurationMinutes } returns flowOf(5)

        every { repository.hasAvailableStorage() } returns true
        every { repository.createFilePath() } returns "/fake/path/test.m4a"

        coordinator = CaptureCoordinator(context, repository, preferencesManager)
    }

    private fun grantPermission() {
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
    }

    private fun denyPermission() {
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
    }

    @Test
    fun `initial state is DISARMED`() {
        assertThat(coordinator.state.value).isEqualTo(CaptureState.DISARMED)
    }

    @Test
    fun `arm transitions to ARMED when permission granted`() = runTest {
        grantPermission()
        val result = coordinator.arm()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
        coVerify { preferencesManager.arm() }
    }

    @Test
    fun `arm fails when permission denied`() = runTest {
        denyPermission()
        val result = coordinator.arm()
        assertThat(result.isFailure).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.PERMISSION_DENIED)
    }

    @Test
    fun `disarm transitions to DISARMED from ARMED`() = runTest {
        grantPermission()
        coordinator.arm()
        val result = coordinator.disarm()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.DISARMED)
        coVerify { preferencesManager.disarm() }
    }

    @Test
    fun `requestStartCapture fails when not armed`() = runTest {
        val result = coordinator.requestStartCapture(CaptureSource.TILE)
        assertThat(result.isFailure).isTrue()
        assertThat(coordinator.error.value).isEqualTo(CaptureError.NotArmed)
    }

    @Test
    fun `requestStartCapture transitions to STARTING when armed`() = runTest {
        grantPermission()
        coordinator.arm()
        val result = coordinator.requestStartCapture(CaptureSource.TILE)
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.STARTING)
        assertThat(coordinator.currentCaptureId).isNotNull()
        assertThat(coordinator.currentFilePath).isNotNull()
        assertThat(coordinator.currentSource).isEqualTo(CaptureSource.TILE)
    }

    @Test
    fun `requestStartCapture fails with insufficient storage`() = runTest {
        grantPermission()
        every { repository.hasAvailableStorage() } returns false
        coordinator.arm()
        val result = coordinator.requestStartCapture(CaptureSource.APP)
        assertThat(result.isFailure).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.FAILED)
        assertThat(coordinator.error.value).isEqualTo(CaptureError.InsufficientStorage)
    }

    @Test
    fun `onRecordingStarted transitions to RECORDING from STARTING`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingStarted()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.RECORDING)
        assertThat(coordinator.recordingStartTime.value).isNotNull()
    }

    @Test
    fun `requestStopCapture transitions to STOPPING from RECORDING`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingStarted()
        val result = coordinator.requestStopCapture()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.STOPPING)
    }

    @Test
    fun `requestStopCapture fails when not recording`() = runTest {
        val result = coordinator.requestStopCapture()
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `onCaptureSaved transitions back to ARMED`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.TILE)

        val filePath = coordinator.currentFilePath!!
        every { repository.hasAvailableStorage() } returns true

        coordinator.onRecordingStarted()
        coordinator.requestStopCapture()
        coordinator.onCaptureSaved(5000L)

        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
        assertThat(coordinator.currentCaptureId).isNull()
        assertThat(coordinator.currentFilePath).isNull()
        assertThat(coordinator.recordingStartTime.value).isNull()
    }

    @Test
    fun `onCaptureInterrupted transitions to INTERRUPTED`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingStarted()
        coordinator.onCaptureInterrupted(3000L)
        assertThat(coordinator.state.value).isEqualTo(CaptureState.INTERRUPTED)
    }

    @Test
    fun `rearmAfterTerminal works from INTERRUPTED`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingStarted()
        coordinator.onCaptureInterrupted(3000L)

        val result = coordinator.rearmAfterTerminal()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
    }

    @Test
    fun `rearmAfterTerminal works from FAILED`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingFailed(CaptureError.RecorderInitFailed)

        val result = coordinator.rearmAfterTerminal()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
    }

    @Test
    fun `onRecordingFailed transitions to FAILED and clears state`() = runTest {
        grantPermission()
        coordinator.arm()
        coordinator.requestStartCapture(CaptureSource.APP)
        coordinator.onRecordingFailed(CaptureError.ServiceStartFailed)

        assertThat(coordinator.state.value).isEqualTo(CaptureState.FAILED)
        assertThat(coordinator.error.value).isEqualTo(CaptureError.ServiceStartFailed)
        assertThat(coordinator.currentCaptureId).isNull()
        assertThat(coordinator.currentFilePath).isNull()
    }

    @Test
    fun `auto-disarm expiry causes disarm on startCapture`() = runTest {
        grantPermission()
        coordinator.arm()
        coEvery { preferencesManager.isAutoDisarmExpired() } returns true

        val result = coordinator.requestStartCapture(CaptureSource.TILE)
        assertThat(result.isFailure).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.DISARMED)
        coVerify { preferencesManager.disarm() }
    }

    @Test
    fun `onPermissionRestored transitions from PERMISSION_DENIED to ARMED`() = runTest {
        denyPermission()
        coordinator.arm()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.PERMISSION_DENIED)

        grantPermission()
        coordinator.onPermissionRestored()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
    }

    @Test
    fun `complete capture cycle from DISARMED through SAVING back to ARMED`() = runTest {
        grantPermission()

        assertThat(coordinator.state.value).isEqualTo(CaptureState.DISARMED)

        coordinator.arm()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)

        coordinator.requestStartCapture(CaptureSource.TILE)
        assertThat(coordinator.state.value).isEqualTo(CaptureState.STARTING)

        coordinator.onRecordingStarted()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.RECORDING)

        coordinator.requestStopCapture()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.STOPPING)

        coordinator.onCaptureSaved(10000L)
        assertThat(coordinator.state.value).isEqualTo(CaptureState.ARMED)
    }

    @Test
    fun `cannot start capture without explicit user action (ARMED to RECORDING directly)`() {
        assertThat(CaptureState.ARMED.canTransitionTo(CaptureState.RECORDING)).isFalse()
    }

    @Test
    fun `disarm from PERMISSION_DENIED`() = runTest {
        denyPermission()
        coordinator.arm()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.PERMISSION_DENIED)

        val result = coordinator.disarm()
        assertThat(result.isSuccess).isTrue()
        assertThat(coordinator.state.value).isEqualTo(CaptureState.DISARMED)
    }
}
