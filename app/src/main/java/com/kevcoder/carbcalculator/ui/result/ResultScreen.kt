package com.kevcoder.carbcalculator.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.result

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Result") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onDiscard(onDiscard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (result == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No result available")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Food photo
            item {
                result.imagePath?.let { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = "Food photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Description
            result.foodDescription?.let { desc ->
                item {
                    Text(desc, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Glucose reading (non-blocking — may still be loading)
            item {
                uiState.glucose?.let { glucose ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Current Glucose: ${glucose.mgDl} mg/dL",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            // Food items
            items(result.items) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${item.estimatedCarbs}g carbs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }

            // Total
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total Carbs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${result.totalCarbs}g",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Error
            uiState.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            // Actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onDiscard(onDiscard) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Discard")
                    }
                    Button(
                        onClick = { viewModel.onSave(onSaved) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
