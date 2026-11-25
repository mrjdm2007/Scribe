import Foundation
import Security

public struct ScribeSavedCloudSession: Codable {
    public var url: String
    public var anonKey: String
    public var email: String
    public var refreshToken: String

    public init(url: String, anonKey: String, email: String, refreshToken: String) {
        self.url = url
        self.anonKey = anonKey
        self.email = email
        self.refreshToken = refreshToken
    }
}

public enum ScribeCloudSessionVault {
    private static let service = "com.mrjdm.scribe.cloud-session"
    private static let account = "supabase-refresh-session"

    public static func save(_ session: ScribeSavedCloudSession) throws {
        let data = try JSONEncoder().encode(session)
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let update = SecItemUpdate(base as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if update == errSecItemNotFound {
            var insert = base
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let status = SecItemAdd(insert as CFDictionary, nil)
            guard status == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
            }
        } else if update != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(update))
        }
    }

    public static func load() -> ScribeSavedCloudSession? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(ScribeSavedCloudSession.self, from: data)
    }

    public static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
