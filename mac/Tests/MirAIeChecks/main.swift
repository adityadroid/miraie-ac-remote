import Foundation
@testable import MirAIe

var failures = 0
var passes = 0

func check(_ ok: Bool, _ name: String, file: String = #file, line: Int = #line) {
    if ok { passes += 1 } else { failures += 1; print("FAIL \(name) (line \(line))") }
}

func json(_ data: Data) -> [String: Any] {
    (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
}

// Room temperature quirk
check(ACState.roomTemperature("150.27") == 27, "rmtmp junk prefix 150.27")
check(ACState.roomTemperature("2.29") == 29, "rmtmp junk prefix 2.29")
check(ACState.roomTemperature("23.5") == 23.5, "rmtmp half degree")
check(ACState.roomTemperature("24") == 24, "rmtmp whole")
check(ACState.roomTemperature("x") == nil, "rmtmp garbage")

// Username normalisation
check(normalizeUsername("9876543210") == "+919876543210", "bare mobile gets +91")
check(normalizeUsername(" 98765 43210 ") == "+919876543210", "spaces stripped")
check(normalizeUsername("919876543210") == "+919876543210", "91 prefix gets +")
check(normalizeUsername("+919876543210") == "+919876543210", "+91 kept")
check(normalizeUsername("a@b.com") == "a@b.com", "email kept")
check(normalizeUsername("+91 98765 43210") == "+919876543210", "+91 with spaces compacted")
check(normalizeUsername("+91-98765-43210") == "+919876543210", "+91 with dashes compacted")
check(normalizeUsername("09876543210") == "+919876543210", "leading 0 becomes +91")

// Control payloads
let temp = json(ACCommand.temperature(23).payload())
check(temp["ki"] as? Int == 1 && temp["cnt"] as? String == "an" && temp["sid"] as? String == "1", "base fields")
check(temp["actmp"] as? String == "23.0", "actmp is a string")
check(json(ACCommand.vSwing(.swing).payload())["acvs"] as? Int == 0, "acvs is a number")
let eco = json(ACCommand.eco(true).payload())
check(eco["acem"] as? String == "on" && eco["acpm"] as? String == "off", "eco on clears powerful")
check(json(ACCommand.power(false).payload())["ps"] as? String == "off", "power off")

// Status merge: full then partial
var state = ACState()
state.merge(["onlineStatus": "true", "ps": "on", "acmd": "cool", "actmp": "23.0", "rmtmp": "150.27",
             "acfs": "auto", "acvs": 0, "acem": "off", "acpm": "off"])
check(state.online && state.power && state.mode == .cool && state.setTemp == 23, "full merge")
check(state.roomTemp == 27 && state.fan == .auto && state.vSwing == .swing, "full merge 2")
state.merge(["actmp": 25, "acfs": "high"])
check(state.setTemp == 25 && state.fan == .high && state.power, "partial merge keeps other fields")
state.merge(["acmd": "bogus"])
check(state.mode == .cool, "unknown mode ignored")

// Convertible
var conv = ACState()
check(conv.converti == nil, "convertible unknown until reported")
conv.merge(["cnv": 80])
check(conv.converti == .c80, "cnv 80 parses")
conv.merge(["cnv": 0])
check(conv.converti == .off, "cnv 0 is off")
conv.merge(["cnv": 1])
check(conv.converti == nil, "cnv 1 means unsupported")
let cnvPayload = json(ACCommand.converti(.c55).payload())
check(cnvPayload["cnv"] as? Int == 55 && cnvPayload["acem"] as? String == "off" && cnvPayload["acpm"] as? String == "off",
      "converti payload clears eco and powerful")
check(json(ACCommand.eco(true).payload())["cnv"] as? Int == 0, "eco on resets cnv")
check(json(ACCommand.powerful(true).payload())["cnv"] as? Int == 0, "powerful on resets cnv")
check(json(ACCommand.eco(false).payload())["cnv"] == nil, "eco off leaves cnv alone")

// Timers
let now = Date(timeIntervalSince1970: 1_800_000_000)
// Status reports timers as real Unix times.
var timers = ACState()
timers.merge(["actm": [1_800_003_600, -1]])
check(timers.offAt(now: now) == Date(timeIntervalSince1970: 1_800_003_600), "future off timer kept")
check(timers.onAt(now: now) == nil, "-1 on timer is unset")
timers.merge(["actm": [1_799_990_000, 1_800_007_200]])
check(timers.offAt(now: now) == nil, "past off timer counts as unset")
check(timers.onAt(now: now) != nil, "on timer parsed")

// Device clock: values measured on a real unit, whose clock sat at 1791590400 (10 Oct 00:00 UTC).
let uncalibrated = TimerClock()
check(json(ACCommand.timers(off: 1_800_003_600, on: -1, clock: uncalibrated).payload())["actm"] as? [Int] == [1_800_003_600, -1],
      "uncalibrated clock sends plain Unix time, both slots")
check(TimerClock.measure(sent: 1_791_597_600, sentAt: 1_791_918_142, stored: 1_791_925_342) == 1_791_590_400,
      "measure recovers the device clock from a round trip")
check(TimerClock.measure(sent: 1_791_921_603, sentAt: 1_791_918_003, stored: 1_792_249_209) == 1_791_590_397,
      "first observed round trip gives the same clock within seconds")
let acClock = TimerClock(deviceNow: 1_791_590_400)
let realNow = 1_791_918_142
let target = realNow + 7200
let sentValue = acClock.toDevice(target, now: realNow)
check(sentValue == 1_791_590_400 + 7200, "two hours from now goes out as device clock + 2h")
check(sentValue + realNow - 1_791_590_400 == target, "the AC then reports the intended real time")
check(acClock.toDevice(-1, now: realNow) == -1, "-1 is never converted")

var cal = Calendar(identifier: .gregorian)
cal.timeZone = TimeZone(identifier: "Asia/Kolkata")!
let tenPM = cal.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 22))!
let later = nextOccurrence(hour: 23, minute: 30, after: tenPM, calendar: cal)
check(cal.component(.day, from: later) == 12 && cal.component(.hour, from: later) == 23, "23:30 at 22:00 is today")
let morning = nextOccurrence(hour: 7, minute: 0, after: tenPM, calendar: cal)
check(cal.component(.day, from: morning) == 13 && cal.component(.hour, from: morning) == 7, "07:00 at 22:00 is tomorrow")

// MQTT framing
check(MQTTClient.remainingLength(0) == Data([0]), "varint 0")
check(MQTTClient.remainingLength(127) == Data([0x7F]), "varint 127")
check(MQTTClient.remainingLength(128) == Data([0x80, 0x01]), "varint 128")
check(MQTTClient.remainingLength(16383) == Data([0xFF, 0x7F]), "varint 16383")

let connect = MQTTClient.connectPacket(clientId: "c", username: "u", password: "p", keepAlive: 60)
check(connect == Data([0x10, 19, 0, 4]) + Data("MQTT".utf8) + Data([4, 0xC2, 0, 60, 0, 1, 0x63, 0, 1, 0x75, 0, 1, 0x70]),
      "connect packet bytes")

let pub = MQTTClient.publishPacket(topic: "a/b", payload: Data("{}".utf8))
if let (type, body, used) = MQTTClient.parsePacket(pub + Data([0xD0])) {
    check(type == 0x30 && used == pub.count, "publish round trip header")
    check(body == Data([0, 3]) + Data("a/b{}".utf8), "publish round trip body")
} else { check(false, "publish parses") }
check(MQTTClient.parsePacket(Data([0x30, 5, 0, 3])) == nil, "partial packet waits")

let big = MQTTClient.publishPacket(topic: "t", payload: Data(repeating: 65, count: 300))
check(MQTTClient.parsePacket(big)?.2 == big.count, "multi-byte length parses")

print("\(passes) passed, \(failures) failed")
exit(failures == 0 ? 0 : 1)
