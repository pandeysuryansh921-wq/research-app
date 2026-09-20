package com.ecosystem.research.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import android.provider.OpenableColumns
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ecosystem.research.core.analytics.EpistemicAuditReport
import com.ecosystem.research.core.analytics.ResearchGapEngine
import com.ecosystem.research.core.database.ResearchDatabase
import com.ecosystem.research.core.document.BatchDocumentImporter
import com.ecosystem.research.core.document.DocumentImportItem
import com.ecosystem.research.core.ecosystem.DegreeTrackBridge
import com.ecosystem.research.core.ecosystem.EcosystemBridge
import com.ecosystem.research.core.ecosystem.ThesisChapter
import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.network.DiscoveryClient
import com.ecosystem.research.core.repository.ResearchRepository
import com.ecosystem.research.core.search.SearchFilter
import com.ecosystem.research.ui.components.DualScreeningDialog
import com.ecosystem.research.ui.components.ExportManuscriptDialog
import com.ecosystem.research.ui.components.IrbTrackingDialog
import com.ecosystem.research.ui.components.ResearchGapDialog
import com.ecosystem.research.ui.screens.*
import com.ecosystem.research.ui.theme.ResearchTheme
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    data class ProjectWorkspace(val projectId: String) : Screen()
    data class EvidenceBoard(val projectId: String) : Screen()
    data class EvidenceMatrixView(val projectId: String, val matrixId: String) : Screen()
    data class VisualEvidenceGraph(val projectId: String) : Screen()
    data class DocumentViewer(
        val sourceId: String?,
        val fileUriOrPath: String,
        val title: String,
        val projectId: String?,
        val initialContent: String? = null
    ) : Screen()
    object UniversalInbox : Screen()
    object PaperDiscovery : Screen()
    data class UniversalSearch(val initialQuery: String? = null, val projectId: String? = null) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: ResearchRepository
    private val discoveryClient = DiscoveryClient()
    private var pendingNavigationTarget by mutableStateOf<DeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = ResearchDatabase.getInstance(this)
        repository = ResearchRepository(db)

        lifecycleScope.launch {
            repository.refreshProjects()
            repository.refreshInbox()
        }

        handleIntent(intent)

        setContent {
            ResearchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                    var pendingAttachSourceId by remember { mutableStateOf<String?>(null) }
                    var pendingBatchProjectId by remember { mutableStateOf<String?>(null) }

                    val batchFilePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        val projId = pendingBatchProjectId
                        pendingBatchProjectId = null
                        if (projId != null && uris.isNotEmpty()) {
                            val items = uris.map { uri ->
                                try {
                                    contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (_: Exception) {}
                                val fileName = queryFileName(uri)
                                DocumentImportItem(
                                    uriString = uri.toString(),
                                    fileName = fileName,
                                    mimeType = contentResolver.getType(uri)
                                )
                            }
                            val newSources = BatchDocumentImporter.createSourcesFromBatch(projId, items)
                            lifecycleScope.launch {
                                var importedCount = 0
                                for (src in newSources) {
                                    repository.importSource(src).onSuccess { importedCount++ }
                                }
                                Toast.makeText(this@MainActivity, "Batch imported $importedCount documents!", Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.ProjectWorkspace(projId)
                            }
                        }
                    }

                    // Process deep-link navigation if present
                    LaunchedEffect(pendingNavigationTarget) {
                        when (val target = pendingNavigationTarget) {
                            is DeepLinkTarget.Project -> {
                                currentScreen = Screen.ProjectWorkspace(target.projectId)
                                pendingNavigationTarget = null
                            }
                            is DeepLinkTarget.Source -> {
                                val src = repository.getSourceById(target.sourceId)
                                if (src != null && src.localPdfPath != null) {
                                    currentScreen = Screen.DocumentViewer(
                                        sourceId = src.id,
                                        fileUriOrPath = src.localPdfPath,
                                        title = src.title,
                                        projectId = src.projectId
                                    )
                                } else if (src?.projectId != null) {
                                    currentScreen = Screen.ProjectWorkspace(src.projectId)
                                }
                                pendingNavigationTarget = null
                            }
                            is DeepLinkTarget.Claim -> {
                                pendingNavigationTarget = null
                            }
                            else -> {}
                        }
                    }

                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {}

                            val sId = pendingAttachSourceId
                            pendingAttachSourceId = null
                            if (sId != null) {
                                lifecycleScope.launch {
                                    repository.getSourceById(sId)?.let { s ->
                                        repository.updateSource(s.copy(localPdfPath = uri.toString()))
                                        currentScreen = Screen.DocumentViewer(
                                            sourceId = s.id,
                                            fileUriOrPath = uri.toString(),
                                            title = s.title,
                                            projectId = s.projectId
                                        )
                                    }
                                }
                            } else {
                                currentScreen = Screen.DocumentViewer(
                                    sourceId = null,
                                    fileUriOrPath = uri.toString(),
                                    title = uri.lastPathSegment ?: "Document",
                                    projectId = null
                                )
                            }
                        }
                    }

                    val projects by repository.getAllProjects().collectAsState(initial = emptyList())
                    val inboxItems by repository.getAllInboxItems().collectAsState(initial = emptyList())
                    val pendingInboxItems = remember(inboxItems) { inboxItems.filter { it.status == InboxStatus.PENDING } }

                    when (val screen = currentScreen) {
                        is Screen.Dashboard -> {
                            DashboardScreen(
                                projects = projects,
                                pendingInboxCount = pendingInboxItems.size,
                                onProjectClick = { projId ->
                                    currentScreen = Screen.ProjectWorkspace(projId)
                                },
                                onNewProject = { title, question, discipline ->
                                    lifecycleScope.launch {
                                        val newProj = ResearchProject(
                                            title = title,
                                            primaryQuestion = question.ifBlank { null },
                                            discipline = discipline
                                        )
                                        repository.saveProject(newProj)
                                        val matrix = EvidenceMatrix(
                                            projectId = newProj.id,
                                            title = "$title — Evidence Matrix"
                                        )
                                        repository.saveMatrix(matrix)
                                    }
                                },
                                onOpenInbox = { currentScreen = Screen.UniversalInbox },
                                onOpenDiscovery = { currentScreen = Screen.PaperDiscovery },
                                onOpenFile = {
                                    pendingAttachSourceId = null
                                    filePickerLauncher.launch(arrayOf("application/pdf", "text/*", "application/json"))
                                },
                                onOpenSearch = { currentScreen = Screen.UniversalSearch() }
                            )
                        }

                        is Screen.ProjectWorkspace -> {
                            val activeProject = projects.firstOrNull { it.id == screen.projectId }
                            if (activeProject != null) {
                                var projectSources by remember { mutableStateOf<List<Source>>(emptyList()) }
                                var showSynthesisDialog by remember { mutableStateOf(false) }
                                var showExportDialog by remember { mutableStateOf(false) }
                                var showGapDialog by remember { mutableStateOf(false) }
                                var gapReport by remember { mutableStateOf<EpistemicAuditReport?>(null) }
                                var workspaceClaims by remember { mutableStateOf<List<Claim>>(emptyList()) }
                                var workspaceEvidence by remember { mutableStateOf<Map<String, List<Evidence>>>(emptyMap()) }
                                var workspaceClaimEvidenceMap by remember { mutableStateOf<Map<String, List<Pair<Evidence, Source?>>>>(emptyMap()) }
                                var currentSynthesisReport by remember { mutableStateOf<String?>(null) }
                                var showDualScreeningDialog by remember { mutableStateOf(false) }
                                var screeningDecisions by remember { mutableStateOf<List<ScreeningDecision>>(emptyList()) }
                                var showIrbTrackingDialog by remember { mutableStateOf(false) }
                                var irbProtocols by remember { mutableStateOf<List<IrbProtocol>>(emptyList()) }

                                LaunchedEffect(screen.projectId) {
                                    projectSources = repository.getSourcesByProject(screen.projectId)
                                }

                                ProjectWorkspaceScreen(
                                    project = activeProject,
                                    sources = projectSources,
                                    onBack = { currentScreen = Screen.Dashboard },
                                    onOpenEvidenceBoard = {
                                        currentScreen = Screen.EvidenceBoard(screen.projectId)
                                    },
                                    onOpenMatrix = {
                                        lifecycleScope.launch {
                                            val matrices = repository.getMatricesByProject(screen.projectId)
                                            val m = matrices.firstOrNull()
                                            if (m != null) {
                                                currentScreen = Screen.EvidenceMatrixView(screen.projectId, m.id)
                                            }
                                        }
                                    },
                                    onOpenDiscovery = { currentScreen = Screen.PaperDiscovery },
                                    onOpenVisualGraph = {
                                        currentScreen = Screen.VisualEvidenceGraph(screen.projectId)
                                    },
                                    onExportManuscript = {
                                        lifecycleScope.launch {
                                            val claims = repository.getClaimsByProject(screen.projectId)
                                            val sources = repository.getSourcesByProject(screen.projectId)
                                            val map = mutableMapOf<String, List<Pair<Evidence, Source?>>>()
                                            for (c in claims) {
                                                val evList = repository.getEvidenceForClaim(c.id)
                                                map[c.id] = evList.map { ev ->
                                                    val src = sources.find { it.id == ev.sourceId }
                                                    Pair(ev, src)
                                                }
                                            }
                                            workspaceClaims = claims
                                            workspaceClaimEvidenceMap = map
                                            showExportDialog = true
                                        }
                                    },
                                    onOpenDocument = { source ->
                                        currentScreen = Screen.DocumentViewer(
                                            sourceId = source.id,
                                            fileUriOrPath = source.localPdfPath ?: "",
                                            title = source.title,
                                            projectId = screen.projectId
                                        )
                                    },
                                    onAttachFile = { sourceId ->
                                        pendingAttachSourceId = sourceId
                                        filePickerLauncher.launch(arrayOf("application/pdf", "text/*", "application/json"))
                                    },
                                    onSynthesizeReview = {
                                        lifecycleScope.launch {
                                            val claims = repository.getClaimsByProject(screen.projectId)
                                            val evList = repository.getEvidenceByProject(screen.projectId)
                                            val evMap = mutableMapOf<String, List<Evidence>>()
                                            for (c in claims) {
                                                val linked = repository.getEvidenceForClaim(c.id)
                                                evMap[c.id] = if (linked.isNotEmpty()) linked else evList
                                            }
                                            workspaceClaims = claims
                                            workspaceEvidence = evMap
                                            showSynthesisDialog = true
                                        }
                                    },
                                    onUpdateReadingStatus = { sourceId, newStatus ->
                                        lifecycleScope.launch {
                                            projectSources.firstOrNull { it.id == sourceId }?.let { s ->
                                                repository.updateSource(s.copy(readingStatus = newStatus))
                                                projectSources = repository.getSourcesByProject(screen.projectId)
                                            }
                                        }
                                    },
                                    onAddPaperManually = { title, journal, year, doi ->
                                        lifecycleScope.launch {
                                            val source = Source(
                                                projectId = screen.projectId,
                                                title = title,
                                                journal = journal,
                                                year = year,
                                                externalIds = ExternalIdentifiers(doi = doi),
                                                readingStatus = ReadingStatus.TO_READ
                                            )
                                            repository.importSource(source).fold(
                                                onSuccess = {
                                                    Toast.makeText(this@MainActivity, "Source added", Toast.LENGTH_SHORT).show()
                                                    projectSources = repository.getSourcesByProject(screen.projectId)
                                                },
                                                onFailure = { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                                            )
                                        }
                                    },
                                    onOpenSearch = {
                                        currentScreen = Screen.UniversalSearch(projectId = screen.projectId)
                                    },
                                    onOpenGapRadar = {
                                        lifecycleScope.launch {
                                            val claims = repository.getClaimsByProject(screen.projectId)
                                            val sources = repository.getSourcesByProject(screen.projectId)
                                            val evidenceList = repository.getEvidenceByProject(screen.projectId)
                                            val edges = repository.getGraphEdgesForProject(screen.projectId)
                                            gapReport = ResearchGapEngine.auditProject(activeProject, claims, sources, evidenceList, edges)
                                            showGapDialog = true
                                        }
                                    },
                                    onBatchImport = {
                                        pendingBatchProjectId = screen.projectId
                                        batchFilePickerLauncher.launch(arrayOf("application/pdf", "text/*", "application/json"))
                                    },
                                    onOpenDegreeTrackOutlines = {
                                        lifecycleScope.launch {
                                            val claims = repository.getClaimsByProject(screen.projectId)
                                            val sources = repository.getSourcesByProject(screen.projectId)
                                            val map = mutableMapOf<String, List<Pair<Evidence, Source?>>>()
                                            for (c in claims) {
                                                val evList = repository.getEvidenceForClaim(c.id)
                                                map[c.id] = evList.map { ev ->
                                                    val src = sources.find { it.id == ev.sourceId }
                                                    Pair(ev, src)
                                                }
                                            }
                                            val brief = DegreeTrackBridge.generateChapterBrief(
                                                activeProject,
                                                ThesisChapter.CH2_LITERATURE_REVIEW,
                                                claims,
                                                map
                                            )
                                            currentScreen = Screen.DocumentViewer(
                                                sourceId = null,
                                                fileUriOrPath = "thesis_ch2.md",
                                                title = "${activeProject.title} — Thesis Chapter 2",
                                                projectId = activeProject.id,
                                                initialContent = brief
                                            )
                                        }
                                    },
                                    onOpenDualScreening = {
                                        lifecycleScope.launch {
                                            screeningDecisions = repository.getScreeningDecisions("session_${screen.projectId}")
                                            showDualScreeningDialog = true
                                        }
                                    },
                                    onOpenIrbTracking = {
                                        lifecycleScope.launch {
                                            irbProtocols = repository.getIrbProtocols(screen.projectId)
                                            showIrbTrackingDialog = true
                                        }
                                    }
                                )

                                if (showSynthesisDialog) {
                                    GroundedSynthesisDialog(
                                        project = activeProject,
                                        claims = workspaceClaims,
                                        evidenceMap = workspaceEvidence,
                                        onDismiss = { showSynthesisDialog = false },
                                        onSynthesisReady = { markdown ->
                                            showSynthesisDialog = false
                                            currentSynthesisReport = markdown
                                            currentScreen = Screen.DocumentViewer(
                                                sourceId = null,
                                                fileUriOrPath = "synthesis.md",
                                                title = "${activeProject.title} — Literature Review",
                                                projectId = activeProject.id,
                                                initialContent = markdown
                                            )
                                        }
                                    )
                                }

                                if (showExportDialog) {
                                    ExportManuscriptDialog(
                                        project = activeProject,
                                        claims = workspaceClaims,
                                        claimEvidenceMap = workspaceClaimEvidenceMap,
                                        sources = projectSources,
                                        synthesisReport = currentSynthesisReport,
                                        onDismiss = { showExportDialog = false },
                                        onOpenInViewer = { markdown ->
                                            currentScreen = Screen.DocumentViewer(
                                                sourceId = null,
                                                fileUriOrPath = "manuscript.md",
                                                title = "${activeProject.title} — Manuscript",
                                                projectId = activeProject.id,
                                                initialContent = markdown
                                            )
                                        }
                                    )
                                }

                                if (showGapDialog && gapReport != null) {
                                    ResearchGapDialog(
                                        report = gapReport!!,
                                        onDismiss = { showGapDialog = false }
                                    )
                                }

                                if (showDualScreeningDialog) {
                                    DualScreeningDialog(
                                        sources = projectSources,
                                        decisions = screeningDecisions,
                                        onSaveDecision = { sourceId, screenerId, vote, reason ->
                                            lifecycleScope.launch {
                                                val decision = ScreeningDecision(
                                                    sessionId = "session_${screen.projectId}",
                                                    sourceId = sourceId,
                                                    screenerId = screenerId,
                                                    decision = vote,
                                                    exclusionReason = reason
                                                )
                                                repository.saveScreeningDecision(decision)
                                                screeningDecisions = repository.getScreeningDecisions("session_${screen.projectId}")
                                            }
                                        },
                                        onDismiss = { showDualScreeningDialog = false }
                                    )
                                }

                                if (showIrbTrackingDialog) {
                                    IrbTrackingDialog(
                                        projectId = screen.projectId,
                                        protocols = irbProtocols,
                                        onSaveProtocol = { proto ->
                                            lifecycleScope.launch {
                                                repository.saveIrbProtocol(proto)
                                                irbProtocols = repository.getIrbProtocols(screen.projectId)
                                            }
                                        },
                                        onDeleteProtocol = { protoId ->
                                            lifecycleScope.launch {
                                                repository.deleteIrbProtocol(protoId)
                                                irbProtocols = repository.getIrbProtocols(screen.projectId)
                                            }
                                        },
                                        onDismiss = { showIrbTrackingDialog = false }
                                    )
                                }
                            } else {
                                currentScreen = Screen.Dashboard
                            }
                        }

                        is Screen.VisualEvidenceGraph -> {
                            VisualEvidenceGraphScreen(
                                projectId = screen.projectId,
                                repository = repository,
                                onNavigateBack = {
                                    currentScreen = Screen.ProjectWorkspace(screen.projectId)
                                },
                                onNavigateToSource = { sourceId ->
                                    lifecycleScope.launch {
                                        repository.getSourceById(sourceId)?.let { s ->
                                            if (s.localPdfPath != null) {
                                                currentScreen = Screen.DocumentViewer(
                                                    sourceId = s.id,
                                                    fileUriOrPath = s.localPdfPath,
                                                    title = s.title,
                                                    projectId = screen.projectId
                                                )
                                            } else {
                                                Toast.makeText(this@MainActivity, "No document attached to this source yet", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        is Screen.EvidenceBoard -> {
                            var claims by remember { mutableStateOf<List<Claim>>(emptyList()) }
                            var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
                            var evidenceList by remember { mutableStateOf<List<Evidence>>(emptyList()) }

                            val reloadBoard = {
                                lifecycleScope.launch {
                                    claims = repository.getClaimsByProject(screen.projectId)
                                    sources = repository.getSourcesByProject(screen.projectId)
                                    evidenceList = repository.getEvidenceByProject(screen.projectId)
                                }
                            }

                            LaunchedEffect(screen.projectId) {
                                reloadBoard()
                            }

                            val evidenceMap = remember(claims, evidenceList) {
                                claims.associate { claim ->
                                    claim.id to evidenceList.filter { ev -> ev.projectId == screen.projectId }
                                }
                            }

                            EvidenceBoardScreen(
                                claims = claims,
                                evidenceMap = evidenceMap,
                                sources = sources,
                                onBack = { currentScreen = Screen.ProjectWorkspace(screen.projectId) },
                                onAddClaim = { proposition ->
                                    lifecycleScope.launch {
                                        repository.saveClaim(
                                            Claim(projectId = screen.projectId, proposition = proposition)
                                        )
                                        reloadBoard()
                                    }
                                },
                                onAddEvidenceToClaim = { claimId, sourceId, page, excerpt, interpretation, rel ->
                                    lifecycleScope.launch {
                                        val ev = Evidence(
                                            projectId = screen.projectId,
                                            sourceId = sourceId,
                                            location = SourceLocation(pageNumber = page),
                                            excerptText = excerpt,
                                            userInterpretation = interpretation.ifBlank { null },
                                            relationshipType = rel
                                        )
                                        repository.createEvidence(ev)
                                        repository.linkEvidenceToClaim(claimId, ev.id, rel)
                                        reloadBoard()
                                    }
                                }
                            )
                        }

                        is Screen.EvidenceMatrixView -> {
                            var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
                            var matrix by remember { mutableStateOf<EvidenceMatrix?>(null) }
                            var cells by remember { mutableStateOf<List<MatrixCell>>(emptyList()) }

                            LaunchedEffect(screen.projectId, screen.matrixId) {
                                sources = repository.getSourcesByProject(screen.projectId)
                                val matrices = repository.getMatricesByProject(screen.projectId)
                                val active = matrices.firstOrNull { it.id == screen.matrixId }
                                    ?: EvidenceMatrix(id = screen.matrixId, projectId = screen.projectId, title = "Evidence Matrix")
                                matrix = active
                                cells = repository.getCellsForMatrix(active.id)
                            }

                            matrix?.let { m ->
                                EvidenceMatrixScreen(
                                    matrix = m,
                                    sources = sources,
                                    cells = cells,
                                    onBack = { currentScreen = Screen.ProjectWorkspace(screen.projectId) },
                                    onSaveCell = { updatedCell ->
                                        lifecycleScope.launch {
                                            repository.saveMatrixCell(updatedCell)
                                            cells = repository.getCellsForMatrix(m.id)
                                        }
                                    }
                                )
                            }
                        }

                        is Screen.UniversalInbox -> {
                            UniversalInboxScreen(
                                inboxItems = inboxItems,
                                projects = projects,
                                onBack = { currentScreen = Screen.Dashboard },
                                onProcessItem = { itemId, projId, asType ->
                                    lifecycleScope.launch {
                                        inboxItems.firstOrNull { it.id == itemId }?.let { item ->
                                            val newSource = Source(
                                                projectId = projId,
                                                title = item.rawContent.take(80),
                                                abstractText = item.rawContent,
                                                readingStatus = ReadingStatus.INBOX
                                            )
                                            repository.importSource(newSource)
                                            repository.updateInboxItemStatus(itemId, InboxStatus.PROCESSED)
                                        }
                                    }
                                },
                                onDeleteItem = { itemId ->
                                    lifecycleScope.launch { repository.deleteInboxItem(itemId) }
                                },
                                onManualCapture = { rawType, content ->
                                    lifecycleScope.launch {
                                        repository.captureInboxItem(
                                            InboxItem(
                                                rawType = rawType,
                                                rawContent = content,
                                                sourceApp = "manual"
                                            )
                                        )
                                    }
                                }
                            )
                        }

                        is Screen.PaperDiscovery -> {
                            PaperDiscoveryScreen(
                                projects = projects,
                                onBack = { currentScreen = Screen.Dashboard },
                                onSearchPubMed = { q -> discoveryClient.searchPubMed(q) },
                                onSearchArxiv = { q -> discoveryClient.searchArxiv(q) },
                                onImportSource = { source, projectId ->
                                    lifecycleScope.launch {
                                        val result = repository.importSource(source.copy(projectId = projectId))
                                        result.fold(
                                            onSuccess = { Toast.makeText(this@MainActivity, "Imported to project!", Toast.LENGTH_SHORT).show() },
                                            onFailure = { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                                        )
                                    }
                                }
                            )
                        }

                        is Screen.DocumentViewer -> {
                            DocumentViewerScreen(
                                title = screen.title,
                                fileUriOrPath = screen.fileUriOrPath,
                                sourceId = screen.sourceId,
                                projectId = screen.projectId,
                                initialContent = screen.initialContent,
                                onBack = {
                                    currentScreen = if (screen.projectId != null) {
                                        Screen.ProjectWorkspace(screen.projectId)
                                    } else {
                                        Screen.Dashboard
                                    }
                                },
                                onSaveEvidence = { srcId, pageNum, excerpt, notes, rel ->
                                    lifecycleScope.launch {
                                        val ev = Evidence(
                                            projectId = screen.projectId ?: "",
                                            sourceId = srcId ?: "",
                                            location = SourceLocation(pageNumber = pageNum),
                                            excerptText = excerpt,
                                            userInterpretation = notes.ifBlank { null },
                                            relationshipType = rel
                                        )
                                        repository.createEvidence(ev)
                                    }
                                }
                            )
                        }

                        is Screen.UniversalSearch -> {
                            UniversalSearchScreen(
                                projects = projects,
                                initialProjectId = screen.projectId,
                                initialQuery = screen.initialQuery,
                                onBack = {
                                    currentScreen = if (screen.projectId != null) {
                                        Screen.ProjectWorkspace(screen.projectId)
                                    } else {
                                        Screen.Dashboard
                                    }
                                },
                                onNavigateToSourceDocument = { sourceId, pageNum ->
                                    lifecycleScope.launch {
                                        repository.getSourceById(sourceId)?.let { s ->
                                            if (s.localPdfPath != null) {
                                                currentScreen = Screen.DocumentViewer(
                                                    sourceId = s.id,
                                                    fileUriOrPath = s.localPdfPath,
                                                    title = s.title,
                                                    projectId = s.projectId
                                                )
                                            } else {
                                                Toast.makeText(this@MainActivity, "Source has no document attached yet", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onNavigateToClaim = { projId, _ ->
                                    currentScreen = Screen.EvidenceBoard(projId)
                                },
                                onNavigateToProject = { projId ->
                                    currentScreen = Screen.ProjectWorkspace(projId)
                                },
                                onExecuteSearch = { q, filter ->
                                    repository.searchUniversal(q, filter)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            EcosystemBridge.ACTION_CAPTURE_INBOX, "com.ecosystem.action.SAVE_RESEARCH" -> {
                val text = intent.getStringExtra("extra_text")
                    ?: intent.getStringExtra("text")
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getBundleExtra("payload")?.getString("text")
                val typeStr = intent.getStringExtra("extra_type") ?: "TEXT"
                val sourceApp = intent.getStringExtra("source_app") ?: "likhoji"

                if (!text.isNullOrBlank()) {
                    val rawType = try { InboxItemType.valueOf(typeStr) } catch (e: Exception) { InboxItemType.TEXT }
                    lifecycleScope.launch {
                        repository.captureInboxItem(
                            InboxItem(
                                rawType = rawType,
                                rawContent = text,
                                sourceApp = sourceApp
                            )
                        )
                    }
                    Toast.makeText(this, "Saved to Research Inbox", Toast.LENGTH_SHORT).show()
                }
            }

            EcosystemBridge.ACTION_VOICE_IDEA -> {
                val voicePrompt = intent.getStringExtra("voice_prompt") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!voicePrompt.isNullOrBlank()) {
                    val parsed = EcosystemBridge.parseBolojiAudioTranscript(voicePrompt)
                    lifecycleScope.launch {
                        repository.captureInboxItem(
                            InboxItem(
                                rawType = InboxItemType.VOICE_IDEA,
                                rawContent = "${parsed.title}\n\n${parsed.transcript}",
                                sourceApp = "boloji"
                            )
                        )
                    }
                    Toast.makeText(this, "Voice memo captured from Boloji", Toast.LENGTH_SHORT).show()
                }
            }

            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    lifecycleScope.launch {
                        repository.captureInboxItem(
                            InboxItem(
                                rawType = InboxItemType.TEXT,
                                rawContent = sharedText,
                                sourceApp = "share_sheet"
                            )
                        )
                    }
                    Toast.makeText(this, "Shared text saved to Research Inbox", Toast.LENGTH_SHORT).show()
                }
            }

            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (dataUri != null) {
                    val target = ResearchContractV1.parseUri(dataUri)
                    pendingNavigationTarget = target
                    Toast.makeText(this, "Navigating: $target", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun queryFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "document"
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            name = cursor.getString(idx) ?: name
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return name
    }
}
