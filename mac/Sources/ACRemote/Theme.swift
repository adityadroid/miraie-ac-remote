import SwiftUI

enum Theme {
    static let background = Color(red: 0.055, green: 0.055, blue: 0.055)
    static let tile = Color(red: 0.10, green: 0.10, blue: 0.10)
    static let tileBorder = Color.white.opacity(0.06)
    static let text = Color(red: 0.93, green: 0.91, blue: 0.88)
    static let secondary = Color(red: 0.55, green: 0.53, blue: 0.50)
    static let accent = Color(red: 0.81, green: 0.78, blue: 0.71)
    static let trackFill = LinearGradient(
        colors: [Color(red: 0.24, green: 0.23, blue: 0.20), Color(red: 0.55, green: 0.52, blue: 0.45)],
        startPoint: .leading, endPoint: .trailing
    )
}

struct TileBackground: ViewModifier {
    var radius: CGFloat = 14

    func body(content: Content) -> some View {
        content
            .background(Theme.tile, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Theme.tileBorder))
    }
}

extension View {
    func tile(radius: CGFloat = 14) -> some View { modifier(TileBackground(radius: radius)) }
}

struct RoundIconButton: View {
    let systemName: String
    var size: CGFloat = 36
    var tint: Color = Theme.text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size * 0.42, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .background(Circle().fill(Theme.tile))
                .overlay(Circle().strokeBorder(Theme.tileBorder))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}
