package com.wakecapture.app.system.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.wakecapture.app.capture.CaptureCoordinator
import com.wakecapture.app.capture.CaptureState
import com.wakecapture.app.capture.service.AudioCaptureService
import com.wakecapture.app.data.CaptureSource
import com.wakecapture.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CaptureTileService : TileService() {

    @Inject lateinit var coordinator: CaptureCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        serviceScope.launch {
            coordinator.checkAndHandleExpiry()
            val currentState = coordinator.state.first()

            when (currentState) {
                CaptureState.RECORDING -> {
                    try {
                        startService(AudioCaptureService.stopIntent(this@CaptureTileService))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop service from tile", e)
                    }
                }
                CaptureState.ARMED -> {
                    val result = coordinator.requestStartCapture(CaptureSource.TILE)
                    if (result.isSuccess) {
                        try {
                            startForegroundService(
                                AudioCaptureService.startIntent(this@CaptureTileService)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start foreground service from tile", e)
                            launchAppWithError("Unable to start recording. Please open the app.")
                        }
                    } else {
                        launchAppWithError(result.exceptionOrNull()?.message)
                    }
                }
                CaptureState.DISARMED, CaptureState.PERMISSION_DENIED,
                CaptureState.INTERRUPTED, CaptureState.FAILED -> {
                    launchAppWithError(
                        when (currentState) {
                            CaptureState.PERMISSION_DENIED -> "Microphone permission required"
                            else -> "Arm Wake Capture first"
                        }
                    )
                }
                CaptureState.STARTING, CaptureState.STOPPING -> {
                    // Already transitioning
                }
            }
            updateTile()
        }
    }

    private fun launchAppWithError(message: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message?.let { putExtra(MainActivity.EXTRA_ERROR_MESSAGE, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        serviceScope.launch {
            val currentState = coordinator.state.first()
            tile.state = when (currentState) {
                CaptureState.RECORDING -> Tile.STATE_ACTIVE
                CaptureState.ARMED -> Tile.STATE_INACTIVE
                else -> Tile.STATE_INACTIVE
            }
            tile.label = when (currentState) {
                CaptureState.RECORDING -> "Recording"
                else -> "Capture"
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CaptureTileService"
    }
}
