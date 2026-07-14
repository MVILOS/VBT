import SwiftUI

/// Port (uproszczony - bez wykresu prędkości live-trace, patrz `IOS_PORT_PLAN.md`) z
/// Android `SessionDetailScreen.kt`: tabela rep-by-rep (set | rep | kg | mean vel | peak vel
/// | 1RM est.), edycja ciężaru serii, usuwanie pojedynczego rep, scalanie serii.
struct SessionDetailScreen: View {
    let apiClient: APIClient
    let sessionId: Int

    @State private var viewModel: SessionDetailViewModel?

    var body: some View {
        Group {
            if let viewModel {
                SessionDetailContent(viewModel: viewModel)
            } else {
                VbtColor.background.ignoresSafeArea()
            }
        }
        .task {
            if viewModel == nil {
                let vm = SessionDetailViewModel(apiClient: apiClient, sessionId: sessionId)
                viewModel = vm
                await vm.loadSession()
            }
        }
        .navigationTitle("Szczegóły sesji")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SessionDetailContent: View {
    @Bindable var viewModel: SessionDetailViewModel
    @State private var editingSetNumber: Int?
    @State private var editLoadKg = ""

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView().tint(VbtColor.teal)
            } else if let session = viewModel.session {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(String(session.startedAt.prefix(16)).replacingOccurrences(of: "T", with: " "))
                            .font(VbtFont.body)
                            .foregroundStyle(VbtColor.textSecondary)

                        ForEach(setNumbers(in: session), id: \.self) { setNumber in
                            setCard(setNumber: setNumber, session: session)
                        }
                    }
                    .padding(16)
                }
            } else {
                Text(viewModel.error ?? "Brak danych")
                    .foregroundStyle(VbtColor.textSecondary)
            }
        }
        .background(VbtColor.background)
        .overlay {
            if viewModel.isSaving {
                ProgressView().tint(VbtColor.teal)
            }
        }
        .alert("Edytuj ciężar serii", isPresented: Binding(get: { editingSetNumber != nil }, set: { if !$0 { editingSetNumber = nil } })) {
            TextField("kg", text: $editLoadKg)
                .keyboardType(.decimalPad)
            Button("Zapisz") {
                if let setNumber = editingSetNumber, let kg = Double(editLoadKg) {
                    Task { await viewModel.updateSetWeight(setNumber: setNumber, newLoadKg: kg) }
                }
                editingSetNumber = nil
            }
            Button("Anuluj", role: .cancel) { editingSetNumber = nil }
        }
    }

    private func setNumbers(in session: WorkoutSessionDto) -> [Int] {
        Array(Set((session.reps ?? []).map(\.setNumber))).sorted()
    }

    @ViewBuilder
    private func setCard(setNumber: Int, session: WorkoutSessionDto) -> some View {
        let reps = (session.reps ?? []).filter { $0.setNumber == setNumber }.sorted { $0.repNumber < $1.repNumber }
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Seria \(setNumber)")
                    .font(VbtFont.bodyBold)
                    .foregroundStyle(VbtColor.textPrimary)
                Spacer()
                Button("Edytuj kg") {
                    editingSetNumber = setNumber
                    editLoadKg = reps.first.map { String($0.loadKg) } ?? ""
                }
                .font(VbtFont.caption)
                if setNumber > 1 {
                    Button("Scal z poprzednią") {
                        Task { await viewModel.mergeSetWithPrevious(setNumber: setNumber) }
                    }
                    .font(VbtFont.caption)
                }
            }

            ForEach(reps, id: \.repNumber) { rep in
                HStack {
                    Text("#\(rep.repNumber)")
                        .foregroundStyle(VbtColor.textSecondary)
                        .frame(width: 32, alignment: .leading)
                    Text(String(format: "%.1f kg", rep.loadKg))
                    Spacer()
                    Text(String(format: "mean %.2f", rep.meanVelocity))
                    Spacer()
                    Text(String(format: "peak %.2f", rep.peakVelocity))
                        .foregroundStyle(VbtColor.teal)
                    Spacer()
                    if let rm = rep.estimated1rm {
                        Text(String(format: "1RM %.0f", rm))
                            .foregroundStyle(VbtColor.purple)
                    }
                    if let repId = rep.id {
                        Button {
                            Task { await viewModel.deleteRep(repId) }
                        } label: {
                            Image(systemName: "trash").foregroundStyle(VbtColor.error)
                        }
                    }
                }
                .font(VbtFont.caption)
                .foregroundStyle(VbtColor.textPrimary)
            }
        }
        .padding(12)
        .background(VbtColor.surfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
