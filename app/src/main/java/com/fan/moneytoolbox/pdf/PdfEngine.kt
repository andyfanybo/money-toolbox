package com.fan.moneytoolbox.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import java.io.File
import java.io.IOException
import java.util.UUID

class PdfPasswordRequiredException : IOException("PDF 需要密码")

/** MuPDF 的输入需要可随机读取；把 SAF URI 流式复制到私有缓存，绝不上传文件。 */
object PdfEngine {
    private const val MAX_EXPORT_PIXELS = 40_000_000L

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    fun safeBaseName(name: String): String = name.substringBeforeLast('.', name)
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim().take(80).ifBlank { "document" }

    fun copyToCache(context: Context, uri: Uri): File {
        val file = File(context.cacheDir, "pdf-${UUID.randomUUID()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取 PDF 文件")
            if (file.length() == 0L) throw IOException("PDF 文件为空")
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun open(file: File, password: String? = null): Document {
        val document = Document.openDocument(file.absolutePath)
        if (document.needsPassword() && !document.authenticatePassword(password ?: "")) {
            document.destroy()
            throw PdfPasswordRequiredException()
        }
        return document
    }

    /** 1× 是 PDF 原始 72dpi 尺寸；按页处理，并限制异常大页面造成的内存占用。 */
    fun renderPng(document: Document, pageIndex: Int, scale: Int, output: File) {
        require(scale in 1..4)
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pixels = (bounds.x1 - bounds.x0).toDouble() * (bounds.y1 - bounds.y0) * scale * scale
            if (pixels > MAX_EXPORT_PIXELS) throw IOException("这一页过大，请选择更低清晰度")
            val pixmap = page.toPixmap(Matrix(scale.toFloat(), 0f, 0f, scale.toFloat(), 0f, 0f), ColorSpace.DeviceRGB, false)
            try {
                pixmap.saveAsPNG(output.absolutePath)
            } finally {
                pixmap.destroy()
            }
        } finally {
            page.destroy()
        }
    }

    fun renderPreview(context: Context, document: Document, pageIndex: Int): Bitmap {
        val preview = File.createTempFile("pdf-preview-", ".png", context.cacheDir)
        return try {
            val page = document.loadPage(pageIndex)
            val bounds = try { page.bounds } finally { page.destroy() }
            val basePixels = (bounds.x1 - bounds.x0) * (bounds.y1 - bounds.y0)
            renderPng(document, pageIndex, if (basePixels * 4 <= 8_000_000) 2 else 1, preview)
            BitmapFactory.decodeFile(preview.absolutePath) ?: throw IOException("无法显示这一页")
        } finally {
            preview.delete()
        }
    }
}
