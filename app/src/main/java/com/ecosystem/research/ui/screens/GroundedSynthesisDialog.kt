package com.ecosystem.research.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.ai.AIServiceFactory
import com.ecosystem.research.core.model.Claim
import com.ecosystem.research.core.model.Evidence
import com.ecosystem.research.core.model.ResearchProject
import kotlinx.coroutines.launch

@Composable
fun GroundedSynthesisDialog(
    project: ResearchProject,
    claims: List<Claim>,
    evidenceMap: Map<String, List<Evidence>>,
    onDismiss: () -> Unit,
    onSynthesisReady: (markdown: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSynthesizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val totalEvidence = remember(evidenceMap) { evidenceMap.values.sumOf { it.size } }

    AlertDialog(
        onDismissRequest = { if (!isSynthesizing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grounded Synthesis Studio", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Synthesizes an evidence-based literature review strictly grounded in your project's claims and paper excerpts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Research Question:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = project.primaryQuestion ?: project.title,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Claims: ${claims.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Evidence Excerpts: $totalEvidence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isSynthesizing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Generating grounded review with citations...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSynthesizing = true
                    errorMessage = null
                    scope.launch {
                        val provider = AIServiceFactory.getProvider(context)
                        val question = project.primaryQuestion ?: project.title
                        val result = provider.synthesizeGroundedReview(question, claims, evidenceMap)
                        isSynthesizing = false
                        result.fold(
                            onSuccess = { markdown ->
                                onSynthesisReady(markdown)
                            },
                            onFailure = { err ->
                                errorMessage = "Synthesis failed: ${err.message}"
                            }
                        )
                    }
                },
                enabled = !isSynthesizing
            ) {
                Text("Generate Review")
            }
        },
        dismissButton = {
            if (!isSynthesizing) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
