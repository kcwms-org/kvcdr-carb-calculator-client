package com.kevcoder.carbcalculator.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSubmissionScreen(
    onSaved: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: EditSubmissionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBusy = uiState.isSubmitting || uiState.isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Result") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onDiscard(onDiscard) }) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.foodDescription,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    label = { Text("Food description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
            }

            item {
                Text("Food items", style = MaterialTheme.typography.titleSmall)
            }

            items(uiState.items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { viewModel.onItemNameChange(item.id, it) },
                        label = { Text("Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = item.estimatedCarbs,
                        onValueChange = { viewModel.onItemCarbsChange(item.id, it) },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    IconButton(onClick = { viewModel.onRemoveItem(item.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                TextButton(
                    onClick = { viewModel.onAddItem() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add item")
                }
            }

            uiState.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onSaveEdits(onSaved) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save Edits")
                        }
                    }
                    Button(
                        onClick = { viewModel.onResubmit(onSaved) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Re-analyze")
                        }
                    }
                }
            }
        }
    }
}
