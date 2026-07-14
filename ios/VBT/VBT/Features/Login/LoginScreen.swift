import SwiftUI

/// Port 1:1 z Android `LoginScreen.kt` — dark tło, pola username/password, przycisk
/// "Zaloguj" -> POST /auth/login -> zapis tokenu + roli -> nawigacja do Home (obsłużona
/// przez `RootView`, które przełącza widok na podstawie `AuthRepository.isLoggedIn`).
struct LoginScreen: View {
    @Environment(AuthRepository.self) private var authRepository
    @State private var viewModel: LoginViewModel?
    @State private var showRegister = false

    var body: some View {
        content
            .onAppear {
                if viewModel == nil {
                    viewModel = LoginViewModel(authRepository: authRepository)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let viewModel {
            LoginForm(viewModel: viewModel, showRegister: $showRegister)
        } else {
            VbtColor.background.ignoresSafeArea()
        }
    }
}

private struct LoginForm: View {
    @Bindable var viewModel: LoginViewModel
    @Binding var showRegister: Bool

    var body: some View {
        ZStack {
            VbtColor.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    Spacer(minLength: 80)

                    Text("VBT")
                        .font(.system(size: 44, weight: .heavy, design: .rounded))
                        .foregroundStyle(VbtColor.teal)

                    Text("Velocity Based Training")
                        .font(VbtFont.body)
                        .foregroundStyle(VbtColor.textSecondary)

                    Spacer(minLength: 48)

                    VStack(spacing: 16) {
                        TextField("", text: $viewModel.username, prompt: Text("Nazwa użytkownika").foregroundStyle(VbtColor.textSecondary))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding()
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .foregroundStyle(VbtColor.textPrimary)

                        SecureField("", text: $viewModel.password, prompt: Text("Hasło").foregroundStyle(VbtColor.textSecondary))
                            .padding()
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .foregroundStyle(VbtColor.textPrimary)
                    }

                    if let error = viewModel.error {
                        Text(error)
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.error)
                    }

                    Button {
                        Task { _ = await viewModel.login() }
                    } label: {
                        ZStack {
                            if viewModel.isLoading {
                                ProgressView().tint(VbtColor.background)
                            } else {
                                Text("ZALOGUJ")
                                    .font(VbtFont.bodyBold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.background)
                    .disabled(viewModel.isLoading)

                    Button("Nie masz konta? Zarejestruj się") {
                        showRegister = true
                    }
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
                    .padding(.top, 8)
                }
                .padding(.horizontal, 32)
            }
        }
        .sheet(isPresented: $showRegister) {
            RegisterScreen()
        }
    }
}
