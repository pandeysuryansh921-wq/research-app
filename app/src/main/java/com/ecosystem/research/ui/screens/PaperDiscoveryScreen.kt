package com.ecosystem.research.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.ResearchProject
import com.ecosystem.research.core.model.Source
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDiscoveryScreen(
    projects: List<ResearchProject>,
    onBack: () -> Unit,
    onSearchPubMed: suspend (String) -> Result<List<Source>>,
    onSearchArxiv: suspend (String) -> Result<List<Source>>,
    onImportSource: (Source, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf("pubmed") } // "pubmed" or "arxiv"
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Source>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var sourceToImport by remember { mutableStateOf<Source?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Literature Discovery", fontWeight = FontWeight.Bold) },
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
            // Service Selector & Query Input
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedService == "pubmed",
                    onClick = { selectedService = "pubmed" },
                    label = { Text("PubMed (Clinical / Bio)") }
                )
                FilterChip(
                    selected = selectedService == "arxiv",
                    onClick = { selectedService = "arxiv" },
                    label = { Text("arXiv (AI / CS / Quantitative)") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search topics, keywords, authors, or DOIs") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            errorMessage = null
                            coroutineScope.launch {
                                val result = if (selectedService == "pubmed") {
                                    onSearchPubMed(searchQuery.trim())
                                } else {
                                    onSearchArxiv(searchQuery.trim())
                                }
                                isSearching = false
                                result.fold(
                                    onSuccess = { searchResults = it },
                                    onFailure = { errorMessage = it.localizedMessage ?: "Search failed" }
                                )
                            }
                        }
                    },
                    enabled = !isSearching && searchQuery.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(searchResults) { source ->
                    DiscoveryResultCard(
                        source = source,
                        onImport = { sourceToImport = source }
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    sourceToImport?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToImport = null },
            title = { Text("Import to Project") },
            text = {
                if (projects.isEmpty()) {
                    Text("Please create a project on the Dashboard before importing papers.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select project to attach: \"${source.title}\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                        projects.forEach { p ->
                            OutlinedButton(
                                onClick = {
                                    onImportSource(source, p.id)
                                    sourceToImport = null
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
                TextButton(onClick = { sourceToImport = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DiscoveryResultCard(
    source: Source,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val meta = listOfNotNull(
                source.authors.firstOrNull()?.let { "$it et al." },
                source.journal,
                source.year?.toString()
            ).joinToString(" • ")
            Text(text = meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                source.externalIds.canonicalKey()?.let { idStr ->
                    Text(
                        text = idStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))

                Button(
                    onClick = onImport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Import to Project", fontSize = 12.sp)
                }
            }
        }
    }
}
