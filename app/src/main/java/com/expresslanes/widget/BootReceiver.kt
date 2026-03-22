package com.expresslanes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules the update alarm when the device boots. AlarmManager alarms do not survive reboots.
 * Only schedules if at least one widget instance exists.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ExpressLanesUpdateReceiver.scheduleIfWidgetsExist(context)
    }
}
