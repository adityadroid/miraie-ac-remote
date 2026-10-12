import Foundation

public enum ACMode: String, CaseIterable, Sendable {
    case cool, auto, dry, fan, heat

    public var label: String {
        switch self {
        case .cool: "Cool"
        case .auto: "Auto"
        case .dry: "Dry"
        case .fan: "Fan"
        case .heat: "Heat"
        }
    }
}

public enum FanSpeed: String, CaseIterable, Sendable {
    case auto, quiet, low, medium, high

    public var label: String { rawValue.capitalized }
}

/// Vane position: 0 swings, 1–5 are fixed positions.
public enum Swing: Int, CaseIterable, Sendable {
    case swing = 0, p1, p2, p3, p4, p5

    public var label: String { self == .swing ? "Swing" : "Position \(rawValue)" }
    public var shortLabel: String { self == .swing ? "Swing" : "\(rawValue)" }
}

public struct ACState: Equatable, Sendable {
    public var online = false
    public var power = false
    public var mode = ACMode.cool
    public var setTemp = 24
    public var roomTemp: Double?
    public var fan = FanSpeed.auto
    public var vSwing = Swing.swing
    public var eco = false
    public var powerful = false

    public init() {}

    public static let minTemp = 16
    public static let maxTemp = 30

    /// Merge a status payload (full REST status or partial MQTT update).
    public mutating func merge(_ s: [String: Any]) {
        if let v = s["onlineStatus"] { online = Self.string(v) == "true" }
        if let v = s["ps"] { power = Self.string(v) == "on" }
        if let v = s["acmd"], let m = ACMode(rawValue: Self.string(v)) { mode = m }
        if let v = s["actmp"], let t = Double(Self.string(v)) { setTemp = Int(t.rounded()) }
        if let v = s["rmtmp"], let t = Self.roomTemperature(Self.string(v)) { roomTemp = t }
        if let v = s["acfs"], let f = FanSpeed(rawValue: Self.string(v)) { fan = f }
        if let v = s["acvs"], let n = Int(Self.string(v)), let w = Swing(rawValue: n) { vSwing = w }
        if let v = s["acem"] { eco = Self.string(v) == "on" }
        if let v = s["acpm"] { powerful = Self.string(v) == "on" }
    }

    /// Room temperature is reported in 0.5° steps. Some firmware prefixes junk,
    /// e.g. "150.27"; then the fraction holds the real value (27).
    public static func roomTemperature(_ raw: String) -> Double? {
        guard let t = Double(raw) else { return nil }
        let fraction = t - t.rounded(.towardZero)
        if fraction != 0 && fraction != 0.5 {
            return (fraction * 100).rounded()
        }
        return t
    }

    static func string(_ v: Any) -> String {
        switch v {
        case let s as String: s
        case let n as NSNumber: n.stringValue
        default: "\(v)"
        }
    }
}

/// One control message. `fields` are merged into the base payload.
public struct ACCommand: Sendable {
    public let fields: [String: any Sendable]

    public static func power(_ on: Bool) -> ACCommand { .init(fields: ["ps": on ? "on" : "off"]) }
    public static func temperature(_ t: Int) -> ACCommand { .init(fields: ["actmp": String(format: "%.1f", Double(t))]) }
    public static func mode(_ m: ACMode) -> ACCommand { .init(fields: ["acmd": m.rawValue]) }
    public static func fan(_ f: FanSpeed) -> ACCommand { .init(fields: ["acfs": f.rawValue]) }
    public static func vSwing(_ s: Swing) -> ACCommand { .init(fields: ["acvs": s.rawValue]) }
    public static func eco(_ on: Bool) -> ACCommand {
        .init(fields: on ? ["acem": "on", "acpm": "off"] : ["acem": "off"])
    }
    public static func powerful(_ on: Bool) -> ACCommand {
        .init(fields: on ? ["acpm": "on", "acem": "off"] : ["acpm": "off"])
    }

    public func payload() -> Data {
        var body: [String: Any] = ["ki": 1, "cnt": "an", "sid": "1"]
        for (k, v) in fields { body[k] = v }
        return try! JSONSerialization.data(withJSONObject: body, options: [.sortedKeys])
    }
}

/// Normalise what the user typed: emails pass through, bare 10-digit numbers get +91.
public func normalizeUsername(_ raw: String) -> String {
    let s = raw.trimmingCharacters(in: .whitespaces)
    if s.contains("@") || s.hasPrefix("+") { return s }
    let digits = s.filter(\.isNumber)
    if digits.count == 10 { return "+91" + digits }
    if digits.count == 12, digits.hasPrefix("91") { return "+" + digits }
    return s
}
