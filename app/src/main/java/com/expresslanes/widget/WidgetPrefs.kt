package com.expresslanes.widget

object WidgetPrefs {
    const val PREFS_NAME = "express_lanes_widget"
    const val KEY_API_KEY = "api_key"
    const val KEY_UPDATE_INTERVAL = "update_interval_minutes"
    const val KEY_CLICK_URL = "click_url"

    const val KEY_NOTIFY_ON_CHANGE = "notify_on_status_change"
    const val KEY_NOTIFY_WHEN_STALE = "notify_when_stale"
    const val KEY_NOTIFY_ON_ODD = "notify_on_odd_response"
    const val KEY_LAST_STATUS = "last_status"
    const val KEY_LAST_API_RESPONSE = "last_api_response"

    const val DEFAULT_API_KEY = "c5d9a59a326b4a499e27ee4723705798"
    const val DEFAULT_UPDATE_INTERVAL = 5
    const val DEFAULT_CLICK_URL = "https://511ga.org"

    const val DEFAULT_NOTIFY_ON_CHANGE = true
    const val DEFAULT_NOTIFY_WHEN_STALE = true
    const val DEFAULT_NOTIFY_ON_ODD = true

    const val STALE_THRESHOLD_SECONDS = 24L * 60 * 60  // 24 hours
}
