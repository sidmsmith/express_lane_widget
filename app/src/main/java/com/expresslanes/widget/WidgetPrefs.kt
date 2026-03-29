package com.expresslanes.widget

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetPrefs {
    const val PREFS_NAME = "express_lanes_widget"
    const val KEY_API_KEY = "api_key"
    const val KEY_UPDATE_INTERVAL = "update_interval_minutes"
    const val KEY_CLICK_URL = "click_url"
    const val KEY_CAMERA_URL = "camera_url"

    const val KEY_NOTIFY_ON_CHANGE = "notify_on_status_change"
    const val KEY_NOTIFY_WHEN_STALE = "notify_when_stale"
    const val KEY_NOTIFY_ON_ODD = "notify_on_odd_response"
    const val KEY_NOTIFY_FALLBACK_UNEXPECTED = "notify_fallback_unexpected"
    const val KEY_SUPPRESS_REPEAT = "suppress_repeat_notifications"
    const val KEY_LAST_STATUS = "last_status"
    const val KEY_LAST_WAS_STALE = "last_was_stale"
    const val KEY_LAST_WAS_ODD = "last_was_odd"
    const val KEY_LAST_WAS_FALLBACK_UNEXPECTED = "last_was_fallback_unexpected"
    const val KEY_LAST_API_RESPONSE = "last_api_response"
    const val KEY_API_RESPONSE_HISTORY = "api_response_history"

    const val API_RESPONSE_HISTORY_MAX = 3
    private val TIMESTAMP_FORMAT = SimpleDateFormat("EEEE M/d h:mm a", Locale.US)

    const val DEFAULT_API_KEY = "c5d9a59a326b4a499e27ee4723705798"
    const val DEFAULT_UPDATE_INTERVAL = 5
    const val DEFAULT_CLICK_URL = "https://511ga.org"
    const val DEFAULT_CAMERA_URL =
        "https://srtaivedds.com/Images/Cameras/75B-ROS31-CMS-CAM31-00001.jpg"

    const val DEFAULT_NOTIFY_ON_CHANGE = true
    const val DEFAULT_NOTIFY_WHEN_STALE = true
    const val DEFAULT_NOTIFY_ON_ODD = true
    const val DEFAULT_NOTIFY_FALLBACK_UNEXPECTED = true
    const val DEFAULT_SUPPRESS_REPEAT = true

    const val STALE_THRESHOLD_SECONDS = 24L * 60 * 60  // 24 hours

    /**
     * Appends a timestamped API response to history (max 3). Format: "Sunday 3/22 12:57 AM: {response}"
     */
    fun appendApiResponseHistory(context: Context, rawResponse: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = TIMESTAMP_FORMAT.format(Date())
        val entry = "$timestamp: $rawResponse"

        val history = try {
            JSONArray(prefs.getString(KEY_API_RESPONSE_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val list = mutableListOf<String>()
        list.add(entry)
        for (i in 0 until history.length().coerceAtMost(API_RESPONSE_HISTORY_MAX - 1)) {
            list.add(history.getString(i))
        }
        val newArr = JSONArray(list.take(API_RESPONSE_HISTORY_MAX))
        prefs.edit().putString(KEY_API_RESPONSE_HISTORY, newArr.toString()).apply()
    }

    /**
     * Returns the last 3 API responses (newest first) for display.
     */
    fun getApiResponseHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            val arr = JSONArray(prefs.getString(KEY_API_RESPONSE_HISTORY, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
