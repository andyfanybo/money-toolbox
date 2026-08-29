package com.fan.moneytoolbox

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fan.moneytoolbox.data.ParkingConfig
import com.fan.moneytoolbox.data.ParkingSession
import com.fan.moneytoolbox.data.RemindMode
import com.fan.moneytoolbox.data.SettingsRepository
import com.fan.moneytoolbox.notify.Notifier
import com.fan.moneytoolbox.notify.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ParkingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    /** 默认配置(上次使用的值) */
    val config: StateFlow<ParkingConfig> = repo.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ParkingConfig())

    /** 进行中的停车会话 */
    val session: StateFlow<ParkingSession?> = repo.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 提醒方式 */
    val remindMode: StateFlow<RemindMode> = repo.remindModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemindMode.NOTIFICATION)

    fun setRemindMode(mode: RemindMode) {
        viewModelScope.launch { repo.saveRemindMode(mode) }
    }

    fun startSession(entryEpochMs: Long, cfg: ParkingConfig) {
        viewModelScope.launch {
            repo.saveConfig(cfg)
            repo.saveSession(ParkingSession(entryEpochMs, cfg))
            ReminderScheduler.scheduleNext(getApplication())
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication())
            repo.saveSession(null)
            NotificationManagerCompat.from(getApplication()).cancel(Notifier.REMIND_NOTIF_ID)
        }
    }
}
