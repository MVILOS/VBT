package com.vbt.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Estimate1RMUseCaseTest {

    private val useCase = Estimate1RMUseCase()

    // --- Tabela prędkość -> %1RM (González-Badillo) ---

    @Test
    fun `predkosc 0_3 odpowiada 100 procentom 1RM`() {
        assertEquals(100.0, useCase.percentOf1RM(0.3f), 0.001)
        // 100 kg przy MVT = to jest 1RM
        assertEquals(100.0, useCase.estimateFromVelocity(100f, 0.3f)!!, 0.01)
    }

    @Test
    fun `predkosc 0_5 odpowiada 90 procentom 1RM`() {
        assertEquals(90.0, useCase.percentOf1RM(0.5f), 0.001)
        // 90 kg @ 0.5 m/s -> 1RM = 90 / 0.9 = 100 kg
        assertEquals(100.0, useCase.estimateFromVelocity(90f, 0.5f)!!, 0.01)
    }

    @Test
    fun `predkosc 1_0 odpowiada 70 procentom 1RM`() {
        assertEquals(70.0, useCase.percentOf1RM(1.0f), 0.001)
        assertEquals(100.0, useCase.estimateFromVelocity(70f, 1.0f)!!, 0.01)
    }

    @Test
    fun `interpolacja liniowa miedzy punktami tabeli`() {
        // Środek odcinka 0.5-0.75: (90+80)/2 = 85%
        assertEquals(85.0, useCase.percentOf1RM(0.625f), 0.001)
        // Środek odcinka 0.3-0.5: (100+90)/2 = 95%
        assertEquals(95.0, useCase.percentOf1RM(0.4f), 0.001)
    }

    @Test
    fun `predkosci poza zakresem tabeli sa klamrowane`() {
        // Bardzo szybko -> nie mniej niż 70%
        assertEquals(70.0, useCase.percentOf1RM(1.8f), 0.001)
        // Bardzo wolno (grinder) -> maksymalnie 100%
        assertEquals(100.0, useCase.percentOf1RM(0.1f), 0.001)
    }

    @Test
    fun `nieprawidlowe dane zwracaja null`() {
        assertNull(useCase.estimateFromVelocity(0f, 0.5f))
        assertNull(useCase.estimateFromVelocity(-10f, 0.5f))
        assertNull(useCase.estimateFromVelocity(100f, 0f))
        assertNull(useCase.estimateFromVelocity(100f, -0.5f))
    }

    // --- Fallback Epley ---

    @Test
    fun `epley liczy z liczby powtorzen w serii`() {
        // 100 kg x 10 powtórzeń -> 100 * (1 + 10/30) = 133.33
        assertEquals(133.333, useCase.estimateEpley(100f, 10), 0.01)
        // Pojedyncze powtórzenie: 100 * (1 + 1/30)
        assertEquals(103.333, useCase.estimateEpley(100f, 1), 0.01)
        assertEquals(0.0, useCase.estimateEpley(100f, 0), 0.001)
    }

    @Test
    fun `estimate preferuje predkosc a spada do epleya`() {
        // Z prędkością: VBT (90 kg @ 0.5 -> 100 kg)
        assertEquals(100.0, useCase.estimate(90f, 0.5f, 5)!!, 0.01)
        // Bez prędkości: Epley z liczbą powtórzeń w serii
        assertEquals(133.333, useCase.estimate(100f, null, 10)!!, 0.01)
        // Prędkość nieprawidłowa -> też Epley
        assertEquals(133.333, useCase.estimate(100f, 0f, 10)!!, 0.01)
    }

    // --- Profil load-velocity (regresja) ---

    @Test
    fun `profil load-velocity ekstrapoluje do mvt`() {
        // Idealna liniowa relacja: v = 1.0 - 0.007 * load
        // Dla mvt = 0.3: load = (1.0 - 0.3) / 0.007 = 100 kg
        val points = listOf(50f to 0.65f, 70f to 0.51f, 90f to 0.37f)
        val result = useCase.estimateFromProfile(points, mvt = 0.3f)
        assertNotNull(result)
        assertEquals(100f, result!!, 0.5f)
    }

    @Test
    fun `profil wymaga co najmniej dwoch punktow i ujemnego nachylenia`() {
        assertNull(useCase.estimateFromProfile(listOf(100f to 0.5f), mvt = 0.3f))
        // Dodatnie nachylenie (prędkość rośnie z ciężarem) = dane bez sensu
        assertNull(useCase.estimateFromProfile(listOf(50f to 0.3f, 100f to 0.8f), mvt = 0.3f))
    }
}
