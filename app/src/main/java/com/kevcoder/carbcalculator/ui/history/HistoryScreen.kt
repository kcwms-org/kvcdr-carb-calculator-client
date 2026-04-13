package com.kevcoder.carbcalculator.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kevcoder.carbcalculator.domain.model.CarbLog
import com.kevcoder.carbcalculator.domain.model.SubmissionLog
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val historyItems by viewModel.historyItems.collectAsState()
    val expandedItemId by viewModel.expandedItemId.collectAsState()
    val expandedSubmissions by viewModel.expandedSubmissions.collectAsState()
    val viewingImageLog by viewModel.viewingImageLog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No logs yet. Take a photo to get started!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(historyItems, key = { item ->
                    when (item) {
                        is HistoryItem.SuccessfulLog -> "carb-${item.carbLog.id}"
                        is HistoryItem.ErrorLog -> "error-${item.submissionLog.id}"
                    }
                }) { item ->
                    when (item) {
                        is HistoryItem.SuccessfulLog -> {
                            val isExpanded = expandedItemId == "carb-${item.carbLog.id}"
                            CarbLogCard(
                                log = item.carbLog,
                                isExpanded = isExpanded,
                                submissions = if (isExpanded) expandedSubmissions else emptyList(),
                                onToggleExpand = { viewModel.toggleExpand("carb-${item.carbLog.id}") },
                                onDelete = { viewModel.deleteSuccessfulLog(item.carbLog.id) },
                                onImageClick = { viewModel.onImageClick(item.carbLog) },
                            )
                        }
                        is HistoryItem.ErrorLog -> {
                            val isExpanded = expandedItemId == "error-${item.submissionLog.id}"
                            ErrorLogCard(
                                submissionLog = item.submissionLog,
                                isExpanded = isExpanded,
                                onToggleExpand = { viewModel.toggleExpand("error-${item.submissionLog.id}") },
                                onDelete = { viewModel.deleteErrorLog(item.submissionLog.id) },
                            )
                        }
                    }
                }
            }
        }

        // Full-screen image viewer
        viewingImageLog?.let { log ->
            FullScreenImageViewer(log = log, onDismiss = { viewModel.onImageViewerDismiss() })
        }
    }
}

@Composable
private fun ErrorLogCard(
    submissionLog: SubmissionLog,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submissionLog.foodDescription ?: "Failed submission",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = submissionLog.errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                    submissionLog.httpStatusCode?.let { code ->
                        Text(
                            text = "HTTP $code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = dateFormat.format(Date(submissionLog.requestTimestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Chevron row to expand/collapse details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Error details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val context = LocalContext.current
                    var requestExpanded by remember { mutableStateOf(false) }
                    var responseExpanded by remember { mutableStateOf(false) }

                    submissionLog.requestHeaders?.let { headers ->
                        HttpSection(
                            label = "Request",
                            content = headers,
                            expanded = requestExpanded,
                            onToggle = { requestExpanded = !requestExpanded },
                            onCopy = { copyToClipboard(context, "request_${submissionLog.id}", headers) },
                        )
                    }

                    submissionLog.responseHeaders?.let { headers ->
                        HttpSection(
                            label = "Response",
                            content = headers,
                            expanded = responseExpanded,
                            onToggle = { responseExpanded = !responseExpanded },
                            onCopy = { copyToClipboard(context, "response_${submissionLog.id}", headers) },
                        )
                    }

                    submissionLog.responseBody?.let { body ->
                        Text(
                            text = "Response body: $body",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarbLogCard(
    log: CarbLog,
    isExpanded: Boolean,
    submissions: List<SubmissionLog>,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Image with click handler - prefer imageData if available
                if (log.imageData != null || log.thumbnailPath != null) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clickable(onClick = onImageClick),
                    ) {
                        if (log.imageData != null) {
                            val bitmap = remember { BitmapFactory.decodeByteArray(log.imageData, 0, log.imageData.size) }
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Food thumbnail",
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AsyncImage(
                                model = log.thumbnailPath,
                                contentDescription = "Food thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.foodDescription ?: log.items.firstOrNull()?.name ?: "Food",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = "Total: ${log.totalCarbs}g carbs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    log.glucose?.let { glucose ->
                        Text(
                            text = "Glucose: ${glucose.mgDl} mg/dL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Chevron row to expand/collapse submission detail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Submission detail",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                HorizontalDivider()
                if (submissions.isEmpty()) {
                    Text(
                        text = "No submission data recorded.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    submissions.forEach { sub ->
                        SubmissionDetail(submission = sub)
                        if (sub != submissions.last()) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionDetail(submission: SubmissionLog) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    var requestExpanded by remember { mutableStateOf(false) }
    var responseExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(status = submission.status)
            IconButton(
                onClick = { copyToClipboard(context, "submission_${submission.id}", submission.toJsonString()) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy as JSON",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = dateFormatter.format(Date(submission.requestTimestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        submission.foodDescription?.let { desc ->
            Text(desc, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }

        if (submission.status == "success" && submission.items.isNotEmpty()) {
            submission.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${item.estimatedCarbs}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            submission.totalCarbs?.let { carbs ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total carbs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("${carbs}g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        submission.errorMessage?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (!submission.requestHeaders.isNullOrBlank() || !submission.responseHeaders.isNullOrBlank() || !submission.responseBody.isNullOrBlank()) {
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            HttpSection(
                label = "Request",
                content = submission.requestHeaders,
                expanded = requestExpanded,
                onToggle = { requestExpanded = !requestExpanded },
                onCopy = { copyToClipboard(context, "request_${submission.id}", submission.requestHeaders ?: "") },
            )

            val responseContent = buildString {
                submission.responseHeaders?.let { append(it).append("\n\n") }
                submission.responseBody?.let { append(it) }
            }.ifBlank { null }

            HttpSection(
                label = "Response",
                content = responseContent,
                expanded = responseExpanded,
                onToggle = { responseExpanded = !responseExpanded },
                onCopy = { copyToClipboard(context, "response_${submission.id}", responseContent ?: "") },
            )
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
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun SubmissionLog.toJsonString(): String {
    val obj = org.json.JSONObject()
    obj.put("id", id)
    obj.put("carbLogId", carbLogId)
    obj.put("requestTimestamp", requestTimestamp)
    obj.put("imagePath", imagePath ?: org.json.JSONObject.NULL)
    obj.put("imageSizeBytes", imageSizeBytes ?: org.json.JSONObject.NULL)
    obj.put("foodDescription", foodDescription ?: org.json.JSONObject.NULL)
    obj.put("status", status)
    obj.put("totalCarbs", totalCarbs ?: org.json.JSONObject.NULL)
    obj.put("errorMessage", errorMessage ?: org.json.JSONObject.NULL)
    obj.put("responseTimestamp", responseTimestamp ?: org.json.JSONObject.NULL)
    obj.put("requestHeaders", requestHeaders ?: org.json.JSONObject.NULL)
    obj.put("responseHeaders", responseHeaders ?: org.json.JSONObject.NULL)
    obj.put("responseBody", responseBody ?: org.json.JSONObject.NULL)
    val itemsArray = org.json.JSONArray()
    items.forEach { item ->
        val itemObj = org.json.JSONObject()
        itemObj.put("name", item.name)
        itemObj.put("estimatedCarbs", item.estimatedCarbs)
        itemsArray.put(itemObj)
    }
    obj.put("items", itemsArray)
    return obj.toString(2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenImageViewer(
    log: CarbLog,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Close button top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Full-size image in the center
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (log.imageData != null) {
                    val bitmap = remember { BitmapFactory.decodeByteArray(log.imageData, 0, log.imageData.size) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Full-size food photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    log.thumbnailPath?.let { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = "Food photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            // Metadata bottom sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Food items
                    if (log.items.isNotEmpty()) {
                        Text(
                            text = "Items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        log.items.forEach { item ->
                            Text(
                                text = "${item.name} - ${item.estimatedCarbs}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Total carbs
                    Text(
                        text = "Total: ${log.totalCarbs}g carbs",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Glucose if available
                    log.glucose?.let { glucose ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Glucose: ${glucose.mgDl} mg/dL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
