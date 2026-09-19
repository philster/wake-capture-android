package com.wakecapture.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureDetailScreen(
    onBack: () -> Unit,
    viewModel: CaptureDetailViewModel = hiltViewModel()
) {
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        val entity = capture
        if (entity == null) {
            Text(
                "Capture not found",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.getDefault())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Date") },
                        supportingContent = { Text(dateFormat.format(Date(entity.createdAt))) }
                    )
                    ListItem(
                        headlineContent = { Text("Duration") },
                        supportingContent = {
                            val s = entity.durationMs / 1000
                            Text("${s / 60}m ${s % 60}s")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Status") },
                        supportingContent = { Text(entity.state.name) }
                    )
                    ListItem(
                        headlineContent = { Text("Source") },
                        supportingContent = { Text(entity.createdFrom.name) }
                    )
                    ListItem(
                        headlineContent = { Text("Format") },
                        supportingContent = {
                            Text("${entity.codec} ${entity.sampleRate ?: "?"}Hz ${entity.channels ?: "?"}ch")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Transcription not yet available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete capture?") },
                text = { Text("This will permanently delete the recording.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete()
                        showDeleteDialog = false
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
