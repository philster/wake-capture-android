package com.wakecapture.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecapture.app.capture.CaptureState
import com.wakecapture.app.capture.service.AudioCaptureService
import com.wakecapture.app.data.PreferencesManager
import com.wakecapture.app.ui.theme.ArmedGreen
import com.wakecapture.app.ui.theme.DisarmedGrey
import com.wakecapture.app.ui.theme.RecordingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake Capture") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.captureState == CaptureState.RECORDING) {
                val context = LocalContext.current
                FloatingActionButton(
                    onClick = { stopRecording(context) },
                    containerColor = RecordingRed
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop recording")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArmCard(
                captureState = uiState.captureState,
                armTimestamp = uiState.armTimestamp,
                autoDisarmDurationMs = uiState.autoDisarmDurationMs,
                onToggle = { viewModel.toggleArm() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.captureState == CaptureState.ARMED) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        scope.launch {
                            val result = viewModel.requestStartCapture()
                            if (result.isSuccess) {
                                context.startForegroundService(
                                    AudioCaptureService.startIntent(context)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RecordingRed)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Start Capture",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.captureState == CaptureState.RECORDING) {
                RecordingIndicator(startTime = uiState.recordingStartTime)
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorCard(message = uiState.errorMessage!!)
            }
        }
    }
}

@Composable
private fun ArmCard(
    captureState: CaptureState,
    armTimestamp: Long?,
    autoDisarmDurationMs: Long,
    onToggle: () -> Unit
) {
    val isArmed = captureState != CaptureState.DISARMED
    val cardColor by animateColorAsState(
        targetValue = when (captureState) {
            CaptureState.RECORDING -> RecordingRed.copy(alpha = 0.1f)
            CaptureState.ARMED, CaptureState.STARTING -> ArmedGreen.copy(alpha = 0.1f)
            else -> DisarmedGrey.copy(alpha = 0.05f)
        },
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wake Capture",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when (captureState) {
                            CaptureState.DISARMED -> "Disarmed"
                            CaptureState.ARMED -> "Armed"
                            CaptureState.STARTING -> "Starting..."
                            CaptureState.RECORDING -> "Recording"
                            CaptureState.STOPPING -> "Stopping..."
                            CaptureState.INTERRUPTED -> "Interrupted"
                            CaptureState.PERMISSION_DENIED -> "Permission required"
                            CaptureState.FAILED -> "Failed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isArmed && captureState != CaptureState.PERMISSION_DENIED,
                    onCheckedChange = { onToggle() },
                    enabled = captureState !in setOf(
                        CaptureState.STARTING, CaptureState.RECORDING, CaptureState.STOPPING
                    )
                )
            }

            if (isArmed && armTimestamp != null && autoDisarmDurationMs != PreferencesManager.DURATION_UNTIL_DISARM) {
                Spacer(modifier = Modifier.height(8.dp))
                AutoDisarmCountdown(armTimestamp, autoDisarmDurationMs)
            }
        }
    }
}

@Composable
private fun AutoDisarmCountdown(armTimestamp: Long, durationMs: Long) {
    var remainingMs by remember { mutableStateOf(armTimestamp + durationMs - System.currentTimeMillis()) }

    LaunchedEffect(armTimestamp, durationMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = armTimestamp + durationMs - System.currentTimeMillis()
        }
    }

    if (remainingMs > 0) {
        val totalSeconds = (remainingMs / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val text = if (hours > 0) {
            String.format("Auto-disarm in %dh %02dm", hours, minutes)
        } else {
            String.format("Auto-disarm in %02d:%02d", minutes, seconds)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = "Auto-disarm expired",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun RecordingIndicator(startTime: Long?) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(startTime) {
        if (startTime != null) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1000)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RecordingRed.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = RecordingRed,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text("Recording in progress", style = MaterialTheme.typography.titleMedium)
                if (startTime != null) {
                    Text(
                        text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun stopRecording(context: Context) {
    context.startService(
        Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
    )
}
