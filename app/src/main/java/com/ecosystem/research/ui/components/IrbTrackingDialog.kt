package com.ecosystem.research.ui.components

import androidx.compose.foundation.background
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
import com.ecosystem.research.core.model.IrbProtocol
import com.ecosystem.research.core.model.IrbStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrbTrackingDialog(
    projectId: String,
    protocols: List<IrbProtocol>,
    onSaveProtocol: (IrbProtocol) -> Unit,
    onDeleteProtocol: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var protocolNumber by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var protocolTitle by remember { mutableStateOf("") }
    var protocolVersion by remember { mutableStateOf("1.0") }
    var status by remember { mutableStateOf(IrbStatus.APPROVED) }
    var validityMonths by remember { mutableStateOf("12") }

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
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "IRB & Ethics Committee Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Human Subjects & Institutional Review Board Compliance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar: Add Protocol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${protocols.size} Registered Protocol(s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Register Approval")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (protocols.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Ethics / IRB Approvals Linked",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Register protocol numbers, ICF forms, and expiration alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(protocols, key = { it.id }) { item ->
                            val alert = item.getComplianceAlert()
                            val (badgeBg, badgeText) = when {
                                alert.startsWith("ACTIVE") -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
                                alert.startsWith("EXPIRING") -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                                alert == "EXEMPT" -> Pair(Color(0xFFE0E7FF), Color(0xFF4338CA))
                                else -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
                            }

                            val expiryStr = if (item.expirationDate != null) {
                                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.expirationDate))
                            } else "None / Exempt"

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
                                        Text(
                                            text = item.protocolNumber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Text(
                                            text = alert,
                                            color = badgeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(badgeBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Institution: ${item.institution} • Version: ${item.protocolVersion ?: "1.0"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Expiration: $expiryStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onDeleteProtocol(item.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", color = MaterialTheme.colorScheme.error)
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

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Register IRB Protocol") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protocolNumber,
                        onValueChange = { protocolNumber = it },
                        label = { Text("Protocol / Approval Number (e.g. IRB-24-102)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Institution / Ethics Board") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = protocolTitle,
                        onValueChange = { protocolTitle = it },
                        label = { Text("Study Protocol Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = protocolVersion,
                            onValueChange = { protocolVersion = it },
                            label = { Text("Version") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = validityMonths,
                            onValueChange = { validityMonths = it },
                            label = { Text("Valid (Months)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (protocolNumber.isNotBlank() && institution.isNotBlank()) {
                            val months = validityMonths.toIntOrNull() ?: 12
                            val now = System.currentTimeMillis()
                            val expiration = now + (months.toLong() * 30L * 24L * 60L * 60L * 1000L)
                            val newProto = IrbProtocol(
                                projectId = projectId,
                                protocolNumber = protocolNumber.trim(),
                                institution = institution.trim(),
                                title = if (protocolTitle.isNotBlank()) protocolTitle.trim() else "Human Research Protocol $protocolNumber",
                                protocolVersion = protocolVersion.trim(),
                                status = IrbStatus.APPROVED,
                                approvalDate = now,
                                expirationDate = expiration
                            )
                            onSaveProtocol(newProto)
                            protocolNumber = ""
                            institution = ""
                            protocolTitle = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save Approval")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
