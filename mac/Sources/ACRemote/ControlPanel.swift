import SwiftUI
import MirAIe

struct ControlPanel: View {
    @EnvironmentObject var store: ACStore
    @State private var showSettings = false
    @State private var showTimer = false

    private var s: ACState { store.state }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            ACIllustration(running: s.power, setTemp: s.setTemp, mode: s.mode.rawValue)
                .frame(maxWidth: .infinity)
            temperatureRow
            TempSlider(value: s.setTemp) { store.setTemperature($0) }
                .opacity(s.power ? 1 : 0.45)
            VStack(spacing: 8) {
                tiles
                secondRow
            }
            footer
        }
        .padding(18)
        .frame(width: 380)
        .background(Theme.background)
        .foregroundStyle(Theme.text)
        .onAppear { store.refreshQuietly() }
    }

    private var header: some View {
        HStack {
            Image(systemName: "thermometer.medium")
                .foregroundStyle(Theme.accent)
            Text("Room").foregroundStyle(Theme.secondary)
            Text(s.roomTemp.map { $0.formatted(.number.precision(.fractionLength(0...1))) + "°C" } ?? "–")
                .fontWeight(.semibold)
            Spacer()
            RoundIconButton(
                systemName: "power",
                size: 40,
                tint: s.power ? Color(red: 0.95, green: 0.82, blue: 0.55) : Theme.secondary
            ) { store.togglePower() }
            .shadow(color: s.power ? Color.orange.opacity(0.35) : .clear, radius: 8)
            .help(s.power ? "Turn off" : "Turn on")
        }
        .font(.system(size: 14))
    }

    private var temperatureRow: some View {
        HStack(alignment: .center) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text("\(s.setTemp)")
                    .font(.system(size: 52, weight: .semibold, design: .rounded))
                    .contentTransition(.numericText())
                    .animation(.snappy, value: s.setTemp)
                Text("°C")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(Theme.secondary)
            }
            Spacer()
            HStack(spacing: 10) {
                Image(systemName: "leaf")
                    .foregroundStyle(s.eco ? Color.green.opacity(0.8) : Theme.secondary)
                Text("Eco Mode").font(.system(size: 14, weight: .medium))
                    .fixedSize()
                PillSwitch(isOn: s.eco) { store.setEco(!s.eco) }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .tile()
        }
    }

    private var tiles: some View {
        HStack(spacing: 8) {
            MenuTile(icon: "snowflake", title: "Mode", value: s.mode.label) {
                ForEach(ACMode.allCases, id: \.self) { m in
                    Button(m.label) { store.setMode(m) }
                }
            }
            MenuTile(icon: "fan", title: "Fan speed", value: s.fan.label) {
                ForEach(FanSpeed.allCases, id: \.self) { f in
                    Button(f.label) { store.setFan(f) }
                }
            }
            ToggleTile(icon: "bolt", title: "Powerful", isOn: s.powerful) {
                store.setPowerful(!s.powerful)
            }
            MenuTile(icon: "wind", title: "Airflow", value: s.vSwing.shortLabel) {
                ForEach(Swing.allCases, id: \.self) { w in
                    Button(w.label) { store.setSwing(w) }
                }
            }
        }
    }

    private var secondRow: some View {
        HStack(spacing: 8) {
            if let converti = s.converti {
                MenuTile(icon: "gauge.with.dots.needle.67percent", title: "Convertible", value: converti.label) {
                    ForEach(Converti.allCases, id: \.self) { c in
                        Button(c.label) { store.setConverti(c) }
                    }
                }
            }
            Button { showTimer.toggle() } label: {
                TileLabel(icon: "clock", title: "Timer", value: timerSummary) {
                    Image(systemName: "chevron.down").font(.system(size: 10, weight: .semibold))
                }
            }
            .buttonStyle(.plain)
            .popover(isPresented: $showTimer, arrowEdge: .bottom) {
                TimerEditor().environmentObject(store).preferredColorScheme(.dark)
            }
        }
    }

    private var timerSummary: String {
        let off = s.offAt().map { "Off \($0.formatted(date: .omitted, time: .shortened))" }
        let on = s.onAt().map { "On \($0.formatted(date: .omitted, time: .shortened))" }
        return [off, on].compactMap { $0 }.joined(separator: " · ").nilIfEmpty ?? "None"
    }

    private var footer: some View {
        HStack(spacing: 6) {
            switch store.phase {
            case .connected where store.syncing:
                ProgressView().controlSize(.mini)
                Text("Syncing…")
            case .connected where !s.online:
                Image(systemName: "wifi.exclamationmark")
                Text("AC offline")
            case .connected:
                Image(systemName: "checkmark.shield")
                Text("Connected")
            case .loading:
                ProgressView().controlSize(.mini)
                Text("Connecting…")
            case .offline(let reason):
                Image(systemName: "exclamationmark.triangle")
                Text(reason).lineLimit(1).truncationMode(.tail)
            case .loggedOut:
                EmptyView()
            }
            Spacer()
            Button { showSettings.toggle() } label: {
                Image(systemName: "slider.horizontal.3")
            }
            .buttonStyle(.plain)
            .popover(isPresented: $showSettings, arrowEdge: .bottom) { settings }
        }
        .font(.system(size: 12))
        .foregroundStyle(Theme.secondary)
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.deviceName).font(.headline)
            Button("Sign out") { showSettings = false; store.logout() }
            Divider()
            Button("Quit AC Remote") { NSApp.terminate(nil) }
        }
        .padding(14)
        .frame(width: 200)
    }
}

/// Light pill thumb sliding in a dark track, like the reference design.
struct PillSwitch: View {
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Capsule()
                    .fill(isOn ? Theme.accent.opacity(0.35) : Color.black.opacity(0.55))
                    .overlay(Capsule().strokeBorder(Theme.tileBorder))
                Capsule()
                    .fill(isOn ? Theme.text : Color(white: 0.85))
                    .frame(width: 24, height: 16)
                    .padding(3)
            }
            .frame(width: 46, height: 22)
            .animation(.snappy(duration: 0.2), value: isOn)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Eco Mode")
        .accessibilityValue(isOn ? "On" : "Off")
    }
}

/// 16–30° slider with a warm gradient fill and pill thumb.
struct TempSlider: View {
    let value: Int
    let onChange: (Int) -> Void

    private let range = ACState.minTemp...ACState.maxTemp

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                RoundIconButton(systemName: "minus", size: 34) { onChange(value - 1) }
                GeometryReader { geo in
                    let thumbW: CGFloat = 22
                    let usable = geo.size.width - thumbW
                    let fraction = CGFloat(value - range.lowerBound) / CGFloat(range.upperBound - range.lowerBound)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.black.opacity(0.5))
                            .overlay(Capsule().strokeBorder(Theme.tileBorder))
                        Capsule().fill(Theme.trackFill)
                            .frame(width: thumbW + usable * fraction)
                        Capsule().fill(Color(white: 0.9))
                            .frame(width: thumbW, height: geo.size.height - 6)
                            .shadow(color: .black.opacity(0.5), radius: 3)
                            .offset(x: usable * fraction)
                            .padding(.vertical, 3)
                    }
                    .animation(.snappy(duration: 0.2), value: value)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                        let f = min(max((g.location.x - thumbW / 2) / usable, 0), 1)
                        let v = range.lowerBound + Int((f * CGFloat(range.upperBound - range.lowerBound)).rounded())
                        if v != value { onChange(v) }
                    })
                }
                .frame(height: 38)
                RoundIconButton(systemName: "plus", size: 34) { onChange(value + 1) }
            }
            HStack {
                Text("\(range.lowerBound)°")
                Spacer()
                Text("\(range.upperBound)°")
            }
            .font(.system(size: 11))
            .foregroundStyle(Theme.secondary)
            .padding(.horizontal, 52)
        }
    }
}

struct MenuTile<Items: View>: View {
    let icon: String
    let title: String
    let value: String
    @ViewBuilder let items: () -> Items

    var body: some View {
        Menu {
            items()
        } label: {
            TileLabel(icon: icon, title: title, value: value) {
                Image(systemName: "chevron.down").font(.system(size: 10, weight: .semibold))
            }
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
    }
}

struct ToggleTile: View {
    let icon: String
    let title: String
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            TileLabel(icon: icon, title: title, value: isOn ? "On" : "Off") {
                Circle()
                    .fill(isOn ? Color(red: 0.95, green: 0.82, blue: 0.55) : Theme.secondary.opacity(0.6))
                    .frame(width: 6, height: 6)
            }
        }
        .buttonStyle(.plain)
    }
}

struct TileLabel<Accessory: View>: View {
    let icon: String
    let title: String
    let value: String
    @ViewBuilder let accessory: () -> Accessory

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Image(systemName: icon).font(.system(size: 18))
                Spacer()
                accessory().foregroundStyle(Theme.secondary)
            }
            .padding(.bottom, 6)
            Text(title).font(.system(size: 11)).foregroundStyle(Theme.secondary)
            Text(value).font(.system(size: 14, weight: .semibold)).lineLimit(1)
        }
        .foregroundStyle(Theme.text)
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .tile(radius: 12)
        .contentShape(Rectangle())
    }
}
