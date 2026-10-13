import SwiftUI
import MirAIe

@main
struct Launcher {
    @MainActor
    static func main() {
        // `ACRemote --snapshot out.png` renders the panel with sample data and exits.
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "--snapshot"), i + 1 < args.count {
            Snapshot.render(to: URL(fileURLWithPath: args[i + 1]), login: args.contains("--login"), timer: args.contains("--timer"))
            return
        }
        if args.contains("--dump-status") {
            DumpStatus.run()
            return
        }
        if let i = args.firstIndex(of: "--actm-set"), i + 2 < args.count, let off = Int(args[i + 1]), let on = Int(args[i + 2]) {
            DumpStatus.set(off: off, on: on)
            return
        }
        ACRemoteApp.main()
    }
}

/// `ACRemote --dump-status`: prints the AC's raw status (no credentials) using the keychain login,
/// with each timer slot decoded several ways, to diagnose the actm encoding.
enum DumpStatus {
    static func run() {
        let done = DispatchSemaphore(value: 0)
        Task.detached { // detached: the caller blocks the main actor while waiting
            defer { done.signal() }
            await dump()
        }
        done.wait()
    }

    /// Sends exactly one raw actm pair, waits, then prints the stored value from REST.
    static func set(off: Int, on: Int) {
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            defer { done.signal() }
            guard let creds = Keychain.load() else { print("No saved login"); return }
            do {
                let api = MirAIeAPI()
                let session = try await api.login(username: creds.username, password: creds.password)
                let home = try await api.home(session)
                let device = home.devices[0]
                let connected = AsyncStream<Void>.makeStream()
                let client = MQTTClient { if case .connected = $0 { connected.continuation.yield() } }
                client.connect(clientId: "acremote-set-\(Int.random(in: 0..<99999))", username: home.id,
                               password: session.accessToken, subscribe: [])
                for await _ in connected.stream { break }
                let body: [String: Any] = ["ki": 1, "cnt": "an", "sid": "1", "actm": [off, on]]
                let sentAt = Int(Date().timeIntervalSince1970)
                client.publish(topic: "\(device.topic)/control", payload: try JSONSerialization.data(withJSONObject: body))
                try await Task.sleep(for: .seconds(6))
                client.disconnect()
                let stored = (try await api.status(device, session)["actm"] as? [Any])?.compactMap { Int("\($0)") } ?? []
                print("sent [\(off), \(on)] at \(sentAt)  stored \(stored)")
                for (sent, got) in zip([off, on], stored) where sent > 0 && got > 0 {
                    print("  K = sent + sentAt - stored = \(sent + sentAt - got)")
                }
            } catch {
                print("Error: \(error.localizedDescription)")
            }
        }
        done.wait()
    }

    private static func dump() async {
        guard let creds = Keychain.load() else { print("No saved login; sign in from the menu bar first."); return }
        do {
            let api = MirAIeAPI()
            let session = try await api.login(username: creds.username, password: creds.password)
            let home = try await api.home(session)
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(identifier: "UTC")!
            for device in home.devices {
                let status = try await api.status(device, session)
                let data = try JSONSerialization.data(withJSONObject: status, options: [.prettyPrinted, .sortedKeys])
                print("== \(device.name)\n\(String(decoding: data, as: UTF8.self))")
                let now = Int(Date().timeIntervalSince1970)
                print("now unix=\(now)  local=\(Date().formatted(date: .abbreviated, time: .standard))  tz=\(TimeZone.current.identifier)")
                for (i, raw) in ((status["actm"] as? [Any]) ?? []).enumerated() {
                    guard let v = Int("\(raw)"), v > 0 else { print("actm[\(i)] = \(raw)"); continue }
                    let asUnix = Date(timeIntervalSince1970: TimeInterval(v))
                    let c = utc.dateComponents([.day, .month, .hour, .minute], from: asUnix)
                    print("actm[\(i)] = \(v)  as unix→local: \(asUnix.formatted(date: .abbreviated, time: .standard))"
                          + "  as UTC wall: \(c.day!)/\(c.month!) \(c.hour!):\(String(format: "%02d", c.minute!))"
                          + "  minus now: \(v - now)s")
                }
            }
        } catch {
            print("Error: \(error.localizedDescription)")
        }
    }
}

@MainActor
enum Snapshot {
    static func render(to url: URL, login: Bool, timer: Bool) {
        let store = ACStore.demo()
        let view = Group {
            if login { LoginView() } else if timer { TimerEditor() } else { ControlPanel() }
        }
        .environmentObject(store)
        .environment(\.colorScheme, .dark)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        guard let image = renderer.nsImage, let tiff = image.tiffRepresentation,
              let png = NSBitmapImageRep(data: tiff)?.representation(using: .png, properties: [:]) else {
            print("render failed")
            return
        }
        try? png.write(to: url)
        print("wrote \(url.path)")
    }
}

struct ACRemoteApp: App {
    @StateObject private var store = ACStore()

    var body: some Scene {
        MenuBarExtra {
            Group {
                if store.phase == .loggedOut {
                    LoginView()
                } else {
                    ControlPanel()
                }
            }
            .environmentObject(store)
            .preferredColorScheme(.dark)
        } label: {
            MenuBarLabel(state: store.state, phase: store.phase)
        }
        .menuBarExtraStyle(.window)
    }
}

struct MenuBarLabel: View {
    let state: ACState
    let phase: ACStore.Phase

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: state.power ? "air.conditioner.horizontal.fill" : "air.conditioner.horizontal")
            if phase == .connected, state.power {
                Text("\(state.setTemp)°")
            }
        }
    }
}
