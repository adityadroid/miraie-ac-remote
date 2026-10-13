package dev.adityadroid.acremote.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pure MirAIe protocol helpers (see PROTOCOL.md). No Android dependencies, so they are JVM-testable. */

const val MIN_TEMP = 16
const val MAX_TEMP = 30

enum class AcMode(val wire: String, val label: String) {
    COOL("cool", "Cool"), AUTO("auto", "Auto"), DRY("dry", "Dry"), FAN("fan", "Fan"), HEAT("heat", "Heat");

    companion object {
        fun fromWire(s: String?): AcMode? = entries.firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) }
    }
}

enum class FanSpeed(val wire: String, val label: String) {
    AUTO("auto", "Auto"), QUIET("quiet", "Quiet"), LOW("low", "Low"), MEDIUM("medium", "Medium"), HIGH("high", "High");

    companion object {
        fun fromWire(s: String?): FanSpeed? = entries.firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) }
    }
}

/** Vertical swing: 0 = swing/auto, 1..5 fixed positions. */
fun swingLabel(v: Int): String = if (v == 0) "Swing" else v.toString()

/** Convertible capacity levels, in menu order. 0 = off. */
val CONVERTIBLE_LEVELS = listOf(0, 110, 100, 90, 80, 70, 55, 40)

fun convertibleLabel(v: Int): String = when (v) {
    0 -> "Off"
    110 -> "110% · HC"
    else -> "$v%"
}

data class AcState(
    val power: Boolean = false,
    val mode: AcMode = AcMode.COOL,
    val setTemp: Double = 24.0,
    val roomTemp: Double? = null,
    val fan: FanSpeed = FanSpeed.AUTO,
    val vSwing: Int = 0,
    val hSwing: Int = 0,
    val eco: Boolean = false,
    val powerful: Boolean = false,
    val online: Boolean = true,
    /** Convertible capacity %, 0 = off, null = the model has no convertible support. */
    val convertible: Int? = null,
    /** Device timers as epoch seconds; null when unset or already past. */
    val offTimer: Long? = null,
    val onTimer: Long? = null,
)

object Protocol {
    const val CLIENT_ID = "PBcMcfG19njNCL8AOgvRzIC8AjQa"
    const val SCOPE = "an_14214235325"
    const val LOGIN_URL = "https://auth.miraie.in/simplifi/v1/userManagement/login"
    const val HOMES_URL = "https://app.miraie.in/simplifi/v1/homeManagement/homes"
    fun statusUrl(deviceId: String) =
        "https://app.miraie.in/simplifi/v1/deviceManagement/devices/$deviceId/mobile/status"

    const val MQTT_URI = "ssl://mqtt.miraie.in:8883"

    /**
     * Emails are kept as-is. A bare 10-digit number gets "+91", "91"+10 digits gets "+",
     * anything already starting with "+" is kept.
     */
    fun normalizeUsername(raw: String): String {
        val s = raw.trim()
        if (s.contains("@")) return s
        // Phone keyboards insert spaces, dashes and brackets; MirAIe only matches the bare +91… form.
        val plus = s.startsWith("+")
        val digits = s.filter(Char::isDigit)
        return when {
            plus -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> s
        }
    }

    fun isEmail(username: String) = username.contains("@")

    fun loginBody(username: String, password: String): JSONObject {
        val user = normalizeUsername(username)
        return JSONObject()
            .put("clientId", CLIENT_ID)
            .put("scope", SCOPE)
            .put(if (isEmail(user)) "email" else "mobile", user)
            .put("password", password)
    }

    /**
     * rmtmp quirk: valid values have a fraction of .0 or .5. Otherwise the real value is
     * round(fraction * 100), e.g. 150.27 -> 27.
     */
    fun fixRoomTemp(v: Double): Double {
        val frac = v - floor(v)
        val eps = 1e-6
        if (abs(frac) < eps || abs(frac - 0.5) < eps || abs(frac - 1.0) < eps) return v
        return (frac * 100).roundToInt().toDouble()
    }

    // ---- control payloads ----

    fun control(settings: Map<String, Any>): JSONObject {
        val o = JSONObject().put("ki", 1).put("cnt", "an").put("sid", "1")
        settings.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    fun power(on: Boolean) = mapOf<String, Any>("ps" to if (on) "on" else "off")
    fun temperature(t: Int): Map<String, Any> =
        mapOf("actmp" to String.format(Locale.US, "%.1f", t.coerceIn(MIN_TEMP, MAX_TEMP).toDouble()))
    fun mode(m: AcMode) = mapOf<String, Any>("acmd" to m.wire)
    fun fan(f: FanSpeed) = mapOf<String, Any>("acfs" to f.wire)
    fun verticalSwing(v: Int) = mapOf<String, Any>("acvs" to v.coerceIn(0, 5))
    fun horizontalSwing(v: Int) = mapOf<String, Any>("achs" to v.coerceIn(0, 5))

    /** Eco on also resets convertible (mutually exclusive), unless the model lacks it. */
    fun eco(on: Boolean, hasConvertible: Boolean = true): Map<String, Any> = when {
        !on -> mapOf("acem" to "off")
        hasConvertible -> linkedMapOf("acem" to "on", "acpm" to "off", "cnv" to 0)
        else -> linkedMapOf("acem" to "on", "acpm" to "off")
    }

    /** Powerful on also resets convertible (mutually exclusive), unless the model lacks it. */
    fun powerful(on: Boolean, hasConvertible: Boolean = true): Map<String, Any> = when {
        !on -> mapOf("acpm" to "off")
        hasConvertible -> linkedMapOf("acpm" to "on", "acem" to "off", "cnv" to 0)
        else -> linkedMapOf("acpm" to "on", "acem" to "off")
    }

    fun convertible(level: Int): Map<String, Any> =
        linkedMapOf("cnv" to level, "acem" to "off", "acpm" to "off")

    /**
     * Both timer slots, always sent together; null becomes -1 (unset). Takes real epoch seconds
     * and converts them to the AC's own clock ([deviceNow], see [TimerClock]).
     */
    fun timers(offAt: Long?, onAt: Long?, deviceNow: Long?, now: Long = System.currentTimeMillis() / 1000): Map<String, Any> =
        mapOf(
            "actm" to JSONArray()
                .put(offAt?.let { TimerClock.toDevice(it, deviceNow, now) } ?: -1L)
                .put(onAt?.let { TimerClock.toDevice(it, deviceNow, now) } ?: -1L)
        )

    // ---- time helpers

    /** Next future occurrence of [hour]:[minute] in [now]'s zone (today, or tomorrow if not after now), as epoch seconds. */
    fun nextOccurrence(hour: Int, minute: Int, now: ZonedDateTime): Long {
        var t = now.with(LocalTime.of(hour, minute))
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toEpochSecond()
    }

    fun nextOccurrence(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) =
        nextOccurrence(hour, minute, ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone))

    // ---- status merge ----

    private fun onOff(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> when (v.trim().lowercase(Locale.US)) {
            "on", "true", "1" -> true
            "off", "false", "0" -> false
            else -> null
        }
        is Number -> when (v.toInt()) { 1 -> true; 0 -> false; else -> null }
        else -> null
    }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { !it.isNaN() && !it.isInfinite() }

    /**
     * Merges a (possibly partial) status / control JSON into [state]. Keys that are absent,
     * malformed or carry unknown enum values leave the current field untouched.
     */
    fun merge(state: AcState, json: JSONObject, nowSec: Long = System.currentTimeMillis() / 1000): AcState {
        var s = state
        fun has(k: String) = json.has(k) && !json.isNull(k)
        if (has("ps")) onOff(json.get("ps"))?.let { s = s.copy(power = it) }
        if (has("acmd")) AcMode.fromWire(json.optString("acmd"))?.let { s = s.copy(mode = it) }
        if (has("actmp")) num(json.get("actmp"))
            ?.takeIf { it in MIN_TEMP.toDouble()..MAX_TEMP.toDouble() }
            ?.let { s = s.copy(setTemp = it) }
        if (has("rmtmp")) num(json.get("rmtmp"))?.let { s = s.copy(roomTemp = fixRoomTemp(it)) }
        if (has("acfs")) FanSpeed.fromWire(json.optString("acfs"))?.let { s = s.copy(fan = it) }
        if (has("acvs")) num(json.get("acvs"))?.toInt()?.takeIf { it in 0..5 }?.let { s = s.copy(vSwing = it) }
        if (has("achs")) num(json.get("achs"))?.toInt()?.takeIf { it in 0..5 }?.let { s = s.copy(hSwing = it) }
        if (has("acem")) onOff(json.get("acem"))?.let { s = s.copy(eco = it) }
        if (has("acpm")) onOff(json.get("acpm"))?.let { s = s.copy(powerful = it) }
        if (has("onlineStatus")) onOff(json.get("onlineStatus"))?.let { s = s.copy(online = it) }
        if (has("cnv")) num(json.get("cnv"))?.toInt()?.let { s = s.copy(convertible = it.takeIf { v -> v != 1 && v >= 0 }) }
        if (has("actm")) json.optJSONArray("actm")?.takeIf { it.length() >= 2 }?.let { arr ->
            // Status reports each slot as the real epoch time it fires.
            fun slot(i: Int): Long? = num(arr.opt(i))?.toLong()?.takeIf { it > nowSec }
            s = s.copy(offTimer = slot(0), onTimer = slot(1))
        }
        return s
    }

    fun merge(state: AcState, settings: Map<String, Any>, nowSec: Long = System.currentTimeMillis() / 1000): AcState =
        merge(state, JSONObject(settings), nowSec)
}

/**
 * The AC counts timers against its own clock, which can be far from real time (one unit sat at
 * 10 Oct 00:00 UTC while it was really the 13th). It fires a timer `sent − deviceNow` seconds
 * later, and MirAIe reports the slot back as the real time it will fire:
 *     stored = sent + realNow − deviceNow
 * So to fire at real time T, send `T − (realNow − deviceNow)`. Status values need no conversion.
 */
object TimerClock {
    /** [deviceNow] null (uncalibrated) sends plain epoch time; the first round trip calibrates it. */
    fun toDevice(realTime: Long, deviceNow: Long?, now: Long): Long =
        if (deviceNow == null) realTime else realTime - (now - deviceNow)

    /** The device clock implied by one round trip: [sent] at [sentAt], reported back as [stored]. */
    fun measure(sent: Long, sentAt: Long, stored: Long): Long = sent + sentAt - stored
}
