package com.ecosystem.research.core.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Native, local-first PDF document helper using Android's built-in PdfRenderer (API 21+).
 * Safely handles both direct file paths and Android content:// URIs without external C++ libraries.
 */
class PdfRendererHelper(
    private val context: Context,
    private val uriOrPath: String
) : AutoCloseable {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempFile: File? = null

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    suspend fun initialize(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pfd: ParcelFileDescriptor = when {
                uriOrPath.startsWith("content://") -> {
                    val uri = Uri.parse(uriOrPath)
                    context.contentResolver.openFileDescriptor(uri, "r")
                        ?: return@withContext Result.failure(Exception("Cannot open content URI: $uriOrPath"))
                }
                uriOrPath.startsWith("file://") -> {
                    val file = File(Uri.parse(uriOrPath).path ?: uriOrPath)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                else -> {
                    val file = File(uriOrPath)
                    if (!file.exists()) {
                        return@withContext Result.failure(Exception("File does not exist: $uriOrPath"))
                    }
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }

            fileDescriptor = pfd
            pdfRenderer = PdfRenderer(pfd)
            Result.success(pdfRenderer?.pageCount ?: 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders a specific 0-indexed page to a Bitmap at target scale.
     */
    suspend fun renderPage(pageIndex: Int, scaleFactor: Float = 2.0f): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val renderer = pdfRenderer ?: return@withContext Result.failure(Exception("Renderer not initialized"))
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return@withContext Result.failure(IndexOutOfBoundsException("Page $pageIndex out of bounds [0, ${renderer.pageCount})"))
            }

            val page = renderer.openPage(pageIndex)
            val width = (page.width * scaleFactor).toInt().coerceAtLeast(1)
            val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Fill background with clean white for clear contrast
            bitmap.eraseColor(Color.WHITE)

            // Render page contents
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun close() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        try {
            tempFile?.delete()
        } catch (_: Exception) {}
        pdfRenderer = null
        fileDescriptor = null
        tempFile = null
    }
}
