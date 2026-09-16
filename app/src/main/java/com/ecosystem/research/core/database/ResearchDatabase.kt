package com.ecosystem.research.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ResearchDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "research_ecosystem.db"
        const val DB_VERSION = 1

        const val TABLE_PROJECTS = "projects"
        const val TABLE_SOURCES = "sources"
        const val TABLE_CLAIMS = "claims"
        const val TABLE_EVIDENCE = "evidence"
        const val TABLE_CLAIM_EVIDENCE_EDGES = "claim_evidence_edges"
        const val TABLE_INBOX_ITEMS = "inbox_items"
        const val TABLE_MATRICES = "evidence_matrices"
        const val TABLE_MATRIX_CELLS = "matrix_cells"

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MATRIX_CELLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MATRICES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_INBOX_ITEMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLAIM_EVIDENCE_EDGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVIDENCE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLAIMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SOURCES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROJECTS")
        onCreate(db)
    }
}
