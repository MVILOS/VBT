import SwiftUI

/// Port (kalendarz jako lista, nie grid - patrz `AthleteProfileViewModel`) z Android
/// `AthleteProfileScreen.kt`: taby Kalendarz / Plany / Sesje.
struct AthleteProfileScreen: View {
    let apiClient: APIClient
    let athleteId: Int
    let athleteName: String

    @State private var viewModel: AthleteProfileViewModel?

    var body: some View {
        Group {
            if let viewModel {
                AthleteProfileContent(viewModel: viewModel, apiClient: apiClient)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = AthleteProfileViewModel(apiClient: apiClient, athleteId: athleteId)
                viewModel = vm
                await vm.loadAll()
            }
        }
        .navigationTitle(athleteName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct AthleteProfileContent: View {
    @Bindable var viewModel: AthleteProfileViewModel
    let apiClient: APIClient
    @State private var showAddEntry = false
    @State private var showAssignPlan = false

    var body: some View {
        VStack(spacing: 0) {
            Picker("Tab", selection: $viewModel.selectedTab) {
                Text("Kalendarz").tag(AthleteProfileTab.calendar)
                Text("Plany").tag(AthleteProfileTab.plans)
                Text("Sesje").tag(AthleteProfileTab.sessions)
            }
            .pickerStyle(.segmented)
            .padding(12)

            if viewModel.isLoading {
                ProgressView().tint(VbtColor.teal)
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        switch viewModel.selectedTab {
                        case .calendar: calendarTab
                        case .plans: plansTab
                        case .sessions: sessionsTab
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(VbtColor.background)
        .sheet(isPresented: $showAddEntry) {
            AddCalendarEntrySheet(plans: viewModel.allPlans) { date, title, planId, timeSlot, notes in
                Task {
                    await viewModel.addCalendarEntry(date: date, title: title, planId: planId, timeSlot: timeSlot, notes: notes)
                    showAddEntry = false
                }
            }
        }
        .sheet(isPresented: $showAssignPlan) {
            NavigationStack {
                List(viewModel.allPlans) { plan in
                    Button(plan.name) {
                        Task {
                            await viewModel.assignPlan(plan.id)
                            showAssignPlan = false
                        }
                    }
                }
                .navigationTitle("Przypisz plan")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    private var calendarTab: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button("+ Dodaj wpis") { showAddEntry = true }
                .buttonStyle(.borderedProminent)
                .tint(VbtColor.teal)
                .foregroundStyle(VbtColor.background)

            if viewModel.calendarEntries.isEmpty {
                Text("Brak zaplanowanych treningów.")
                    .foregroundStyle(VbtColor.textSecondary)
            }
            ForEach(viewModel.calendarEntries) { entry in
                HStack {
                    VStack(alignment: .leading) {
                        Text(entry.title).foregroundStyle(VbtColor.textPrimary)
                        Text("\(entry.date)\(entry.timeSlot.map { " · \($0)" } ?? "")")
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.textSecondary)
                    }
                    Spacer()
                    Button {
                        Task { await viewModel.deleteCalendarEntry(entry.id) }
                    } label: {
                        Image(systemName: "trash").foregroundStyle(VbtColor.error)
                    }
                }
                .padding(12)
                .background(VbtColor.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private var plansTab: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button("+ Przypisz plan") { showAssignPlan = true }
                .buttonStyle(.borderedProminent)
                .tint(VbtColor.teal)
                .foregroundStyle(VbtColor.background)

            if viewModel.assignedPlans.isEmpty {
                Text("Brak przypisanych planów.")
                    .foregroundStyle(VbtColor.textSecondary)
            }
            ForEach(viewModel.assignedPlans) { plan in
                Text(plan.name)
                    .foregroundStyle(VbtColor.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(VbtColor.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private var sessionsTab: some View {
        VStack(alignment: .leading, spacing: 8) {
            if viewModel.sessions.isEmpty {
                Text("Brak sesji.")
                    .foregroundStyle(VbtColor.textSecondary)
            }
            ForEach(viewModel.sessions) { session in
                NavigationLink {
                    SessionDetailScreen(apiClient: apiClient, sessionId: session.id)
                } label: {
                    HStack {
                        Text(String(session.startedAt.prefix(10))).foregroundStyle(VbtColor.textPrimary)
                        Spacer()
                        Text("\(session.reps?.count ?? 0) reps").foregroundStyle(VbtColor.teal)
                    }
                    .padding(12)
                    .background(VbtColor.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct AddCalendarEntrySheet: View {
    let plans: [TrainingPlanDto]
    let onAdd: (String, String, Int?, String, String) -> Void

    @State private var date = ""
    @State private var title = ""
    @State private var planId: Int?
    @State private var timeSlot = ""
    @State private var notes = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Data (RRRR-MM-DD)", text: $date)
                TextField("Tytuł", text: $title)
                TextField("Godzina (opcjonalnie)", text: $timeSlot)
                Picker("Plan", selection: $planId) {
                    Text("Brak").tag(Int?.none)
                    ForEach(plans) { plan in
                        Text(plan.name).tag(Int?.some(plan.id))
                    }
                }
                TextField("Notatki", text: $notes)
            }
            .navigationTitle("Nowy wpis")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Anuluj") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Dodaj") { onAdd(date, title, planId, timeSlot, notes) }
                        .disabled(date.isEmpty || title.isEmpty)
                }
            }
        }
    }
}
