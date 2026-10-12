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
        ACRemoteApp.main()
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
