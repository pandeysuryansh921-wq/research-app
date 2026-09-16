package com.ecosystem.research.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalInboxScreen(
    inboxItems: List<InboxItem>,
    projects: List<ResearchProject>,
    onBack: () -> Unit,
    onProcessItem: (itemId: String, projectId: String, asType: String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onManualCapture: (rawType: InboxItemType, content: String) -> Unit
) {
    var showCaptureDialog by remember { mutableStateOf(false) }
    var captureText by remember { mutableStateOf("") }
    var captureType by remember { mutableStateOf(InboxItemType.TEXT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Research Inbox", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCaptureDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Quick Capture") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Frictionless capture from Boloji (voice), Likhoji (selection), Stylus Notes, or quick notes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (inboxItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Inbox is zero! All items triaged.")
                        }
                    }
                }
            } else {
                items(inboxItems, key = { it.id }) { item ->
                    InboxItemCard(
                        item = item,
                        projects = projects,
                        onProcess = { projId, asType -> onProcessItem(item.id, projId, asType) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    if (showCaptureDialog) {
        AlertDialog(
            onDismissRequest = { showCaptureDialog = false },
            title = { Text("Quick Capture to Inbox") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(InboxItemType.TEXT, InboxItemType.DOI, InboxItemType.URL).forEach { type ->
                            FilterChip(
                                selected = captureType == type,
                                onClick = { captureType = type },
                                label = { Text(type.name, fontSize = 10.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = captureText,
                        onValueChange = { captureText = it },
                        label = { Text("Content (Note, DOI, URL, or Idea)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (captureText.isNotBlank()) {
                            onManualCapture(captureType, captureText.trim())
                            captureText = ""
                            showCaptureDialog = false
                        }
                    }
                ) {
                    Text("Capture")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCaptureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InboxItemCard(
    item: InboxItem,
    projects: List<ResearchProject>,
    onProcess: (projectId: String, asType: String) -> Unit,
    onDelete: () -> Unit
) {
    var showProjectPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${item.sourceApp.uppercase()} • ${item.rawType.name}", fontSize = 10.sp) }
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = item.rawContent, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { showProjectPicker = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Assign to Project", fontSize = 12.sp)
                }
            }
        }
    }

    if (showProjectPicker) {
        AlertDialog(
            onDismissRequest = { showProjectPicker = false },
            title = { Text("Select Project") },
            text = {
                if (projects.isEmpty()) {
                    Text("No projects created yet. Create a project first.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projects.forEach { p ->
                            OutlinedButton(
                                onClick = {
                                    onProcess(p.id, "SOURCE")
                                    showProjectPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(p.title, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProjectPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
