package com.fan.moneytoolbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后恢复进行中会话的提醒 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleNext(context)
        }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            RenewalScheduler.restoreAll(context)
        }
    }
}
