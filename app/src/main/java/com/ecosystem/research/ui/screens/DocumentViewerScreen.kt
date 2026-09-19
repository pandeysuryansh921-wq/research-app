package com.ecosystem.research.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosystem.research.core.document.MarkdownContent
import com.ecosystem.research.core.document.PdfRendererHelper
import com.ecosystem.research.core.model.EvidenceRelationship
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class DocumentFormat {
    PDF,
    MARKDOWN,
    TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    title: String,
    fileUriOrPath: String,
    sourceId: String? = null,
    projectId: String? = null,
    onBack: () -> Unit,
    onSaveEvidence: (sourceId: String?, pageNumber: Int, excerpt: String, notes: String, relationship: EvidenceRelationship) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Determine format
    val format = remember(fileUriOrPath) {
        val lower = fileUriOrPath.lowercase()
        when {
            lower.endsWith(".pdf") || fileUriOrPath.contains("pdf", ignoreCase = true) -> DocumentFormat.PDF
            lower.endsWith(".md") || lower.endsWith(".markdown") -> DocumentFormat.MARKDOWN
            else -> DocumentFormat.TEXT
        }
    }

    // PDF State
    var pdfHelper by remember { mutableStateOf<PdfRendererHelper?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPdf by remember { mutableStateOf(true) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableStateOf(1.0f) }
    var showJumpPageDialog by remember { mutableStateOf(false) }

    // Text/Markdown State
    var textContent by remember { mutableStateOf("") }
    var isLoadingText by remember { mutableStateOf(false) }

    // Evidence Extraction Dialog State
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractExcerpt by remember { mutableStateOf("") }
    var extractNotes by remember { mutableStateOf("") }
    var selectedRelationship by remember { mutableStateOf<EvidenceRelationship>(EvidenceRelationship.SUPPORTS) }

    // Initialize document loading
    LaunchedEffect(fileUriOrPath, format) {
        when (format) {
            DocumentFormat.PDF -> {
                isLoadingPdf = true
                pdfError = null
                val helper = PdfRendererHelper(context, fileUriOrPath)
                val initResult = helper.initialize()
                initResult.fold(
                    onSuccess = { count ->
                        pdfHelper = helper
                        pageCount = count
                        currentPage = 0
                        // Render initial page
                        val pageResult = helper.renderPage(0, scaleFactor = 2.0f)
                        pageResult.fold(
                            onSuccess = { bmp ->
                                currentBitmap = bmp
                                isLoadingPdf = false
                            },
                            onFailure = { err ->
                                pdfError = "Failed to render first page: ${err.message}"
                                isLoadingPdf = false
                            }
                        )
                    },
                    onFailure = { err ->
                        pdfError = "Cannot open PDF: ${err.message}"
                        isLoadingPdf = false
                    }
                )
            }
            DocumentFormat.MARKDOWN, DocumentFormat.TEXT -> {
                isLoadingText = true
                val loaded = withContext(Dispatchers.IO) {
                    try {
                        if (fileUriOrPath.startsWith("content://")) {
                            val uri = Uri.parse(fileUriOrPath)
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        } else {
                            val file = File(fileUriOrPath)
                            if (file.exists()) file.readText() else "File not found: $fileUriOrPath"
                        }
                    } catch (e: Exception) {
                        "Error reading document: ${e.message}"
                    }
                }
                textContent = loaded
                isLoadingText = false
            }
        }
    }

    // Dispose PDF resources when leaving
    DisposableEffect(Unit) {
        onDispose {
            pdfHelper?.close()
        }
    }

    // Function to change PDF page
    fun jumpToPage(targetPage: Int) {
        if (targetPage in 0 until pageCount) {
            currentPage = targetPage
            isLoadingPdf = true
            scope.launch {
                val res = pdfHelper?.renderPage(targetPage, scaleFactor = 2.0f)
                res?.fold(
                    onSuccess = { bmp ->
                        currentBitmap = bmp
                        isLoadingPdf = false
                    },
                    onFailure = { err ->
                        pdfError = err.message
                        isLoadingPdf = false
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(
                                containerColor = when (format) {
                                    DocumentFormat.PDF -> MaterialTheme.colorScheme.primary
                                    DocumentFormat.MARKDOWN -> MaterialTheme.colorScheme.secondary
                                    DocumentFormat.TEXT -> MaterialTheme.colorScheme.tertiary
                                }
                            ) {
                                Text(format.name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            if (format == DocumentFormat.PDF && pageCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Page ${currentPage + 1} of $pageCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (format == DocumentFormat.PDF && pageCount > 0) {
                        IconButton(onClick = { showJumpPageDialog = true }) {
                            Icon(Icons.Default.Numbers, contentDescription = "Jump to page")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (format == DocumentFormat.PDF && pageCount > 0) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page
                        IconButton(
                            onClick = { jumpToPage(currentPage - 1) },
                            enabled = currentPage > 0 && !isLoadingPdf
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                        }

                        // Page text & jump trigger
                        TextButton(onClick = { showJumpPageDialog = true }) {
                            Text(
                                text = "Page ${currentPage + 1} / $pageCount",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Next page
                        IconButton(
                            onClick = { jumpToPage(currentPage + 1) },
                            enabled = currentPage < pageCount - 1 && !isLoadingPdf
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                        }

                        // Zoom Controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                            }
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) }
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showExtractDialog = true },
                icon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                text = { Text("Extract Evidence") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (format) {
                DocumentFormat.PDF -> {
                    when {
                        isLoadingPdf -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        pdfError != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = pdfError ?: "Unknown PDF error",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        currentBitmap != null -> {
                            val scrollState = rememberScrollState()
                            val hScrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .horizontalScroll(hScrollState),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = currentBitmap!!.asImageBitmap(),
                                    contentDescription = "PDF Page ${currentPage + 1}",
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale
                                        )
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                DocumentFormat.MARKDOWN -> {
                    if (isLoadingText) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            MarkdownContent(content = textContent)
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                DocumentFormat.TEXT -> {
                    if (isLoadingText) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = textContent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpPageDialog && pageCount > 0) {
        var inputPageText by remember { mutableStateOf((currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Jump to Page (1 - $pageCount)") },
            text = {
                OutlinedTextField(
                    value = inputPageText,
                    onValueChange = { inputPageText = it },
                    singleLine = true,
                    label = { Text("Page Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = inputPageText.toIntOrNull()
                        if (p != null && p in 1..pageCount) {
                            jumpToPage(p - 1)
                            showJumpPageDialog = false
                        } else {
                            Toast.makeText(context, "Enter a valid page between 1 and $pageCount", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Evidence Extraction Dialog
    if (showExtractDialog) {
        val targetPageNumber = if (format == DocumentFormat.PDF) currentPage + 1 else 1
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = {
                Column {
                    Text("Extract Evidence", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (format == DocumentFormat.PDF) "From Page $targetPageNumber" else "From Document",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = extractExcerpt,
                        onValueChange = { extractExcerpt = it },
                        label = { Text("Verbatim Excerpt Quote *") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = extractNotes,
                        onValueChange = { extractNotes = it },
                        label = { Text("Researcher Interpretation / Notes") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Scientific Relationship:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (rel in EvidenceRelationship.entries) {
                            FilterChip(
                                selected = selectedRelationship == rel,
                                onClick = { selectedRelationship = rel },
                                label = { Text(rel.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (extractExcerpt.isNotBlank()) {
                            onSaveEvidence(
                                sourceId,
                                targetPageNumber,
                                extractExcerpt.trim(),
                                extractNotes.trim(),
                                selectedRelationship
                            )
                            Toast.makeText(context, "Evidence extracted from Page $targetPageNumber", Toast.LENGTH_SHORT).show()
                            showExtractDialog = false
                            extractExcerpt = ""
                            extractNotes = ""
                        } else {
                            Toast.makeText(context, "Please enter an excerpt quote", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Evidence")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
