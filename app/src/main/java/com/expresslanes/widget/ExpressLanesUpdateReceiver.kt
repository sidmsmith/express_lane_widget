package com.expresslanes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpressLanesUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UPDATE) return

        val pendingResult = goAsync()
        scope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
                val apiKey = prefs.getString(WidgetPrefs.KEY_API_KEY, WidgetPrefs.DEFAULT_API_KEY)
                    ?: WidgetPrefs.DEFAULT_API_KEY

                val result = ExpressLanesRepository.fetchNorthwestCorridor(apiKey)
                ExpressLanesWidgetProvider.updateAllWidgets(context, result.status)

                val lastStatus = prefs.getString(WidgetPrefs.KEY_LAST_STATUS, null)
                prefs.edit().putString(WidgetPrefs.KEY_LAST_STATUS, result.status.name).apply()
                WidgetPrefs.appendApiResponseHistory(context, result.rawJson)

                val statusChanged = lastStatus != null && lastStatus != result.status.name
                val notifyOnChange = prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_CHANGE, WidgetPrefs.DEFAULT_NOTIFY_ON_CHANGE)
                val notifyWhenStale = prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_WHEN_STALE, WidgetPrefs.DEFAULT_NOTIFY_WHEN_STALE)
                val notifyOnOdd = prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_ODD, WidgetPrefs.DEFAULT_NOTIFY_ON_ODD)
                val suppressRepeat = prefs.getBoolean(WidgetPrefs.KEY_SUPPRESS_REPEAT, WidgetPrefs.DEFAULT_SUPPRESS_REPEAT)
                val lastWasStale = prefs.getBoolean(WidgetPrefs.KEY_LAST_WAS_STALE, false)
                val lastWasOdd = prefs.getBoolean(WidgetPrefs.KEY_LAST_WAS_ODD, false)

                val isStale = result.lastUpdatedSeconds?.let { lastUp ->
                    val nowSec = System.currentTimeMillis() / 1000
                    nowSec - lastUp > WidgetPrefs.STALE_THRESHOLD_SECONDS
                } ?: false

                val editor = prefs.edit()
                    .putBoolean(WidgetPrefs.KEY_LAST_WAS_STALE, isStale)
                    .putBoolean(WidgetPrefs.KEY_LAST_WAS_ODD, result.isOddResponse)

                if (statusChanged && notifyOnChange) {
                    val msg = "NW Corridor: ${result.status.name}"
                    NotificationHelper.show(context, "Express Lane Status Changed", msg, NOTIFY_CHANGE)
                }

                if (isStale && notifyWhenStale) {
                    val shouldNotify = !suppressRepeat || !lastWasStale
                    if (shouldNotify) {
                        val hours = result.lastUpdatedSeconds?.let { lastUp ->
                            (System.currentTimeMillis() / 1000 - lastUp) / 3600
                        } ?: 0
                        NotificationHelper.show(
                            context,
                            "Express Lane Data Stale",
                            "Last API update was ${hours}h ago. Widget may show outdated info.",
                            NOTIFY_STALE
                        )
                    }
                }

                if (result.isOddResponse && notifyOnOdd) {
                    val shouldNotify = !suppressRepeat || !lastWasOdd
                    if (shouldNotify) {
                        val snippet = result.rawJson.take(500) + if (result.rawJson.length > 500) "…" else ""
                        NotificationHelper.show(
                            context,
                            "Odd API Response (NW Corridor)",
                            "Unexpected response. Raw data:\n$snippet",
                            NOTIFY_ODD
                        )
                    }
                }

                editor.apply()
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
                ExpressLanesWidgetProvider.updateAllWidgets(context, ExpressLaneStatus.CLOSED)
                WidgetPrefs.appendApiResponseHistory(context, "Error: ${e.message}")
            } finally {
                scheduleNext(context)
                pendingResult.finish()
            }
        }
    }

    private fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val intervalMin = prefs.getInt(WidgetPrefs.KEY_UPDATE_INTERVAL, WidgetPrefs.DEFAULT_UPDATE_INTERVAL)
            .coerceIn(1, 60)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExpressLanesUpdateReceiver::class.java).apply {
            action = ACTION_UPDATE
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMs = intervalMin * 60 * 1000L
        val triggerTime = System.currentTimeMillis() + intervalMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pending
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pending
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pending
            )
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pending)
        }
    }

    companion object {
        private const val TAG = "ExpressLanesUpdate"
        private const val NOTIFY_CHANGE = 1
        private const val NOTIFY_STALE = 2
        private const val NOTIFY_ODD = 3
        const val ACTION_UPDATE = "com.expresslanes.widget.UPDATE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun schedule(context: Context) {
            cancel(context)
            val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            val intervalMin = prefs.getInt(WidgetPrefs.KEY_UPDATE_INTERVAL, WidgetPrefs.DEFAULT_UPDATE_INTERVAL)
                .coerceIn(1, 60)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExpressLanesUpdateReceiver::class.java).apply {
                action = ACTION_UPDATE
            }
            val pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val intervalMs = intervalMin * 60 * 1000L
            val triggerTime = System.currentTimeMillis() + intervalMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pending
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pending
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pending
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pending)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExpressLanesUpdateReceiver::class.java).apply {
                action = ACTION_UPDATE
            }
            val pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
        }
    }
}
