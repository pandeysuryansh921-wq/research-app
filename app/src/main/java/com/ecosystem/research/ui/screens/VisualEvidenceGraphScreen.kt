package com.ecosystem.research.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.Claim
import com.ecosystem.research.core.model.ClaimStatus
import com.ecosystem.research.core.model.EvidenceRelationship
import com.ecosystem.research.core.model.Source
import com.ecosystem.research.core.repository.ResearchRepository

enum class GraphFilter {
    ALL, CONTRADICTIONS, SUPPORTED, ORPHANS
}

sealed class SelectedNode {
    data class SourceNode(val source: Source) : SelectedNode()
    data class ClaimNode(val claim: Claim) : SelectedNode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualEvidenceGraphScreen(
    projectId: String,
    repository: ResearchRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSource: (sourceId: String) -> Unit
) {
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var claims by remember { mutableStateOf<List<Claim>>(emptyList()) }
    var edges by remember { mutableStateOf<List<ResearchRepository.GraphEdge>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(GraphFilter.ALL) }
    var selectedNode by remember { mutableStateOf<SelectedNode?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(projectId) {
        sources = repository.getSourcesByProject(projectId)
        claims = repository.getClaimsByProject(projectId)
        edges = repository.getGraphEdgesForProject(projectId)
        isLoading = false
    }

    val filteredClaims = remember(claims, edges, selectedFilter) {
        when (selectedFilter) {
            GraphFilter.ALL -> claims
            GraphFilter.CONTRADICTIONS -> {
                claims.filter { claim ->
                    val connectedEdges = edges.filter { it.claimId == claim.id }
                    val hasSup = connectedEdges.any { it.relationshipType == EvidenceRelationship.SUPPORTS }
                    val hasCon = connectedEdges.any { it.relationshipType == EvidenceRelationship.CONTRADICTS }
                    hasSup && hasCon
                }
            }
            GraphFilter.SUPPORTED -> {
                claims.filter { it.status == ClaimStatus.SUPPORTED }
            }
            GraphFilter.ORPHANS -> {
                claims.filter { claim -> edges.none { it.claimId == claim.id } }
            }
        }
    }

    val filteredSources = remember(sources, edges, filteredClaims, selectedFilter) {
        if (selectedFilter == GraphFilter.ALL) {
            sources
        } else {
            val validClaimIds = filteredClaims.map { it.id }.toSet()
            val relevantSourceIds = edges.filter { it.claimId in validClaimIds }.map { it.sourceId }.toSet()
            sources.filter { it.id in relevantSourceIds }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Visual Evidence Graph", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredSources.size} Sources • ${filteredClaims.size} Claims • ${edges.size} Edges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == GraphFilter.ALL,
                        onClick = { selectedFilter = GraphFilter.ALL },
                        label = { Text("All Nodes") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == GraphFilter.CONTRADICTIONS,
                        onClick = { selectedFilter = GraphFilter.CONTRADICTIONS },
                        label = { Text("⚡ Contradictions") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == GraphFilter.SUPPORTED,
                        onClick = { selectedFilter = GraphFilter.SUPPORTED },
                        label = { Text("✓ Supported") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == GraphFilter.ORPHANS,
                        onClick = { selectedFilter = GraphFilter.ORPHANS },
                        label = { Text("⚠️ Orphan Claims") }
                    )
                }
            }

            // Legend Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF0284C7), CircleShape))
                    Text("Sources", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF16A34A), RoundedCornerShape(2.dp)))
                    Text("Supports", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFDC2626), RoundedCornerShape(2.dp)))
                    Text("Contradicts", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFEA580C), RoundedCornerShape(2.dp)))
                    Text("Mixed", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredClaims.isEmpty() && filteredSources.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No nodes match the selected filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Interactive Bipartite Canvas Layout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BipartiteGraphView(
                        sources = filteredSources,
                        claims = filteredClaims,
                        edges = edges,
                        selectedNode = selectedNode,
                        onSelectNode = { selectedNode = it }
                    )
                }

                // Detail Sheet for Selected Node
                selectedNode?.let { node ->
                    SelectedNodeDetailCard(
                        node = node,
                        edges = edges,
                        sources = sources,
                        claims = claims,
                        onClose = { selectedNode = null },
                        onNavigateToSource = onNavigateToSource
                    )
                }
            }
        }
    }
}

@Composable
fun BipartiteGraphView(
    sources: List<Source>,
    claims: List<Claim>,
    edges: List<ResearchRepository.GraphEdge>,
    selectedNode: SelectedNode?,
    onSelectNode: (SelectedNode?) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val sourceX = width * 0.22f
        val claimX = width * 0.78f

        val sourceSpacing = height / (sources.size + 1).coerceAtLeast(2)
        val claimSpacing = height / (claims.size + 1).coerceAtLeast(2)

        val sourcePositions = remember(sources, height) {
            sources.mapIndexed { idx, source ->
                source.id to Offset(sourceX, sourceSpacing * (idx + 1))
            }.toMap()
        }

        val claimPositions = remember(claims, height) {
            claims.mapIndexed { idx, claim ->
                claim.id to Offset(claimX, claimSpacing * (idx + 1))
            }.toMap()
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sources, claims) {
                    detectTapGestures { tapOffset ->
                        val tappedSource = sources.find { source ->
                            val pos = sourcePositions[source.id] ?: return@find false
                            (tapOffset - pos).getDistance() <= 32f
                        }
                        if (tappedSource != null) {
                            onSelectNode(SelectedNode.SourceNode(tappedSource))
                            return@detectTapGestures
                        }

                        val tappedClaim = claims.find { claim ->
                            val pos = claimPositions[claim.id] ?: return@find false
                            (tapOffset - pos).getDistance() <= 32f
                        }
                        if (tappedClaim != null) {
                            onSelectNode(SelectedNode.ClaimNode(tappedClaim))
                            return@detectTapGestures
                        }

                        onSelectNode(null)
                    }
                }
        ) {
            // Draw Edges
            edges.forEach { edge ->
                val start = sourcePositions[edge.sourceId]
                val end = claimPositions[edge.claimId]

                if (start != null && end != null) {
                    val isHighlighted = when (selectedNode) {
                        is SelectedNode.SourceNode -> selectedNode.source.id == edge.sourceId
                        is SelectedNode.ClaimNode -> selectedNode.claim.id == edge.claimId
                        null -> true
                    }

                    val strokeColor = when (edge.relationshipType) {
                        EvidenceRelationship.SUPPORTS -> Color(0xFF16A34A)
                        EvidenceRelationship.CONTRADICTS -> Color(0xFFDC2626)
                        EvidenceRelationship.MIXED -> Color(0xFFEA580C)
                        else -> Color(0xFF64748B)
                    }.copy(alpha = if (isHighlighted) 0.85f else 0.15f)

                    val strokeWidth = if (isHighlighted) 3.5f else 1.2f

                    val pathEffect = if (edge.relationshipType == EvidenceRelationship.CONTRADICTS) {
                        PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    } else null

                    drawLine(
                        color = strokeColor,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        pathEffect = pathEffect
                    )
                }
            }

            // Draw Source Nodes
            sources.forEach { source ->
                val pos = sourcePositions[source.id] ?: return@forEach
                val isSelected = (selectedNode as? SelectedNode.SourceNode)?.source?.id == source.id

                drawCircle(
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF38BDF8),
                    radius = if (isSelected) 22f else 16f,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 22f else 16f,
                    center = pos,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }

            // Draw Claim Nodes
            claims.forEach { claim ->
                val pos = claimPositions[claim.id] ?: return@forEach
                val isSelected = (selectedNode as? SelectedNode.ClaimNode)?.claim?.id == claim.id

                val nodeColor = when (claim.status) {
                    ClaimStatus.SUPPORTED -> Color(0xFF16A34A)
                    ClaimStatus.CONTESTED, ClaimStatus.REFUTED -> Color(0xFFDC2626)
                    ClaimStatus.SYNTHESIZED -> Color(0xFFEA580C)
                    ClaimStatus.PROPOSED -> Color(0xFF2563EB)
                }

                drawCircle(
                    color = nodeColor,
                    radius = if (isSelected) 22f else 16f,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 22f else 16f,
                    center = pos,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
        }

        // Overlay text labels on top of Canvas
        sources.forEach { source ->
            val pos = sourcePositions[source.id] ?: return@forEach
            val author = source.authors.firstOrNull()?.split(" ")?.lastOrNull() ?: "Source"
            val yr = source.year?.toString() ?: ""

            Box(
                modifier = Modifier
                    .offset(x = (pos.x / 3.0f).dp - 85.dp, y = (pos.y / 3.0f).dp - 12.dp)
                    .width(80.dp)
                    .clickable { onSelectNode(SelectedNode.SourceNode(source)) }
            ) {
                Text(
                    text = "$author $yr",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        claims.forEach { claim ->
            val pos = claimPositions[claim.id] ?: return@forEach

            Box(
                modifier = Modifier
                    .offset(x = (pos.x / 3.0f).dp + 16.dp, y = (pos.y / 3.0f).dp - 12.dp)
                    .width(90.dp)
                    .clickable { onSelectNode(SelectedNode.ClaimNode(claim)) }
            ) {
                Text(
                    text = claim.proposition,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SelectedNodeDetailCard(
    node: SelectedNode,
    edges: List<ResearchRepository.GraphEdge>,
    sources: List<Source>,
    claims: List<Claim>,
    onClose: () -> Unit,
    onNavigateToSource: (sourceId: String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (node) {
                    is SelectedNode.SourceNode -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = Color(0xFF0284C7)) { Text("SOURCE", color = Color.White) }
                            Text(
                                node.source.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                    is SelectedNode.ClaimNode -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("CLAIM", color = Color.White) }
                            Text(
                                node.claim.proposition,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (node) {
                is SelectedNode.SourceNode -> {
                    val connectedEdges = edges.filter { it.sourceId == node.source.id }
                    Text(
                        "Authors: ${node.source.authors.joinToString(", ")} (${node.source.year ?: "n.d."})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Connected to ${connectedEdges.size} claim(s)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onNavigateToSource(node.source.id) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Document Viewer")
                    }
                }
                is SelectedNode.ClaimNode -> {
                    val connectedEdges = edges.filter { it.claimId == node.claim.id }
                    val supCount = connectedEdges.count { it.relationshipType == EvidenceRelationship.SUPPORTS }
                    val conCount = connectedEdges.count { it.relationshipType == EvidenceRelationship.CONTRADICTS }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status: ${node.claim.status.name}", style = MaterialTheme.typography.labelSmall)
                        Text("• $supCount Supporting", color = Color(0xFF16A34A), style = MaterialTheme.typography.labelSmall)
                        Text("• $conCount Contradicting", color = Color(0xFFDC2626), style = MaterialTheme.typography.labelSmall)
                    }

                    if (connectedEdges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                            items(connectedEdges) { edge ->
                                val src = sources.find { it.id == edge.sourceId }
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            "${src?.authors?.firstOrNull() ?: "Source"} • ${edge.relationshipType.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "\"${edge.excerptText}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
