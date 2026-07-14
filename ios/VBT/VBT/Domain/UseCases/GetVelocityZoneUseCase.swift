import Foundation

/// Port 1:1 z Android `domain/usecase/GetVelocityZoneUseCase.kt`.
struct GetVelocityZoneUseCase {
    func callAsFunction(_ velocity: Float) -> VelocityZone {
        VelocityZone.from(velocity: velocity)
    }
}
