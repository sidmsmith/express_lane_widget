package com.expresslanes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import java.util.concurrent.ConcurrentHashMap
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
        if (intent.action == ACTION_WIDGET_TAP) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                handleWidgetTap(context.applicationContext, id)
            }
        }
    }

    private fun fetchAndUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        scope.launch {
            val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            val apiKey = prefs.getString(WidgetPrefs.KEY_API_KEY, WidgetPrefs.DEFAULT_API_KEY) ?: WidgetPrefs.DEFAULT_API_KEY
            val result = ExpressLanesRepository.fetchNorthwestCorridor(apiKey)
            WidgetPrefs.appendApiResponseHistory(context, result.rawJson)
            updateWidget(context, appWidgetManager, appWidgetId, result)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        result: FetchResult
    ) {
        ExpressLanesWidgetProvider.updateWidgetViews(context, appWidgetManager, appWidgetId, result)
    }

    private fun schedulePeriodicWork(context: Context) {
        ExpressLanesUpdateReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ExpressLanesUpdateReceiver.cancel(context)
    }

    companion object {
        private const val TAG = "ExpressLanesWidget"
        const val ACTION_WIDGET_TAP = "com.expresslanes.widget.WIDGET_TAP"
        private const val DOUBLE_TAP_TIMEOUT_MS = 350L

        private val tapHandler = Handler(Looper.getMainLooper())
        private val pendingSingleTapByWidget = ConcurrentHashMap<Int, Runnable>()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private fun handleWidgetTap(context: Context, appWidgetId: Int) {
            val pending = pendingSingleTapByWidget.remove(appWidgetId)
            if (pending != null) {
                tapHandler.removeCallbacks(pending)
                openUrl(context, readCameraUrl(context))
                return
            }
            val run = Runnable {
                pendingSingleTapByWidget.remove(appWidgetId)
                openUrl(context, readClickUrl(context))
            }
            pendingSingleTapByWidget[appWidgetId] = run
            tapHandler.postDelayed(run, DOUBLE_TAP_TIMEOUT_MS)
        }

        private fun readClickUrl(context: Context): String {
            val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(WidgetPrefs.KEY_CLICK_URL, WidgetPrefs.DEFAULT_CLICK_URL) ?: WidgetPrefs.DEFAULT_CLICK_URL
        }

        private fun readCameraUrl(context: Context): String {
            val prefs = context.getSharedPreferences(WidgetPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(WidgetPrefs.KEY_CAMERA_URL, WidgetPrefs.DEFAULT_CAMERA_URL) ?: WidgetPrefs.DEFAULT_CAMERA_URL
        }

        private fun openUrl(context: Context, url: String) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL: $url", e)
            }
        }

        fun updateAllWidgets(context: Context, result: FetchResult) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ExpressLanesWidgetProvider::class.java))
            for (id in ids) {
                updateWidgetViews(context, appWidgetManager, id, result)
            }
        }

        internal fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            result: FetchResult
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val iconRes = iconFor(result.status, result.fromPeachPassFallback)
            views.setImageViewResource(R.id.status_icon, iconRes)

            val tapIntent = Intent(context, ExpressLanesWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TAP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setOnClickPendingIntent(
                R.id.status_icon,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Green arrows when 511 GA is fresh; yellow arrows when Peach Pass fallback is used. */
        internal fun iconFor(status: ExpressLaneStatus, fromPeachPassFallback: Boolean): Int {
            return when (status) {
                ExpressLaneStatus.NORTHBOUND ->
                    if (fromPeachPassFallback) R.drawable.ic_arrow_up_yellow else R.drawable.ic_arrow_up_green
                ExpressLaneStatus.SOUTHBOUND ->
                    if (fromPeachPassFallback) R.drawable.ic_arrow_down_yellow else R.drawable.ic_arrow_down_green
                ExpressLaneStatus.CLOSED -> R.drawable.ic_close_red
                ExpressLaneStatus.UNKNOWN -> R.drawable.ic_unknown
            }
        }
    }
}
