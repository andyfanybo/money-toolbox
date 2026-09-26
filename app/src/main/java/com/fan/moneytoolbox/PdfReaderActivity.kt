package com.fan.moneytoolbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fan.moneytoolbox.ui.theme.MoneyBoxTheme

class PdfReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) { finish(); return }
        setContent {
            MoneyBoxTheme {
                val viewModel: PdfReaderViewModel = viewModel()
                ReaderScreen(uri, viewModel, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderScreen(uri: Uri, viewModel: PdfReaderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var password by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var jumpOpen by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var exportScale by remember { mutableStateOf(2) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(uri) { viewModel.open(uri) }
    LaunchedEffect(state.pageIndex) { zoom = 1f; offset = Offset.Zero }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        folder?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { }
            viewModel.exportCurrentPage(it, exportScale)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(state.name, maxLines = 1) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pageCount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { viewModel.goTo(state.pageIndex - 1) }, enabled = state.pageIndex > 0) { Text("上一页") }
                    TextButton(onClick = { jumpText = (state.pageIndex + 1).toString(); jumpOpen = true }, modifier = Modifier.weight(1f)) {
                        Text("${state.pageIndex + 1} / ${state.pageCount} 页")
                    }
                    OutlinedButton(onClick = { viewModel.goTo(state.pageIndex + 1) }, enabled = state.pageIndex + 1 < state.pageCount) { Text("下一页") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(searchText, { searchText = it }, label = { Text("搜索文字") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.search(searchText) }, enabled = searchText.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterVertically)) { Text("查找") }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                state.bitmap?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), contentDescription = "PDF 第 ${state.pageIndex + 1} 页",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(bitmap) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    offset = if (zoom == 1f) Offset.Zero else offset + pan
                                }
                            }
                            .graphicsLayer(scaleX = zoom, scaleY = zoom,
                                translationX = offset.x, translationY = offset.y))
                }
                if (state.loading) CircularProgressIndicator()
            }
            if (state.pageCount > 0) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("导出本页", modifier = Modifier.align(Alignment.CenterVertically))
                    (1..4).forEach { value ->
                        FilterChip(selected = exportScale == value, onClick = { exportScale = value }, label = { Text("${value}×") })
                    }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("保存 PNG") }
                }
            }
            state.message?.let { Text(it, color = if (state.needsPassword) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }

    if (state.needsPassword) AlertDialog(
        onDismissRequest = onBack,
        title = { Text("PDF 需要密码") },
        text = { OutlinedTextField(password, { password = it }, label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton(onClick = { viewModel.open(uri, password) }) { Text("打开") } },
        dismissButton = { TextButton(onClick = onBack) { Text("取消") } },
    )
    if (jumpOpen) AlertDialog(
        onDismissRequest = { jumpOpen = false }, title = { Text("跳转到页码") },
        text = { OutlinedTextField(jumpText, { jumpText = it.filter(Char::isDigit).take(5) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton(onClick = {
            jumpText.toIntOrNull()?.let { viewModel.goTo(it - 1) }
            jumpOpen = false
        }) { Text("跳转") } },
        dismissButton = { TextButton(onClick = { jumpOpen = false }) { Text("取消") } },
    )
}
