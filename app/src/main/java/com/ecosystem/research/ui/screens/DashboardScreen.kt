package com.ecosystem.research.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.ProjectStatus
import com.ecosystem.research.core.model.ResearchProject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    projects: List<ResearchProject>,
    pendingInboxCount: Int,
    onProjectClick: (String) -> Unit,
    onNewProject: (String, String, com.ecosystem.research.core.model.Discipline) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenFile: () -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newQuestion by remember { mutableStateOf("") }
    var selectedDiscipline by remember { mutableStateOf(com.ecosystem.research.core.model.Discipline.MEDICAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Research Command Center", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Universal Cross-Corpus Search")
                    }
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "AI & API Keys")
                    }
                    IconButton(onClick = onOpenFile) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open Document (PDF/MD)")
                    }
                    IconButton(onClick = onOpenDiscovery) {
                        Icon(Icons.Default.TravelExplore, contentDescription = "Discover Papers")
                    }
                    BadgedBox(
                        badge = {
                            if (pendingInboxCount > 0) {
                                Badge { Text(pendingInboxCount.toString()) }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = onOpenInbox) {
                            Icon(Icons.Default.Inbox, contentDescription = "Universal Inbox")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewProjectDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DISCOVER → VERIFY → CONNECT → SYNTHESIZE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Metric Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Active Projects",
                        value = projects.size.toString(),
                        icon = Icons.Default.Folder,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Pending Inbox",
                        value = pendingInboxCount.toString(),
                        icon = Icons.Default.Inbox,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenInbox
                    )
                }
            }

            item {
                Text(
                    text = "Research Projects",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (projects.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No research projects yet", fontWeight = FontWeight.SemiBold)
                            Text("Start by formulating a research question or importing papers.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showNewProjectDialog = true }) {
                                Text("Create First Project")
                            }
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project = project, onClick = { onProjectClick(project.id) })
                }
            }
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Create Research Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Project Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newQuestion,
                        onValueChange = { newQuestion = it },
                        label = { Text("Primary Research Question") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Discipline & Inquiry Framework:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.ecosystem.research.core.model.Discipline.values().forEach { disc ->
                            val label = when (disc) {
                                com.ecosystem.research.core.model.Discipline.MEDICAL -> "Medical (PICO)"
                                com.ecosystem.research.core.model.Discipline.CS -> "CS (Benchmark)"
                                com.ecosystem.research.core.model.Discipline.BIOINFORMATICS -> "Bioinformatics (Genomics)"
                                com.ecosystem.research.core.model.Discipline.SOCIAL_SCIENCE -> "Social Science"
                                com.ecosystem.research.core.model.Discipline.ENGINEERING -> "Engineering"
                            }
                            FilterChip(
                                selected = selectedDiscipline == disc,
                                onClick = { selectedDiscipline = disc },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onNewProject(newTitle.trim(), newQuestion.trim(), selectedDiscipline)
                            newTitle = ""
                            newQuestion = ""
                            selectedDiscipline = com.ecosystem.research.core.model.Discipline.MEDICAL
                            showNewProjectDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showApiKeyDialog) {
        com.ecosystem.research.ui.components.ApiKeySettingsDialog(
            onDismiss = { showApiKeyDialog = false }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectCard(
    project: ResearchProject,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(project.status.name) }
                )
            }
            if (!project.primaryQuestion.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Q: ${project.primaryQuestion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
