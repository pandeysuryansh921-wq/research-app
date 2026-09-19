package com.ecosystem.research.core.repository

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.ecosystem.research.core.database.ResearchDatabase
import com.ecosystem.research.core.model.*
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
}
