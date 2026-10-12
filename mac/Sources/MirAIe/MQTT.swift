import Foundation
import Network

/// Minimal MQTT 3.1.1 client: CONNECT, SUBSCRIBE, PUBLISH (QoS 0), keep-alive.
/// That's all MirAIe needs, so no third-party dependency.
public final class MQTTClient: @unchecked Sendable {
    public enum Event: Sendable {
        case connected
        case disconnected(String)
        case message(topic: String, payload: Data)
    }

    private let host: String
    private let port: UInt16
    private let queue = DispatchQueue(label: "mqtt")
    private var connection: NWConnection?
    private var buffer = Data()
    private var pingTimer: DispatchSourceTimer?
    private var nextPacketId: UInt16 = 1
    private var closed = false
    private let keepAlive: UInt16 = 60
    private let onEvent: @Sendable (Event) -> Void

    public init(host: String = "mqtt.miraie.in", port: UInt16 = 8883, onEvent: @escaping @Sendable (Event) -> Void) {
        self.host = host
        self.port = port
        self.onEvent = onEvent
    }

    public func connect(clientId: String, username: String, password: String, subscribe topics: [String]) {
        queue.async { [self] in
            closed = false
            buffer = Data()
            let conn = NWConnection(host: .init(host), port: .init(rawValue: port)!, using: .tls)
            connection = conn
            conn.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready:
                    pendingSubscriptions = topics
                    send(Self.connectPacket(clientId: clientId, username: username, password: password, keepAlive: keepAlive))
                    receive()
                case .failed(let error):
                    fail("Network: \(error.localizedDescription)")
                case .waiting(let error):
                    fail("Waiting for network: \(error.localizedDescription)")
                default:
                    break
                }
            }
            conn.start(queue: queue)
        }
    }

    private var pendingSubscriptions: [String] = []

    public func publish(topic: String, payload: Data) {
        queue.async { [self] in
            send(Self.publishPacket(topic: topic, payload: payload))
        }
    }

    public func disconnect() {
        queue.async { [self] in
            closed = true
            send(Data([0xE0, 0x00]))
            teardown()
        }
    }

    // MARK: - Internals (all on `queue`)

    private func fail(_ reason: String) {
        guard !closed else { return }
        closed = true
        teardown()
        onEvent(.disconnected(reason))
    }

    private func teardown() {
        pingTimer?.cancel()
        pingTimer = nil
        connection?.cancel()
        connection = nil
    }

    private func send(_ data: Data) {
        connection?.send(content: data, completion: .contentProcessed { [weak self] error in
            if let error { self?.fail("Send: \(error.localizedDescription)") }
        })
    }

    private func receive() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data { buffer.append(data); drain() }
            if let error { fail("Receive: \(error.localizedDescription)"); return }
            if isComplete { fail("Server closed the connection"); return }
            if !closed { receive() }
        }
    }

    private func drain() {
        while let (type, body, used) = Self.parsePacket(buffer) {
            buffer.removeFirst(used)
            handle(type: type, body: body)
        }
    }

    private func handle(type: UInt8, body: Data) {
        switch type >> 4 {
        case 2: // CONNACK
            let rc = body.count >= 2 ? body[body.startIndex + 1] : 0xFF
            guard rc == 0 else {
                fail(rc == 4 || rc == 5 ? "MQTT not authorized" : "MQTT refused (\(rc))")
                return
            }
            for topic in pendingSubscriptions {
                send(Self.subscribePacket(topic: topic, packetId: nextPacketId))
                nextPacketId &+= 1
            }
            startPing()
            onEvent(.connected)
        case 3: // PUBLISH
            guard body.count >= 2 else { return }
            let len = Int(body[body.startIndex]) << 8 | Int(body[body.startIndex + 1])
            let qos = (type >> 1) & 0x03
            let topicEnd = body.startIndex + 2 + len
            guard topicEnd <= body.endIndex else { return }
            let topic = String(decoding: body[(body.startIndex + 2)..<topicEnd], as: UTF8.self)
            let payloadStart = topicEnd + (qos > 0 ? 2 : 0)
            guard payloadStart <= body.endIndex else { return }
            if qos == 1 { send(Data([0x40, 0x02]) + body[topicEnd..<payloadStart]) } // PUBACK
            onEvent(.message(topic: topic, payload: Data(body[payloadStart...])))
        default: // SUBACK, PINGRESP, …
            break
        }
    }

    private func startPing() {
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + .seconds(Int(keepAlive) / 2), repeating: .seconds(Int(keepAlive) / 2))
        timer.setEventHandler { [weak self] in self?.send(Data([0xC0, 0x00])) }
        timer.resume()
        pingTimer = timer
    }

    // MARK: - Packet encoding (internal for tests)

    static func string(_ s: String) -> Data {
        let bytes = Data(s.utf8)
        return Data([UInt8(bytes.count >> 8), UInt8(bytes.count & 0xFF)]) + bytes
    }

    static func remainingLength(_ n: Int) -> Data {
        var n = n
        var out = Data()
        repeat {
            var byte = UInt8(n % 128)
            n /= 128
            if n > 0 { byte |= 0x80 }
            out.append(byte)
        } while n > 0
        return out
    }

    static func packet(_ header: UInt8, _ body: Data) -> Data {
        Data([header]) + remainingLength(body.count) + body
    }

    static func connectPacket(clientId: String, username: String, password: String, keepAlive: UInt16) -> Data {
        var body = string("MQTT")
        body.append(4)     // protocol level 3.1.1
        body.append(0xC2)  // username + password + clean session
        body.append(contentsOf: [UInt8(keepAlive >> 8), UInt8(keepAlive & 0xFF)])
        body += string(clientId) + string(username) + string(password)
        return packet(0x10, body)
    }

    static func subscribePacket(topic: String, packetId: UInt16) -> Data {
        var body = Data([UInt8(packetId >> 8), UInt8(packetId & 0xFF)])
        body += string(topic)
        body.append(0) // QoS 0
        return packet(0x82, body)
    }

    static func publishPacket(topic: String, payload: Data) -> Data {
        packet(0x30, string(topic) + payload)
    }

    /// Returns (fixed header byte, body, bytes consumed) when a whole packet is buffered.
    static func parsePacket(_ data: Data) -> (UInt8, Data, Int)? {
        guard data.count >= 2 else { return nil }
        let start = data.startIndex
        var multiplier = 1
        var length = 0
        var i = 1
        while true {
            guard i < data.count, i <= 4 else { return nil }
            let byte = data[start + i]
            length += Int(byte & 0x7F) * multiplier
            multiplier *= 128
            i += 1
            if byte & 0x80 == 0 { break }
        }
        guard data.count >= i + length else { return nil }
        return (data[start], Data(data[(start + i)..<(start + i + length)]), i + length)
    }
}
