package com.kevcoder.carbcalculator.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevcoder.carbcalculator.BuildConfig

private data class ChangelogEntry(val version: String, val notes: List<String>)

private val changelog = listOf(
    ChangelogEntry(
        version = "1.7.0",
        notes = listOf(
            "Configurable export directory: choose where to save backups",
        ),
    ),
    ChangelogEntry(
        version = "1.6.0",
        notes = listOf(
            "App logo: adaptive vector icon for all sizes",
        ),
    ),
    ChangelogEntry(
        version = "1.5.0",
        notes = listOf(
            "Import/export: backup and restore settings and history as JSON",
        ),
    ),
    ChangelogEntry(
        version = "1.4.0",
        notes = listOf(
            "Image compression: resize and compress photos before upload",
            "DateTime picker: backfill submission timestamp for past meals",
            "Full-size image viewer: tap to view full image from history",
            "Save images to device: optional toggle to persist images to gallery",
        ),
    ),
    ChangelogEntry(
        version = "1.3.0",
        notes = listOf(
            "Data persistence: survive app reinstalls via Android Auto Backup",
            "CI improvements: stable keystore and auto-incrementing version code",
        ),
    ),
    ChangelogEntry(
        version = "1.2.0",
        notes = listOf(
            "HTTP logging: detailed request/response logs for debugging",
        ),
    ),
    ChangelogEntry(
        version = "1.1.0",
        notes = listOf(
            "Keystore setup: signed APK builds for sideload updates",
        ),
    ),
    ChangelogEntry(
        version = "1.0.0",
        notes = listOf(
            "Initial release",
            "Camera capture and AI-powered carb estimation",
            "Dexcom CGM integration",
            "History and submission log",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val versionString = "${BuildConfig.VERSION_NAME}+${BuildConfig.GIT_COMMIT_SHA}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Carb Calculator", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        versionString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }
            item { Text("Changelog", style = MaterialTheme.typography.titleMedium) }
            items(changelog) { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.version,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    entry.notes.forEach { note ->
                        Text("\u2022  $note", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
