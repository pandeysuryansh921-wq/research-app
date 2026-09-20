package com.ecosystem.research.core.repository

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.ecosystem.research.core.database.ResearchDatabase
import com.ecosystem.research.core.model.*
import com.ecosystem.research.core.search.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ResearchRepository(
    private val dbHelper: ResearchDatabase,
    private val gson: Gson = Gson()
) {
    private val _projectsFlow = MutableStateFlow<List<ResearchProject>>(emptyList())
    val projectsFlow = _projectsFlow.asStateFlow()

    private val _inboxFlow = MutableStateFlow<List<InboxItem>>(emptyList())
    val inboxFlow = _inboxFlow.asStateFlow()

    suspend fun refreshProjects() = withContext(Dispatchers.IO) {
        val list = mutableListOf<ResearchProject>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_PROJECTS} ORDER BY updated_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProject(cursor))
            }
        }
        _projectsFlow.value = list
    }

    suspend fun refreshInbox() = withContext(Dispatchers.IO) {
        val list = mutableListOf<InboxItem>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_INBOX_ITEMS} ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToInboxItem(cursor))
            }
        }
        _inboxFlow.value = list
    }

    // Projects
    fun getAllProjects(): Flow<List<ResearchProject>> = projectsFlow

    suspend fun getProjectById(id: String): ResearchProject? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_PROJECTS} WHERE id = ? LIMIT 1", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursorToProject(cursor) else null
        }
    }

    suspend fun saveProject(project: ResearchProject) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", project.id)
            put("title", project.title)
            put("description", project.description)
            put("primary_question", project.primaryQuestion)
            put("status", project.status.name)
            put("discipline", project.discipline.name)
            put("tags", project.tags.joinToString(","))
            put("created_at", project.createdAt)
            put("updated_at", project.updatedAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_PROJECTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        refreshProjects()
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ResearchDatabase.TABLE_PROJECTS, "id = ?", arrayOf(id))
        refreshProjects()
    }

    // Sources
    suspend fun getSourcesByProject(projectId: String): List<Source> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Source>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SOURCES} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSource(cursor))
            }
        }
        list
    }

    suspend fun importSource(source: Source): Result<String> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase

        // De-duplication check for DOI
        source.externalIds.doi?.let { doi ->
            db.rawQuery("SELECT title FROM ${ResearchDatabase.TABLE_SOURCES} WHERE doi = ? LIMIT 1", arrayOf(doi)).use { c ->
                if (c.moveToFirst()) {
                    val title = c.getString(0)
                    return@withContext Result.failure(IllegalArgumentException("Source with DOI $doi already exists: \"$title\""))
                }
            }
        }
        // De-duplication check for PMID
        source.externalIds.pmid?.let { pmid ->
            db.rawQuery("SELECT title FROM ${ResearchDatabase.TABLE_SOURCES} WHERE pmid = ? LIMIT 1", arrayOf(pmid)).use { c ->
                if (c.moveToFirst()) {
                    val title = c.getString(0)
                    return@withContext Result.failure(IllegalArgumentException("Source with PMID $pmid already exists: \"$title\""))
                }
            }
        }

        // Automatic preprint detection
        val detection = com.ecosystem.research.core.analytics.PreprintDetector.detect(source)
        val isPreprint = source.isPreprint || detection.isPreprint
        val preprintSource = source.preprintSource ?: detection.sourceName

        val cv = ContentValues().apply {
            put("id", source.id)
            put("project_id", source.projectId)
            put("title", source.title)
            put("authors_json", gson.toJson(source.authors))
            put("journal", source.journal)
            put("year", source.year)
            put("doi", source.externalIds.doi)
            put("pmid", source.externalIds.pmid)
            put("arxiv_id", source.externalIds.arxivId)
            put("url", source.externalIds.url)
            put("abstract_text", source.abstractText)
            put("local_pdf_path", source.localPdfPath)
            put("reading_status", source.readingStatus.name)
            put("study_type", source.studyType.name)
            put("is_preprint", if (isPreprint) 1 else 0)
            put("preprint_source", preprintSource)
            put("peer_reviewed_version_doi", source.peerReviewedVersionDoi)
            put("discipline", source.discipline.name)
            put("priority", source.priority)
            put("rationale", source.rationale)
            put("origin_type", source.provenance.originType.name)
            put("verification_state", source.provenance.verificationState.name)
            put("created_by", source.provenance.attribution.createdBy)
            put("created_at", source.createdAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_SOURCES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        Result.success(source.id)
    }

    suspend fun getSourceById(id: String): Source? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SOURCES} WHERE id = ? LIMIT 1", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursorToSource(cursor) else null
        }
    }

    suspend fun updateSource(source: Source) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("reading_status", source.readingStatus.name)
            put("priority", source.priority)
            put("rationale", source.rationale)
            put("local_pdf_path", source.localPdfPath)
        }
        db.update(ResearchDatabase.TABLE_SOURCES, cv, "id = ?", arrayOf(source.id))
    }

    // Claims
    suspend fun getClaimsByProject(projectId: String): List<Claim> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Claim>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_CLAIMS} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToClaim(cursor))
            }
        }
        list
    }

    suspend fun saveClaim(claim: Claim) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", claim.id)
            put("project_id", claim.projectId)
            put("proposition", claim.proposition)
            put("status", claim.status.name)
            put("notes", claim.notes)
            put("origin_type", claim.provenance.originType.name)
            put("verification_state", claim.provenance.verificationState.name)
            put("created_by", claim.provenance.attribution.createdBy)
            put("created_at", claim.createdAt)
            put("updated_at", claim.updatedAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_CLAIMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Evidence & Edges
    suspend fun getEvidenceByProject(projectId: String): List<Evidence> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Evidence>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_EVIDENCE} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEvidence(cursor))
            }
        }
        list
    }

    suspend fun createEvidence(evidence: Evidence) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase

        // Check if underlying source is a preprint to apply 50% confidence haircut
        val source = getSourceById(evidence.sourceId)
        val isPreprint = evidence.isPreprint || (source?.isPreprint == true)
        val confidenceScore = if (isPreprint) evidence.confidenceScore * 0.5f else evidence.confidenceScore

        val cv = ContentValues().apply {
            put("id", evidence.id)
            put("project_id", evidence.projectId)
            put("source_id", evidence.sourceId)
            put("page_number", evidence.location.pageNumber)
            put("char_offset_start", evidence.location.charOffsetStart)
            put("char_offset_end", evidence.location.charOffsetEnd)
            put("section", evidence.location.section)
            put("bounding_box", evidence.location.boundingBox)
            put("excerpt_text", evidence.excerptText)
            put("user_interpretation", evidence.userInterpretation)
            put("relationship_type", evidence.relationshipType.name)
            put("confidence_score", confidenceScore)
            put("is_preprint", if (isPreprint) 1 else 0)
            put("origin_type", evidence.provenance.originType.name)
            put("verification_state", evidence.provenance.verificationState.name)
            put("created_by", evidence.provenance.attribution.createdBy)
            put("created_at", evidence.createdAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_EVIDENCE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun linkEvidenceToClaim(
        claimId: String,
        evidenceId: String,
        relationshipType: EvidenceRelationship,
        reviewNote: String? = null
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("claim_id", claimId)
            put("evidence_id", evidenceId)
            put("relationship_type", relationshipType.name)
            put("review_note", reviewNote)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_CLAIM_EVIDENCE_EDGES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getEvidenceForClaim(claimId: String): List<Evidence> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Evidence>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT e.* FROM ${ResearchDatabase.TABLE_EVIDENCE} e
            INNER JOIN ${ResearchDatabase.TABLE_CLAIM_EVIDENCE_EDGES} edge ON e.id = edge.evidence_id
            WHERE edge.claim_id = ?
            ORDER BY e.created_at DESC
        """.trimIndent()
        db.rawQuery(query, arrayOf(claimId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEvidence(cursor))
            }
        }
        list
    }

    data class GraphEdge(
        val claimId: String,
        val evidenceId: String,
        val sourceId: String,
        val relationshipType: EvidenceRelationship,
        val excerptText: String
    )

    suspend fun getGraphEdgesForProject(projectId: String): List<GraphEdge> = withContext(Dispatchers.IO) {
        val list = mutableListOf<GraphEdge>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT edge.claim_id, edge.evidence_id, e.source_id, edge.relationship_type, e.excerpt_text
            FROM ${ResearchDatabase.TABLE_CLAIM_EVIDENCE_EDGES} edge
            INNER JOIN ${ResearchDatabase.TABLE_EVIDENCE} e ON edge.evidence_id = e.id
            WHERE e.project_id = ?
        """.trimIndent()
        db.rawQuery(query, arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                val rel = try {
                    EvidenceRelationship.valueOf(cursor.getString(3))
                } catch (e: Exception) {
                    EvidenceRelationship.UNDETERMINED
                }
                list.add(
                    GraphEdge(
                        claimId = cursor.getString(0),
                        evidenceId = cursor.getString(1),
                        sourceId = cursor.getString(2),
                        relationshipType = rel,
                        excerptText = cursor.getString(4)
                    )
                )
            }
        }
        list
    }

    // Inbox
    fun getAllInboxItems(): Flow<List<InboxItem>> = inboxFlow

    suspend fun captureInboxItem(item: InboxItem) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", item.id)
            put("raw_type", item.rawType.name)
            put("raw_content", item.rawContent)
            put("source_app", item.sourceApp)
            put("assigned_project_id", item.assignedProjectId)
            put("processed_source_id", item.processedSourceId)
            put("status", item.status.name)
            put("note", item.note)
            put("created_at", item.createdAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_INBOX_ITEMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        refreshInbox()
    }

    suspend fun updateInboxItemStatus(id: String, status: InboxStatus) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("status", status.name)
        }
        db.update(ResearchDatabase.TABLE_INBOX_ITEMS, cv, "id = ?", arrayOf(id))
        refreshInbox()
    }

    suspend fun deleteInboxItem(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ResearchDatabase.TABLE_INBOX_ITEMS, "id = ?", arrayOf(id))
        refreshInbox()
    }

    // Matrices
    suspend fun getMatricesByProject(projectId: String): List<EvidenceMatrix> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EvidenceMatrix>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_MATRICES} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val colsJson = cursor.getString(cursor.getColumnIndexOrThrow("columns_json"))
                val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                val cols: List<MatrixColumn> = try {
                    val type = object : TypeToken<List<MatrixColumn>>() {}.type
                    gson.fromJson(colsJson, type) ?: EvidenceMatrix.defaultColumns()
                } catch (e: Exception) {
                    EvidenceMatrix.defaultColumns()
                }
                list.add(EvidenceMatrix(id = id, projectId = projectId, title = title, columns = cols, createdAt = createdAt))
            }
        }
        list
    }

    suspend fun saveMatrix(matrix: EvidenceMatrix) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", matrix.id)
            put("project_id", matrix.projectId)
            put("title", matrix.title)
            put("columns_json", gson.toJson(matrix.columns))
            put("created_at", matrix.createdAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_MATRICES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getCellsForMatrix(matrixId: String): List<MatrixCell> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MatrixCell>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_MATRIX_CELLS} WHERE matrix_id = ?", arrayOf(matrixId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    MatrixCell(
                        matrixId = matrixId,
                        sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
                        columnKey = cursor.getString(cursor.getColumnIndexOrThrow("column_key")),
                        cellValue = cursor.getString(cursor.getColumnIndexOrThrow("cell_value")),
                        evidenceId = cursor.getString(cursor.getColumnIndexOrThrow("evidence_id")),
                        verificationState = try {
                            VerificationState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_state")))
                        } catch (e: Exception) {
                            VerificationState.UNVERIFIED
                        }
                    )
                )
            }
        }
        list
    }

    suspend fun saveMatrixCell(cell: MatrixCell) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("matrix_id", cell.matrixId)
            put("source_id", cell.sourceId)
            put("column_key", cell.columnKey)
            put("cell_value", cell.cellValue)
            put("evidence_id", cell.evidenceId)
            put("verification_state", cell.verificationState.name)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_MATRIX_CELLS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getAllSources(): List<Source> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Source>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SOURCES} ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToSource(cursor))
            }
        }
        list
    }

    suspend fun searchUniversal(
        query: String,
        filter: SearchFilter = SearchFilter()
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val pattern = "%$trimmed%"
        val results = mutableListOf<SearchResult>()
        val db = dbHelper.readableDatabase

        // Preload project titles cache
        val projectTitles = mutableMapOf<String, String>()
        db.rawQuery("SELECT id, title FROM ${ResearchDatabase.TABLE_PROJECTS}", null).use { c ->
            while (c.moveToNext()) {
                projectTitles[c.getString(0)] = c.getString(1)
            }
        }

        // 1. Evidence Search
        if (filter.entityType == SearchEntityType.ALL || filter.entityType == SearchEntityType.EVIDENCE) {
            val sqlBuilder = StringBuilder("""
                SELECT e.*, s.title as source_title, s.local_pdf_path
                FROM ${ResearchDatabase.TABLE_EVIDENCE} e
                LEFT JOIN ${ResearchDatabase.TABLE_SOURCES} s ON e.source_id = s.id
                WHERE (e.excerpt_text LIKE ? OR e.user_interpretation LIKE ?)
            """.trimIndent())
            val args = mutableListOf(pattern, pattern)

            if (filter.projectId != null) {
                sqlBuilder.append(" AND e.project_id = ?")
                args.add(filter.projectId)
            }
            if (filter.relationshipType != null) {
                sqlBuilder.append(" AND e.relationship_type = ?")
                args.add(filter.relationshipType.name)
            }
            sqlBuilder.append(" ORDER BY e.created_at DESC LIMIT 50")

            db.rawQuery(sqlBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val ev = cursorToEvidence(cursor)
                    val sTitle = if (!cursor.isNull(cursor.getColumnIndexOrThrow("source_title"))) cursor.getString(cursor.getColumnIndexOrThrow("source_title")) else null
                    val sPath = if (!cursor.isNull(cursor.getColumnIndexOrThrow("local_pdf_path"))) cursor.getString(cursor.getColumnIndexOrThrow("local_pdf_path")) else null
                    val field = if (ev.excerptText.contains(trimmed, ignoreCase = true)) "Excerpt" else "Interpretation"
                    results.add(
                        SearchResult(
                            id = ev.id,
                            entityType = SearchEntityType.EVIDENCE,
                            title = sTitle ?: "Evidence Excerpt",
                            snippet = ev.excerptText,
                            matchedField = field,
                            projectId = ev.projectId,
                            projectName = projectTitles[ev.projectId],
                            sourceId = ev.sourceId,
                            sourceTitle = sTitle,
                            pageNumber = ev.location.pageNumber,
                            localFilePath = sPath,
                            verificationState = ev.provenance.verificationState,
                            relationshipType = ev.relationshipType,
                            timestamp = ev.createdAt
                        )
                    )
                }
            }
        }

        // 2. Sources Search
        if (filter.entityType == SearchEntityType.ALL || filter.entityType == SearchEntityType.SOURCE) {
            val sqlBuilder = StringBuilder("""
                SELECT * FROM ${ResearchDatabase.TABLE_SOURCES}
                WHERE (title LIKE ? OR abstract_text LIKE ? OR authors_json LIKE ? OR journal LIKE ? OR doi LIKE ?)
            """.trimIndent())
            val args = mutableListOf(pattern, pattern, pattern, pattern, pattern)

            if (filter.projectId != null) {
                sqlBuilder.append(" AND project_id = ?")
                args.add(filter.projectId)
            }
            sqlBuilder.append(" ORDER BY created_at DESC LIMIT 50")

            db.rawQuery(sqlBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val s = cursorToSource(cursor)
                    val matchedField = when {
                        s.title.contains(trimmed, ignoreCase = true) -> "Title"
                        s.abstractText?.contains(trimmed, ignoreCase = true) == true -> "Abstract"
                        s.authors.any { it.contains(trimmed, ignoreCase = true) } -> "Author"
                        s.journal?.contains(trimmed, ignoreCase = true) == true -> "Journal"
                        s.externalIds.doi?.contains(trimmed, ignoreCase = true) == true -> "DOI"
                        else -> "Metadata"
                    }
                    val snippet = s.abstractText?.take(160) ?: s.authors.joinToString(", ")
                    results.add(
                        SearchResult(
                            id = s.id,
                            entityType = SearchEntityType.SOURCE,
                            title = s.title,
                            snippet = snippet,
                            matchedField = matchedField,
                            projectId = s.projectId,
                            projectName = s.projectId?.let { projectTitles[it] },
                            sourceId = s.id,
                            sourceTitle = s.title,
                            localFilePath = s.localPdfPath,
                            verificationState = s.provenance.verificationState,
                            timestamp = s.createdAt
                        )
                    )
                }
            }
        }

        // 3. Claims Search
        if (filter.entityType == SearchEntityType.ALL || filter.entityType == SearchEntityType.CLAIM) {
            val sqlBuilder = StringBuilder("""
                SELECT * FROM ${ResearchDatabase.TABLE_CLAIMS}
                WHERE (proposition LIKE ? OR notes LIKE ?)
            """.trimIndent())
            val args = mutableListOf(pattern, pattern)

            if (filter.projectId != null) {
                sqlBuilder.append(" AND project_id = ?")
                args.add(filter.projectId)
            }
            sqlBuilder.append(" ORDER BY updated_at DESC LIMIT 50")

            db.rawQuery(sqlBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val c = cursorToClaim(cursor)
                    val matchedField = if (c.proposition.contains(trimmed, ignoreCase = true)) "Proposition" else "Notes"
                    results.add(
                        SearchResult(
                            id = c.id,
                            entityType = SearchEntityType.CLAIM,
                            title = c.proposition,
                            snippet = c.notes ?: "Status: ${c.status.name}",
                            matchedField = matchedField,
                            projectId = c.projectId,
                            projectName = projectTitles[c.projectId],
                            verificationState = c.provenance.verificationState,
                            timestamp = c.updatedAt
                        )
                    )
                }
            }
        }

        // 4. Inbox Search
        if (filter.entityType == SearchEntityType.ALL || filter.entityType == SearchEntityType.INBOX) {
            val sqlBuilder = StringBuilder("""
                SELECT * FROM ${ResearchDatabase.TABLE_INBOX_ITEMS}
                WHERE (raw_content LIKE ? OR note LIKE ?)
            """.trimIndent())
            val args = mutableListOf(pattern, pattern)

            if (filter.projectId != null) {
                sqlBuilder.append(" AND (assigned_project_id = ? OR assigned_project_id IS NULL)")
                args.add(filter.projectId)
            }
            sqlBuilder.append(" ORDER BY created_at DESC LIMIT 50")

            db.rawQuery(sqlBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = cursorToInboxItem(cursor)
                    results.add(
                        SearchResult(
                            id = item.id,
                            entityType = SearchEntityType.INBOX,
                            title = "[${item.sourceApp.uppercase()}] ${item.rawType.name}",
                            snippet = item.rawContent.take(160),
                            matchedField = "Inbox Content",
                            projectId = item.assignedProjectId,
                            projectName = item.assignedProjectId?.let { projectTitles[it] },
                            timestamp = item.createdAt
                        )
                    )
                }
            }
        }

        // Sort: title/exact matches first, then by timestamp descending
        results.sortedWith(
            compareByDescending<SearchResult> { it.title.contains(trimmed, ignoreCase = true) }
                .thenByDescending { it.timestamp }
        )
    }

    // Helper mappers
    private fun cursorToProject(cursor: Cursor): ResearchProject {
        val tagsStr = cursor.getString(cursor.getColumnIndexOrThrow("tags")) ?: ""
        return ResearchProject(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            primaryQuestion = cursor.getString(cursor.getColumnIndexOrThrow("primary_question")),
            status = try {
                ProjectStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            } catch (e: Exception) {
                ProjectStatus.ACTIVE
            },
            discipline = try {
                Discipline.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("discipline")))
            } catch (e: Exception) {
                Discipline.MEDICAL
            },
            tags = if (tagsStr.isBlank()) emptyList() else tagsStr.split(",").map { it.trim() },
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToSource(cursor: Cursor): Source {
        val authorsJson = cursor.getString(cursor.getColumnIndexOrThrow("authors_json")) ?: "[]"
        val authorsList: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(authorsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return Source(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            authors = authorsList,
            journal = cursor.getString(cursor.getColumnIndexOrThrow("journal")),
            year = if (cursor.isNull(cursor.getColumnIndexOrThrow("year"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("year")),
            externalIds = ExternalIdentifiers(
                doi = cursor.getString(cursor.getColumnIndexOrThrow("doi")),
                pmid = cursor.getString(cursor.getColumnIndexOrThrow("pmid")),
                arxivId = cursor.getString(cursor.getColumnIndexOrThrow("arxiv_id")),
                url = cursor.getString(cursor.getColumnIndexOrThrow("url"))
            ),
            abstractText = cursor.getString(cursor.getColumnIndexOrThrow("abstract_text")),
            localPdfPath = cursor.getString(cursor.getColumnIndexOrThrow("local_pdf_path")),
            readingStatus = try {
                ReadingStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("reading_status")))
            } catch (e: Exception) {
                ReadingStatus.INBOX
            },
            studyType = try {
                StudyType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("study_type")))
            } catch (e: Exception) {
                StudyType.OTHER
            },
            isPreprint = try {
                cursor.getInt(cursor.getColumnIndexOrThrow("is_preprint")) == 1
            } catch (e: Exception) {
                false
            },
            preprintSource = try {
                cursor.getString(cursor.getColumnIndexOrThrow("preprint_source"))
            } catch (e: Exception) {
                null
            },
            peerReviewedVersionDoi = try {
                cursor.getString(cursor.getColumnIndexOrThrow("peer_reviewed_version_doi"))
            } catch (e: Exception) {
                null
            },
            discipline = try {
                Discipline.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("discipline")))
            } catch (e: Exception) {
                Discipline.MEDICAL
            },
            priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
            rationale = cursor.getString(cursor.getColumnIndexOrThrow("rationale")),
            provenance = ProvenanceRecord(
                originType = try {
                    OriginType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("origin_type")))
                } catch (e: Exception) {
                    OriginType.SOURCE_METADATA
                },
                verificationState = try {
                    VerificationState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_state")))
                } catch (e: Exception) {
                    VerificationState.UNVERIFIED
                },
                attribution = Attribution(
                    createdBy = cursor.getString(cursor.getColumnIndexOrThrow("created_by")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            ),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun cursorToClaim(cursor: Cursor): Claim {
        return Claim(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            proposition = cursor.getString(cursor.getColumnIndexOrThrow("proposition")),
            status = try {
                ClaimStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            } catch (e: Exception) {
                ClaimStatus.PROPOSED
            },
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            provenance = ProvenanceRecord(
                originType = try {
                    OriginType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("origin_type")))
                } catch (e: Exception) {
                    OriginType.USER_WRITTEN
                },
                verificationState = try {
                    VerificationState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_state")))
                } catch (e: Exception) {
                    VerificationState.UNVERIFIED
                },
                attribution = Attribution(
                    createdBy = cursor.getString(cursor.getColumnIndexOrThrow("created_by")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            ),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun cursorToEvidence(cursor: Cursor): Evidence {
        val page = cursor.getInt(cursor.getColumnIndexOrThrow("page_number"))
        val charStart = if (cursor.isNull(cursor.getColumnIndexOrThrow("char_offset_start"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("char_offset_start"))
        val charEnd = if (cursor.isNull(cursor.getColumnIndexOrThrow("char_offset_end"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("char_offset_end"))
        val section = cursor.getString(cursor.getColumnIndexOrThrow("section"))
        val boundingBox = cursor.getString(cursor.getColumnIndexOrThrow("bounding_box"))

        val loc = SourceLocation(
            pageNumber = page,
            charOffsetStart = charStart,
            charOffsetEnd = charEnd,
            section = section,
            boundingBox = boundingBox
        )

        return Evidence(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
            location = loc,
            excerptText = cursor.getString(cursor.getColumnIndexOrThrow("excerpt_text")),
            userInterpretation = cursor.getString(cursor.getColumnIndexOrThrow("user_interpretation")),
            relationshipType = try {
                EvidenceRelationship.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("relationship_type")))
            } catch (e: Exception) {
                EvidenceRelationship.UNDETERMINED
            },
            confidenceScore = try {
                cursor.getFloat(cursor.getColumnIndexOrThrow("confidence_score"))
            } catch (e: Exception) {
                1.0f
            },
            isPreprint = try {
                cursor.getInt(cursor.getColumnIndexOrThrow("is_preprint")) == 1
            } catch (e: Exception) {
                false
            },
            provenance = ProvenanceRecord(
                originType = try {
                    OriginType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("origin_type")))
                } catch (e: Exception) {
                    OriginType.DIRECT_EXCERPT
                },
                verificationState = try {
                    VerificationState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_state")))
                } catch (e: Exception) {
                    VerificationState.UNVERIFIED
                },
                location = loc,
                attribution = Attribution(
                    createdBy = cursor.getString(cursor.getColumnIndexOrThrow("created_by")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            ),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun cursorToInboxItem(cursor: Cursor): InboxItem {
        return InboxItem(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            rawType = try {
                InboxItemType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("raw_type")))
            } catch (e: Exception) {
                InboxItemType.TEXT
            },
            rawContent = cursor.getString(cursor.getColumnIndexOrThrow("raw_content")),
            sourceApp = cursor.getString(cursor.getColumnIndexOrThrow("source_app")),
            assignedProjectId = cursor.getString(cursor.getColumnIndexOrThrow("assigned_project_id")),
            processedSourceId = cursor.getString(cursor.getColumnIndexOrThrow("processed_source_id")),
            status = try {
                InboxStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            } catch (e: Exception) {
                InboxStatus.PENDING
            },
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    // IRB Tracking (Feature 71)
    suspend fun getIrbProtocols(projectId: String): List<IrbProtocol> = withContext(Dispatchers.IO) {
        val list = mutableListOf<IrbProtocol>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_IRB_TRACKING} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToIrbProtocol(cursor))
            }
        }
        list
    }

    suspend fun saveIrbProtocol(protocol: IrbProtocol) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", protocol.id)
            put("project_id", protocol.projectId)
            put("protocol_number", protocol.protocolNumber)
            put("institution", protocol.institution)
            put("title", protocol.title)
            put("status", protocol.status.name)
            put("approval_date", protocol.approvalDate)
            put("expiration_date", protocol.expirationDate)
            put("protocol_version", protocol.protocolVersion)
            put("icf_template_path", protocol.icfTemplatePath)
            put("decision_letter_path", protocol.decisionLetterPath)
            put("notes", protocol.notes)
            put("created_at", protocol.createdAt)
            put("updated_at", protocol.updatedAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_IRB_TRACKING, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteIrbProtocol(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(ResearchDatabase.TABLE_IRB_TRACKING, "id = ?", arrayOf(id))
    }

    private fun cursorToIrbProtocol(cursor: Cursor): IrbProtocol {
        return IrbProtocol(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            protocolNumber = cursor.getString(cursor.getColumnIndexOrThrow("protocol_number")),
            institution = cursor.getString(cursor.getColumnIndexOrThrow("institution")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            status = try {
                IrbStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            } catch (e: Exception) {
                IrbStatus.PENDING
            },
            approvalDate = if (cursor.isNull(cursor.getColumnIndexOrThrow("approval_date"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("approval_date")),
            expirationDate = if (cursor.isNull(cursor.getColumnIndexOrThrow("expiration_date"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("expiration_date")),
            protocolVersion = cursor.getString(cursor.getColumnIndexOrThrow("protocol_version")),
            icfTemplatePath = cursor.getString(cursor.getColumnIndexOrThrow("icf_template_path")),
            decisionLetterPath = cursor.getString(cursor.getColumnIndexOrThrow("decision_letter_path")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }

    // Dual Screening Workspace (Feature 75)
    suspend fun getScreeningSessions(projectId: String): List<ScreeningSession> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreeningSession>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SCREENING_SESSIONS} WHERE project_id = ? ORDER BY created_at DESC", arrayOf(projectId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToScreeningSession(cursor))
            }
        }
        list
    }

    suspend fun saveScreeningSession(session: ScreeningSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", session.id)
            put("project_id", session.projectId)
            put("title", session.title)
            put("inclusion_criteria", session.inclusionCriteria)
            put("exclusion_criteria", session.exclusionCriteria)
            put("screener1_name", session.screener1Name)
            put("screener2_name", session.screener2Name)
            put("arbitrator_name", session.arbitratorName)
            put("status", session.status.name)
            put("created_at", session.createdAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_SCREENING_SESSIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getScreeningDecisions(sessionId: String): List<ScreeningDecision> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScreeningDecision>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SCREENING_DECISIONS} WHERE session_id = ? ORDER BY decided_at ASC", arrayOf(sessionId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToScreeningDecision(cursor))
            }
        }
        list
    }

    suspend fun saveScreeningDecision(decision: ScreeningDecision) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", decision.id)
            put("session_id", decision.sessionId)
            put("source_id", decision.sourceId)
            put("screener_id", decision.screenerId)
            put("decision", decision.decision.name)
            put("exclusion_reason", decision.exclusionReason)
            put("notes", decision.notes)
            put("decided_at", decision.decidedAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_SCREENING_DECISIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getScreeningReliability(sessionId: String): ScreeningReliability? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT * FROM ${ResearchDatabase.TABLE_SCREENING_RELIABILITY} WHERE session_id = ? LIMIT 1", arrayOf(sessionId)).use { cursor ->
            if (cursor.moveToFirst()) cursorToScreeningReliability(cursor) else null
        }
    }

    suspend fun saveScreeningReliability(reliability: ScreeningReliability) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("session_id", reliability.sessionId)
            put("total_screened", reliability.totalScreened)
            put("agreed_count", reliability.agreedCount)
            put("conflicted_count", reliability.conflictedCount)
            put("percent_agreement", reliability.percentAgreement)
            put("cohens_kappa", reliability.cohensKappa)
            put("interpretation", reliability.interpretation)
            put("calculated_at", reliability.calculatedAt)
        }
        db.insertWithOnConflict(ResearchDatabase.TABLE_SCREENING_RELIABILITY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cursorToScreeningSession(cursor: Cursor): ScreeningSession {
        return ScreeningSession(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            inclusionCriteria = cursor.getString(cursor.getColumnIndexOrThrow("inclusion_criteria")) ?: "",
            exclusionCriteria = cursor.getString(cursor.getColumnIndexOrThrow("exclusion_criteria")) ?: "",
            screener1Name = cursor.getString(cursor.getColumnIndexOrThrow("screener1_name")),
            screener2Name = cursor.getString(cursor.getColumnIndexOrThrow("screener2_name")),
            arbitratorName = cursor.getString(cursor.getColumnIndexOrThrow("arbitrator_name")),
            status = try {
                ScreeningSessionStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            } catch (e: Exception) {
                ScreeningSessionStatus.IN_PROGRESS
            },
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun cursorToScreeningDecision(cursor: Cursor): ScreeningDecision {
        return ScreeningDecision(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
            sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
            screenerId = cursor.getString(cursor.getColumnIndexOrThrow("screener_id")),
            decision = try {
                ScreeningVote.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("decision")))
            } catch (e: Exception) {
                ScreeningVote.UNSURE
            },
            exclusionReason = cursor.getString(cursor.getColumnIndexOrThrow("exclusion_reason")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            decidedAt = cursor.getLong(cursor.getColumnIndexOrThrow("decided_at"))
        )
    }

    private fun cursorToScreeningReliability(cursor: Cursor): ScreeningReliability {
        return ScreeningReliability(
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
            totalScreened = cursor.getInt(cursor.getColumnIndexOrThrow("total_screened")),
            agreedCount = cursor.getInt(cursor.getColumnIndexOrThrow("agreed_count")),
            conflictedCount = cursor.getInt(cursor.getColumnIndexOrThrow("conflicted_count")),
            percentAgreement = cursor.getFloat(cursor.getColumnIndexOrThrow("percent_agreement")),
            cohensKappa = cursor.getFloat(cursor.getColumnIndexOrThrow("cohens_kappa")),
            interpretation = cursor.getString(cursor.getColumnIndexOrThrow("interpretation")),
            calculatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("calculated_at"))
        )
    }
}
