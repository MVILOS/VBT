import Foundation

/// Dane nie-wrażliwe (rola, username, id) — odpowiednik reszty `PreferencesManager.kt`
/// poza tokenem, który idzie do `KeychainStore`. UserDefaults jest tu wystarczający,
/// bo te dane nie są sekretem.
final class UserPreferences {
    private let defaults = UserDefaults.standard

    private enum Key {
        static let userId = "vbt.user_id"
        static let username = "vbt.username"
        static let role = "vbt.user_role"
        static let coachId = "vbt.coach_id"
    }

    func save(userId: Int, username: String, role: String, coachId: Int?) {
        defaults.set(userId, forKey: Key.userId)
        defaults.set(username, forKey: Key.username)
        defaults.set(role, forKey: Key.role)
        if let coachId {
            defaults.set(coachId, forKey: Key.coachId)
        }
    }

    var userId: Int? {
        let value = defaults.integer(forKey: Key.userId)
        return defaults.object(forKey: Key.userId) != nil ? value : nil
    }

    var username: String? { defaults.string(forKey: Key.username) }
    var role: String? { defaults.string(forKey: Key.role) }

    var coachId: Int? {
        let value = defaults.integer(forKey: Key.coachId)
        return defaults.object(forKey: Key.coachId) != nil ? value : nil
    }

    func clear() {
        defaults.removeObject(forKey: Key.userId)
        defaults.removeObject(forKey: Key.username)
        defaults.removeObject(forKey: Key.role)
        defaults.removeObject(forKey: Key.coachId)
    }
}
