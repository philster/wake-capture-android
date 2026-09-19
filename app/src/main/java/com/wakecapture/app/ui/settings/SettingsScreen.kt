package com.wakecapture.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.wakecapture.app.data.PreferencesManager
import com.wakecapture.app.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Recording",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            DropdownSetting(
                title = "Auto-stop after silence",
                subtitle = "${uiState.silenceTimeoutSeconds}s (not yet active)",
                options = PreferencesManager.SILENCE_TIMEOUT_OPTIONS.map { "${it}s" },
                onSelected = { index ->
                    viewModel.setSilenceTimeout(PreferencesManager.SILENCE_TIMEOUT_OPTIONS[index])
                }
            )

            DropdownSetting(
                title = "Maximum recording length",
                subtitle = "${uiState.maxRecordingMinutes} min",
                options = PreferencesManager.MAX_RECORDING_OPTIONS.map { "$it min" },
                onSelected = { index ->
                    viewModel.setMaxRecordingDuration(PreferencesManager.MAX_RECORDING_OPTIONS[index])
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Auto-disarm",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val autoDisarmEntries = PreferencesManager.AUTO_DISARM_OPTIONS.entries.toList()
            val currentLabel = autoDisarmEntries.find { it.value == uiState.autoDisarmDurationMs }?.key
                ?: "8 hours"

            DropdownSetting(
                title = "Auto-disarm after",
                subtitle = currentLabel,
                options = autoDisarmEntries.map { it.key },
                onSelected = { index ->
                    viewModel.setAutoDisarmDuration(autoDisarmEntries[index].value)
                }
            )
        }
    }
}

@Composable
private fun DropdownSetting(
    title: String,
    subtitle: String,
    options: List<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
    )

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEachIndexed { index, option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    onSelected(index)
                    expanded = false
                }
            )
        }
    }
}
