package dev.adityadroid.acremote.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
