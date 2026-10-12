import Foundation
import MirAIe

// Diagnostics CLI.
//   miraie-probe status            log in with MIRAIE_USER / MIRAIE_PASS and print the raw status JSON
//   miraie-probe login-check       send a deliberately wrong login to confirm the endpoint answers
//   miraie-probe mqtt HOST PORT [USER PASS]   connect, subscribe, publish to self, expect the echo

final class Once: @unchecked Sendable {
    private var cont: CheckedContinuation<String, Never>?
    var client: MQTTClient?
    private let lock = NSLock()

    init(_ c: CheckedContinuation<String, Never>) { cont = c }

    func finish(_ s: String) {
        lock.lock(); defer { lock.unlock() }
        cont?.resume(returning: s)
        cont = nil
        client?.disconnect()
    }
}

func mqttEcho(host: String, port: UInt16, user: String, pass: String) async -> String {
    let topic = "acremote-probe/\(UUID().uuidString)"
    return await withCheckedContinuation { cont in
        let once = Once(cont)
        let client = MQTTClient(host: host, port: port) { event in
            switch event {
            case .connected:
                once.client?.publish(topic: topic, payload: Data("ping".utf8))
            case .disconnected(let reason):
                once.finish("disconnected: \(reason)")
            case .message(let t, let p):
                once.finish("echo on \(t == topic ? "own topic" : t): \(String(decoding: p, as: UTF8.self))")
            }
        }
        once.client = client
        client.connect(clientId: "acremote-probe-\(Int.random(in: 0..<100000))", username: user, password: pass, subscribe: [topic])
        DispatchQueue.global().asyncAfter(deadline: .now() + 10) { once.finish("timeout") }
    }
}

let args = Array(CommandLine.arguments.dropFirst())

switch args.first {
case "login-check":
    do {
        _ = try await MirAIeAPI().login(username: "+910000000000", password: "wrong-password")
        print("unexpected success")
    } catch {
        print("login rejected as expected: \(error.localizedDescription)")
    }
case "mqtt" where args.count >= 3:
    print(await mqttEcho(
        host: args[1], port: UInt16(args[2])!,
        user: args.count > 3 ? args[3] : "", pass: args.count > 4 ? args[4] : ""
    ))
case "status":
    let env = ProcessInfo.processInfo.environment
    guard let user = env["MIRAIE_USER"], let pass = env["MIRAIE_PASS"] else {
        print("set MIRAIE_USER and MIRAIE_PASS")
        exit(2)
    }
    let api = MirAIeAPI()
    let session = try await api.login(username: user, password: pass)
    let home = try await api.home(session)
    for device in home.devices {
        let status = try await api.status(device, session)
        let data = try JSONSerialization.data(withJSONObject: status, options: [.prettyPrinted, .sortedKeys])
        print("== \(device.name) (\(device.id))\n\(String(decoding: data, as: UTF8.self))")
    }
default:
    print("usage: miraie-probe status | login-check | mqtt HOST PORT [USER PASS]")
}
