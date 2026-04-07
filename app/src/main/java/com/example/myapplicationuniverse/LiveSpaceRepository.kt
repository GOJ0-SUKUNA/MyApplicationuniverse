package com.example.myapplicationuniverse

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveSpaceSummary(
    val stationsCount: Int,
    val stationNames: List<String>,
    val neoCount: Int,
    val hazardousCount: Int,
    val neoNames: List<String>,
    val status: String
)

object LiveSpaceRepository {

    private fun fetchText(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        return try {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun fetchLiveSummary(): LiveSpaceSummary {
        return try {
            val stationText = fetchText("https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=json")
            val stationArray = JSONArray(stationText)

            val stationNames = mutableListOf<String>()
            for (i in 0 until minOf(stationArray.length(), 4)) {
                val obj = stationArray.getJSONObject(i)
                stationNames.add(obj.optString("OBJECT_NAME", "Station-$i"))
            }

            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val today = fmt.format(Date())

            val neoText = fetchText(
                "https://api.nasa.gov/neo/rest/v1/feed?start_date=$today&end_date=$today&api_key=DEMO_KEY"
            )

            val neoRoot = JSONObject(neoText)
            val neoArray = neoRoot.getJSONObject("near_earth_objects").optJSONArray(today) ?: JSONArray()

            var hazardous = 0
            val neoNames = mutableListOf<String>()
            for (i in 0 until minOf(neoArray.length(), 5)) {
                val obj = neoArray.getJSONObject(i)
                neoNames.add(obj.optString("name", "NEO-$i"))
            }
            for (i in 0 until neoArray.length()) {
                val obj = neoArray.getJSONObject(i)
                if (obj.optBoolean("is_potentially_hazardous_asteroid", false)) hazardous++
            }

            LiveSpaceSummary(
                stationsCount = stationArray.length(),
                stationNames = stationNames,
                neoCount = neoArray.length(),
                hazardousCount = hazardous,
                neoNames = neoNames,
                status = "Live data loaded"
            )
        } catch (e: Exception) {
            LiveSpaceSummary(
                stationsCount = 0,
                stationNames = emptyList(),
                neoCount = 0,
                hazardousCount = 0,
                neoNames = emptyList(),
                status = "Live data fetch failed: " + (e.message ?: "unknown error")
            )
        }
    }
}
