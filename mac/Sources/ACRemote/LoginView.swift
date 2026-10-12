import SwiftUI

struct LoginView: View {
    @EnvironmentObject var store: ACStore
    @State private var username = ""
    @State private var password = ""
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "air.conditioner.horizontal")
                Text("Sign in to MirAIe").font(.system(size: 16, weight: .semibold))
            }
            Text("Use the mobile number and password from the Panasonic MirAIe app. They're kept in your keychain.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.secondary)
                .fixedSize(horizontal: false, vertical: true)

            field(TextField("Mobile number or email", text: $username))
            field(SecureField("Password", text: $password))

            if let error = store.loginError {
                Text(error).font(.system(size: 12)).foregroundStyle(.red.opacity(0.85))
            }

            HStack {
                Button("Quit") { NSApp.terminate(nil) }
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.secondary)
                Spacer()
                Button {
                    busy = true
                    Task {
                        await store.login(username: username, password: password)
                        busy = false
                    }
                } label: {
                    HStack(spacing: 6) {
                        if busy { ProgressView().controlSize(.mini) }
                        Text("Sign in").fontWeight(.semibold)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Theme.accent, in: Capsule())
                    .foregroundStyle(Theme.background)
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.defaultAction)
                .disabled(username.isEmpty || password.isEmpty || busy)
            }
        }
        .padding(20)
        .frame(width: 380)
        .background(Theme.background)
        .foregroundStyle(Theme.text)
    }

    private func field(_ content: some View) -> some View {
        content
            .textFieldStyle(.plain)
            .padding(10)
            .tile(radius: 10)
    }
}
