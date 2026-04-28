package com.kevcoder.carbcalculator.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevcoder.carbcalculator.data.local.datastore.AppPreferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Data for a single star particle
private data class StarParticle(
    val startX: Float,       // fraction of screen width [0,1]
    val startY: Float,       // starting Y as fraction of screen height (slightly above top)
    val angle: Float,        // fall angle in degrees (near-vertical with slight drift)
    val speed: Float,        // fall speed multiplier
    val size: Float,         // star point radius in dp
    val color: Color,
    val rotationSpeed: Float,
    val delay: Float,        // start offset [0,1] of total duration
)

private val starColors = listOf(
    Color(0xFFFFD700), // gold
    Color(0xFFFFF9C4), // pale yellow
    Color(0xFFFFEB3B), // yellow
    Color(0xFFFFFFFF), // white
    Color(0xFFB3E5FC), // light blue
    Color(0xFFE1BEE7), // light purple
)

private fun generateStars(count: Int): List<StarParticle> = List(count) {
    StarParticle(
        startX = Random.nextFloat(),
        startY = -Random.nextFloat() * 0.3f,
        angle = Random.nextFloat() * 30f - 15f, // -15..+15 degrees from vertical
        speed = 0.5f + Random.nextFloat() * 0.8f,
        size = 4f + Random.nextFloat() * 6f,
        color = starColors[Random.nextInt(starColors.size)],
        rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
        delay = Random.nextFloat(),
    )
}

@Composable
private fun StarShower(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stars = remember { generateStars(60) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2200, easing = LinearEasing),
            )
            delay(100)
            onFinished()
        }
    }

    if (active || progress.value > 0f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val p = progress.value
            stars.forEach { star ->
                // Each star has its own delay; shift progress so they stagger
                val localP = ((p - star.delay * 0.4f) / 0.6f).coerceIn(0f, 1f)
                if (localP <= 0f) return@forEach

                val angleRad = Math.toRadians(star.angle.toDouble())
                val dx = sin(angleRad).toFloat()
                val dy = cos(angleRad).toFloat()

                val x = (star.startX + dx * localP * star.speed * 0.4f) * size.width
                val y = (star.startY + dy * localP * star.speed * 1.6f) * size.height

                val alpha = when {
                    localP < 0.1f -> localP / 0.1f
                    localP > 0.8f -> 1f - (localP - 0.8f) / 0.2f
                    else -> 1f
                }

                val rotation = star.rotationSpeed * localP
                val r = star.size.dp.toPx()

                rotate(degrees = rotation, pivot = Offset(x, y)) {
                    drawStar(
                        center = Offset(x, y),
                        outerRadius = r,
                        innerRadius = r * 0.4f,
                        points = 5,
                        color = star.color.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    points: Int,
    color: Color,
) {
    val path = androidx.compose.ui.graphics.Path()
    val totalPoints = points * 2
    for (i in 0 until totalPoints) {
        val angle = Math.toRadians((i * 360.0 / totalPoints) - 90.0)
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var apiUrlDraft by remember(uiState.carbApiUrl) { mutableStateOf(uiState.carbApiUrl) }
    var showStarShower by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.authUrlEvent.collect { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect {
            showStarShower = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
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

                // --- Image Quality ---
                Text("Image Quality", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Images are compressed before upload. Lower quality = smaller file size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Low", style = MaterialTheme.typography.labelSmall)
                    Text("${uiState.imageQuality}%", style = MaterialTheme.typography.labelMedium)
                    Text("High", style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = uiState.imageQuality.toFloat(),
                    onValueChange = { viewModel.onImageQualityChanged(it.toInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // --- Image Storage ---
                Text("Image Storage", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("Save images to device") },
                    supportingContent = { Text("Save a copy to your photo gallery") },
                    trailingContent = {
                        Switch(
                            checked = uiState.saveImagesToDevice,
                            onCheckedChange = { viewModel.onSaveImagesToDeviceChanged(it) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // --- History Display ---
                Text("History Display", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("Expand submissions by default") },
                    supportingContent = { Text("Show submission details automatically in history") },
                    trailingContent = {
                        Switch(
                            checked = uiState.expandSubmissionsDefault,
                            onCheckedChange = { viewModel.onExpandSubmissionsDefaultChanged(it) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // --- Dexcom ---
                Text("Dexcom Integration", style = MaterialTheme.typography.titleMedium)

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

                HorizontalDivider()

                // --- Submission Log ---
                Text("Submission Log", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Auto-purge submissions older than:",
                    style = MaterialTheme.typography.labelMedium,
                )

                val purgeOptions = listOf(
                    AppPreferencesDataStore.PURGE_NEVER to "Never",
                    AppPreferencesDataStore.PURGE_HOURLY to "1 Hour",
                    AppPreferencesDataStore.PURGE_DAILY to "1 Day",
                    AppPreferencesDataStore.PURGE_WEEKLY to "1 Week",
                    AppPreferencesDataStore.PURGE_MONTHLY to "1 Month",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    purgeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.submissionPurgeInterval == value,
                            onClick = { viewModel.onSubmissionPurgeIntervalChanged(value) },
                            label = { Text(label) },
                        )
                    }
                }

                HorizontalDivider()

                // --- Backup & Restore ---
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export settings and history to a JSON file in Downloads, or import from a previously exported file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val context = LocalContext.current
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            val tempFile = File(context.cacheDir, "import-backup.json")
                            tempFile.outputStream().use { output ->
                                stream.copyTo(output)
                            }
                            viewModel.onImportBackup(tempFile)
                        }
                    }
                }

                LaunchedEffect(viewModel.backupEvent) {
                    viewModel.backupEvent.collectLatest { event ->
                        when (event) {
                            is BackupEvent.ExportSuccess ->
                                Toast.makeText(context, "Backup saved to ${event.filePath}", Toast.LENGTH_LONG).show()
                            is BackupEvent.ImportSuccess ->
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            is BackupEvent.Error ->
                                Toast.makeText(context, "Error: ${event.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.onExportBackup() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export")
                    }
                    Button(
                        onClick = { importLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Import")
                    }
                }

                HorizontalDivider()

                // --- About ---
                TextButton(
                    onClick = onNavigateToAbout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Star shower overlay — rendered on top of everything, pointer-transparent
        StarShower(
            active = showStarShower,
            onFinished = { showStarShower = false },
            modifier = Modifier.matchParentSize(),
        )
    }
}
