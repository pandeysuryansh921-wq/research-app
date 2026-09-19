package com.ecosystem.research.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.ecosystem.EcosystemBridge
import com.ecosystem.research.core.model.*
import com.ecosystem.research.ui.components.ProvenanceBadge
import com.ecosystem.research.ui.components.ReadingStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(
    project: ResearchProject,
    sources: List<Source>,
    onBack: () -> Unit,
    onOpenEvidenceBoard: () -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenVisualGraph: () -> Unit,
    onExportManuscript: () -> Unit,
    onOpenDocument: (Source) -> Unit,
    onAttachFile: (String) -> Unit,
    onSynthesizeReview: () -> Unit,
    onUpdateReadingStatus: (String, ReadingStatus) -> Unit,
    onAddPaperManually: (String, String?, Int?, String?) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabStatuses = listOf(
        null, // All
        ReadingStatus.TO_SCREEN,
        ReadingStatus.TO_READ,
        ReadingStatus.READING,
        ReadingStatus.READ,
        ReadingStatus.KEY_PAPER
    )

    var showAddPaperDialog by remember { mutableStateOf(false) }
    var paperTitle by remember { mutableStateOf("") }
    var paperJournal by remember { mutableStateOf("") }
    var paperYear by remember { mutableStateOf("") }
    var paperDoi by remember { mutableStateOf("") }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val filteredSources = remember(sources, selectedTab) {
        val status = tabStatuses[selectedTab]
        if (status == null) sources else sources.filter { it.readingStatus == status }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.title, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            text = "${sources.size} Sources in Library",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSynthesizeReview) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Synthesize Review")
                    }
                    IconButton(onClick = onOpenVisualGraph) {
                        Icon(Icons.Default.Hub, contentDescription = "Visual Evidence Graph")
                    }
                    IconButton(onClick = onExportManuscript) {
                        Icon(Icons.Default.Publish, contentDescription = "Export to Likhoji")
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Discover Online Papers") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenDiscovery()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Literature Comparison Matrix") },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenMatrix()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Claim Evidence Board") },
                                leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenEvidenceBoard()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPaperDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Paper") }
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
            // Research Question Formulation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Research Question (PICO)", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = project.primaryQuestion ?: "No structured question defined yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Quick Access Ecosystem Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenVisualGraph,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Evidence Graph", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onExportManuscript,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export to Likhoji", fontSize = 12.sp)
                    }
                }
            }

            // Reading Queue Status Filter Tabs
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp
                ) {
                    val tabTitles = listOf("All (${sources.size})", "To Screen", "To Read", "Reading", "Read", "Key")
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            if (filteredSources.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No papers found in this reading queue state.")
                    }
                }
            } else {
                items(filteredSources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        onOpenDocument = { onOpenDocument(source) },
                        onAttachFile = { onAttachFile(source.id) },
                        onStatusChange = { newStatus ->
                            onUpdateReadingStatus(source.id, newStatus)
                        },
                        onPushToProductivity = {
                            val success = EcosystemBridge.createReadingTaskInProductivity(
                                context = context,
                                sourceTitle = source.title,
                                sourceId = source.id
                            )
                            if (success) {
                                Toast.makeText(context, "Reading task sent to Productivity!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not open Productivity app.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    if (showAddPaperDialog) {
        AlertDialog(
            onDismissRequest = { showAddPaperDialog = false },
            title = { Text("Add Paper to Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = paperTitle,
                        onValueChange = { paperTitle = it },
                        label = { Text("Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paperJournal,
                        onValueChange = { paperJournal = it },
                        label = { Text("Journal") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paperYear,
                        onValueChange = { paperYear = it },
                        label = { Text("Year") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paperDoi,
                        onValueChange = { paperDoi = it },
                        label = { Text("DOI (e.g. 10.1016/...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (paperTitle.isNotBlank()) {
                            onAddPaperManually(
                                paperTitle.trim(),
                                paperJournal.trim().ifBlank { null },
                                paperYear.toIntOrNull(),
                                paperDoi.trim().ifBlank { null }
                            )
                            paperTitle = ""
                            paperJournal = ""
                            paperYear = ""
                            paperDoi = ""
                            showAddPaperDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaperDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SourceCard(
    source: Source,
    onOpenDocument: () -> Unit,
    onAttachFile: () -> Unit,
    onStatusChange: (ReadingStatus) -> Unit,
    onPushToProductivity: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (source.localPdfPath != null) "Replace Attached File" else "Attach File (PDF/MD)") },
                            onClick = {
                                onAttachFile()
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Push to Productivity App") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            onClick = {
                                onPushToProductivity()
                                expandedMenu = false
                            }
                        )
                        HorizontalDivider()
                        ReadingStatus.values().forEach { status ->
                            DropdownMenuItem(
                                text = { Text("Mark as ${status.name.replace('_', ' ')}") },
                                onClick = {
                                    onStatusChange(status)
                                    expandedMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            if (source.authors.isNotEmpty() || source.year != null || source.journal != null) {
                val metadata = listOfNotNull(
                    source.authors.firstOrNull()?.let { "$it et al." },
                    source.journal,
                    source.year?.toString()
                ).joinToString(" • ")
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!source.abstractText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = source.abstractText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingStatusBadge(status = source.readingStatus)
                    ProvenanceBadge(provenance = source.provenance)
                }

                if (source.localPdfPath != null) {
                    Button(
                        onClick = onOpenDocument,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Paper", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAttachFile,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach File", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
