package com.example.myapplicationuniverse

import android.graphics.Color
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LiveAstronomyRepository {

    private fun fetchText(urlString: String): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }

        return try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchCelesTrakStations(centerX: Double, centerY: Double): MutableList<Body> {
        val out = mutableListOf<Body>()
        return try {
            val text = fetchText("https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=json")
            val arr = JSONArray(text)
            val nowSec = System.currentTimeMillis() / 1000.0

            for (i in 0 until minOf(arr.length(), 12)) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("OBJECT_NAME", "SAT-" + i)
                val meanMotion = obj.optDouble("MEAN_MOTION", 15.0)
                val incDeg = obj.optDouble("INCLINATION", 51.6)

                val radius = 42.0 + i * 10.0
                val squash = 0.45 + 0.55 * abs(cos(Math.toRadians(incDeg)))
                val phase = nowSec * (meanMotion / 86400.0) * 2.0 * PI + i * 0.7

                val x = centerX + cos(phase) * radius
                val y = centerY + sin(phase) * radius * squash

                val speed = (meanMotion / 86400.0) * 2.0 * PI * radius
                val vx = -sin(phase) * speed
                val vy = cos(phase) * speed * squash

                out.add(
                    Body(
                        name = name,
                        kind = "Satellite",
                        x = x,
                        y = y,
                        vx = vx,
                        vy = vy,
                        mass = 0.8,
                        radius = 3.5f,
                        color = Color.WHITE
                    )
                )
            }
            out
        } catch (_: Exception) {
            out
        }
    }

    fun fetchTodayNeoBodies(centerX: Double, centerY: Double): MutableList<Body> {
        val out = mutableListOf<Body>()
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val today = fmt.format(Date())

            val text = fetchText(
                "https://api.nasa.gov/neo/rest/v1/feed?start_date=" + today + "&end_date=" + today + "&api_key=DEMO_KEY"
            )

            val root = JSONObject(text)
            val neos = root.getJSONObject("near_earth_objects").optJSONArray(today) ?: JSONArray()
            val now = System.currentTimeMillis() / 1000.0

            for (i in 0 until minOf(neos.length(), 6)) {
                val obj = neos.getJSONObject(i)
                val name = obj.optString("name", "NEO-" + i)
                val hazardous = obj.optBoolean("is_potentially_hazardous_asteroid", false)

                val diaKm = obj
                    .getJSONObject("estimated_diameter")
                    .getJSONObject("kilometers")
                    .optDouble("estimated_diameter_max", 1.0)

                val closeData = obj.optJSONArray("close_approach_data")?.optJSONObject(0)
                val missKm = closeData
                    ?.optJSONObject("miss_distance")
                    ?.optDouble("kilometers", 10000000.0) ?: 10000000.0

                val radius = 650.0 + minOf(380.0, missKm / 3000000.0)
                val angle = now * 0.00004 + i * 0.95
                val squash = 0.78 + (i * 0.02)

                val x = centerX + cos(angle) * radius
                val y = centerY + sin(angle) * radius * squash

                val speed = 95.0 / sqrt(radius / 650.0)
                val vx = -sin(angle) * speed
                val vy = cos(angle) * speed * squash

                out.add(
                    Body(
                        name = name,
                        kind = "Asteroid",
                        x = x,
                        y = y,
                        vx = vx,
                        vy = vy,
                        mass = 6.0 + diaKm,
                        radius = if (hazardous) 7f else 5f,
                        color = if (hazardous) Color.RED else Color.LTGRAY
                    )
                )
            }
            out
        } catch (_: Exception) {
            out
        }
    }
}
