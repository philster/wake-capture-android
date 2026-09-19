package com.wakecapture.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecapture.app.data.CaptureEntity
import com.wakecapture.app.data.PersistenceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureHistoryScreen(
    onBack: () -> Unit,
    onCaptureClick: (String) -> Unit,
    viewModel: CaptureHistoryViewModel = hiltViewModel()
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (captures.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No captures yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(captures, key = { it.id }) { capture ->
                    CaptureListItem(capture = capture, onClick = { onCaptureClick(capture.id) })
                }
            }
        }
    }
}

@Composable
private fun CaptureListItem(capture: CaptureEntity, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    val duration = formatDuration(capture.durationMs)
    val stateLabel = when (capture.state) {
        PersistenceState.SAVED -> ""
        PersistenceState.INTERRUPTED -> " (interrupted)"
        PersistenceState.FAILED -> " (failed)"
    }

    ListItem(
        headlineContent = {
            Text(capture.title ?: dateFormat.format(Date(capture.createdAt)))
        },
        supportingContent = {
            Text("$duration$stateLabel — via ${capture.createdFrom.name.lowercase()}")
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}
