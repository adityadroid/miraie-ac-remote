import Foundation
import MirAIe

@MainActor
final class ACStore: ObservableObject {
    enum Phase: Equatable {
        case loggedOut
        case loading
        case connected
        case offline(String)
    }

    @Published private(set) var phase: Phase = .loading
    @Published private(set) var state = ACState()
    @Published private(set) var syncing = false
    @Published private(set) var deviceName = "AC"
    @Published var loginError: String?

    private let api = MirAIeAPI()
    private var creds: Keychain.Credentials?
    private var session: Session?
    private var home: Home?
    private var mqtt: MQTTClient?
    private var syncDeadline: Task<Void, Never>?
    private var tempDebounce: Task<Void, Never>?
    private var reconnect: Task<Void, Never>?

    private var device: Device? { home?.devices.first }

    /// Offline store with sample state, for rendering snapshots of the UI.
    static func demo() -> ACStore {
        let store = ACStore(loadSaved: false)
        store.state.merge(["onlineStatus": "true", "ps": "on", "acmd": "cool", "actmp": "23",
                           "rmtmp": "26.5", "acfs": "auto", "acvs": 0, "acem": "off", "acpm": "off",
                           "cnv": 80, "actm": [Int(Date().timeIntervalSince1970) + 7200, -1]])
        store.phase = .connected
        store.deviceName = "Bedroom AC"
        return store
    }

    init(loadSaved: Bool = true) {
        guard loadSaved else { return }
        creds = Keychain.load()
        if creds == nil { phase = .loggedOut } else { Task { await start() } }
    }

    // MARK: - Session

    func login(username: String, password: String) async {
        loginError = nil
        phase = .loading
        do {
            session = try await api.login(username: username, password: password)
            creds = .init(username: username, password: password)
            Keychain.save(creds!)
            await start()
        } catch {
            loginError = error.localizedDescription
            phase = .loggedOut
        }
    }

    func logout() {
        mqtt?.disconnect()
        mqtt = nil
        reconnect?.cancel()
        Keychain.clear()
        creds = nil
        session = nil
        home = nil
        state = ACState()
        phase = .loggedOut
    }

    private func validSession() async throws -> Session {
        if let session, session.expiresAt > Date() { return session }
        guard let creds else { throw APIError.unauthorized }
        let fresh = try await api.login(username: creds.username, password: creds.password)
        session = fresh
        return fresh
    }

    private func start() async {
        do {
            let s = try await validSession()
            let h = try await api.home(s)
            home = h
            deviceName = h.devices[0].name
            try await refresh()
            connectMQTT(s, h)
        } catch APIError.badLogin(let message) {
            loginError = message.isEmpty ? "Saved login was rejected. Sign in again." : message
            logout()
        } catch {
            phase = .offline(error.localizedDescription)
            scheduleReconnect()
        }
    }

    /// Pull full status over REST. Called on launch and whenever the popover opens.
    func refresh() async throws {
        guard let device else { return }
        do {
            let status = try await api.status(device, validSession())
            state.merge(status)
        } catch APIError.unauthorized {
            session = nil
            state.merge(try await api.status(device, validSession()))
        }
    }

    func refreshQuietly() {
        guard phase == .connected, !syncing else { return }
        Task { try? await refresh() }
    }

    private func connectMQTT(_ s: Session, _ h: Home) {
        mqtt?.disconnect()
        let client = MQTTClient { [weak self] event in
            Task { @MainActor in self?.handle(event) }
        }
        mqtt = client
        let topics = h.devices.flatMap { ["\($0.topic)/status", "\($0.topic)/connectionStatus"] }
        client.connect(
            clientId: "acremote-mac-\(UUID().uuidString.prefix(8))",
            username: h.id,
            password: s.accessToken,
            subscribe: topics
        )
    }

    private func handle(_ event: MQTTClient.Event) {
        switch event {
        case .connected:
            phase = .connected
        case .disconnected(let reason):
            phase = .offline(reason)
            if reason.contains("authorized") { session = nil }
            scheduleReconnect()
        case .message(let topic, let payload):
            guard let json = (try? JSONSerialization.jsonObject(with: payload)) as? [String: Any] else { return }
            if topic.hasSuffix("/connectionStatus") {
                if let online = json["onlineStatus"] { state.online = "\(online)" == "true" }
            } else {
                state.merge(json)
                syncing = false
                syncDeadline?.cancel()
            }
        }
    }

    private func scheduleReconnect() {
        reconnect?.cancel()
        reconnect = Task {
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled, creds != nil else { return }
            await start()
        }
    }

    // MARK: - Controls

    private func send(_ command: ACCommand) {
        guard let mqtt, let device else { return }
        mqtt.publish(topic: "\(device.topic)/control", payload: command.payload())
        syncing = true
        syncDeadline?.cancel()
        syncDeadline = Task {
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            syncing = false
            try? await refresh()
        }
    }

    func togglePower() {
        state.power.toggle()
        send(.power(state.power))
    }

    /// Updates the UI immediately; the command goes out once the user stops dragging or tapping.
    func setTemperature(_ t: Int) {
        let clamped = min(max(t, ACState.minTemp), ACState.maxTemp)
        guard clamped != state.setTemp else { return }
        state.setTemp = clamped
        tempDebounce?.cancel()
        tempDebounce = Task {
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            send(.temperature(clamped))
        }
    }

    func setMode(_ m: ACMode) { state.mode = m; send(.mode(m)) }
    func setFan(_ f: FanSpeed) { state.fan = f; send(.fan(f)) }
    func setSwing(_ s: Swing) { state.vSwing = s; send(.vSwing(s)) }

    func setEco(_ on: Bool) {
        state.eco = on
        if on { state.powerful = false; resetConverti() }
        send(.eco(on))
    }

    func setPowerful(_ on: Bool) {
        state.powerful = on
        if on { state.eco = false; resetConverti() }
        send(.powerful(on))
    }

    func setConverti(_ c: Converti) {
        state.converti = c
        state.eco = false
        state.powerful = false
        send(.converti(c))
    }

    private func resetConverti() {
        if state.converti != nil { state.converti = .off }
    }

    /// Pass nil to clear. The AC keeps both slots, so the other one is resent unchanged.
    func setOffTimer(_ date: Date?) {
        setTimers(off: date.map { Int($0.timeIntervalSince1970) } ?? -1, on: state.onAt() == nil ? -1 : state.onTimer)
    }

    func setOnTimer(_ date: Date?) {
        setTimers(off: state.offAt() == nil ? -1 : state.offTimer, on: date.map { Int($0.timeIntervalSince1970) } ?? -1)
    }

    private static let clockKey = "timerDeviceClock"
    private var timerClock = TimerClock(deviceNow: UserDefaults.standard.object(forKey: clockKey) as? Int)
    private var timerTask: Task<Void, Never>?

    private func setTimers(off: Int, on: Int) {
        state.offTimer = off
        state.onTimer = on
        timerTask?.cancel()
        timerTask = Task { await sendTimers(off: off, on: on, retry: true) }
    }

    /// Sends real times converted to the AC's clock, then checks what the AC stored. If a slot
    /// landed more than a minute off, the AC's clock has moved: recalibrate and send once more.
    private func sendTimers(off: Int, on: Int, retry: Bool) async {
        let sentAt = Int(Date().timeIntervalSince1970)
        let command = ACCommand.timers(off: off, on: on, clock: timerClock)
        let sent = command.fields["actm"] as? [Int] ?? [off, on]
        send(command)

        try? await Task.sleep(for: .seconds(5))
        guard !Task.isCancelled, let device,
              let status = try? await api.status(device, validSession()),
              let stored = (status["actm"] as? [Any])?.compactMap({ Int("\($0)") }), stored.count == 2
        else { return }

        for (target, (wire, got)) in zip([off, on], zip(sent, stored)) where target > 0 && got > 0 {
            let deviceNow = TimerClock.measure(sent: wire, sentAt: sentAt, stored: got)
            if timerClock.deviceNow == nil || abs(got - target) > 60 {
                timerClock.deviceNow = deviceNow
                UserDefaults.standard.set(deviceNow, forKey: Self.clockKey)
                if abs(got - target) > 60, retry {
                    await sendTimers(off: off, on: on, retry: false)
                    return
                }
            }
            break
        }
        state.merge(status)
    }
}
