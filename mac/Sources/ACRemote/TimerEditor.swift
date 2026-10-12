import SwiftUI
import MirAIe

/// Sets the AC's own off/on timers (`actm`). They run on the unit, so they fire with this app closed.
struct TimerEditor: View {
    @EnvironmentObject var store: ACStore

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Timer").font(.headline)
            TimerRow(title: "Turn off at", icon: "moon", current: store.state.offAt()) { store.setOffTimer($0) }
            HStack(spacing: 6) {
                Text("Off in").font(.system(size: 12)).foregroundStyle(Theme.secondary)
                ForEach([1, 2, 4, 8], id: \.self) { hours in
                    Button("\(hours)h") {
                        store.setOffTimer(Date().addingTimeInterval(TimeInterval(hours * 3600)))
                    }
                    .buttonStyle(ChipStyle())
                }
            }
            Divider()
            TimerRow(title: "Turn on at", icon: "sun.max", current: store.state.onAt()) { store.setOnTimer($0) }
        }
        .padding(16)
        .frame(width: 300)
        .background(Theme.background)
        .foregroundStyle(Theme.text)
    }
}

private struct TimerRow: View {
    let title: String
    let icon: String
    let current: Date?
    let set: (Date?) -> Void

    @State private var picked = Date()
    @State private var editing = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon).foregroundStyle(Theme.accent)
                Text(title)
                Spacer()
                Text(current.map { $0.formatted(date: .omitted, time: .shortened) } ?? "Not set")
                    .fontWeight(.semibold)
                    .foregroundStyle(current == nil ? Theme.secondary : Theme.text)
            }
            HStack {
                if editing {
                    DatePicker("", selection: $picked, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .datePickerStyle(.stepperField)
                    Button("Save") {
                        let parts = Calendar.current.dateComponents([.hour, .minute], from: picked)
                        set(nextOccurrence(hour: parts.hour ?? 0, minute: parts.minute ?? 0))
                        editing = false
                    }
                    .buttonStyle(ChipStyle(prominent: true))
                } else {
                    Button(current == nil ? "Set time" : "Change") {
                        picked = current ?? Date().addingTimeInterval(3600)
                        editing = true
                    }
                    .buttonStyle(ChipStyle())
                }
                Spacer()
                if current != nil {
                    Button("Clear") { set(nil) }.buttonStyle(ChipStyle())
                }
            }
        }
        .font(.system(size: 13))
    }
}

struct ChipStyle: ButtonStyle {
    var prominent = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12, weight: .medium))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(prominent ? Theme.accent : Theme.tile, in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.tileBorder))
            .foregroundStyle(prominent ? Theme.background : Theme.text)
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
