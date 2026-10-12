import SwiftUI

/// Wall-mounted split unit with animated airflow while running.
struct ACIllustration: View {
    let running: Bool
    let setTemp: Int
    let mode: String

    var body: some View {
        ZStack(alignment: .top) {
            if running {
                Airflow(mode: mode)
                    .frame(width: 170, height: 70)
                    .offset(y: 44)
                    .transition(.opacity)
            }
            unit
        }
        .frame(height: 120, alignment: .top)
        .animation(.easeInOut(duration: 0.6), value: running)
    }

    private var unit: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(LinearGradient(
                    colors: [.white, Color(white: 0.90), Color(white: 0.80)],
                    startPoint: .top, endPoint: .bottom
                ))
                .shadow(color: .black.opacity(0.6), radius: 10, y: 6)

            // Side panel shading for depth
            HStack {
                Spacer()
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(LinearGradient(colors: [Color(white: 0.85), Color(white: 0.72)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: 10)
            }

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Text(running ? "\(setTemp)" : "")
                        .font(.system(size: 6, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color(red: 0.2, green: 0.55, blue: 0.95).opacity(0.8))
                        .padding(.trailing, 22)
                        .padding(.top, 8)
                }
                Spacer()
                // Vent
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color(white: 0.18))
                    .frame(height: 6)
                    .overlay(
                        HStack(spacing: 3) {
                            ForEach(0..<24, id: \.self) { _ in
                                Rectangle().fill(Color(white: 0.32)).frame(width: 1)
                            }
                        }
                        .padding(.horizontal, 4)
                    )
                    .padding(.horizontal, 8)
                    .padding(.bottom, 5)
            }
        }
        .frame(width: 150, height: 46)
    }
}

/// Soft streaks drifting down from the vent.
private struct Airflow: View {
    let mode: String

    private var tint: Color {
        switch mode {
        case "heat": Color(red: 1.0, green: 0.75, blue: 0.55)
        case "cool": Color(red: 0.85, green: 0.92, blue: 1.0)
        default: .white
        }
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            Canvas { ctx, size in
                let streaks = 26
                for i in 0..<streaks {
                    let seed = Double(i) * 12.9898
                    let x0 = size.width * (0.08 + 0.84 * Double(i) / Double(streaks - 1))
                    let speed = 0.45 + 0.25 * abs(sin(seed))
                    let phase = (t * speed + abs(sin(seed * 3.1))).truncatingRemainder(dividingBy: 1)
                    let length = size.height * (0.55 + 0.35 * abs(cos(seed)))
                    let top = size.height * phase * 0.5
                    // Streaks bend outward toward the edges, like air leaving the vanes.
                    let spread = (x0 - size.width / 2) * 0.35
                    var path = Path()
                    path.move(to: CGPoint(x: x0, y: top))
                    path.addQuadCurve(
                        to: CGPoint(x: x0 + spread - 10, y: top + length),
                        control: CGPoint(x: x0 + spread * 0.2, y: top + length * 0.6)
                    )
                    let fade = sin(phase * .pi)
                    ctx.stroke(
                        path,
                        with: .linearGradient(
                            Gradient(colors: [tint.opacity(0.35 * fade), tint.opacity(0)]),
                            startPoint: CGPoint(x: x0, y: top),
                            endPoint: CGPoint(x: x0, y: top + length)
                        ),
                        lineWidth: 3
                    )
                }
            }
            .blur(radius: 2.5)
        }
        .mask(LinearGradient(colors: [.black, .black.opacity(0)], startPoint: .top, endPoint: .bottom))
    }
}
