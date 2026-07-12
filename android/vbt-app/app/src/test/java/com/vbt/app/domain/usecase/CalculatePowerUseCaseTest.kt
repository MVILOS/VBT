package com.vbt.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatePowerUseCaseTest {

    private val useCase = CalculatePowerUseCase()

    @Test
    fun `moc szczytowa to sila razy predkosc szczytowa`() {
        // 100 kg * 9.81 * 1.0 m/s = 981 W
        assertEquals(981f, useCase.calculatePeakPower(100f, 1.0f), 0.01f)
        // 60 kg * 9.81 * 0.5 m/s = 294.3 W
        assertEquals(294.3f, useCase.calculatePeakPower(60f, 0.5f), 0.01f)
    }

    @Test
    fun `moc srednia liczona z dystansu i czasu`() {
        // 100 kg, 0.5 m w 1000 ms -> v = 0.5 m/s -> 100*9.81*0.5 = 490.5 W
        assertEquals(490.5f, useCase.calculateMeanPower(100f, 0.5f, 1000), 0.01f)
        // Dwa razy krótszy czas -> dwa razy większa moc
        assertEquals(981f, useCase.calculateMeanPower(100f, 0.5f, 500), 0.01f)
    }

    @Test
    fun `zerowy lub ujemny czas trwania daje zero mocy`() {
        assertEquals(0f, useCase.calculateMeanPower(100f, 0.5f, 0), 0.0f)
        assertEquals(0f, useCase.calculateMeanPower(100f, 0.5f, -100), 0.0f)
    }
}
