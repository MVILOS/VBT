package com.vbt.app.domain.usecase

import com.vbt.app.domain.model.VelocityZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GetVelocityZoneUseCaseTest {

    private val useCase = GetVelocityZoneUseCase()

    @Test
    fun `strefy dobierane wg progow predkosci`() {
        assertEquals(VelocityZone.ABSOLUTE_STRENGTH, useCase(0.2f))
        assertEquals(VelocityZone.STRENGTH_SPEED, useCase(0.45f))
        assertEquals(VelocityZone.POWER, useCase(0.75f))
        assertEquals(VelocityZone.SPEED_STRENGTH, useCase(1.0f))
        assertEquals(VelocityZone.SPEED, useCase(1.3f))
        assertEquals(VelocityZone.BALLISTIC, useCase(2.0f))
    }

    @Test
    fun `dolna granica strefy nalezy do tej strefy`() {
        assertEquals(VelocityZone.STRENGTH_SPEED, useCase(0.35f))
        assertEquals(VelocityZone.POWER, useCase(0.6f))
        assertEquals(VelocityZone.BALLISTIC, useCase(1.5f))
    }

    @Test
    fun `predkosc zero i ujemna wpada do absolute strength`() {
        assertEquals(VelocityZone.ABSOLUTE_STRENGTH, useCase(0f))
        assertEquals(VelocityZone.ABSOLUTE_STRENGTH, useCase(-0.1f))
    }
}
