package com.expresslanes.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ExpressLanesRepository {

    private const val TARGET_LANE_ID = "75B"  // Northwest Corridor

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchNorthwestCorridor(apiKey: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://511ga.org/api/v2/get/expresslanes?key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext FetchResult(
                    ExpressLaneStatus.CLOSED,
                    isOddResponse = true,
                    rawJson = "HTTP ${response.code}",
                    lastUpdatedSeconds = null
                )
            }
            val body = response.body?.string() ?: ""
            parseResponse(body)
        } catch (e: Exception) {
            FetchResult(
                ExpressLaneStatus.CLOSED,
                isOddResponse = true,
                rawJson = "Error: ${e.message}",
                lastUpdatedSeconds = null
            )
        }
    }

    private fun parseResponse(json: String): FetchResult {
        return try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("Id") == TARGET_LANE_ID) {
                    val (status, isOdd) = getIconFor75B(obj)
                    val lastUpdated = obj.optLong("LastUpdated", 0L).takeIf { it > 0 }
                    return FetchResult(status, isOdd, json, lastUpdated)
                }
            }
            FetchResult(
                ExpressLaneStatus.CLOSED,
                isOddResponse = true,
                rawJson = json,
                lastUpdatedSeconds = null
            )
        } catch (e: Exception) {
            FetchResult(
                ExpressLaneStatus.CLOSED,
                isOddResponse = true,
                rawJson = json.ifBlank { "Parse error: ${e.message}" },
                lastUpdatedSeconds = null
            )
        }
    }

    private fun getIconFor75B(item: JSONObject): Pair<ExpressLaneStatus, Boolean> {
        val desc = item.optString("Description", "").lowercase()
        val status = item.optString("Status", "").lowercase()

        if ("in transition" in desc || "closed" in desc || "to closed" in desc)
            return ExpressLaneStatus.CLOSED to false

        if ("open" !in desc)
            return ExpressLaneStatus.CLOSED to false  // No open mentioned → assume closed

        if ("northbound" in desc || status == "northbound")
            return ExpressLaneStatus.NORTHBOUND to false
        if ("southbound" in desc || status == "southbound")
            return ExpressLaneStatus.SOUTHBOUND to false

        return ExpressLaneStatus.CLOSED to true  // Unknown/unexpected state
    }
}
