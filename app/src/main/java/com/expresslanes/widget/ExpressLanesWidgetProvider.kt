package com.expresslanes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpressLanesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        schedulePeriodicWork(context)
        for (appWidgetId in appWidgetIds) {
            fetchAndUpdate(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_OPEN_URL) {
            val url = getClickUrl(context)
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL", e)
            }
        }
    }

    private fun fetchAndUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        scope.launch {
            val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            val apiKey = prefs.getString(WidgetPrefs.KEY_API_KEY, WidgetPrefs.DEFAULT_API_KEY) ?: WidgetPrefs.DEFAULT_API_KEY
            val result = ExpressLanesRepository.fetchNorthwestCorridor(apiKey)
            updateWidget(context, appWidgetManager, appWidgetId, result.status)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        status: ExpressLaneStatus
    ) {
        ExpressLanesWidgetProvider.updateWidgetViews(context, appWidgetManager, appWidgetId, status)
    }

    private fun schedulePeriodicWork(context: Context) {
        ExpressLanesUpdateReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ExpressLanesUpdateReceiver.cancel(context)
    }

    private fun getClickUrl(context: Context): String {
        val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(WidgetPrefs.KEY_CLICK_URL, WidgetPrefs.DEFAULT_CLICK_URL) ?: WidgetPrefs.DEFAULT_CLICK_URL
    }

    companion object {
        private const val TAG = "ExpressLanesWidget"
        const val ACTION_OPEN_URL = "com.expresslanes.widget.OPEN_URL"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun updateAllWidgets(context: Context, status: ExpressLaneStatus) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ExpressLanesWidgetProvider::class.java))
            for (id in ids) {
                updateWidgetViews(context, appWidgetManager, id, status)
            }
        }

        internal fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            status: ExpressLaneStatus
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val iconRes = when (status) {
                ExpressLaneStatus.NORTHBOUND -> R.drawable.ic_arrow_up_green
                ExpressLaneStatus.SOUTHBOUND -> R.drawable.ic_arrow_down_yellow
                ExpressLaneStatus.CLOSED -> R.drawable.ic_close_red
                ExpressLaneStatus.UNKNOWN -> R.drawable.ic_unknown
            }
            views.setImageViewResource(R.id.status_icon, iconRes)

            val openUrlIntent = Intent(context, ExpressLanesWidgetProvider::class.java).apply {
                action = ACTION_OPEN_URL
            }
            views.setOnClickPendingIntent(
                R.id.status_icon,
                PendingIntent.getBroadcast(context, 0, openUrlIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
