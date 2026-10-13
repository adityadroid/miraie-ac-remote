package dev.adityadroid.acremote.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RoomTempQuirkTest {
    @Test fun fractionalGarbageUsesFractionTimes100() {
        assertEquals(27.0, Protocol.fixRoomTemp(150.27), 1e-9)
        assertEquals(29.0, Protocol.fixRoomTemp(2.29), 1e-9)
    }

    @Test fun halfAndWholeValuesAreKept() {
        assertEquals(23.5, Protocol.fixRoomTemp(23.5), 1e-9)
        assertEquals(24.0, Protocol.fixRoomTemp(24.0), 1e-9)
    }

    @Test fun quirkAppliedWhenMergingStringRmtmp() {
        val s = Protocol.merge(AcState(), JSONObject("""{"rmtmp":"150.27"}"""))
        assertEquals(27.0, s.roomTemp!!, 1e-9)
    }
}

class UsernameTest {
    @Test fun bareTenDigitsGetPlus91() = assertEquals("+919876543210", Protocol.normalizeUsername("9876543210"))
    @Test fun ninetyOnePrefixGetsPlus() = assertEquals("+919876543210", Protocol.normalizeUsername("919876543210"))
    @Test fun plusNumberKept() = assertEquals("+14155550100", Protocol.normalizeUsername("+14155550100"))
    @Test fun emailKept() = assertEquals("me@example.com", Protocol.normalizeUsername("me@example.com"))
    @Test fun whitespaceTrimmed() = assertEquals("+919876543210", Protocol.normalizeUsername(" 98765 43210 "))
    @Test fun plusNumberWithSpacesCompacted() = assertEquals("+919876543210", Protocol.normalizeUsername("+91 98765 43210"))
    @Test fun plusNumberWithDashesCompacted() = assertEquals("+919876543210", Protocol.normalizeUsername("+91-98765-43210"))
    @Test fun leadingZeroBecomesPlus91() = assertEquals("+919876543210", Protocol.normalizeUsername("09876543210"))

    @Test fun loginUsesMobileKeyForNumbers() {
        val b = Protocol.loginBody("9876543210", "secret")
        assertEquals("+919876543210", b.getString("mobile"))
        assertFalse(b.has("email"))
        assertEquals(Protocol.CLIENT_ID, b.getString("clientId"))
        assertEquals(Protocol.SCOPE, b.getString("scope"))
    }

    @Test fun loginUsesEmailKeyForEmails() {
        val b = Protocol.loginBody("me@example.com", "secret")
        assertEquals("me@example.com", b.getString("email"))
        assertFalse(b.has("mobile"))
    }
}

class ControlPayloadTest {
    private fun base(o: JSONObject) {
        assertEquals(1, o.getInt("ki"))
        assertTrue(o.get("ki") is Number)
        assertEquals("an", o.getString("cnt"))
        assertEquals("1", o.get("sid"))
    }

    @Test fun temperatureIsStringWithOneDecimal() {
        val o = JSONObject(Protocol.control(Protocol.temperature(23)).toString())
        base(o)
        assertEquals("23.0", o.get("actmp"))
        assertTrue(o.get("actmp") is String)
    }

    @Test fun temperatureIsClamped() {
        assertEquals("30.0", Protocol.temperature(35)["actmp"])
        assertEquals("16.0", Protocol.temperature(10)["actmp"])
    }

    @Test fun verticalSwingIsNumber() {
        val o = JSONObject(Protocol.control(Protocol.verticalSwing(3)).toString())
        base(o)
        assertTrue(o.get("acvs") is Number)
        assertEquals(3, o.getInt("acvs"))
    }

    @Test fun powerModeFan() {
        assertEquals("on", Protocol.control(Protocol.power(true)).getString("ps"))
        assertEquals("off", Protocol.control(Protocol.power(false)).getString("ps"))
        assertEquals("heat", Protocol.control(Protocol.mode(AcMode.HEAT)).getString("acmd"))
        assertEquals("quiet", Protocol.control(Protocol.fan(FanSpeed.QUIET)).getString("acfs"))
    }

    @Test fun ecoOnTurnsPowerfulOff() {
        val o = Protocol.control(Protocol.eco(true))
        assertEquals("on", o.getString("acem"))
        assertEquals("off", o.getString("acpm"))
    }

    @Test fun ecoOffOnlyTouchesEco() {
        val o = Protocol.control(Protocol.eco(false))
        assertEquals("off", o.getString("acem"))
        assertFalse(o.has("acpm"))
    }

    @Test fun powerfulOnTurnsEcoOff() {
        val o = Protocol.control(Protocol.powerful(true))
        assertEquals("on", o.getString("acpm"))
        assertEquals("off", o.getString("acem"))
    }

    @Test fun powerfulOffOnlyTouchesPowerful() {
        val o = Protocol.control(Protocol.powerful(false))
        assertEquals("off", o.getString("acpm"))
        assertFalse(o.has("acem"))
    }

    @Test fun optimisticExclusivityInLocalState() {
        val ecoOn = Protocol.merge(AcState(powerful = true), Protocol.eco(true))
        assertTrue(ecoOn.eco)
        assertFalse(ecoOn.powerful)
        val boost = Protocol.merge(ecoOn, Protocol.powerful(true))
        assertTrue(boost.powerful)
        assertFalse(boost.eco)
    }
}

class StatusMergeTest {
    private val full = JSONObject(
        """{"ty":"AC","onlineStatus":"true","ps":"on","acmd":"cool","actmp":"23.0","rmtmp":"26.5",
           "acfs":"auto","acvs":0,"achs":0,"acem":"off","acpm":"off"}"""
    )

    @Test fun fullStatusParses() {
        val s = Protocol.merge(AcState(), full)
        assertTrue(s.power)
        assertEquals(AcMode.COOL, s.mode)
        assertEquals(23.0, s.setTemp, 1e-9)
        assertEquals(26.5, s.roomTemp!!, 1e-9)
        assertEquals(FanSpeed.AUTO, s.fan)
        assertEquals(0, s.vSwing)
        assertTrue(s.online)
    }

    @Test fun partialUpdateKeepsOtherFields() {
        val before = Protocol.merge(AcState(), full)
        val after = Protocol.merge(before, JSONObject("""{"actmp":25}"""))
        assertEquals(25.0, after.setTemp, 1e-9)
        assertEquals(before.copy(setTemp = 25.0), after)
    }

    @Test fun unknownEnumValuesAreIgnored() {
        val before = Protocol.merge(AcState(), full)
        val after = Protocol.merge(before, JSONObject("""{"acmd":"turbo","acfs":"hurricane","ps":"maybe","acvs":9,"acmd2":"x"}"""))
        assertEquals(before, after)
    }

    @Test fun outOfRangeOrGarbageTempIgnored() {
        val before = Protocol.merge(AcState(), full)
        assertEquals(before, Protocol.merge(before, JSONObject("""{"actmp":"abc"}""")))
        assertEquals(before, Protocol.merge(before, JSONObject("""{"actmp":"45.0"}""")))
    }

    @Test fun numericAndStringFormsAccepted() {
        val s = Protocol.merge(AcState(), JSONObject("""{"actmp":22,"acvs":"4","onlineStatus":false,"acem":"on"}"""))
        assertEquals(22.0, s.setTemp, 1e-9)
        assertEquals(4, s.vSwing)
        assertFalse(s.online)
        assertTrue(s.eco)
    }

    @Test fun homesParsing() {
        val homes = MiraieApi.parseHomes(
            JSONArray(
                """[{"homeId":"h1","spaces":[{"devices":[{"deviceId":"d1","deviceName":"Bedroom","topic":["u/h1/d1"]}]}]}]"""
            )
        )
        assertEquals("h1", homes[0].homeId)
        assertEquals(Device("d1", "Bedroom", "u/h1/d1"), homes[0].devices[0])
    }
}

class ConvertibleTest {
    private fun parse(json: String) = Protocol.merge(AcState(), JSONObject(json))

    @Test fun zeroIsOff() = assertEquals(0, parse("""{"cnv":0}""").convertible)
    @Test fun eightyParses() = assertEquals(80, parse("""{"cnv":80}""").convertible)
    @Test fun stringFormParses() = assertEquals(110, parse("""{"cnv":"110"}""").convertible)
    @Test fun missingIsUnsupported() = assertNull(parse("""{"ps":"on"}""").convertible)
    @Test fun oneIsUnsupported() = assertNull(parse("""{"cnv":1}""").convertible)

    @Test fun labels() {
        assertEquals("Off", convertibleLabel(0))
        assertEquals("110% · HC", convertibleLabel(110))
        assertEquals("55%", convertibleLabel(55))
        assertEquals(listOf(0, 110, 100, 90, 80, 70, 55, 40), CONVERTIBLE_LEVELS)
    }

    @Test fun payloadTurnsEcoAndPowerfulOff() {
        val o = JSONObject(Protocol.control(Protocol.convertible(80)).toString())
        assertEquals(80, o.getInt("cnv"))
        assertTrue(o.get("cnv") is Number)
        assertEquals("off", o.getString("acem"))
        assertEquals("off", o.getString("acpm"))
    }

    @Test fun ecoOnSendsCnvZero() {
        val o = JSONObject(Protocol.control(Protocol.eco(true)).toString())
        assertEquals(0, o.getInt("cnv"))
        assertEquals("on", o.getString("acem"))
    }

    @Test fun powerfulOnSendsCnvZero() {
        val o = JSONObject(Protocol.control(Protocol.powerful(true)).toString())
        assertEquals(0, o.getInt("cnv"))
    }

    @Test fun ecoOffAndUnsupportedModelsDoNotSendCnv() {
        assertFalse(Protocol.control(Protocol.eco(false)).has("cnv"))
        assertFalse(Protocol.control(Protocol.eco(true, hasConvertible = false)).has("cnv"))
        assertFalse(Protocol.control(Protocol.powerful(true, hasConvertible = false)).has("cnv"))
    }

    @Test fun optimisticExclusivity() {
        val cnv = Protocol.merge(AcState(eco = true, convertible = 0), Protocol.convertible(70))
        assertEquals(70, cnv.convertible)
        assertFalse(cnv.eco)
        assertFalse(cnv.powerful)
        val eco = Protocol.merge(cnv, Protocol.eco(true))
        assertTrue(eco.eco)
        assertEquals(0, eco.convertible)
    }
}

class TimerTest {
    private val now = 1_800_000_000L

    private fun parse(json: String) = Protocol.merge(AcState(), JSONObject(json), nowSec = now)
    private fun actm(settings: Map<String, Any>) = Protocol.control(settings).getJSONArray("actm")

    @Test fun unsetSlotsAreNull() {
        val s = parse("""{"actm":[-1,-1]}""")
        assertNull(s.offTimer)
        assertNull(s.onTimer)
    }

    @Test fun missingIsNull() {
        val s = parse("""{"ps":"on"}""")
        assertNull(s.offTimer)
        assertNull(s.onTimer)
    }

    @Test fun pastIsNull() {
        val s = parse("""{"actm":[${now - 60},${now + 600}]}""")
        assertNull(s.offTimer)
        assertEquals(now + 600, s.onTimer)
    }

    @Test fun statusIsRealTimeWithoutConversion() {
        val s = parse("""{"actm":[${now + 3600},-1]}""")
        assertEquals(now + 3600, s.offTimer)
        assertNull(s.onTimer)
    }

    @Test fun uncalibratedSendsPlainEpochInBothSlots() {
        val arr = actm(Protocol.timers(now + 100, now + 9000, deviceNow = null, now = now))
        assertEquals(2, arr.length())
        assertEquals(now + 100, arr.getLong(0))
        assertEquals(now + 9000, arr.getLong(1))
        assertTrue(arr.get(0) is Number)
    }

    @Test fun clearSendsMinusOneUnconverted() {
        val arr = actm(Protocol.timers(null, null, deviceNow = 1_791_590_400L, now = now))
        assertEquals(-1L, arr.getLong(0))
        assertEquals(-1L, arr.getLong(1))
    }

    // ---- values measured on a real unit whose clock sat at 1791590400 (10 Oct 00:00 UTC)

    @Test fun measureRecoversDeviceClock() {
        assertEquals(1_791_590_400L, TimerClock.measure(sent = 1_791_597_600L, sentAt = 1_791_918_142L, stored = 1_791_925_342L))
    }

    @Test fun calibratedSendLandsOnTarget() {
        val deviceNow = 1_791_590_400L
        val realNow = 1_791_918_142L
        val target = realNow + 7200
        val wire = actm(Protocol.timers(null, target, deviceNow, realNow)).getLong(1)
        assertEquals(deviceNow + 7200, wire)
        // The AC reports stored = sent + realNow - deviceNow, which must equal the target.
        assertEquals(target, wire + realNow - deviceNow)
    }

    @Test fun uncalibratedSendShowsTheObservedBug() {
        // Plain epoch for a target 1 h ahead came back ~91 h later on that unit.
        val realNow = 1_791_918_003L
        val wire = actm(Protocol.timers(null, realNow + 3600, null, realNow)).getLong(1)
        val stored = wire + realNow - 1_791_590_400L
        assertEquals(327_603L + 3600, stored - realNow)
    }
}

class NextOccurrenceTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 12, 22, 0, 0, 0, zone)

    @Test fun laterTodayStaysToday() {
        val expected = ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, zone).toEpochSecond()
        assertEquals(expected, Protocol.nextOccurrence(23, 30, now))
    }

    @Test fun earlierRollsToTomorrow() {
        val expected = ZonedDateTime.of(2026, 10, 13, 7, 0, 0, 0, zone).toEpochSecond()
        assertEquals(expected, Protocol.nextOccurrence(7, 0, now))
    }

    @Test fun sameMinuteRollsToTomorrow() {
        val expected = ZonedDateTime.of(2026, 10, 13, 22, 0, 0, 0, zone).toEpochSecond()
        assertEquals(expected, Protocol.nextOccurrence(22, 0, now))
    }

    @Test fun epochMillisOverloadUsesZone() {
        val expected = ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, zone).toEpochSecond()
        assertEquals(expected, Protocol.nextOccurrence(23, 30, now.toInstant().toEpochMilli(), zone))
    }
}
