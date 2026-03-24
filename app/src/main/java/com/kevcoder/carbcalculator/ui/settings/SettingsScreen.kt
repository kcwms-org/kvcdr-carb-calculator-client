package com.kevcoder.carbcalculator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var apiUrlDraft by remember(uiState.carbApiUrl) { mutableStateOf(uiState.carbApiUrl) }

    // Launch OAuth2 URL in Custom Tabs when emitted
    LaunchedEffect(Unit) {
        viewModel.authUrlEvent.collect { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // --- Carb API URL ---
            Text("Carb Calculator API", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiUrlDraft,
                onValueChange = { apiUrlDraft = it },
                label = { Text("API endpoint URL") },
                placeholder = { Text(AppPreferencesDataStore.DEFAULT_CARB_API_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { viewModel.onSaveApiUrl(apiUrlDraft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save API URL")
            }

            HorizontalDivider()

            // --- Dexcom ---
            Text("Dexcom Integration", style = MaterialTheme.typography.titleMedium)

            // Environment toggle
            Text("Environment", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.dexcomEnv == AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION,
                    onClick = { viewModel.onDexcomEnvChanged(AppPreferencesDataStore.DEXCOM_ENV_PRODUCTION) },
                    label = { Text("Production") },
                )
                FilterChip(
                    selected = uiState.dexcomEnv == AppPreferencesDataStore.DEXCOM_ENV_SANDBOX,
                    onClick = { viewModel.onDexcomEnvChanged(AppPreferencesDataStore.DEXCOM_ENV_SANDBOX) },
                    label = { Text("Sandbox") },
                )
            }

            // Connection status + action
            if (uiState.isDexcomConnected) {
                Text(
                    "Connected to Dexcom",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { viewModel.onDisconnectDexcom() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect Dexcom")
                }
            } else {
                Text(
                    "Not connected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { viewModel.onConnectDexcom() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect Dexcom")
                }
            }

            Text(
                "Note: Dexcom API is read-only. Carb logs are saved in this app only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
