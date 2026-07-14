import Foundation

enum APIError: Error, LocalizedError {
    case invalidResponse
    case unauthorized
    case http(status: Int, body: String?)
    case decoding(Error)
    case transport(Error)

    var errorDescription: String? {
        switch self {
        case .invalidResponse: return "Nieprawidłowa odpowiedź serwera."
        case .unauthorized: return "Sesja wygasła — zaloguj się ponownie."
        case .http(let status, let body): return "Błąd serwera (\(status)): \(body ?? "-")"
        case .decoding: return "Nie udało się odczytać odpowiedzi serwera."
        case .transport(let error): return error.localizedDescription
        }
    }
}
