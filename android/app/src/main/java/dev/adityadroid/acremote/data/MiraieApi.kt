package dev.adityadroid.acremote.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : IOException(message)

data class Session(val accessToken: String, val userId: String?, val expiresAtMs: Long)

data class Device(val deviceId: String, val name: String, val topicBase: String)

data class Home(val homeId: String, val devices: List<Device>)

/** Thin blocking REST client (call from Dispatchers.IO). */
object MiraieApi {
    private const val TIMEOUT_MS = 10_000

    private fun request(url: String, method: String, token: String?, body: JSONObject?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                // MirAIe explains rejections in {"message": "..."}; surface it instead of a bare code.
                val serverMessage = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw HttpException(code, serverMessage?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    fun login(username: String, password: String): Session {
        val text = request(Protocol.LOGIN_URL, "POST", null, Protocol.loginBody(username, password))
        val o = JSONObject(text)
        val token = o.optString("accessToken").takeIf { it.isNotEmpty() }
            ?: throw IOException("Login response had no access token")
        val expiresIn = o.optLong("expiresIn", 3600L)
        return Session(
            accessToken = token,
            userId = o.optString("userId").ifEmpty { null },
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
        )
    }

    fun homes(token: String): List<Home> = parseHomes(JSONArray(request(Protocol.HOMES_URL, "GET", token, null)))

    fun status(token: String, deviceId: String): JSONObject =
        JSONObject(request(Protocol.statusUrl(deviceId), "GET", token, null))

    fun parseHomes(arr: JSONArray): List<Home> = (0 until arr.length()).map { i ->
        val h = arr.getJSONObject(i)
        val devices = mutableListOf<Device>()
        val spaces = h.optJSONArray("spaces") ?: JSONArray()
        for (s in 0 until spaces.length()) {
            val devs = spaces.getJSONObject(s).optJSONArray("devices") ?: continue
            for (d in 0 until devs.length()) {
                val dj = devs.getJSONObject(d)
                val topic = dj.optJSONArray("topic")?.optString(0)?.takeIf { it.isNotEmpty() } ?: continue
                devices += Device(
                    deviceId = dj.optString("deviceId"),
                    name = dj.optString("deviceName").ifEmpty { "AC" },
                    topicBase = topic,
                )
            }
        }
        Home(homeId = h.opt("homeId")?.toString().orEmpty(), devices = devices)
    }
}
