package com.wakecapture.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wakecapture.app.ui.detail.CaptureDetailScreen
import com.wakecapture.app.ui.history.CaptureHistoryScreen
import com.wakecapture.app.ui.home.HomeScreen
import com.wakecapture.app.ui.onboarding.OnboardingScreen
import com.wakecapture.app.ui.permission.PermissionDeniedScreen
import com.wakecapture.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DETAIL = "detail/{captureId}"

    fun detail(captureId: String) = "detail/$captureId"
}

@Composable
fun WakeCaptureNavGraph(
    navController: NavHostController,
    startDestination: String,
    onDisarm: () -> Unit
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onPermissionsGranted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            CaptureHistoryScreen(
                onBack = { navController.popBackStack() },
                onCaptureClick = { id -> navController.navigate(Routes.detail(id)) }
            )
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("captureId") { type = NavType.StringType })
        ) {
            CaptureDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
