package com.ecosystem.research.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ecosystem.research.core.database.ResearchDatabase
import com.ecosystem.research.core.ecosystem.EcosystemBridge
import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.network.DiscoveryClient
import com.ecosystem.research.core.repository.ResearchRepository
import com.ecosystem.research.ui.screens.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ecosystem.research.ui.theme.ResearchTheme
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    data class ProjectWorkspace(val projectId: String) : Screen()
    data class EvidenceBoard(val projectId: String) : Screen()
    data class EvidenceMatrixView(val projectId: String, val matrixId: String) : Screen()
    data class DocumentViewer(
        val sourceId: String?,
        val fileUriOrPath: String,
        val title: String,
        val projectId: String?
    ) : Screen()
    object UniversalInbox : Screen()
    object PaperDiscovery : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: ResearchRepository
    private val discoveryClient = DiscoveryClient()

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
                                onNewProject = { title, question ->
                                    lifecycleScope.launch {
                                        val newProj = ResearchProject(
                                            title = title,
                                            primaryQuestion = question.ifBlank { null }
                                        )
                                        repository.saveProject(newProj)
                                        // Create default evidence matrix for this project
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
                                }
                            )
                        }

                        is Screen.ProjectWorkspace -> {
                            val activeProject = projects.firstOrNull { it.id == screen.projectId }
                            if (activeProject != null) {
                                var projectSources by remember { mutableStateOf<List<Source>>(emptyList()) }
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
                                    }
                                )
                            } else {
                                currentScreen = Screen.Dashboard
                            }
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
                    lifecycleScope.launch {
                        repository.captureInboxItem(
                            InboxItem(
                                rawType = InboxItemType.VOICE_IDEA,
                                rawContent = voicePrompt,
                                sourceApp = "boloji"
                            )
                        )
                    }
                    Toast.makeText(this, "Voice idea captured to Research Inbox", Toast.LENGTH_SHORT).show()
                }
            }

            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (dataUri != null) {
                    val target = ResearchContractV1.parseUri(dataUri)
                    Toast.makeText(this, "Navigating: $target", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
