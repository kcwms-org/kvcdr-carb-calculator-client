package com.kevcoder.carbcalculator.ui.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kevcoder.carbcalculator.camera.CameraManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToResult: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    val cameraManager = remember { CameraManager(context) }
    val previewView = remember { PreviewView(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) cameraManager.bindToLifecycle(lifecycleOwner, previewView)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carb Calculator") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Camera preview or captured photo thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = uiState) {
                    is CaptureUiState.PhotoTaken -> {
                        AsyncImage(
                            model = state.imagePath,
                            contentDescription = "Captured food photo",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("Camera permission required")
                        }
                    }
                }

                if (uiState is CaptureUiState.Uploading) {
                    CircularProgressIndicator()
                }
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Food description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = false,
                    maxLines = 3,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Shutter button — only when no photo taken yet
                    if (uiState !is CaptureUiState.PhotoTaken) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val file = cameraManager.takePicture()
                                        viewModel.onPhotoCaptured(file)
                                    } catch (e: Exception) {
                                        viewModel.onCaptureFailed(e.message ?: "Capture failed")
                                    }
                                }
                            },
                            enabled = hasCameraPermission && uiState !is CaptureUiState.Uploading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Take Photo")
                        }
                    } else {
                        // Retake
                        OutlinedButton(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retake")
                        }
                    }

                    // Analyze
                    Button(
                        onClick = {
                            val state = uiState
                            if (state is CaptureUiState.PhotoTaken) {
                                viewModel.onAnalyze(
                                    imageFile = File(state.imagePath),
                                    description = description,
                                    onSuccess = onNavigateToResult,
                                )
                            }
                        },
                        enabled = uiState is CaptureUiState.PhotoTaken,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Analyze")
                    }
                }

                // Error display
                if (uiState is CaptureUiState.Error) {
                    Text(
                        text = (uiState as CaptureUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
