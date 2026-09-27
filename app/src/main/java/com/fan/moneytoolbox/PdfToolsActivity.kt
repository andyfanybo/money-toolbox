package com.fan.moneytoolbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fan.moneytoolbox.pdf.PdfEngine
import com.fan.moneytoolbox.ui.theme.MoneyBoxTheme

class PdfToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoneyBoxTheme {
                val viewModel: PdfToolsViewModel = viewModel()
                PdfToolsScreen(viewModel, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PdfToolsScreen(viewModel: PdfToolsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var scale by remember { mutableStateOf(2) }
    var password by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    fun keepReadPermission(uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { }
    }
    val readPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            keepReadPermission(it)
            context.startActivity(Intent(context, PdfReaderActivity::class.java)
                .setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        files = uri?.let { keepReadPermission(it); listOf(it) } ?: emptyList()
    }
    val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(::keepReadPermission)
        files = uris
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        folder = uri
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("PDF 工具") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("阅读 PDF", style = MaterialTheme.typography.titleMedium)
                    Text("浏览、缩放、搜索、跳页，也能导出正在阅读的页面。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { readPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("打开 PDF 阅读")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PDF 转图片", style = MaterialTheme.typography.titleMedium)
                    Text("每页导出为 PNG。可选一个或多个 PDF，结果保存到你指定的文件夹。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { singlePicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("选择单个 PDF")
                    }
                    OutlinedButton(onClick = { multiplePicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("批量选择 PDF")
                    }
                    if (files.isNotEmpty()) {
                        Text("已选 ${files.size} 个文件", style = MaterialTheme.typography.labelLarge)
                        files.take(5).forEach { Text(PdfEngine.displayName(context, it), style = MaterialTheme.typography.bodySmall) }
                        if (files.size > 5) Text("还有 ${files.size - 5} 个文件", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("清晰度", style = MaterialTheme.typography.labelLarge)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { value ->
                            FilterChip(selected = scale == value, onClick = { scale = value }, label = { Text("${value}×") })
                        }
                    }
                    Text("1× 为 PDF 基准尺寸；倍数越高，图片越清晰、占用空间越大。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(password, { password = it }, label = { Text("PDF 密码（可选）") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (folder == null) "选择保存文件夹" else "保存位置：${folder?.lastPathSegment ?: "已选择"}")
                    }
                    Button(
                        onClick = { viewModel.convert(files, folder!!, scale, password.ifBlank { null }) },
                        enabled = files.isNotEmpty() && folder != null && !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("开始转换") }
                    if (state.running) {
                        val p = state.progress
                        Text(if (p == null) "正在准备 PDF…" else "第 ${p.fileNumber}/${p.fileCount} 个文件 · 第 ${p.pageNumber}/${p.pageCount} 页 · 已导出 ${p.completedImages} 张")
                        TextButton(onClick = viewModel::cancel) { Text("取消转换") }
                    }
                    if (state.finished) Text("完成：导出 ${state.progress?.completedImages ?: 0} 张图片。", color = MaterialTheme.colorScheme.primary)
                    state.progress?.errors?.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
