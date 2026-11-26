import Foundation
import Security

public struct ScribeFirebaseSavedSession: Codable {
    public var apiKey: String
    public var projectId: String
    public var email: String
    public var refreshToken: String
    public init(apiKey: String, projectId: String, email: String, refreshToken: String) {
        self.apiKey = apiKey; self.projectId = projectId; self.email = email; self.refreshToken = refreshToken
    }
}

public enum ScribeFirebaseSessionVault {
    private static let service = "com.mrjdm.scribe.firebase-session"
    private static let account = "firebase-refresh-token"
    public static func save(_ session: ScribeFirebaseSavedSession) throws {
        let data = try JSONEncoder().encode(session)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: account]
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let result = SecItemAdd(insert as CFDictionary, nil)
            guard result == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(result)) }
        } else if status != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }
    public static func load() -> ScribeFirebaseSavedSession? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: account,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(ScribeFirebaseSavedSession.self, from: data)
    }
    public static func clear() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: account] as CFDictionary)
    }
}

public enum ScribeFirebaseClient {
    private static func jsonRequest(_ url: URL, method: String, body: Data? = nil, token: String? = nil,
                                    contentType: String = "application/json") async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 45
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
        request.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            let raw = String(data: data, encoding: .utf8) ?? "Unknown server response"
            let detail = ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?["error"] as? [String: Any]
            let message = detail?["message"] as? String ?? raw
            throw NSError(domain: "ScribeFirebase", code: status,
                userInfo: [NSLocalizedDescriptionKey: "Firebase request failed (\(status)): \(message)"])
        }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
    }

    public static func authenticate(apiKey: String, projectId: String, email: String, password: String,
                                    createAccount: Bool) async throws -> ScribeFirebaseSavedSession {
        guard !apiKey.isEmpty, projectId.range(of: "^[a-z][a-z0-9-]{4,28}[a-z0-9]$", options: .regularExpression) != nil else {
            throw NSError(domain: "ScribeFirebase", code: 1, userInfo: [NSLocalizedDescriptionKey: "Enter the Firebase Web API key and project ID."])
        }
        guard !email.isEmpty, password.count >= 6 else {
            throw NSError(domain: "ScribeFirebase", code: 2, userInfo: [NSLocalizedDescriptionKey: "Enter an email and a password of at least six characters."])
        }
        let operation = createAccount ? "signUp" : "signInWithPassword"
        let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:\(operation)?key=\(apiKey)")!
        let body = try JSONSerialization.data(withJSONObject: ["email": email, "password": password, "returnSecureToken": true])
        let result = try await jsonRequest(url, method: "POST", body: body)
        guard let refreshToken = result["refreshToken"] as? String else {
            throw NSError(domain: "ScribeFirebase", code: 3, userInfo: [NSLocalizedDescriptionKey: "Firebase did not return a refresh token."])
        }
        return ScribeFirebaseSavedSession(apiKey: apiKey, projectId: projectId, email: email, refreshToken: refreshToken)
    }

    public static func refreshed(_ session: ScribeFirebaseSavedSession) async throws -> (String, ScribeFirebaseSavedSession) {
        var components = URLComponents(string: "https://securetoken.googleapis.com/v1/token")!
        components.queryItems = [URLQueryItem(name: "key", value: session.apiKey)]
        let body = "grant_type=refresh_token&refresh_token=" + (session.refreshToken.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")
        let result = try await jsonRequest(components.url!, method: "POST", body: body.data(using: .utf8),
                                           contentType: "application/x-www-form-urlencoded")
        guard let idToken = result["id_token"] as? String, let refreshToken = result["refresh_token"] as? String else {
            throw NSError(domain: "ScribeFirebase", code: 4, userInfo: [NSLocalizedDescriptionKey: "Firebase session refresh was incomplete."])
        }
        var next = session; next.refreshToken = refreshToken
        return (idToken, next)
    }

    public static func studyAssistant(payload: [String: Any]) async throws -> [String: Any] {
        guard let session = ScribeFirebaseSessionVault.load() else {
            throw NSError(domain: "ScribeFirebase", code: 7,
                userInfo: [NSLocalizedDescriptionKey: "Connect a Firebase account in Settings before using Scribe Intelligence."])
        }
        let (idToken, refreshedSession) = try await refreshed(session)
        try ScribeFirebaseSessionVault.save(refreshedSession)
        let url = URL(string: "https://asia-south1-\(session.projectId).cloudfunctions.net/studyAssistant")!
        let body = try JSONSerialization.data(withJSONObject: payload)
        return try await jsonRequest(url, method: "POST", body: body, token: idToken)
    }

    public static func requestLibrary(projectId: String, idToken: String, method: String,
                                       body: [String: Any]? = nil, cursor: String? = nil) async throws -> [String: Any] {
        var components = URLComponents(string: "https://asia-south1-\(projectId).cloudfunctions.net/studyLibrary")!
        if method == "GET" {
            components.queryItems = [URLQueryItem(name: "limit", value: "500")]
            if let cursor { components.queryItems?.append(URLQueryItem(name: "cursor", value: cursor)) }
        }
        let data = try body.map { try JSONSerialization.data(withJSONObject: $0) }
        return try await jsonRequest(components.url!, method: method, body: data, token: idToken)
    }
}
