import Foundation

/// Odpowiednik `di/NetworkModule.kt` (OkHttp + Retrofit + Gson) — pojedynczy klient HTTP
/// oparty o URLSession/async-await. Dodaje nagłówek JWT z Keychain i obsługuje wygaśnięcie
/// sesji (401 poza /auth/*) tak samo jak interceptory po stronie Androida.
final class APIClient {
    // Backend jest za nginx, który wymusza HTTPS; bezpośrednie :8000 pomija nginx.
    private static let baseURL = URL(string: "https://130.61.232.212/api/")!

    private let session: URLSession
    private let keychain: KeychainStore
    private let sessionExpiredNotifier: SessionExpiredNotifier
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(
        keychain: KeychainStore,
        sessionExpiredNotifier: SessionExpiredNotifier,
        session: URLSession = .shared
    ) {
        self.keychain = keychain
        self.sessionExpiredNotifier = sessionExpiredNotifier
        self.session = session
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    /// Żądanie zwracające zdekodowaną odpowiedź.
    func send<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        let data = try await sendRaw(endpoint)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding(error)
        }
    }

    /// Żądanie bez treści odpowiedzi (np. DELETE -> 204/200 bez body).
    /// Nazwa celowo różna od `send<T:Decodable>` — przeciążenie po samym typie zwracanym
    /// (`Void` vs generyk) jest niejednoznaczne dla kompilatora, gdy wynik jest odrzucany.
    func sendNoContent(_ endpoint: Endpoint) async throws {
        _ = try await sendRaw(endpoint)
    }

    @discardableResult
    private func sendRaw(_ endpoint: Endpoint) async throws -> Data {
        var components = URLComponents(url: Self.baseURL.appendingPathComponent(endpoint.path), resolvingAgainstBaseURL: false)!
        let queryItems = endpoint.query.compactMap { key, value -> URLQueryItem? in
            guard let value else { return nil }
            return URLQueryItem(name: key, value: value)
        }
        if !queryItems.isEmpty { components.queryItems = queryItems }

        var request = URLRequest(url: components.url!)
        request.httpMethod = endpoint.method.rawValue
        request.httpBody = endpoint.body
        if endpoint.body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        if let token = keychain.readToken(), !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.transport(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        let isAuthEndpoint = endpoint.path.contains("auth/login") || endpoint.path.contains("auth/register")
        if http.statusCode == 401 && !isAuthEndpoint {
            keychain.clear()
            sessionExpiredNotifier.notifyExpired()
            throw APIError.unauthorized
        }

        guard (200..<300).contains(http.statusCode) else {
            throw APIError.http(status: http.statusCode, body: String(data: data, encoding: .utf8))
        }

        return data
    }
}
