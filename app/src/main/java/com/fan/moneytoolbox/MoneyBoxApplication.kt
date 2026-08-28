package com.fan.moneytoolbox

import android.app.Application
import com.fan.moneytoolbox.notify.Notifier

class MoneyBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }
}
