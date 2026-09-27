package com.fan.moneytoolbox.pdf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class PdfConversionProgress(
    val fileNumber: Int,
    val fileCount: Int,
    val pageNumber: Int,
    val pageCount: Int,
    val completedImages: Int,
    val errors: List<String>,
)

/** 导出到用户通过系统选择的目录；每页单独渲染和释放，支持取消。 */
object PdfBatchConverter {
    suspend fun exportPage(
        context: Context,
        document: Document,
        displayName: String,
        pageIndex: Int,
        outputTree: Uri,
        scale: Int,
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            outputTree, DocumentsContract.getTreeDocumentId(outputTree),
        )
        val temporary = File.createTempFile("pdf-export-", ".png", context.cacheDir)
        try {
            PdfEngine.renderPng(document, pageIndex, scale, temporary)
            val name = "${PdfEngine.safeBaseName(displayName)}-p${(pageIndex + 1).toString().padStart(4, '0')}-${scale}x.png"
            val destination = DocumentsContract.createDocument(resolver, parent, "image/png", name)
                ?: throw IOException("无法创建输出文件")
            try {
                resolver.openOutputStream(destination)?.use { output ->
                    temporary.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("无法写入输出文件")
            } catch (error: Exception) {
                DocumentsContract.deleteDocument(resolver, destination)
                throw error
            }
            name
        } finally {
            temporary.delete()
        }
    }

    suspend fun convert(
        context: Context,
        files: List<Uri>,
        outputTree: Uri,
        scale: Int,
        password: String?,
        onProgress: (PdfConversionProgress) -> Unit,
    ): PdfConversionProgress = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            outputTree, DocumentsContract.getTreeDocumentId(outputTree),
        )
        val errors = mutableListOf<String>()
        var completed = 0
        files.forEachIndexed { fileIndex, uri ->
            coroutineContext.ensureActive()
            val displayName = PdfEngine.displayName(context, uri)
            val cached = try { PdfEngine.copyToCache(context, uri) } catch (error: Exception) {
                errors += "$displayName：${error.message ?: "无法读取"}"
                return@forEachIndexed
            }
            try {
                val document = PdfEngine.open(cached, password)
                try {
                    val pages = document.countPages()
                    for (pageIndex in 0 until pages) {
                        coroutineContext.ensureActive()
                        val temporary = File.createTempFile("pdf-export-", ".png", context.cacheDir)
                        try {
                            PdfEngine.renderPng(document, pageIndex, scale, temporary)
                            val name = "${fileIndex + 1}-${PdfEngine.safeBaseName(displayName)}-p${(pageIndex + 1).toString().padStart(4, '0')}-${scale}x.png"
                            val destination = DocumentsContract.createDocument(resolver, parent, "image/png", name)
                                ?: throw IOException("无法创建输出文件")
                            try {
                                resolver.openOutputStream(destination)?.use { output ->
                                    temporary.inputStream().use { input -> input.copyTo(output) }
                                } ?: throw IOException("无法写入输出文件")
                            } catch (error: Exception) {
                                DocumentsContract.deleteDocument(resolver, destination)
                                throw error
                            }
                            completed++
                            onProgress(PdfConversionProgress(fileIndex + 1, files.size, pageIndex + 1, pages, completed, errors.toList()))
                        } finally {
                            temporary.delete()
                        }
                    }
                } finally {
                    document.destroy()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errors += "$displayName：${error.message ?: "转换失败"}"
                onProgress(PdfConversionProgress(fileIndex + 1, files.size, 0, 0, completed, errors.toList()))
            } finally {
                cached.delete()
            }
        }
        PdfConversionProgress(files.size, files.size, 0, 0, completed, errors.toList())
    }
}
