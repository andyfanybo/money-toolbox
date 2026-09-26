@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fan.moneytoolbox.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fan.moneytoolbox.ParkingViewModel
import com.fan.moneytoolbox.data.ParkingConfig
import com.fan.moneytoolbox.data.ParkingMath
import com.fan.moneytoolbox.data.ParkingSession
import com.fan.moneytoolbox.data.RemindMode
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ceil

private val HeroGradient = Brush.linearGradient(
    listOf(Color(0xFF10B981), Color(0xFF047857))
)

private val Gold = Color(0xFFFBBF24)

// 预设选项
private val FREE_PRESETS = listOf(5, 10, 15, 30)
private val UNIT_PRESETS = listOf(15, 30, 60)
private val GRACE_PRESETS = listOf(5, 10, 15, 30)
private val BUFFER_PRESETS = listOf(1, 2, 3, 5)

private fun freeReminderLeadText(cfg: ParkingConfig): String {
    val session = ParkingSession(0L, cfg)
    val remind = ParkingMath.freeRemindMs(session) ?: return "不会发送免费到期提醒"
    val leadSeconds = (ParkingMath.freeEndMs(session) - remind) / 1000
    return if (leadSeconds % 60L == 0L) "到期前 ${leadSeconds / 60L} 分钟提醒你"
    else "到期前 $leadSeconds 秒提醒你"
}

@Composable
fun ParkingScreen(viewModel: ParkingViewModel, onBack: () -> Unit) {
    val context = LocalContext.current

    // Android 13+ 通知运行时权限
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("停车收费提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExactAlarmBanner()
            val s by viewModel.session.collectAsState()
            if (s == null) {
                SetupSection(viewModel)
            } else {
                ActiveSection(viewModel, s!!)
            }
        }
    }
}

// ---------------------------------------------------------------- 权限提示

@Composable
private fun ExactAlarmBanner() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < 31) return
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (am.canScheduleExactAlarms()) return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "建议开启「闹钟和提醒」权限",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "开启后锁屏/后台也能准时提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:" + context.packageName))
                    )
                } catch (_: Exception) {
                }
            }) { Text("去开启") }
        }
    }
}

// ---------------------------------------------------------------- 通用小组件

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 预设值 + 「自定义」的一排筛选块(FlowRow 整块换行,文字永不截断换行) */
@Composable
private fun ValueChips(
    presets: List<Int>,
    value: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
    onCustomClick: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { p ->
            FilterChip(
                selected = value == p,
                onClick = { onSelect(p) },
                label = { Text(format(p), maxLines = 1, softWrap = false) },
            )
        }
        FilterChip(
            selected = presets.none { it == value },
            onClick = onCustomClick,
            label = { Text("自定义", maxLines = 1, softWrap = false) },
        )
    }
}

/** 数字输入框(纯数字键盘,自动收敛到合法区间) */
@Composable
private fun NumberField(
    value: Int,
    label: String,
    suffixText: String,
    range: IntRange,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { v ->
            val n = v.filter { ch -> ch.isDigit() }.take(4).toIntOrNull() ?: 0
            onChange(n.coerceIn(range))
        },
        label = { Text(label) },
        suffix = { Text(suffixText) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- 设置区

@Composable
private fun SetupSection(viewModel: ParkingViewModel) {
    val defaults by viewModel.config.collectAsState()

    // 用户改过的配置优先于默认值
    var edited by remember { mutableStateOf<ParkingConfig?>(null) }
    val cfg = edited ?: defaults

    var entryMs by remember {
        mutableStateOf(System.currentTimeMillis() - System.currentTimeMillis() % ParkingMath.MINUTE_MS)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }

    fun update(transform: (ParkingConfig) -> ParkingConfig) {
        edited = transform(cfg)
    }

    // 入场时间卡片
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.LocalParking,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(22.dp),
                    )
                }
                Column {
                    Text("入场时间", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "进入闸口的时间,默认当前",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Format.clock(entryMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    Format.date(entryMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryButton("此刻", Modifier.weight(1f)) {
                    entryMs = System.currentTimeMillis() - System.currentTimeMillis() % ParkingMath.MINUTE_MS
                }
                EntryButton("−5 分", Modifier.weight(1f)) { entryMs -= 5 * ParkingMath.MINUTE_MS }
                EntryButton("+5 分", Modifier.weight(1f)) { entryMs += 5 * ParkingMath.MINUTE_MS }
                EntryButton("自定义", Modifier.weight(1f)) { showDatePicker = true }
            }
        }
    }

    // 免费时长卡片
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("免费时长", freeReminderLeadText(cfg))
            ValueChips(
                presets = FREE_PRESETS,
                value = cfg.freeMinutes,
                format = { "$it 分钟" },
                onSelect = { v -> update { it.copy(freeMinutes = v) } },
                onCustomClick = {
                    update { c ->
                        c.copy(freeMinutes = if (c.freeMinutes in FREE_PRESETS) 20 else c.freeMinutes)
                    }
                },
            )
            if (cfg.freeMinutes !in FREE_PRESETS) {
                NumberField(
                    value = cfg.freeMinutes,
                    label = "自定义免费时长",
                    suffixText = "分钟",
                    range = 0..1440,
                    modifier = Modifier.fillMaxWidth(),
                ) { v -> update { it.copy(freeMinutes = v) } }
                Text(
                    "输入 0 表示没有免费时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("提前提醒", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { v ->
                    FilterChip(
                        selected = cfg.remindBeforeFreeMinutes == v,
                        onClick = { update { it.copy(remindBeforeFreeMinutes = v) } },
                        label = { Text("${v} 分钟", maxLines = 1, softWrap = false) },
                    )
                }
            }
        }
    }

    // 收费规则卡片
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("收费规则", "超出免费时长后如何计费")
            ValueChips(
                presets = UNIT_PRESETS,
                value = cfg.billingUnitMinutes,
                format = { if (it % 60 == 0) "每 ${it / 60} 小时" else "每 $it 分钟" },
                onSelect = { v -> update { it.copy(billingUnitMinutes = v) } },
                onCustomClick = {
                    update { c ->
                        c.copy(billingUnitMinutes = if (c.billingUnitMinutes in UNIT_PRESETS) 45 else c.billingUnitMinutes)
                    }
                },
            )
            if (cfg.billingUnitMinutes !in UNIT_PRESETS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        value = cfg.billingUnitMinutes,
                        label = "自定义时长",
                        suffixText = "分钟",
                        range = 1..720,
                        modifier = Modifier.weight(1f),
                    ) { v -> update { it.copy(billingUnitMinutes = v) } }
                    NumberField(
                        value = cfg.rateYuan,
                        label = "收费",
                        suffixText = "元",
                        range = 0..999,
                        modifier = Modifier.weight(1f),
                    ) { v -> update { it.copy(rateYuan = v) } }
                }
            } else {
                NumberField(
                    value = cfg.rateYuan,
                    label = "收费",
                    suffixText = "元",
                    range = 0..999,
                    modifier = Modifier.fillMaxWidth(),
                ) { v -> update { it.copy(rateYuan = v) } }
            }
        }
    }

    // 缴费与出场宽限卡片
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("缴费与出场宽限", "缴费后一般有 10~15 分钟出场时间")
            ValueChips(
                presets = GRACE_PRESETS,
                value = cfg.exitGraceMinutes,
                format = { "$it 分钟" },
                onSelect = { v -> update { it.copy(exitGraceMinutes = v) } },
                onCustomClick = {
                    update { c ->
                        c.copy(exitGraceMinutes = if (c.exitGraceMinutes in GRACE_PRESETS) 20 else c.exitGraceMinutes)
                    }
                },
            )
            if (cfg.exitGraceMinutes !in GRACE_PRESETS) {
                NumberField(
                    value = cfg.exitGraceMinutes,
                    label = "自定义宽限",
                    suffixText = "分钟",
                    range = 1..180,
                    modifier = Modifier.fillMaxWidth(),
                ) { v -> update { it.copy(exitGraceMinutes = v) } }
            }
            Row {
                Text(
                    "缴费缓冲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BUFFER_PRESETS.forEach { v ->
                    FilterChip(
                        selected = cfg.payBufferMinutes == v,
                        onClick = { update { it.copy(payBufferMinutes = v) } },
                        label = { Text("${v} 分钟", maxLines = 1, softWrap = false) },
                    )
                }
            }
            Text(
                "每个计费周期截止前 ${cfg.payBufferMinutes} 分钟提醒你缴费;缴费后 ${cfg.exitGraceMinutes} 分钟内驶出闸口即可,超时将从宽限结束重新计费。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 提醒方式卡片
    RemindModeSection(viewModel)

    // 替你算好了(表格)
    PreviewCard(entryMs, cfg)

    Button(
        onClick = { viewModel.startSession(entryMs, cfg) },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Text("开始停车提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }

    // 选择时间弹窗
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(entryMs)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateUtc = dpState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = dpState) }
    }
    if (showTimePicker) {
        val local = Instant.ofEpochMilli(entryMs).atZone(ZoneId.systemDefault())
        val tpState = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择入场时间") },
            text = {
                TimePicker(state = tpState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickedDateUtc?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    } ?: local.toLocalDate()
                    entryMs = LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, tpState.hour, tpState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    pickedDateUtc = null
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
    }
}

@Composable
private fun EntryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- 提醒方式

@Composable
private fun RemindModeSection(viewModel: ParkingViewModel) {
    val context = LocalContext.current
    val mode by viewModel.remindMode.collectAsState()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var fullScreenGranted by remember { mutableStateOf(fullScreenIntentGranted(context)) }

    // 从系统设置页返回时刷新授权状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                fullScreenGranted = fullScreenIntentGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("提醒方式", "到点时怎么提醒你,可随时更换")
            RemindModeRow(
                title = "通知",
                description = "普通系统通知,适合大多数情况",
                selected = mode == RemindMode.NOTIFICATION,
                granted = true,
                onGrant = null,
                onSelect = { viewModel.setRemindMode(RemindMode.NOTIFICATION) },
            )
            RemindModeRow(
                title = "闹钟和提醒",
                description = "锁屏时全屏亮起并响铃,像闹钟一样醒目",
                selected = mode == RemindMode.FULL_SCREEN,
                granted = fullScreenGranted,
                onGrant = { openFullScreenIntentSettings(context) },
                onSelect = { viewModel.setRemindMode(RemindMode.FULL_SCREEN) },
            )
            RemindModeRow(
                title = "弹出窗口提醒",
                description = "在其他应用上方弹出窗口,像来电一样",
                selected = mode == RemindMode.OVERLAY,
                granted = overlayGranted,
                onGrant = { openOverlaySettings(context) },
                onSelect = { viewModel.setRemindMode(RemindMode.OVERLAY) },
            )
            Text(
                "小米/华为等系统若弹窗未显示,请在系统设置的权限管理中允许本应用「锁屏显示」「后台弹出界面」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun RemindModeRow(
    title: String,
    description: String,
    selected: Boolean,
    granted: Boolean,
    onGrant: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted && onGrant != null) {
            TextButton(onClick = onGrant) { Text("去授权") }
        }
    }
}

private fun fullScreenIntentGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .canUseFullScreenIntent()
    } else {
        true
    }

private fun openOverlaySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
        )
    } catch (_: Exception) {
    }
}

private fun openFullScreenIntentSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
            )
        } else {
            openAppDetails(context)
        }
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        )
    } catch (_: Exception) {
    }
}

// ---------------------------------------------------------------- 替你算好了(表格)

@Composable
private fun PreviewCard(entryMs: Long, cfg: ParkingConfig) {
    val preview = ParkingSession(entryMs, cfg)
    val freeRemind = ParkingMath.freeRemindMs(preview)
    val freeEnd = ParkingMath.freeEndMs(preview)
    val firstSave = ParkingMath.kthSaveRemindMs(preview, 1)
    val secondSave = ParkingMath.kthSaveRemindMs(preview, 2)
    val twoHourCost = run {
        val paidMinutes = (120L - cfg.freeMinutes).coerceAtLeast(0L)
        ceil(paidMinutes.toDouble() / cfg.billingUnitMinutes).toInt() * cfg.rateYuan
    }

    val rows = buildList {
        add("入场时间" to Format.dateTime(entryMs))
        if (freeRemind != null) {
            add("首次提醒" to "${Format.clock(freeRemind)}(${freeReminderLeadText(cfg)})")
        }
        add("首次收费提醒" to "${Format.clock(firstSave)}(周期截止前 ${cfg.payBufferMinutes} 分钟)")
        add("下次收费提醒" to Format.clock(secondSave))
        add("若停满 2 小时" to "约付 ¥$twoHourCost")
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "替你算好了",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier.width(116.dp),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append("免费截止 ${Format.clock(freeEnd)}。")
                    append("每周期截止前 ${cfg.payBufferMinutes} 分钟提醒一次;缴费后另有 ${cfg.exitGraceMinutes} 分钟出场时间,")
                    append("宽限结束仍在场则从该时刻重新按周期计费并继续提醒。")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

// ---------------------------------------------------------------- 进行中

@Composable
private fun ActiveSection(viewModel: ParkingViewModel, session: ParkingSession) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    val status = ParkingMath.status(session, now)
    val cfg = session.config

    val heroTitle: String
    val countdownMs: Long
    val countdownCaption: String
    if (status.inFree) {
        heroTitle = "免费停车中"
        countdownMs = status.freeRemainMs
        countdownCaption = "${Format.clock(status.nextBoundaryMs)} 免费结束 · 之后 ¥${cfg.rateYuan}/${ParkingMath.unitText(cfg)}"
    } else if (status.inPaidWindow) {
        heroTitle = "已缴费 · 出场宽限中"
        countdownMs = status.paidWindowRemainMs
        countdownCaption = "请于 ${Format.clock(status.nextBoundaryMs)} 前驶出 · 超时将按 ¥${cfg.rateYuan}/${ParkingMath.unitText(cfg)} 继续计费"
    } else if (status.inSaveWindow) {
        heroTitle = "现在缴费,立省 ¥${cfg.rateYuan}"
        countdownMs = status.nextBoundaryMs - now
        countdownCaption = "请在 ${Format.clock(status.nextBoundaryMs)} 前缴费 · 缴费后另有 ${cfg.exitGraceMinutes} 分钟出场时间"
    } else {
        heroTitle = "计费中 · 第 ${status.paidUnits} 个周期"
        countdownMs = status.nextBoundaryMs - now
        countdownCaption = "当前约 ¥${status.currentCostYuan} · ${Format.clock(status.saveWindowStartMs)} 起会提醒你缴费省钱"
    }

    // 主卡: 渐变背景 + 进度环 + 倒计时
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(HeroGradient).padding(vertical = 28.dp)) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status.inSaveWindow) {
                    Surface(shape = RoundedCornerShape(999.dp), color = Gold) {
                        Text(
                            "💰 最佳缴费窗口",
                            color = Color(0xFF713F12),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    heroTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                val ringProgress = when {
                    status.inFree -> 1f - status.freeUsedFraction
                    status.inPaidWindow -> status.paidWindowFraction
                    else -> ((countdownMs.toFloat()) / (cfg.billingUnitMinutes * ParkingMath.MINUTE_MS)).coerceIn(0f, 1f)
                }
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(190.dp)) {
                        val stroke = 12.dp.toPx()
                        drawArc(
                            Color.White.copy(alpha = 0.25f), 0f, 360f, false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            Color.White, -90f, 360f * ringProgress, false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            Format.countdown(countdownMs.coerceAtLeast(0)),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                status.inSaveWindow -> "距计费周期截止"
                                status.inPaidWindow -> "距宽限结束"
                                else -> "倒计时"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    countdownCaption,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    // 概要
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("入场时间", Format.dateTime(session.entryEpochMs))
            if (cfg.freeMinutes > 0) SummaryRow("免费时长", "${cfg.freeMinutes} 分钟(${freeReminderLeadText(cfg)})")
            SummaryRow("计费规则", "¥${cfg.rateYuan} / ${ParkingMath.unitText(cfg)}")
            SummaryRow("出场宽限", "${cfg.exitGraceMinutes} 分钟")
        }
    }

    // 提醒时间轴
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("提醒计划", style = MaterialTheme.typography.titleMedium)
            val items = ParkingMath.reminderTimeline(session, now, maxItems = 4)
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                if (item.passed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            )
                    )
                    Text(
                        Format.clock(item.timeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (item.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Text(
                "提醒由系统精确闹钟触发,无需保持应用后台运行,重启手机后也会自动恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 操作按钮
    if (!status.inFree) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.markPaid() },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(if (status.inPaidWindow) "再次缴费" else "我已缴费,稍后驶出", textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = { viewModel.stopSession() },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("结束本次停车", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
            }
        }
    } else {
        OutlinedButton(
            onClick = { viewModel.stopSession() },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("结束本次停车", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
