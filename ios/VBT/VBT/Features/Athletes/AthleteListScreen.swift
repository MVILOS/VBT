import SwiftUI

/// Port z Android `AthleteListScreen.kt`: lista zawodników, tworzenie nowego konta,
/// przypisanie istniejącego użytkownika po username.
struct AthleteListScreen: View {
    let apiClient: APIClient
    @State private var viewModel: AthleteListViewModel?

    var body: some View {
        Group {
            if let viewModel {
                AthleteListContent(viewModel: viewModel, apiClient: apiClient)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = AthleteListViewModel(apiClient: apiClient)
                viewModel = vm
                await vm.loadAthletes()
            }
        }
        .navigationTitle("Zawodnicy")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AthleteListContent: View {
    @Bindable var viewModel: AthleteListViewModel
    let apiClient: APIClient
    @State private var showCreateSheet = false
    @State private var showAssignSheet = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Button("+ Nowy zawodnik") { showCreateSheet = true }
                        .buttonStyle(.borderedProminent)
                        .tint(VbtColor.teal)
                        .foregroundStyle(VbtColor.background)
                    Button("Przypisz istniejącego") { showAssignSheet = true }
                        .buttonStyle(.bordered)
                }

                if viewModel.isLoading {
                    ProgressView().tint(VbtColor.teal).frame(maxWidth: .infinity)
                } else if viewModel.athletes.isEmpty {
                    Text("Brak przypisanych zawodników.")
                        .foregroundStyle(VbtColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 48)
                } else {
                    ForEach(viewModel.athletes) { athlete in
                        NavigationLink {
                            AthleteProfileScreen(apiClient: apiClient, athleteId: athlete.id, athleteName: athlete.username)
                        } label: {
                            HStack {
                                Circle()
                                    .fill(VbtColor.teal.opacity(0.2))
                                    .frame(width: 40, height: 40)
                                    .overlay(Text(String(athlete.username.prefix(1)).uppercased()).foregroundStyle(VbtColor.teal))
                                VStack(alignment: .leading) {
                                    Text(athlete.username).foregroundStyle(VbtColor.textPrimary)
                                    if let email = athlete.email {
                                        Text(email).font(VbtFont.caption).foregroundStyle(VbtColor.textSecondary)
                                    }
                                }
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(VbtColor.textSecondary)
                            }
                            .padding(12)
                            .background(VbtColor.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .background(VbtColor.background)
        .refreshable { await viewModel.loadAthletes() }
        .sheet(isPresented: $showCreateSheet) {
            CreateAthleteSheet { username, email, password in
                Task {
                    if await viewModel.createAthlete(username: username, email: email, password: password) {
                        showCreateSheet = false
                    }
                }
            }
        }
        .sheet(isPresented: $showAssignSheet) {
            AssignByUsernameSheet { username in
                Task {
                    if await viewModel.assignByUsername(username) {
                        showAssignSheet = false
                    }
                }
            }
        }
        .alert("Błąd", isPresented: Binding(get: { viewModel.error != nil }, set: { if !$0 { viewModel.error = nil } })) {
            Button("OK", role: .cancel) { viewModel.error = nil }
        } message: {
            Text(viewModel.error ?? "")
        }
    }
}

private struct CreateAthleteSheet: View {
    let onCreate: (String, String, String) -> Void
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Nazwa użytkownika", text: $username).textInputAutocapitalization(.never)
                TextField("Email (opcjonalnie)", text: $email).textInputAutocapitalization(.never)
                SecureField("Hasło", text: $password)
            }
            .navigationTitle("Nowy zawodnik")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Anuluj") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Utwórz") { onCreate(username, email, password) }
                        .disabled(username.isEmpty || password.isEmpty)
                }
            }
        }
    }
}

private struct AssignByUsernameSheet: View {
    let onAssign: (String) -> Void
    @State private var username = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Nazwa użytkownika", text: $username).textInputAutocapitalization(.never)
            }
            .navigationTitle("Przypisz zawodnika")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Anuluj") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Przypisz") { onAssign(username) }
                        .disabled(username.isEmpty)
                }
            }
        }
    }
}
