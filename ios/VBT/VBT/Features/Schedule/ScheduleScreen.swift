import SwiftUI

/// Port (tydzień jako pionowa lista dni, nie poziomy grid jak w Androidzie/Webie) z
/// Android `ScheduleScreen.kt`: nawigacja tydzień wprzód/wstecz, filtr po zawodniku (coach).
struct ScheduleScreen: View {
    let apiClient: APIClient
    var onStartWorkout: ((Int?, Int, Int) -> Void)? = nil

    @Environment(AuthRepository.self) private var authRepository
    @State private var viewModel: ScheduleViewModel?

    var body: some View {
        Group {
            if let viewModel {
                ScheduleContent(viewModel: viewModel)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = ScheduleViewModel(apiClient: apiClient, authRepository: authRepository)
                viewModel = vm
                await vm.loadData()
            }
        }
        .navigationTitle("Harmonogram")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ScheduleContent: View {
    @Bindable var viewModel: ScheduleViewModel
    @State private var addEntryDay: Date?

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "EEEE, d MMM"
        f.locale = Locale(identifier: "pl_PL")
        return f
    }()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { viewModel.prevWeek(); Task { await viewModel.loadData() } } label: {
                    Image(systemName: "chevron.left")
                }
                Spacer()
                Button("Dziś") { viewModel.goToToday(); Task { await viewModel.loadData() } }
                    .font(VbtFont.caption)
                Spacer()
                Button { viewModel.nextWeek(); Task { await viewModel.loadData() } } label: {
                    Image(systemName: "chevron.right")
                }
            }
            .padding(12)
            .foregroundStyle(VbtColor.teal)

            if viewModel.isCoach && !viewModel.athletes.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip(label: "Wszyscy", isSelected: viewModel.filterAthleteId == nil) { viewModel.filterAthleteId = nil }
                        ForEach(viewModel.athletes) { athlete in
                            chip(label: athlete.username, isSelected: viewModel.filterAthleteId == athlete.id) {
                                viewModel.filterAthleteId = athlete.id
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                }
            }

            if viewModel.isLoading {
                ProgressView().tint(VbtColor.teal)
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(viewModel.weekDays, id: \.self) { day in
                            dayCard(day)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(VbtColor.background)
        .sheet(item: $addEntryDay) { day in
            AddScheduleEntrySheet(day: day, isCoach: viewModel.isCoach, athletes: viewModel.athletes, plans: viewModel.plans) { athleteId, planId, title, timeSlot, notes in
                Task {
                    await viewModel.addEntry(date: day, athleteId: athleteId, planId: planId, title: title, timeSlot: timeSlot, notes: notes)
                    addEntryDay = nil
                }
            }
        }
    }

    private func chip(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(VbtFont.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? VbtColor.teal : VbtColor.surfaceVariant)
                .foregroundStyle(isSelected ? VbtColor.background : VbtColor.textPrimary)
                .clipShape(Capsule())
        }
    }

    private func dayCard(_ day: Date) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(Self.dayFormatter.string(from: day))
                    .font(VbtFont.bodyBold)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                Button {
                    addEntryDay = day
                } label: {
                    Image(systemName: "plus.circle").foregroundStyle(VbtColor.teal)
                }
            }
            let dayEntries = viewModel.entries(for: day)
            if dayEntries.isEmpty {
                Text("Brak treningów")
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
            }
            ForEach(dayEntries) { entry in
                HStack {
                    VStack(alignment: .leading) {
                        Text(entry.title).foregroundStyle(VbtColor.textPrimary)
                        Text("\(entry.timeSlot ?? "") · \(entry.status)")
                            .font(VbtFont.caption)
                            .foregroundStyle(VbtColor.textSecondary)
                    }
                    Spacer()
                    Button {
                        Task { await viewModel.deleteEntry(entry.id) }
                    } label: {
                        Image(systemName: "trash").foregroundStyle(VbtColor.error)
                    }
                }
                .font(VbtFont.caption)
                .padding(8)
                .background(VbtColor.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }
        }
        .padding(12)
        .background(VbtColor.surface)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

extension Date: Identifiable {
    public var id: TimeInterval { timeIntervalSinceReferenceDate }
}

private struct AddScheduleEntrySheet: View {
    let day: Date
    let isCoach: Bool
    let athletes: [UserDto]
    let plans: [TrainingPlanDto]
    let onAdd: (Int?, Int?, String, String, String) -> Void

    @State private var athleteId: Int?
    @State private var planId: Int?
    @State private var title = ""
    @State private var timeSlot = ""
    @State private var notes = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if isCoach {
                    Picker("Zawodnik", selection: $athleteId) {
                        Text("Ja").tag(Int?.none)
                        ForEach(athletes) { athlete in
                            Text(athlete.username).tag(Int?.some(athlete.id))
                        }
                    }
                }
                TextField("Tytuł", text: $title)
                TextField("Godzina", text: $timeSlot)
                Picker("Plan", selection: $planId) {
                    Text("Brak").tag(Int?.none)
                    ForEach(plans) { plan in
                        Text(plan.name).tag(Int?.some(plan.id))
                    }
                }
                TextField("Notatki", text: $notes)
            }
            .navigationTitle("Nowy trening")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Anuluj") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Dodaj") { onAdd(athleteId, planId, title, timeSlot, notes) }
                        .disabled(title.isEmpty)
                }
            }
        }
    }
}
