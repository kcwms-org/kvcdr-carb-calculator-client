package com.kevcoder.carbcalculator.ui.submissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubmissionsViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Submissions?") },
            text = { Text("This will permanently delete all submission logs and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submissions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.copyAllAsJson() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy all as JSON")
                        }
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No submissions yet. Take a photo to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(logs, key = { it.id }) { log ->
                    SubmissionLogCard(
                        log = log,
                        onCopyJson = { viewModel.copyLogAsJson(log) },
                        onCopyRequest = { viewModel.copyRequest(log) },
                        onCopyResponse = { viewModel.copyResponse(log) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmissionLogCard(
    log: SubmissionLog,
    onCopyJson: () -> Unit,
    onCopyRequest: () -> Unit,
    onCopyResponse: () -> Unit,
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    var requestExpanded by remember { mutableStateOf(false) }
    var responseExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Header row: status chip + copy-JSON button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(status = log.status)
                IconButton(onClick = onCopyJson, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy as JSON",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = dateFormatter.format(Date(log.requestTimestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            log.foodDescription?.let { desc ->
                Text(desc, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            // Food items (success only)
            if (log.status == "success" && log.items.isNotEmpty()) {
                log.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(item.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${item.estimatedCarbs}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                log.totalCarbs?.let { carbs ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total carbs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${carbs}g",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            log.errorMessage?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (log.savedLogId != null) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Saved to History", style = MaterialTheme.typography.labelSmall) },
                )
            }

            // HTTP details
            if (!log.requestHeaders.isNullOrBlank() || !log.responseHeaders.isNullOrBlank() || !log.responseBody.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

                // Request section
                HttpSection(
                    label = "Request",
                    content = log.requestHeaders,
                    expanded = requestExpanded,
                    onToggle = { requestExpanded = !requestExpanded },
                    onCopy = onCopyRequest,
                )

                // Response section
                val responseContent = buildString {
                    log.responseHeaders?.let { append(it).append("\n\n") }
                    log.responseBody?.let { append(it) }
                }.ifBlank { null }

                HttpSection(
                    label = "Response",
                    content = responseContent,
                    expanded = responseExpanded,
                    onToggle = { responseExpanded = !responseExpanded },
                    onCopy = onCopyResponse,
                )
            }
        }
    }
}

@Composable
private fun HttpSection(
    label: String,
    content: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = content ?: "(none)",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "success" -> "Success" to MaterialTheme.colorScheme.secondary
        "error" -> "Error" to MaterialTheme.colorScheme.error
        else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
