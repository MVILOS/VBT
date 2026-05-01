package com.vbt.app.domain.usecase

import com.vbt.app.domain.model.VelocityZone
import javax.inject.Inject

class GetVelocityZoneUseCase @Inject constructor() {
    operator fun invoke(velocity: Float): VelocityZone {
        return VelocityZone.fromVelocity(velocity)
    }
}
