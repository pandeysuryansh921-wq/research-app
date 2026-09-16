package com.ecosystem.research.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.*
import com.ecosystem.research.ui.components.ProvenanceBadge
import com.ecosystem.research.ui.components.RelationshipBadge
import com.ecosystem.research.ui.theme.ContradictsRed
import com.ecosystem.research.ui.theme.ContradictsRedBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceBoardScreen(
    claims: List<Claim>,
    evidenceMap: Map<String, List<Evidence>>,
    sources: List<Source>,
    onBack: () -> Unit,
    onAddClaim: (String) -> Unit,
    onAddEvidenceToClaim: (claimId: String, sourceId: String, page: Int, excerpt: String, interpretation: String, relationship: EvidenceRelationship) -> Unit
) {
    var showAddClaimDialog by remember { mutableStateOf(false) }
    var newClaimText by remember { mutableStateOf("") }

    var targetClaimForEvidence by remember { mutableStateOf<Claim?>(null) }
    var selectedSourceId by remember { mutableStateOf("") }
    var evidencePage by remember { mutableStateOf("1") }
    var evidenceExcerpt by remember { mutableStateOf("") }
    var evidenceInterpretation by remember { mutableStateOf("") }
    var selectedRelationship by remember { mutableStateOf(EvidenceRelationship.SUPPORTS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claim ↔ Evidence Board", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddClaimDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Claim")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddClaimDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Claim") }
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
                Text(
                    text = "A claim represents a proposition supported or contradicted by empirical evidence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (claims.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No claims formulated yet.")
                            Button(onClick = { showAddClaimDialog = true }) {
                                Text("Propose First Claim")
                            }
                        }
                    }
                }
            } else {
                items(claims, key = { it.id }) { claim ->
                    val linkedEv = evidenceMap[claim.id] ?: emptyList()
                    val hasSupports = linkedEv.any { it.relationshipType == EvidenceRelationship.SUPPORTS }
                    val hasContradicts = linkedEv.any { it.relationshipType == EvidenceRelationship.CONTRADICTS }
                    val isContested = hasSupports && hasContradicts

                    ClaimSectionCard(
                        claim = claim,
                        isContested = isContested,
                        evidenceList = linkedEv,
                        sources = sources,
                        onAddEvidence = { targetClaimForEvidence = claim }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    if (showAddClaimDialog) {
        AlertDialog(
            onDismissRequest = { showAddClaimDialog = false },
            title = { Text("Propose Claim") },
            text = {
                OutlinedTextField(
                    value = newClaimText,
                    onValueChange = { newClaimText = it },
                    label = { Text("Claim Proposition (e.g. Model generalizability is limited...)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newClaimText.isNotBlank()) {
                            onAddClaim(newClaimText.trim())
                            newClaimText = ""
                            showAddClaimDialog = false
                        }
                    }
                ) {
                    Text("Save Claim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddClaimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    targetClaimForEvidence?.let { claim ->
        AlertDialog(
            onDismissRequest = { targetClaimForEvidence = null },
            title = { Text("Link Evidence to Claim") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Claim: \"${claim.proposition}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Source selector
                    Text("Select Source Paper:", style = MaterialTheme.typography.labelSmall)
                    if (sources.isEmpty()) {
                        Text("No sources available in project. Add papers first.", color = MaterialTheme.colorScheme.error)
                    } else {
                        var sourceMenuExpanded by remember { mutableStateOf(false) }
                        val activeSource = sources.firstOrNull { it.id == selectedSourceId } ?: sources.first()
                        if (selectedSourceId.isEmpty()) selectedSourceId = activeSource.id

                        OutlinedButton(
                            onClick = { sourceMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(activeSource.title, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false }
                        ) {
                            sources.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.title, maxLines = 1) },
                                    onClick = {
                                        selectedSourceId = s.id
                                        sourceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = evidencePage,
                        onValueChange = { evidencePage = it },
                        label = { Text("Page Number *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = evidenceExcerpt,
                        onValueChange = { evidenceExcerpt = it },
                        label = { Text("Exact Excerpt / Quote *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = evidenceInterpretation,
                        onValueChange = { evidenceInterpretation = it },
                        label = { Text("Researcher Interpretation (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Relationship Type:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            EvidenceRelationship.SUPPORTS,
                            EvidenceRelationship.CONTRADICTS,
                            EvidenceRelationship.BACKGROUND
                        ).forEach { rel ->
                            FilterChip(
                                selected = selectedRelationship == rel,
                                onClick = { selectedRelationship = rel },
                                label = { Text(rel.name, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedSourceId.isNotBlank() && evidenceExcerpt.isNotBlank()) {
                            onAddEvidenceToClaim(
                                claim.id,
                                selectedSourceId,
                                evidencePage.toIntOrNull() ?: 1,
                                evidenceExcerpt.trim(),
                                evidenceInterpretation.trim(),
                                selectedRelationship
                            )
                            evidenceExcerpt = ""
                            evidenceInterpretation = ""
                            targetClaimForEvidence = null
                        }
                    }
                ) {
                    Text("Attach Evidence")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetClaimForEvidence = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ClaimSectionCard(
    claim: Claim,
    isContested: Boolean,
    evidenceList: List<Evidence>,
    sources: List<Source>,
    onAddEvidence: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Contradiction alert header if contested
            if (isContested) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ContradictsRedBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ContradictsRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Contradiction Detected: Studies report opposing findings for this claim!",
                        color = ContradictsRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = claim.proposition,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAddEvidence) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Evidence", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            if (evidenceList.isEmpty()) {
                Text(
                    text = "No empirical evidence linked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    evidenceList.forEach { ev ->
                        val source = sources.firstOrNull { it.id == ev.sourceId }
                        EvidenceCardItem(evidence = ev, sourceTitle = source?.title ?: "Source [${ev.sourceId}]")
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceCardItem(
    evidence: Evidence,
    sourceTitle: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RelationshipBadge(relationship = evidence.relationshipType)
                ProvenanceBadge(provenance = evidence.provenance)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\"${evidence.excerptText}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (!evidence.userInterpretation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Analysis: ${evidence.userInterpretation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$sourceTitle (Page ${evidence.location.pageNumber})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Open Original",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        // Open source location
                    }
                )
            }
        }
    }
}
