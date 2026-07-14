import SwiftUI

/// Port z Android `PlanListScreen.kt`: karty planów, coach może tworzyć/przypisywać/usuwać,
/// athlete widzi swoje i startuje trening.
struct PlanListScreen: View {
    let apiClient: APIClient

    @Environment(AuthRepository.self) private var authRepository
    @State private var viewModel: PlanListViewModel?

    var body: some View {
        Group {
            if let viewModel {
                PlanListContent(viewModel: viewModel, apiClient: apiClient)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = PlanListViewModel(apiClient: apiClient, authRepository: authRepository)
                viewModel = vm
                await vm.loadPlans()
            }
        }
        .navigationTitle("Plany")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct PlanListContent: View {
    @Bindable var viewModel: PlanListViewModel
    let apiClient: APIClient
    @State private var assigningPlan: TrainingPlanDto?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if viewModel.isCoach {
                    NavigationLink {
                        PlanEditScreen(apiClient: apiClient, planId: nil)
                    } label: {
                        Text("+ Nowy Plan")
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VbtColor.teal)
                    .foregroundStyle(VbtColor.background)
                }

                if viewModel.isLoading {
                    ProgressView().tint(VbtColor.teal).frame(maxWidth: .infinity)
                } else if viewModel.plans.isEmpty {
                    Text("Brak planów treningowych.")
                        .foregroundStyle(VbtColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 48)
                } else {
                    ForEach(viewModel.plans) { plan in
                        PlanCard(plan: plan, isCoach: viewModel.isCoach, apiClient: apiClient) {
                            assigningPlan = plan
                        } onDelete: {
                            Task { await viewModel.deletePlan(plan.id) }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(VbtColor.background)
        .refreshable { await viewModel.loadPlans() }
        .sheet(item: $assigningPlan) { plan in
            AssignPlanSheet(plan: plan, athletes: viewModel.athletes) { athleteId in
                Task {
                    await viewModel.assignPlan(plan.id, to: athleteId)
                    assigningPlan = nil
                }
            }
        }
    }
}

private struct PlanCard: View {
    let plan: TrainingPlanDto
    let isCoach: Bool
    let apiClient: APIClient
    let onAssign: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(plan.name)
                    .font(VbtFont.bodyBold)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                Text("\(plan.exercises.count) ćwiczeń")
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
            }
            if let description = plan.description, !description.isEmpty {
                Text(description)
                    .font(VbtFont.caption)
                    .foregroundStyle(VbtColor.textSecondary)
            }

            HStack {
                if isCoach {
                    NavigationLink {
                        PlanEditScreen(apiClient: apiClient, planId: plan.id)
                    } label: {
                        Text("Edytuj").font(VbtFont.caption)
                    }
                    Button("Przypisz", action: onAssign).font(VbtFont.caption)
                    Button("Usuń", role: .destructive, action: onDelete).font(VbtFont.caption)
                }
            }
        }
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct AssignPlanSheet: View {
    let plan: TrainingPlanDto
    let athletes: [UserDto]
    let onAssign: (Int) -> Void

    var body: some View {
        NavigationStack {
            List(athletes) { athlete in
                Button(athlete.username) { onAssign(athlete.id) }
            }
            .navigationTitle("Przypisz: \(plan.name)")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
