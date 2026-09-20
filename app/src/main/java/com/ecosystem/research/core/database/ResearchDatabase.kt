package com.ecosystem.research.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ResearchDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "research_ecosystem.db"
        const val DB_VERSION = 2

        const val TABLE_PROJECTS = "projects"
        const val TABLE_SOURCES = "sources"
        const val TABLE_CLAIMS = "claims"
        const val TABLE_EVIDENCE = "evidence"
        const val TABLE_CLAIM_EVIDENCE_EDGES = "claim_evidence_edges"
        const val TABLE_INBOX_ITEMS = "inbox_items"
        const val TABLE_MATRICES = "evidence_matrices"
        const val TABLE_MATRIX_CELLS = "matrix_cells"

        // v2.2 Relational Tables
        const val TABLE_IRB_TRACKING = "irb_tracking"
        const val TABLE_SCREENING_SESSIONS = "screening_sessions"
        const val TABLE_SCREENING_DECISIONS = "screening_decisions"
        const val TABLE_SCREENING_RELIABILITY = "screening_reliability"

        @Volatile
        private var INSTANCE: ResearchDatabase? = null

        fun getInstance(context: Context): ResearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = ResearchDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PROJECTS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                primary_question TEXT,
                status TEXT NOT NULL,
                discipline TEXT NOT NULL DEFAULT 'MEDICAL',
                tags TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SOURCES (
                id TEXT PRIMARY KEY,
                project_id TEXT,
                title TEXT NOT NULL,
                authors_json TEXT,
                journal TEXT,
                year INTEGER,
                doi TEXT,
                pmid TEXT,
                arxiv_id TEXT,
                url TEXT,
                abstract_text TEXT,
                local_pdf_path TEXT,
                reading_status TEXT NOT NULL,
                study_type TEXT NOT NULL,
                is_preprint INTEGER NOT NULL DEFAULT 0,
                preprint_source TEXT,
                peer_reviewed_version_doi TEXT,
                discipline TEXT NOT NULL DEFAULT 'MEDICAL',
                priority INTEGER NOT NULL,
                rationale TEXT,
                origin_type TEXT NOT NULL,
                verification_state TEXT NOT NULL,
                created_by TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sources_project ON $TABLE_SOURCES(project_id)")
        db.execSQL("CREATE INDEX idx_sources_doi ON $TABLE_SOURCES(doi)")
        db.execSQL("CREATE INDEX idx_sources_pmid ON $TABLE_SOURCES(pmid)")
        db.execSQL("CREATE INDEX idx_sources_reading_status ON $TABLE_SOURCES(reading_status)")
        db.execSQL("CREATE INDEX idx_sources_preprint ON $TABLE_SOURCES(is_preprint)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_CLAIMS (
                id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                proposition TEXT NOT NULL,
                status TEXT NOT NULL,
                notes TEXT,
                origin_type TEXT NOT NULL,
                verification_state TEXT NOT NULL,
                created_by TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_claims_project ON $TABLE_CLAIMS(project_id)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_EVIDENCE (
                id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                source_id TEXT NOT NULL,
                page_number INTEGER NOT NULL,
                char_offset_start INTEGER,
                char_offset_end INTEGER,
                section TEXT,
                bounding_box TEXT,
                excerpt_text TEXT NOT NULL,
                user_interpretation TEXT,
                relationship_type TEXT NOT NULL,
                confidence_score REAL NOT NULL DEFAULT 1.0,
                is_preprint INTEGER NOT NULL DEFAULT 0,
                origin_type TEXT NOT NULL,
                verification_state TEXT NOT NULL,
                created_by TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_evidence_project ON $TABLE_EVIDENCE(project_id)")
        db.execSQL("CREATE INDEX idx_evidence_source ON $TABLE_EVIDENCE(source_id)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_CLAIM_EVIDENCE_EDGES (
                claim_id TEXT NOT NULL,
                evidence_id TEXT NOT NULL,
                relationship_type TEXT NOT NULL,
                review_note TEXT,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (claim_id, evidence_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_INBOX_ITEMS (
                id TEXT PRIMARY KEY,
                raw_type TEXT NOT NULL,
                raw_content TEXT NOT NULL,
                source_app TEXT NOT NULL,
                assigned_project_id TEXT,
                processed_source_id TEXT,
                status TEXT NOT NULL,
                note TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_inbox_status ON $TABLE_INBOX_ITEMS(status)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_MATRICES (
                id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                title TEXT NOT NULL,
                columns_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_matrices_project ON $TABLE_MATRICES(project_id)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_MATRIX_CELLS (
                matrix_id TEXT NOT NULL,
                source_id TEXT NOT NULL,
                column_key TEXT NOT NULL,
                cell_value TEXT NOT NULL,
                evidence_id TEXT,
                verification_state TEXT NOT NULL,
                PRIMARY KEY (matrix_id, source_id, column_key)
            )
            """.trimIndent()
        )

        createV2Tables(db)
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_IRB_TRACKING (
                id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                protocol_number TEXT NOT NULL,
                institution TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                approval_date INTEGER,
                expiration_date INTEGER,
                protocol_version TEXT,
                icf_template_path TEXT,
                decision_letter_path TEXT,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_irb_project ON $TABLE_IRB_TRACKING(project_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCREENING_SESSIONS (
                id TEXT PRIMARY KEY,
                project_id TEXT NOT NULL,
                title TEXT NOT NULL,
                inclusion_criteria TEXT,
                exclusion_criteria TEXT,
                screener1_name TEXT NOT NULL,
                screener2_name TEXT NOT NULL,
                arbitrator_name TEXT,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screening_project ON $TABLE_SCREENING_SESSIONS(project_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCREENING_DECISIONS (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                source_id TEXT NOT NULL,
                screener_id TEXT NOT NULL,
                decision TEXT NOT NULL,
                exclusion_reason TEXT,
                notes TEXT,
                decided_at INTEGER NOT NULL,
                UNIQUE(session_id, source_id, screener_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decisions_session ON $TABLE_SCREENING_DECISIONS(session_id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCREENING_RELIABILITY (
                session_id TEXT PRIMARY KEY,
                total_screened INTEGER NOT NULL,
                agreed_count INTEGER NOT NULL,
                conflicted_count INTEGER NOT NULL,
                percent_agreement REAL NOT NULL,
                cohens_kappa REAL NOT NULL,
                interpretation TEXT NOT NULL,
                calculated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PROJECTS ADD COLUMN discipline TEXT NOT NULL DEFAULT 'MEDICAL'")
            } catch (e: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_SOURCES ADD COLUMN is_preprint INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_SOURCES ADD COLUMN preprint_source TEXT")
                db.execSQL("ALTER TABLE $TABLE_SOURCES ADD COLUMN peer_reviewed_version_doi TEXT")
                db.execSQL("ALTER TABLE $TABLE_SOURCES ADD COLUMN discipline TEXT NOT NULL DEFAULT 'MEDICAL'")
            } catch (e: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_EVIDENCE ADD COLUMN confidence_score REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE $TABLE_EVIDENCE ADD COLUMN is_preprint INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {}

            createV2Tables(db)
        }
    }
}
