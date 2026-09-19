package com.ecosystem.research.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.ResearchProject
import com.ecosystem.research.core.search.SearchEntityType
import com.ecosystem.research.core.search.SearchFilter
import com.ecosystem.research.core.search.SearchResult
import com.ecosystem.research.ui.components.RelationshipBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSearchScreen(
    projects: List<ResearchProject>,
    initialProjectId: String? = null,
    initialQuery: String? = null,
    onBack: () -> Unit,
    onNavigateToSourceDocument: (sourceId: String, pageNumber: Int?) -> Unit,
    onNavigateToClaim: (projectId: String, claimId: String) -> Unit,
    onNavigateToProject: (projectId: String) -> Unit,
    onExecuteSearch: suspend (query: String, filter: SearchFilter) -> List<SearchResult>
) {
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var selectedEntityType by remember { mutableStateOf(SearchEntityType.ALL) }
    var selectedProjectId by remember { mutableStateOf(initialProjectId) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank()) {
            searchResults = emptyList()
            return
        }
        coroutineScope.launch {
            isSearching = true
            val filter = SearchFilter(
                entityType = selectedEntityType,
                projectId = selectedProjectId
            )
            searchResults = onExecuteSearch(q, filter)
            isSearching = false
        }
    }

    LaunchedEffect(query, selectedEntityType, selectedProjectId) {
        doSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cross-Corpus Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search excerpts, claims, sources, authors, inbox...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val entityTypes = listOf(
                    SearchEntityType.ALL to "All",
                    SearchEntityType.EVIDENCE to "Evidence",
                    SearchEntityType.SOURCE to "Sources",
                    SearchEntityType.CLAIM to "Claims",
                    SearchEntityType.INBOX to "Inbox"
                )
                entityTypes.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedEntityType == type,
                        onClick = { selectedEntityType = type },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Project Scope Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Project Scope: ", style = MaterialTheme.typography.labelMedium)
                Box {
                    TextButton(onClick = { projectDropdownExpanded = true }) {
                        val currentProjectName = projects.find { it.id == selectedProjectId }?.title ?: "All Projects"
                        Text(currentProjectName, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = projectDropdownExpanded,
                        onDismissRequest = { projectDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Projects") },
                            onClick = {
                                selectedProjectId = null
                                projectDropdownExpanded = false
                            }
                        )
                        projects.forEach { proj ->
                            DropdownMenuItem(
                                text = { Text(proj.title) },
                                onClick = {
                                    selectedProjectId = proj.id
                                    projectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (query.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SavedSearch,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Type keywords to search across empirical evidence, sources, claims, and inbox ideas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching results found for \"$query\".")
                }
            } else {
                Text(
                    text = "${searchResults.size} matches found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { "${it.entityType}_${it.id}" }) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = {
                                when (item.entityType) {
                                    SearchEntityType.EVIDENCE -> {
                                        item.sourceId?.let { sId ->
                                            onNavigateToSourceDocument(sId, item.pageNumber)
                                        }
                                    }
                                    SearchEntityType.SOURCE -> {
                                        item.sourceId?.let { sId ->
                                            onNavigateToSourceDocument(sId, null)
                                        }
                                    }
                                    SearchEntityType.CLAIM -> {
                                        item.projectId?.let { pId ->
                                            onNavigateToClaim(pId, item.id)
                                        }
                                    }
                                    SearchEntityType.INBOX -> {
                                        item.projectId?.let { pId ->
                                            onNavigateToProject(pId)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(item.entityType.name, fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (item.entityType) {
                                SearchEntityType.EVIDENCE -> MaterialTheme.colorScheme.primaryContainer
                                SearchEntityType.SOURCE -> MaterialTheme.colorScheme.secondaryContainer
                                SearchEntityType.CLAIM -> MaterialTheme.colorScheme.tertiaryContainer
                                SearchEntityType.INBOX -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.matchedField,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.relationshipType != null) {
                    RelationshipBadge(relationship = item.relationshipType)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )

            if (item.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subText = buildString {
                    if (item.projectName != null) append(item.projectName)
                    if (item.pageNumber != null) {
                        if (isNotEmpty()) append(" • ")
                        append("Page ${item.pageNumber}")
                    }
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
