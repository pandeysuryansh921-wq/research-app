package com.ecosystem.research.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ecosystem.research.core.ecosystem.CitationExportEngine
import com.ecosystem.research.core.ecosystem.EcosystemBridge
import com.ecosystem.research.core.ecosystem.ManuscriptExportEngine
import com.ecosystem.research.core.model.Claim
import com.ecosystem.research.core.model.Evidence
import com.ecosystem.research.core.model.ResearchProject
import com.ecosystem.research.core.model.Source

@Composable
fun ExportManuscriptDialog(
    project: ResearchProject,
    claims: List<Claim>,
    claimEvidenceMap: Map<String, List<Pair<Evidence, Source?>>>,
    sources: List<Source>,
    synthesisReport: String? = null,
    onDismiss: () -> Unit,
    onOpenInViewer: (markdown: String) -> Unit
) {
    val context = LocalContext.current
    var includeYaml by remember { mutableStateOf(true) }
    var includeReview by remember { mutableStateOf(true) }
    var includeEvidence by remember { mutableStateOf(true) }
    var includeContradictions by remember { mutableStateOf(true) }
    var includeBibTeX by remember { mutableStateOf(true) }
    var includeFootnotes by remember { mutableStateOf(true) }

    fun buildCurrentManuscript(): String {
        return ManuscriptExportEngine.generateManuscript(
            project = project,
            claims = claims,
            claimEvidenceMap = claimEvidenceMap,
            sources = sources,
            synthesisReport = synthesisReport,
            options = ManuscriptExportEngine.ExportOptions(
                includeYamlFrontmatter = includeYaml,
                includeLiteratureReview = includeReview,
                includeEvidenceMatrix = includeEvidence,
                includeContradictionsSection = includeContradictions,
                includeBibTeXReferences = includeBibTeX,
                includeFootnotes = includeFootnotes
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Text(
                    "Export Manuscript to Likhoji",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Produces an academic manuscript with citation anchors, empirical tables, and full BibTeX references.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Options Section
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Manuscript Sections to Include:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    CheckboxRow("YAML Metadata Frontmatter", includeYaml) { includeYaml = it }
                    CheckboxRow("Literature Review Synthesis", includeReview) { includeReview = it }
                    CheckboxRow("Empirical Claims & Evidence Tables", includeEvidence) { includeEvidence = it }
                    CheckboxRow("Academic Footnotes with Verbatim Quotes", includeFootnotes) { includeFootnotes = it }
                    CheckboxRow("Scholarly Controversies & Conflicts", includeContradictions) { includeContradictions = it }
                    CheckboxRow("Complete BibTeX Reference List", includeBibTeX) { includeBibTeX = it }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Ecosystem Export Summary",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• ${claims.size} verified project claims\n" +
                                "• ${claimEvidenceMap.values.sumOf { it.size }} empirical evidence coordinates\n" +
                                "• ${sources.size} primary literature sources\n" +
                                "• Target App: Likhoji (Academic Markdown Format)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val manuscript = buildCurrentManuscript()
                            try {
                                val intent = EcosystemBridge.createLikhojiExportIntent(
                                    manuscriptMarkdown = manuscript,
                                    title = project.title
                                )
                                context.startActivity(intent)
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not launch Share/Likhoji: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Directly to Likhoji / Share")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val manuscript = buildCurrentManuscript()
                                onOpenInViewer(manuscript)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview")
                        }

                        OutlinedButton(
                            onClick = {
                                val manuscript = buildCurrentManuscript()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Research Manuscript", manuscript)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Manuscript copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy MD")
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
