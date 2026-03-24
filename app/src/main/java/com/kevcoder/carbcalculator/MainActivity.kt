package com.kevcoder.carbcalculator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kevcoder.carbcalculator.data.repository.DexcomRepository
import com.kevcoder.carbcalculator.ui.capture.CaptureScreen
import com.kevcoder.carbcalculator.ui.history.HistoryScreen
import com.kevcoder.carbcalculator.ui.result.ResultScreen
import com.kevcoder.carbcalculator.ui.settings.SettingsScreen
import com.kevcoder.carbcalculator.ui.settings.SettingsViewModel
import com.kevcoder.carbcalculator.ui.theme.CarbCalculatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

object Routes {
    const val CAPTURE = "capture"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dexcomRepository: DexcomRepository

    private var pendingOAuthCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            CarbCalculatorTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = Routes.CAPTURE) {

                    composable(Routes.CAPTURE) {
                        CaptureScreen(
                            onNavigateToResult = { navController.navigate(Routes.RESULT) },
                            onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                            onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        )
                    }

                    composable(Routes.RESULT) {
                        ResultScreen(
                            onSaved = {
                                navController.navigate(Routes.HISTORY) {
                                    popUpTo(Routes.CAPTURE)
                                }
                            },
                            onDiscard = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        val settingsViewModel: SettingsViewModel = hiltViewModel()

                        // Handle pending OAuth code if we were redirected back mid-navigation
                        LaunchedEffect(pendingOAuthCode) {
                            pendingOAuthCode?.let { code ->
                                dexcomRepository.exchangeCode(code)
                                settingsViewModel.onDexcomConnected()
                                pendingOAuthCode = null
                            }
                        }

                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            viewModel = settingsViewModel,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Handle Dexcom OAuth2 redirect: kvcdr-carb://oauth2callback?code=<code>
        val uri = intent?.data ?: return
        if (uri.scheme == "kvcdr-carb" && uri.host == "oauth2callback") {
            pendingOAuthCode = uri.getQueryParameter("code")
        }
    }
}
