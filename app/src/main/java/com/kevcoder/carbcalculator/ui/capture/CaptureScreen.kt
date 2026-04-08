package com.kevcoder.carbcalculator.ui.capture

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Photo
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
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import androidx.compose.material.icons.filled.EditCalendar
import java.time.format.DateTimeFormatter

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
    val selectedDatetime by viewModel.selectedDatetime.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val cameraManager = remember { CameraManager(context) }
    val previewView = remember { PreviewView(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) cameraManager.bindToLifecycle(lifecycleOwner, previewView)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = uriToTempFile(context, uri)
            if (file != null) viewModel.onPhotoCaptured(file)
        }
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

                // DateTime picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePickerDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.EditCalendar, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(selectedDatetime.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                    }
                    OutlinedButton(
                        onClick = { showTimePickerDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(selectedDatetime.format(DateTimeFormatter.ofPattern("h:mm a")))
                    }
                }

                // Date picker dialog
                if (showDatePickerDialog) {
                    val currentDate = selectedDatetime.toLocalDate()
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePickerDialog = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val selectedDate = LocalDate.ofEpochDay(datePickerState.selectedDateMillis!! / (24 * 60 * 60 * 1000))
                                    val newDatetime = OffsetDateTime.of(
                                        selectedDate,
                                        selectedDatetime.toLocalTime(),
                                        selectedDatetime.offset
                                    )
                                    viewModel.onDatetimeSelected(newDatetime)
                                    showDatePickerDialog = false
                                }
                            ) { Text("OK") }
                        }
                    ) {
                        DatePicker(datePickerState)
                    }
                }

                // Time picker dialog
                if (showTimePickerDialog) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = selectedDatetime.hour,
                        initialMinute = selectedDatetime.minute,
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePickerDialog = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val newDatetime = OffsetDateTime.of(
                                        selectedDatetime.toLocalDate(),
                                        LocalTime.of(timePickerState.hour, timePickerState.minute),
                                        selectedDatetime.offset
                                    )
                                    viewModel.onDatetimeSelected(newDatetime)
                                    showTimePickerDialog = false
                                }
                            ) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePickerDialog = false }) { Text("Cancel") }
                        },
                        text = {
                            TimePicker(timePickerState)
                        }
                    )
                }

                // Image source row: Camera + Gallery (+ Retake when photo is taken)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        Spacer(Modifier.width(4.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = uiState !is CaptureUiState.Uploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery")
                    }
                    if (uiState is CaptureUiState.PhotoTaken) {
                        OutlinedButton(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retake")
                        }
                    }
                }

                // Analyze row: always visible, enabled when there's a photo or a description
                val canAnalyze = (uiState is CaptureUiState.PhotoTaken || description.isNotBlank()) &&
                    uiState !is CaptureUiState.Uploading
                Button(
                    onClick = {
                        val imageFile = (uiState as? CaptureUiState.PhotoTaken)
                            ?.let { File(it.imagePath) }
                        viewModel.onAnalyze(
                            imageFile = imageFile,
                            description = description,
                            onSuccess = onNavigateToResult,
                        )
                    },
                    enabled = canAnalyze,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Analyze")
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

private fun uriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
        inputStream.close()
        tempFile
    } catch (e: Exception) {
        null
    }
}
