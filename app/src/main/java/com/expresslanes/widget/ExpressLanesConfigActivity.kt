package com.expresslanes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ExpressLanesConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        setContentView(R.layout.activity_config)

        val prefs = getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)

        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val intervals = arrayOf(5, 15, 30, 60)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals.map { "$it min" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val currentInterval = prefs.getInt(WidgetPrefs.KEY_UPDATE_INTERVAL, WidgetPrefs.DEFAULT_UPDATE_INTERVAL)
        val selectedIndex = intervals.indexOf(currentInterval).coerceAtLeast(0)
        spinner.setSelection(selectedIndex)

        val editApiKey = findViewById<EditText>(R.id.edit_api_key)
        editApiKey.setText(prefs.getString(WidgetPrefs.KEY_API_KEY, WidgetPrefs.DEFAULT_API_KEY))

        val editUrl = findViewById<EditText>(R.id.edit_url)
        editUrl.setText(prefs.getString(WidgetPrefs.KEY_CLICK_URL, WidgetPrefs.DEFAULT_CLICK_URL))

        findViewById<Switch>(R.id.switch_notify_change).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_CHANGE, WidgetPrefs.DEFAULT_NOTIFY_ON_CHANGE)
        findViewById<Switch>(R.id.switch_notify_stale).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_WHEN_STALE, WidgetPrefs.DEFAULT_NOTIFY_WHEN_STALE)
        findViewById<Switch>(R.id.switch_notify_odd).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_ODD, WidgetPrefs.DEFAULT_NOTIFY_ON_ODD)

        val lastResponse = prefs.getString(WidgetPrefs.KEY_LAST_API_RESPONSE, null)
        findViewById<TextView>(R.id.text_last_response).text =
            lastResponse?.take(2000) ?: getString(R.string.config_no_data)

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val interval = intervals[spinner.selectedItemPosition]
            val apiKey = editApiKey.text.toString().ifBlank { WidgetPrefs.DEFAULT_API_KEY }
            val url = editUrl.text.toString().ifBlank { WidgetPrefs.DEFAULT_CLICK_URL }

            prefs.edit()
                .putInt(WidgetPrefs.KEY_UPDATE_INTERVAL, interval)
                .putString(WidgetPrefs.KEY_API_KEY, apiKey)
                .putString(WidgetPrefs.KEY_CLICK_URL, url)
                .putBoolean(WidgetPrefs.KEY_NOTIFY_ON_CHANGE, findViewById<Switch>(R.id.switch_notify_change).isChecked)
                .putBoolean(WidgetPrefs.KEY_NOTIFY_WHEN_STALE, findViewById<Switch>(R.id.switch_notify_stale).isChecked)
                .putBoolean(WidgetPrefs.KEY_NOTIFY_ON_ODD, findViewById<Switch>(R.id.switch_notify_odd).isChecked)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(this)
            val ids = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                intArrayOf(appWidgetId)
            } else {
                appWidgetManager.getAppWidgetIds(ComponentName(this, ExpressLanesWidgetProvider::class.java))
            }
            if (ids.isNotEmpty()) {
                val updateIntent = Intent(this, ExpressLanesWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                sendBroadcast(updateIntent)
            }

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultIntent)
            }
            finish()
        }
    }
}
