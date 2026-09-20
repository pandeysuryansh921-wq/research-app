package com.ecosystem.research.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ecosystem.research.core.analytics.DualScreeningEngine
import com.ecosystem.research.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualScreeningDialog(
    sources: List<Source>,
    decisions: List<ScreeningDecision>,
    onSaveDecision: (sourceId: String, screenerId: String, vote: ScreeningVote, reason: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var activeScreenerId by remember { mutableStateOf("screener1") } // "screener1", "screener2", "arbitrator"
    var selectedTab by remember { mutableStateOf(0) } // 0: Screen Papers, 1: Conflicts & Arbitration

    val reliability = remember(decisions) {
        DualScreeningEngine.calculateReliability("current_session", decisions)
    }

    val conflicts = remember(sources, decisions) {
        DualScreeningEngine.findConflicts(sources, decisions)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dual Independent Screening",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cohen's Kappa Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inter-Rater Reliability (Cohen's κ)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val kappaColor = when {
                                reliability.cohensKappa >= 0.8f -> Color(0xFF15803D) // Green
                                reliability.cohensKappa >= 0.6f -> Color(0xFF0284C7) // Blue
                                reliability.cohensKappa >= 0.4f -> Color(0xFFD97706) // Amber
                                else -> Color(0xFFDC2626) // Red
                            }
                            Text(
                                text = "κ = ${String.format("%.2f", reliability.cohensKappa)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = kappaColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${reliability.agreedCount} agreed / ${reliability.conflictedCount} conflicts (${reliability.percentAgreement}%)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = reliability.interpretation,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Screener Role Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeScreenerId == "screener1",
                        onClick = { activeScreenerId = "screener1" },
                        label = { Text("Screener 1 (Primary)") },
                        leadingIcon = if (activeScreenerId == "screener1") {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = activeScreenerId == "screener2",
                        onClick = { activeScreenerId = "screener2" },
                        label = { Text("Screener 2 (Independent)") },
                        leadingIcon = if (activeScreenerId == "screener2") {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = activeScreenerId == "arbitrator",
                        onClick = { activeScreenerId = "arbitrator" },
                        label = { Text("Arbitrator (${conflicts.count { !it.isResolved }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Screen vs Conflicts
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Screen Papers (${sources.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Conflicts & Arbitration (${conflicts.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Screening List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sources, key = { it.id }) { source ->
                            val s1 = decisions.find { it.sourceId == source.id && it.screenerId == "screener1" }
                            val s2 = decisions.find { it.sourceId == source.id && it.screenerId == "screener2" }
                            val currentDec = decisions.find { it.sourceId == source.id && it.screenerId == activeScreenerId }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = source.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            if (source.authors.isNotEmpty()) {
                                                Text(
                                                    text = "${source.authors.take(2).joinToString(", ")}${if (source.authors.size > 2) " et al." else ""} (${source.year ?: "N/A"})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (source.isPreprint) {
                                            PreprintBadge(isPreprint = true, preprintSource = source.preprintSource)
                                        }
                                    }

                                    if (!source.abstractText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = source.abstractText.take(220) + if (source.abstractText.length > 220) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Status from both screeners
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "S1: ${s1?.decision?.name ?: "Pending"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (s1?.decision == ScreeningVote.INCLUDE) Color(0xFF15803D) else Color(0xFF64748B)
                                            )
                                            Text(
                                                text = "S2: ${s2?.decision?.name ?: "Pending"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (s2?.decision == ScreeningVote.INCLUDE) Color(0xFF15803D) else Color(0xFF64748B)
                                            )
                                        }

                                        // Action buttons for active screener
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalButton(
                                                onClick = {
                                                    onSaveDecision(source.id, activeScreenerId, ScreeningVote.INCLUDE, null)
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = if (currentDec?.decision == ScreeningVote.INCLUDE) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Include", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    onSaveDecision(source.id, activeScreenerId, ScreeningVote.EXCLUDE, "Not meeting criteria")
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (currentDec?.decision == ScreeningVote.EXCLUDE) Color(0xFFFEE2E2) else Color.Transparent
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Exclude", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Conflicts List
                    if (conflicts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "🎉 No inter-screener conflicts! Complete consensus.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(conflicts, key = { it.sourceId }) { conflict ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (conflict.isResolved) MaterialTheme.colorScheme.surface else Color(0xFFFFFBEB)
                                    ),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = conflict.sourceTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Screener 1: ${conflict.screener1Decision?.decision?.name ?: "Pending"}",
                                                color = if (conflict.screener1Decision?.decision == ScreeningVote.INCLUDE) Color(0xFF15803D) else Color(0xFFDC2626),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "Screener 2: ${conflict.screener2Decision?.decision?.name ?: "Pending"}",
                                                color = if (conflict.screener2Decision?.decision == ScreeningVote.INCLUDE) Color(0xFF15803D) else Color(0xFFDC2626),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (conflict.isResolved) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF15803D),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Arbitrated: ${conflict.arbitrationDecision?.decision?.name}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Arbitrate as 3rd Reviewer: ",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Button(
                                                    onClick = {
                                                        onSaveDecision(conflict.sourceId, "arbitrator", ScreeningVote.INCLUDE, "Arbitration resolution: Included")
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Include", fontSize = 11.sp)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        onSaveDecision(conflict.sourceId, "arbitrator", ScreeningVote.EXCLUDE, "Arbitration resolution: Excluded")
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Exclude", fontSize = 11.sp)
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
        }
    }
}
