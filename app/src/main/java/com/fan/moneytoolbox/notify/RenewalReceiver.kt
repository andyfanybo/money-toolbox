package com.fan.moneytoolbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fan.moneytoolbox.data.RenewalRepository

class RenewalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val period = intent.getIntExtra(EXTRA_PERIOD_INDEX, -1)
        val phase = intent.getStringExtra(EXTRA_PHASE) ?: return
        val item = RenewalRepository(context).itemsBlocking()
            .firstOrNull { it.id == id && it.periodIndex == period && !it.completed } ?: return
        if (phase != "early" && phase != "due") return
        RenewalNotifier.notify(context, item, phase == "due")
    }

    companion object {
        const val ACTION_FIRE = "com.fan.moneytoolbox.ACTION_RENEWAL_REMINDER"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_PERIOD_INDEX = "period_index"
        const val EXTRA_PHASE = "phase"
    }
}
