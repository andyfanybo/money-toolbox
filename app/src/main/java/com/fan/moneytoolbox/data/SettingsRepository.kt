package com.fan.moneytoolbox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moneybox_settings")

/**
 * 本地持久化: 默认配置 + 进行中的停车会话。
 * 会话写入磁盘后,即使进程被杀、手机重启,提醒依然会由 AlarmManager 触发。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val FREE = intPreferencesKey("cfg_free_minutes")
        val RATE = intPreferencesKey("cfg_rate_yuan")
        val UNIT = intPreferencesKey("cfg_unit_minutes")
        val GRACE = intPreferencesKey("cfg_grace_minutes")
        val REMIND_BEFORE = intPreferencesKey("cfg_remind_before_free")
        val SESSION_ENTRY = longPreferencesKey("session_entry_ms")
        // 会话自带的配置快照
        val S_FREE = intPreferencesKey("s_free")
        val S_RATE = intPreferencesKey("s_rate")
        val S_UNIT = intPreferencesKey("s_unit")
        val S_GRACE = intPreferencesKey("s_grace")
        val S_REMIND_BEFORE = intPreferencesKey("s_remind_before")
        val REMIND_MODE = stringPreferencesKey("remind_mode")
    }

    /** 默认配置(上次使用的值) */
    val configFlow: Flow<ParkingConfig> = context.dataStore.data.map { p ->
        ParkingConfig(
            freeMinutes = p[Keys.FREE] ?: 30,
            rateYuan = p[Keys.RATE] ?: 6,
            billingUnitMinutes = p[Keys.UNIT] ?: 60,
            exitGraceMinutes = p[Keys.GRACE] ?: 15,
            remindBeforeFreeMinutes = p[Keys.REMIND_BEFORE] ?: 10,
        )
    }

    /** 进行中的停车会话,null 表示没有 */
    val sessionFlow: Flow<ParkingSession?> = context.dataStore.data.map { p ->
        val entry = p[Keys.SESSION_ENTRY] ?: -1L
        if (entry <= 0L) return@map null
        ParkingSession(
            entryEpochMs = entry,
            config = ParkingConfig(
                freeMinutes = p[Keys.S_FREE] ?: 30,
                rateYuan = p[Keys.S_RATE] ?: 6,
                billingUnitMinutes = p[Keys.S_UNIT] ?: 60,
                exitGraceMinutes = p[Keys.S_GRACE] ?: 15,
                remindBeforeFreeMinutes = p[Keys.S_REMIND_BEFORE] ?: 10,
            ),
        )
    }

    /** 提醒方式 */
    val remindModeFlow: Flow<RemindMode> = context.dataStore.data.map { p ->
        when (p[Keys.REMIND_MODE]) {
            "full_screen" -> RemindMode.FULL_SCREEN
            "overlay" -> RemindMode.OVERLAY
            else -> RemindMode.NOTIFICATION
        }
    }

    suspend fun saveRemindMode(mode: RemindMode) {
        context.dataStore.edit { p ->
            p[Keys.REMIND_MODE] = when (mode) {
                RemindMode.FULL_SCREEN -> "full_screen"
                RemindMode.OVERLAY -> "overlay"
                RemindMode.NOTIFICATION -> "notification"
            }
        }
    }

    suspend fun saveConfig(cfg: ParkingConfig) {
        context.dataStore.edit { p ->
            p[Keys.FREE] = cfg.freeMinutes
            p[Keys.RATE] = cfg.rateYuan
            p[Keys.UNIT] = cfg.billingUnitMinutes
            p[Keys.GRACE] = cfg.exitGraceMinutes
            p[Keys.REMIND_BEFORE] = cfg.remindBeforeFreeMinutes
        }
    }

    suspend fun saveSession(session: ParkingSession?) {
        context.dataStore.edit { p ->
            if (session == null) {
                p.remove(Keys.SESSION_ENTRY)
                p.remove(Keys.S_FREE)
                p.remove(Keys.S_RATE)
                p.remove(Keys.S_UNIT)
                p.remove(Keys.S_GRACE)
                p.remove(Keys.S_REMIND_BEFORE)
            } else {
                p[Keys.SESSION_ENTRY] = session.entryEpochMs
                p[Keys.S_FREE] = session.config.freeMinutes
                p[Keys.S_RATE] = session.config.rateYuan
                p[Keys.S_UNIT] = session.config.billingUnitMinutes
                p[Keys.S_GRACE] = session.config.exitGraceMinutes
                p[Keys.S_REMIND_BEFORE] = session.config.remindBeforeFreeMinutes
            }
        }
    }

    /** 供 BroadcastReceiver 使用的同步读取(数据量极小,毫秒级) */
    fun sessionBlocking(): ParkingSession? = kotlinx.coroutines.runBlocking { sessionFlow.first() }

    fun remindModeBlocking(): RemindMode = kotlinx.coroutines.runBlocking { remindModeFlow.first() }
}
