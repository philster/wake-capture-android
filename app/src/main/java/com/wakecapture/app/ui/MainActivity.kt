package com.wakecapture.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wakecapture.app.capture.CaptureCoordinator
import com.wakecapture.app.data.PreferencesManager
import com.wakecapture.app.ui.navigation.Routes
import com.wakecapture.app.ui.navigation.WakeCaptureNavGraph
import com.wakecapture.app.ui.theme.WakeCaptureTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var coordinator: CaptureCoordinator
    @Inject lateinit var preferencesManager: PreferencesManager

    private var startDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
        if (errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }

        enableEdgeToEdge()

        lifecycleScope.launch {
            startDestination = determineStartDestination()
        }

        setContent {
            WakeCaptureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val dest = startDestination
                    if (dest != null) {
                        val navController = rememberNavController()
                        WakeCaptureNavGraph(
                            navController = navController,
                            startDestination = dest,
                            onDisarm = {
                                lifecycleScope.launch { coordinator.disarm() }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            coordinator.checkAndHandleExpiry()
            if (coordinator.hasRecordAudioPermission()) {
                coordinator.onPermissionRestored()
            }
        }
    }

    private suspend fun determineStartDestination(): String {
        val hasPermissions = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val onboardingDone = preferencesManager.onboardingCompleted.first()

        return when {
            !onboardingDone -> Routes.ONBOARDING
            !hasPermissions -> Routes.PERMISSION_DENIED
            else -> Routes.HOME
        }
    }

    companion object {
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }
}
