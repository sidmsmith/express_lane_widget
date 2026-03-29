package com.expresslanes.widget

import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpressLanesConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onResume() {
        super.onResume()
        ExpressLanesUpdateReceiver.scheduleIfWidgetsExist(this)
        registerResponseHistoryListener()
    }

    override fun onPause() {
        super.onPause()
        unregisterResponseHistoryListener()
    }

    private fun registerResponseHistoryListener() {
        if (prefsListener != null) return
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WidgetPrefs.KEY_API_RESPONSE_HISTORY) {
                runOnUiThread { updateResponseDisplay() }
            }
        }
        getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun unregisterResponseHistoryListener() {
        prefsListener?.let { listener ->
            getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener)
            prefsListener = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        setContentView(R.layout.activity_config)
        supportActionBar?.setTitle(R.string.config_title)

        val prefs = getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)

        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val intervals = arrayOf(1, 3, 5, 15, 30, 60)
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

        findViewById<EditText>(R.id.edit_camera_url).setText(
            prefs.getString(WidgetPrefs.KEY_CAMERA_URL, WidgetPrefs.DEFAULT_CAMERA_URL)
        )

        findViewById<Switch>(R.id.switch_notify_change).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_CHANGE, WidgetPrefs.DEFAULT_NOTIFY_ON_CHANGE)
        findViewById<Switch>(R.id.switch_notify_stale).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_WHEN_STALE, WidgetPrefs.DEFAULT_NOTIFY_WHEN_STALE)
        findViewById<Switch>(R.id.switch_notify_odd).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_ON_ODD, WidgetPrefs.DEFAULT_NOTIFY_ON_ODD)
        findViewById<Switch>(R.id.switch_notify_fallback_unexpected).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_NOTIFY_FALLBACK_UNEXPECTED, WidgetPrefs.DEFAULT_NOTIFY_FALLBACK_UNEXPECTED)
        findViewById<Switch>(R.id.switch_suppress_repeat).isChecked =
            prefs.getBoolean(WidgetPrefs.KEY_SUPPRESS_REPEAT, WidgetPrefs.DEFAULT_SUPPRESS_REPEAT)

        val history = WidgetPrefs.getApiResponseHistory(this)
        val responseView = findViewById<TextView>(R.id.text_last_response)
        if (history.isEmpty()) {
            responseView.text = getString(R.string.config_no_data)
        } else {
            val spannable = SpannableStringBuilder()
            for ((index, entry) in history.withIndex()) {
                val processed = addLastUpdatedSuffix(entry)
                val start = spannable.length
                spannable.append(processed)
                val isError = entry.contains("Error:", ignoreCase = true) ||
                    entry.startsWith("HTTP ") ||
                    entry.contains("Parse error", ignoreCase = true)
                val color = if (isError) Color.RED else Color.BLACK
                spannable.setSpan(ForegroundColorSpan(color), start, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (index < history.size - 1) spannable.append("\n\n---\n\n")
            }
            responseView.text = spannable
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener { saveAndFinish() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_config, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh) {
            refreshNow()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshNow() {
        lifecycleScope.launch {
            val apiKey = getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)
                .getString(WidgetPrefs.KEY_API_KEY, WidgetPrefs.DEFAULT_API_KEY) ?: WidgetPrefs.DEFAULT_API_KEY
            val result = withContext(Dispatchers.IO) {
                ExpressLanesRepository.fetchNorthwestCorridor(apiKey)
            }
            ExpressLanesWidgetProvider.updateAllWidgets(this@ExpressLanesConfigActivity, result)
            WidgetPrefs.appendApiResponseHistory(this@ExpressLanesConfigActivity, result.rawJson)
            ExpressLanesUpdateReceiver.scheduleIfWidgetsExist(this@ExpressLanesConfigActivity)
            updateResponseDisplay()
            Toast.makeText(this@ExpressLanesConfigActivity, "Refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateResponseDisplay() {
        val history = WidgetPrefs.getApiResponseHistory(this)
        val responseView = findViewById<TextView>(R.id.text_last_response)
        if (history.isEmpty()) {
            responseView.text = getString(R.string.config_no_data)
        } else {
            val spannable = SpannableStringBuilder()
            for ((index, entry) in history.withIndex()) {
                val processed = addLastUpdatedSuffix(entry)
                val start = spannable.length
                spannable.append(processed)
                val isError = entry.contains("Error:", ignoreCase = true) ||
                    entry.startsWith("HTTP ") ||
                    entry.contains("Parse error", ignoreCase = true)
                val color = if (isError) Color.RED else Color.BLACK
                spannable.setSpan(ForegroundColorSpan(color), start, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (index < history.size - 1) spannable.append("\n\n---\n\n")
            }
            responseView.text = spannable
        }
    }

    private fun saveAndFinish() {
        val prefs = getSharedPreferences(WidgetPrefs.PREFS_NAME, MODE_PRIVATE)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val intervals = arrayOf(1, 3, 5, 15, 30, 60)
        val interval = intervals[spinner.selectedItemPosition]
        val editApiKey = findViewById<EditText>(R.id.edit_api_key)
        val editUrl = findViewById<EditText>(R.id.edit_url)
        val editCameraUrl = findViewById<EditText>(R.id.edit_camera_url)
        val apiKey = editApiKey.text.toString().ifBlank { WidgetPrefs.DEFAULT_API_KEY }
        val url = editUrl.text.toString().ifBlank { WidgetPrefs.DEFAULT_CLICK_URL }
        val cameraUrl = editCameraUrl.text.toString().ifBlank { WidgetPrefs.DEFAULT_CAMERA_URL }

        prefs.edit()
            .putInt(WidgetPrefs.KEY_UPDATE_INTERVAL, interval)
            .putString(WidgetPrefs.KEY_API_KEY, apiKey)
            .putString(WidgetPrefs.KEY_CLICK_URL, url)
            .putString(WidgetPrefs.KEY_CAMERA_URL, cameraUrl)
            .putBoolean(WidgetPrefs.KEY_NOTIFY_ON_CHANGE, findViewById<Switch>(R.id.switch_notify_change).isChecked)
            .putBoolean(WidgetPrefs.KEY_NOTIFY_WHEN_STALE, findViewById<Switch>(R.id.switch_notify_stale).isChecked)
            .putBoolean(WidgetPrefs.KEY_NOTIFY_ON_ODD, findViewById<Switch>(R.id.switch_notify_odd).isChecked)
            .putBoolean(
                WidgetPrefs.KEY_NOTIFY_FALLBACK_UNEXPECTED,
                findViewById<Switch>(R.id.switch_notify_fallback_unexpected).isChecked
            )
            .putBoolean(WidgetPrefs.KEY_SUPPRESS_REPEAT, findViewById<Switch>(R.id.switch_suppress_repeat).isChecked)
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

    /**
     * Finds "LastUpdated":{unixSeconds} in the response and appends (Mon 3:45 PM ET) for readability.
     */
    private fun addLastUpdatedSuffix(entry: String): String {
        val regex = """"LastUpdated"\s*:\s*(\d+)""".toRegex()
        return regex.replace(entry) { match ->
            val timestamp = match.groupValues[1].toLongOrNull() ?: return@replace match.value
            val millis = if (timestamp > 1e12) timestamp else timestamp * 1000
            val sdf = SimpleDateFormat("EEE h:mm a", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("America/New_York")
            }
            val formatted = sdf.format(Date(millis))
            "\"LastUpdated\": $timestamp ($formatted ET)"
        }
    }
}
