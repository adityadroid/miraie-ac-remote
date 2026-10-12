import Foundation

public struct Session: Sendable {
    public let accessToken: String
    public let expiresAt: Date
}

public struct Device: Sendable, Equatable {
    public let id: String
    public let name: String
    public let topic: String
}

public struct Home: Sendable {
    public let id: String
    public let devices: [Device]
}

public enum APIError: LocalizedError {
    case badLogin(String)
    case unauthorized
    case http(Int, String)
    case noDevices

    public var errorDescription: String? {
        switch self {
        case .badLogin(let m): m.isEmpty ? "Wrong mobile number or password" : m
        case .unauthorized: "Session expired"
        case .http(let code, let body): "Server error \(code): \(body.prefix(120))"
        case .noDevices: "No AC found on this MirAIe account"
        }
    }
}

/// MirAIe REST endpoints. See PROTOCOL.md.
public struct MirAIeAPI: Sendable {
    static let clientId = "PBcMcfG19njNCL8AOgvRzIC8AjQa"
    static let scope = "an_14214235325"
    static let loginURL = URL(string: "https://auth.miraie.in/simplifi/v1/userManagement/login")!
    static let homesURL = URL(string: "https://app.miraie.in/simplifi/v1/homeManagement/homes")!

    public init() {}

    public func login(username: String, password: String) async throws -> Session {
        let user = normalizeUsername(username)
        var body: [String: Any] = ["clientId": Self.clientId, "scope": Self.scope, "password": password]
        body[user.contains("@") ? "email" : "mobile"] = user

        var req = URLRequest(url: Self.loginURL)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard code == 200, let token = json["accessToken"] as? String else {
            throw APIError.badLogin(json["message"] as? String ?? "")
        }
        let expires = (json["expiresIn"] as? NSNumber)?.doubleValue ?? 3600
        return Session(accessToken: token, expiresAt: Date().addingTimeInterval(expires - 60))
    }

    public func home(_ session: Session) async throws -> Home {
        guard let homes = try await get(Self.homesURL, session) as? [[String: Any]],
              let first = homes.first, let homeId = first["homeId"].map(ACState.string)
        else { throw APIError.noDevices }

        var devices: [Device] = []
        for space in first["spaces"] as? [[String: Any]] ?? [] {
            for d in space["devices"] as? [[String: Any]] ?? [] {
                guard let id = d["deviceId"].map(ACState.string),
                      let topic = (d["topic"] as? [String])?.first else { continue }
                devices.append(Device(id: id, name: d["deviceName"] as? String ?? "AC", topic: topic))
            }
        }
        guard !devices.isEmpty else { throw APIError.noDevices }
        return Home(id: homeId, devices: devices)
    }

    public func status(_ device: Device, _ session: Session) async throws -> [String: Any] {
        let url = URL(string: "https://app.miraie.in/simplifi/v1/deviceManagement/devices/\(device.id)/mobile/status")!
        return try await get(url, session) as? [String: Any] ?? [:]
    }

    private func get(_ url: URL, _ session: Session) async throws -> Any {
        var req = URLRequest(url: url)
        req.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 401 { throw APIError.unauthorized }
        guard code == 200 else { throw APIError.http(code, String(decoding: data, as: UTF8.self)) }
        return try JSONSerialization.jsonObject(with: data)
    }
}
