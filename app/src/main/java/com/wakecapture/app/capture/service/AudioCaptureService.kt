package com.wakecapture.app.capture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wakecapture.app.R
import com.wakecapture.app.capture.CaptureCoordinator
import com.wakecapture.app.capture.CaptureError
import com.wakecapture.app.data.PreferencesManager
import com.wakecapture.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject lateinit var coordinator: CaptureCoordinator
    @Inject lateinit var audioRecorder: AudioRecorder
    @Inject lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var maxDurationJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var recordingStartTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val filePath = coordinator.currentFilePath
        if (filePath == null) {
            Log.e(TAG, "No file path set")
            serviceScope.launch { coordinator.onRecordingFailed(CaptureError.RecorderInitFailed) }
            stopSelf()
            return
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            serviceScope.launch { coordinator.onRecordingFailed(CaptureError.ServiceStartFailed) }
            stopSelf()
            return
        }

        try {
            audioRecorder.initialize(filePath)
            audioRecorder.start()
            recordingStartTime = System.currentTimeMillis()
            serviceScope.launch { coordinator.onRecordingStarted() }
            startMaxDurationTimer()
            startNotificationUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recorder", e)
            serviceScope.launch { coordinator.onRecordingFailed(CaptureError.RecorderInitFailed) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            coordinator.requestStopCapture()
            finalizeRecording()
        }
    }

    private fun finalizeRecording() {
        maxDurationJob?.cancel()
        notificationUpdateJob?.cancel()
        val durationMs = System.currentTimeMillis() - recordingStartTime

        try {
            audioRecorder.stop()
            audioRecorder.release()
            serviceScope.launch {
                coordinator.onCaptureSaved(durationMs)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording", e)
            serviceScope.launch {
                coordinator.onCaptureInterrupted(durationMs)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startMaxDurationTimer() {
        maxDurationJob = serviceScope.launch {
            val maxMinutes = preferencesManager.maxRecordingDurationMinutes.first()
            delay(maxMinutes * 60 * 1000L)
            Log.i(TAG, "Max recording duration reached ($maxMinutes min)")
            coordinator.requestStopCapture()
            finalizeRecording()
        }
    }

    private fun startNotificationUpdates() {
        notificationUpdateJob = serviceScope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            while (true) {
                delay(1000)
                val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                nm.notify(NOTIFICATION_ID, buildNotification(elapsed))
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while Wake Capture is recording audio"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSeconds: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val timeText = String.format("%d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        if (audioRecorder.isRecording) {
            val durationMs = System.currentTimeMillis() - recordingStartTime
            audioRecorder.stop()
            audioRecorder.release()
            serviceScope.launch { coordinator.onCaptureInterrupted(durationMs) }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        const val CHANNEL_ID = "wake_capture_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.wakecapture.app.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.wakecapture.app.ACTION_STOP_RECORDING"

        fun startIntent(context: Context): Intent {
            return Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_START
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, AudioCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
