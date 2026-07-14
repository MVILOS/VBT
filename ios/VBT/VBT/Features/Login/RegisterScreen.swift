import SwiftUI

/// Port 1:1 z Android `RegisterScreen.kt`/`RegisterViewModel.kt`.
struct RegisterScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @Environment(\.dismiss) private var dismiss

    @State private var username = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isLoading = false
    @State private var error: String?

    var body: some View {
        ZStack {
            VbtColor.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    Text("Rejestracja")
                        .font(VbtFont.headline)
                        .foregroundStyle(VbtColor.textPrimary)
                        .padding(.top, 32)

                    TextField("", text: $username, prompt: Text("Nazwa użytkownika").foregroundStyle(VbtColor.textSecondary))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding()
                        .background(VbtColor.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(VbtColor.textPrimary)

                    SecureField("", text: $password, prompt: Text("Hasło").foregroundStyle(VbtColor.textSecondary))
                        .padding()
                        .background(VbtColor.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(VbtColor.textPrimary)

                    SecureField("", text: $confirmPassword, prompt: Text("Potwierdź hasło").foregroundStyle(VbtColor.textSecondary))
                        .padding()
                        .background(VbtColor.surfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .foregroundStyle(VbtColor.textPrimary)

                    if let error {
                        Text(error)
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.error)
                    }

                    Button {
                        Task { await register() }
                    } label: {
                        ZStack {
                            if isLoading {
                                ProgressView().tint(VbtColor.background)
                            } else {
                                Text("ZAREJESTRUJ")
                                    .font(VbtFont.bodyBold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.background)
                    .disabled(isLoading)
                }
                .padding(.horizontal, 32)
            }
        }
    }

    @MainActor
    private func register() async {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty,
              !password.isEmpty else {
            error = "Wypełnij wszystkie pola"
            return
        }
        guard password == confirmPassword else {
            error = "Hasła nie są identyczne"
            return
        }
        guard password.count >= 6 else {
            error = "Hasło musi mieć co najmniej 6 znaków"
            return
        }

        isLoading = true
        error = nil
        let result = await authRepository.register(username: username, password: password)
        isLoading = false

        switch result {
        case .success:
            dismiss()
        case .failure(let err):
            error = err.localizedDescription
        }
    }
}
