@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fan.moneytoolbox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fan.moneytoolbox.RenewalViewModel
import com.fan.moneytoolbox.data.RenewalCategory
import com.fan.moneytoolbox.data.RenewalCycle
import com.fan.moneytoolbox.data.RenewalItem
import com.fan.moneytoolbox.data.RenewalMath
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun RenewalScreen(viewModel: RenewalViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RenewalItem?>(null) }
    var deleting by remember { mutableStateOf<RenewalItem?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("续费提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "停车月租、影音会员和其他账单，到期前记得决定是否续费。提醒在当天 09:00 发出。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { editing = null; editorOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("新增续费项目", modifier = Modifier.padding(start = 8.dp))
            }
            val active = items.filterNot { it.completed }.sortedBy { RenewalMath.dueDate(it) }
            if (active.isEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("还没有待续费项目。添加停车月租或会员，到期前就会收到提醒。", Modifier.padding(20.dp))
                }
            }
            active.forEach { item ->
                RenewalCard(
                    item = item,
                    onEdit = { editing = item; editorOpen = true },
                    onPaid = { viewModel.markPaid(item.id) },
                    onDelete = { deleting = item },
                )
            }
            if (items.any { it.completed }) {
                Text("已完成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                items.filter { it.completed }.forEach { item ->
                    RenewalCard(item, onEdit = { editing = item; editorOpen = true }, onPaid = {}, onDelete = { deleting = item })
                }
            }
            Text("仅在本机保存，不会代扣或自动续费。未点“已续费”的项目会保留到期或逾期状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (editorOpen) {
        RenewalEditor(
            existing = editing,
            onDismiss = { editorOpen = false },
            onSave = { viewModel.save(it); editorOpen = false },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除续费项目？") },
            text = { Text("将删除“${item.name}”及其尚未触发的提醒。") },
            confirmButton = { TextButton(onClick = { viewModel.delete(item.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RenewalCard(item: RenewalItem, onEdit: () -> Unit, onPaid: () -> Unit, onDelete: () -> Unit) {
    val due = RenewalMath.dueDate(item)
    val days = ChronoUnit.DAYS.between(LocalDate.now(), due)
    val status = when {
        item.completed -> "已完成"
        days < 0 -> "逾期 ${-days} 天"
        days == 0L -> "今天到期"
        else -> "$days 天后到期"
    }
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(status, color = if (days <= 0 && !item.completed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Text("${item.category.label} · ${item.cycle.label} · $due", style = MaterialTheme.typography.bodyMedium)
            item.amountCents?.let {
                Text("预计 ¥${it / 100}.${(it % 100).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(if (item.remindDaysBefore == 0) "到期当天提醒" else "提前 ${item.remindDaysBefore} 天和到期当天提醒", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                if (!item.completed) TextButton(onClick = onPaid) { Text("已续费") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun RenewalEditor(existing: RenewalItem?, onDismiss: () -> Unit, onSave: (RenewalItem) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: RenewalCategory.PARKING) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amountCents?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" } ?: "") }
    var selectedDue by remember(existing?.id) { mutableStateOf(existing?.let(RenewalMath::dueDate) ?: LocalDate.now().plusDays(7)) }
    var cycle by remember(existing?.id) { mutableStateOf(existing?.cycle ?: RenewalCycle.MONTHLY) }
    var remindDays by remember(existing?.id) { mutableStateOf((existing?.remindDaysBefore ?: 3).toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val amountCents = parseAmount(amount)
    val days = remindDays.toIntOrNull()
    val valid = name.isNotBlank() && (amount.isBlank() || amountCents != null) && days != null && days in 0..365

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增续费项目" else "编辑续费项目") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("项目名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("类型", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenewalCategory.entries.forEach { option ->
                        FilterChip(category == option, onClick = { category = option }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("金额（元，可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = amount.isNotBlank() && amountCents == null, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { showDatePicker = true }) { Text("下次到期日：$selectedDue") }
                Text("续费周期", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenewalCycle.entries.forEach { option ->
                        FilterChip(cycle == option, onClick = { cycle = option }, label = { Text(option.label) })
                    }
                }
                Text("提前提醒", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 3, 7, 30).forEach { value ->
                        FilterChip(remindDays == value.toString(), onClick = { remindDays = value.toString() }, label = { Text("$value 天") })
                    }
                }
                OutlinedTextField(remindDays, { remindDays = it.filter(Char::isDigit).take(3) },
                    label = { Text("自定义提前天数（0–365）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = days == null || days !in 0..365, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("到期当天也会提醒。若提前日期已过，将保留到期当天提醒。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(RenewalItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(), category = category, amountCents = amountCents,
                    firstDueEpochDay = if (existing != null && selectedDue == RenewalMath.dueDate(existing) && existing.cycle == cycle)
                        existing.firstDueEpochDay else selectedDue.toEpochDay(),
                    cycle = cycle,
                    periodIndex = if (existing != null && selectedDue == RenewalMath.dueDate(existing) && existing.cycle == cycle)
                        existing.periodIndex else 0,
                    remindDaysBefore = days!!, completed = false,
                ))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDue.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { selectedDue = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                showDatePicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = picker) }
    }
}

private fun parseAmount(input: String): Long? {
    if (!Regex("[0-9]{1,7}(\\.[0-9]{1,2})?").matches(input.trim())) return null
    return runCatching { BigDecimal(input.trim()).movePointRight(2).longValueExact() }.getOrNull()
}
