package com.fan.moneytoolbox

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fan.moneytoolbox.pdf.PdfBatchConverter
import com.fan.moneytoolbox.pdf.PdfConversionProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PdfConversionUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val progress: PdfConversionProgress? = null,
    val error: String? = null,
)

class PdfToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val mutableState = MutableStateFlow(PdfConversionUiState())
    val state: StateFlow<PdfConversionUiState> = mutableState
    private var conversion: Job? = null

    fun convert(files: List<Uri>, folder: Uri, scale: Int, password: String?) {
        if (conversion?.isActive == true || files.isEmpty()) return
        mutableState.value = PdfConversionUiState(running = true)
        conversion = viewModelScope.launch {
            try {
                val result = PdfBatchConverter.convert(getApplication(), files, folder, scale, password) {
                    mutableState.value = mutableState.value.copy(progress = it)
                }
                mutableState.value = PdfConversionUiState(finished = true, progress = result)
            } catch (_: CancellationException) {
                mutableState.value = mutableState.value.copy(running = false, error = "已取消转换")
            } catch (_: LinkageError) {
                mutableState.value = mutableState.value.copy(running = false, error = "PDF 引擎加载失败，请更新应用")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(running = false, error = error.message ?: "转换失败")
            }
        }
    }

    fun cancel() { conversion?.cancel() }
}
