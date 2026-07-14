import Foundation
import Security

/// Bezpieczne przechowywanie tokenu JWT. Odpowiednik `PreferencesManager.AUTH_TOKEN`
/// z Androida — ale token, w przeciwieństwie do Androidowego DataStore (plaintext XML/proto),
/// tu celowo idzie do Keychain, nie do UserDefaults.
final class KeychainStore {
    private let service = "com.vbt.app.auth"
    private let account = "jwt_token"

    private func baseQuery() -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]
    }

    func saveToken(_ token: String) {
        let data = Data(token.utf8)
        var query = baseQuery()
        query[kSecValueData] = data
        query[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let update: [CFString: Any] = [kSecValueData: data]
            SecItemUpdate(baseQuery() as CFDictionary, update as CFDictionary)
        }
    }

    func readToken() -> String? {
        var query = baseQuery()
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
    }
}
