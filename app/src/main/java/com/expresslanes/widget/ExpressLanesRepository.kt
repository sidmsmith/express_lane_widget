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
    private const val PEACH_PASS_URL =
        "https://peachpass.com/wp-admin/admin-ajax.php?action=pp_lane_status"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Temporary ops switch: skip 511 GA entirely and use Peach Pass only.
    // Keep existing 511 logic below for fast re-enable when needed.
    private const val FORCE_PEACH_PASS_ONLY = true

    suspend fun fetchNorthwestCorridor(apiKey: String): FetchResult = withContext(Dispatchers.IO) {
        if (FORCE_PEACH_PASS_ONLY) {
            val peach = fetchPeachPassRaw()
            val rawForHistory = buildString {
                append("Peach Pass only mode: 511 GA call skipped.\n")
                append("Peach Pass: ")
                append(peach.rawJson)
            }
            return@withContext FetchResult(
                status = peach.status,
                isOddResponse = false,
                rawJson = rawForHistory,
                lastUpdatedSeconds = null,
                fromPeachPassFallback = true,
                isPeachPassUnexpected = peach.isUnexpected
            )
        }

        val primaryOutcome = run511Fetch(apiKey)
        when (primaryOutcome) {
            is Outcome511.UsePrimary -> primaryOutcome.result.copy(fromPeachPassFallback = false)
            is Outcome511.NeedFallback -> {
                val peach = fetchPeachPassRaw()
                val rawForHistory = buildString {
                    append("Peach Pass fallback (511 GA not used as primary).\n")
                    append("511 snapshot: ")
                    append(primaryOutcome.snapshotText.take(800))
                    if (primaryOutcome.snapshotText.length > 800) append("…")
                    append("\n---\nPeach Pass: ")
                    append(peach.rawJson)
                }
                FetchResult(
                    status = peach.status,
                    isOddResponse = peach.isUnexpected,
                    rawJson = rawForHistory,
                    lastUpdatedSeconds = primaryOutcome.lastUpdatedForStale,
                    fromPeachPassFallback = true,
                    isPeachPassUnexpected = peach.isUnexpected
                )
            }
        }
    }

    private sealed class Outcome511 {
        data class UsePrimary(val result: FetchResult) : Outcome511()
        data class NeedFallback(
            val snapshotText: String,
            val lastUpdatedForStale: Long?
        ) : Outcome511()
    }

    private fun run511Fetch(apiKey: String): Outcome511 {
        return try {
            val url = "https://511ga.org/api/v2/get/expresslanes?key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Outcome511.NeedFallback("HTTP ${response.code}", null)
            }
            val parsed = parse511Response(body)
            if (parsed == null) {
                return Outcome511.NeedFallback(body, null)
            }
            val nowSec = System.currentTimeMillis() / 1000
            val fresh = parsed.lastUpdatedSeconds?.let { lastUp ->
                nowSec - lastUp <= WidgetPrefs.STALE_THRESHOLD_SECONDS
            } ?: false
            if (fresh && !parsed.isOddResponse) {
                Outcome511.UsePrimary(parsed)
            } else {
                Outcome511.NeedFallback(body, parsed.lastUpdatedSeconds)
            }
        } catch (e: Exception) {
            Outcome511.NeedFallback("Error: ${e.message}", null)
        }
    }

    private fun parse511Response(json: String): FetchResult? {
        return try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("Id") == TARGET_LANE_ID) {
                    val (status, isOdd) = getIconFor75B(obj)
                    val lastUpdated = obj.optLong("LastUpdated", 0L).takeIf { it > 0 }
                    return FetchResult(status, isOdd, json, lastUpdatedSeconds = lastUpdated)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private data class PeachParse(val status: ExpressLaneStatus, val isUnexpected: Boolean, val rawJson: String)

    private fun fetchPeachPassRaw(): PeachParse {
        return try {
            val request = Request.Builder().url(PEACH_PASS_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, "HTTP ${response.code}: $body")
            }
            parsePeachPassJson(body)
        } catch (e: Exception) {
            PeachParse(ExpressLaneStatus.CLOSED, true, "Error: ${e.message}")
        }
    }

    /**
     * Parses Peach Pass JSON: expects `success`, `data.north` string with open/closed cues and direction.
     * [isUnexpected] is true when JSON is invalid, structure is wrong, or text does not clearly indicate state.
     */
    private fun parsePeachPassJson(body: String): PeachParse {
        return try {
            val obj = JSONObject(body)
            if (!obj.optBoolean("success", false)) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
            val data = obj.optJSONObject("data")
                ?: return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            if (!data.has("north")) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
            val north = data.optString("north", "").trim().lowercase()
            if (north.isEmpty()) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
            if ("transition" in north) {
                return PeachParse(ExpressLaneStatus.TRANSITION, false, body)
            }
            val hasOpen = "open" in north
            val hasClosed = "closed" in north
            if (!hasOpen && !hasClosed) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
            if (hasClosed && !hasOpen) {
                return PeachParse(ExpressLaneStatus.CLOSED, false, body)
            }
            if (hasOpen && hasClosed) {
                return PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
            when {
                "north" in north -> PeachParse(ExpressLaneStatus.NORTHBOUND, false, body)
                "south" in north -> PeachParse(ExpressLaneStatus.SOUTHBOUND, false, body)
                else -> PeachParse(ExpressLaneStatus.CLOSED, true, body)
            }
        } catch (e: Exception) {
            PeachParse(ExpressLaneStatus.CLOSED, true, body.ifBlank { "Parse error: ${e.message}" })
        }
    }

    private fun getIconFor75B(item: JSONObject): Pair<ExpressLaneStatus, Boolean> {
        val desc = item.optString("Description", "").lowercase()
        val status = item.optString("Status", "").lowercase()

        if ("in transition" in desc || "closed" in desc || "to closed" in desc)
            return ExpressLaneStatus.CLOSED to false

        if ("open" !in desc)
            return ExpressLaneStatus.CLOSED to false

        if ("northbound" in desc || status == "northbound")
            return ExpressLaneStatus.NORTHBOUND to false
        if ("southbound" in desc || status == "southbound")
            return ExpressLaneStatus.SOUTHBOUND to false

        return ExpressLaneStatus.CLOSED to true
    }
}

