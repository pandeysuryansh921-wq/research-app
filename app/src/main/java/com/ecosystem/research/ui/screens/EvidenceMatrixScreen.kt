package com.ecosystem.research.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.model.*
import com.ecosystem.research.ui.components.StatisticalExportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceMatrixScreen(
    matrix: EvidenceMatrix,
    sources: List<Source>,
    cells: List<MatrixCell>,
    onBack: () -> Unit,
    onSaveCell: (MatrixCell) -> Unit
) {
    var inspectedCell by remember { mutableStateOf<MatrixCell?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    val horizontalScrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(matrix.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Analytics, contentDescription = "Export to Statistical Software")
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
            Text(
                text = "Tap any cell to inspect its exact source evidence and provenance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (sources.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Add sources to project to populate the evidence matrix.")
                }
            } else {
                Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    Column {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, Color.LightGray)
                        ) {
                            Text(
                                text = "Study / Source",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .width(180.dp)
                                    .padding(8.dp)
                            )
                            matrix.columns.forEach { col ->
                                Text(
                                    text = col.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(8.dp)
                                )
                            }
                        }

                        // Data Rows
                        LazyColumn {
                            items(sources, key = { it.id }) { source ->
                                Row(
                                    modifier = Modifier
                                        .border(0.5.dp, Color.LightGray)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .width(180.dp)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = source.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                        source.year?.let {
                                            Text(
                                                text = "($it)",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    matrix.columns.forEach { col ->
                                        val cell = cells.firstOrNull { it.sourceId == source.id && it.columnKey == col.key }
                                        val displayVal = cell?.cellValue ?: "—"

                                        Box(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .clickable {
                                                    inspectedCell = cell ?: MatrixCell(
                                                        matrixId = matrix.id,
                                                        sourceId = source.id,
                                                        columnKey = col.key,
                                                        cellValue = ""
                                                    )
                                                }
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = displayVal,
                                                fontSize = 11.sp,
                                                maxLines = 3
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

    inspectedCell?.let { cell ->
        var editVal by remember { mutableStateOf(cell.cellValue) }
        val source = sources.firstOrNull { it.id == cell.sourceId }

        AlertDialog(
            onDismissRequest = { inspectedCell = null },
            title = { Text("Evidence Cell Provenance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Source: ${source?.title ?: cell.sourceId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Column: ${cell.columnKey}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedTextField(
                        value = editVal,
                        onValueChange = { editVal = it },
                        label = { Text("Extracted Value") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCell(cell.copy(cellValue = editVal.trim()))
                        inspectedCell = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedCell = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showExportDialog) {
        StatisticalExportDialog(
            matrix = matrix,
            sources = sources,
            cells = cells,
            onDismiss = { showExportDialog = false }
        )
    }
}
