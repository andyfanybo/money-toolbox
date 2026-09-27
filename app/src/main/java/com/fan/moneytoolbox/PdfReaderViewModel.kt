package com.fan.moneytoolbox

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.artifex.mupdf.fitz.Document
import com.fan.moneytoolbox.pdf.PdfBatchConverter
import com.fan.moneytoolbox.pdf.PdfEngine
import com.fan.moneytoolbox.pdf.PdfPasswordRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class PdfReaderUiState(
    val name: String = "PDF 阅读",
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val bitmap: Bitmap? = null,
    val loading: Boolean = true,
    val needsPassword: Boolean = false,
    val message: String? = null,
)

class PdfReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val mutableState = MutableStateFlow(PdfReaderUiState())
    val state: StateFlow<PdfReaderUiState> = mutableState
    private val mutex = Mutex()
    private var sourceUri: Uri? = null
    private var cachedFile: File? = null
    private var document: Document? = null
    private var renderJob: Job? = null

    fun open(uri: Uri, password: String? = null) {
        if (sourceUri == uri && document != null && password == null) return
        sourceUri = uri
        renderJob?.cancel()
        mutableState.value = PdfReaderUiState(name = PdfEngine.displayName(getApplication(), uri))
        viewModelScope.launch {
            try {
                val (pageCount, pageIndex, bitmap) = withContext(Dispatchers.IO) {
                    mutex.withLock {
                        document?.destroy()
                        document = null
                        cachedFile?.delete()
                        val file = PdfEngine.copyToCache(getApplication(), uri)
                        cachedFile = file
                        val opened = PdfEngine.open(file, password)
                        document = opened
                        val count = opened.countPages()
                        if (count == 0) throw IllegalArgumentException("PDF 没有可显示的页面")
                        val saved = getApplication<Application>().getSharedPreferences("pdf_reading", 0)
                            .getInt(uri.toString(), 0).coerceIn(0, count - 1)
                        Triple(count, saved, PdfEngine.renderPreview(getApplication(), opened, saved))
                    }
                }
                mutableState.value = PdfReaderUiState(name = PdfEngine.displayName(getApplication(), uri),
                    pageCount = pageCount, pageIndex = pageIndex, bitmap = bitmap, loading = false)
            } catch (_: PdfPasswordRequiredException) {
                mutableState.value = mutableState.value.copy(loading = false, needsPassword = true, message = "请输入 PDF 密码")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, message = error.message ?: "无法打开 PDF")
            }
        }
    }

    fun goTo(index: Int, message: String? = null) {
        val current = mutableState.value
        if (index !in 0 until current.pageCount) return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, message = null)
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    mutex.withLock { PdfEngine.renderPreview(getApplication(), document ?: error("PDF 已关闭"), index) }
                }
                mutableState.value = mutableState.value.copy(pageIndex = index, bitmap = bitmap, loading = false, message = message)
                sourceUri?.let { getApplication<Application>().getSharedPreferences("pdf_reading", 0)
                    .edit().putInt(it.toString(), index).apply() }
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, message = error.message ?: "页面显示失败")
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                val current = mutableState.value
                mutableState.value = current.copy(message = "正在搜索…")
                val found = withContext(Dispatchers.IO) {
                    mutex.withLock {
                        val doc = document ?: return@withLock null
                        for (offset in 1..current.pageCount) {
                            val index = (current.pageIndex + offset) % current.pageCount
                            val page = doc.loadPage(index)
                            try {
                                if (page.search(query).isNotEmpty()) return@withLock index
                            } finally { page.destroy() }
                        }
                        null
                    }
                }
                if (found == null) mutableState.value = mutableState.value.copy(message = "未找到“$query”")
                else goTo(found, "已找到第 ${found + 1} 页")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "搜索失败")
            }
        }
    }

    fun exportCurrentPage(folder: Uri, scale: Int) {
        viewModelScope.launch {
            val current = mutableState.value
            mutableState.value = current.copy(message = "正在导出第 ${current.pageIndex + 1} 页…")
            try {
                val name = mutex.withLock {
                    PdfBatchConverter.exportPage(getApplication(), document ?: error("PDF 已关闭"),
                        current.name, current.pageIndex, folder, scale)
                }
                mutableState.value = mutableState.value.copy(message = "已保存 $name")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "导出失败")
            }
        }
    }

    override fun onCleared() {
        document?.destroy()
        cachedFile?.delete()
        super.onCleared()
    }
}
