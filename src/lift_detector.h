#ifndef LIFT_DETECTOR_H
#define LIFT_DETECTOR_H

#include <Arduino.h>
#include "quad_encoder.h"
#include "spool_model.h"

// Struktura przechowująca wynik pojedynczego podniesienia
struct LiftResult {
    float maxVelocity;      // Maksymalna prędkość (peak) w m/s
    float meanVelocity;     // Średnia prędkość w m/s
    float distance;         // Przebyta odległość w m
    unsigned long timestamp; // Timestamp (millis od bootu)
    uint16_t duration;      // Czas trwania podniesienia w ms
};

class LiftDetector {
private:
    QuadEncoder* encoder;
    SpoolModel spoolModel;

    // Parametry konfiguracyjne
    const float RAD_PER_STEP;
    const float ALPHA;
    float MIN_LIFT_VELOCITY;
    float END_LIFT_VELOCITY;
    const unsigned long MIN_REP_DURATION;
    float MIN_REP_DISTANCE;

    // Okres blokady po zakończeniu powtórzenia - ignoruje odbicie sztangi
    // od podłoża/stojaka (fałszywe wykrycie nowego powtórzenia)
    static const unsigned long POST_REP_LOCKOUT_MS = 600;
    // Liczba kolejnych próbek poniżej END_LIFT_VELOCITY wymagana do
    // faktycznego zakończenia powtórzenia - chroni przed przedwczesnym
    // ucięciem przy sticking poincie (typowe w przysiadach)
    static const uint8_t END_CONFIRM_SAMPLES = 3;

    // Zmienne stanu
    bool isLifting;
    float maxVelocitySession;
    float velocitySum;
    uint16_t velocitySampleCount;
    unsigned long liftStartTime;
    float startDistance;
    float cumulativeDistance;
    unsigned long lastRepEndTime;
    uint8_t belowEndThresholdCount;

    // Zmienne pomiaru
    unsigned long lastTimeMicros;
    int64_t lastPosition;
    float currentVelocityEMA;

    // Flaga nowego wyniku
    bool newResultAvailable;
    LiftResult lastResult;
    uint16_t repCount;

public:
    LiftDetector(QuadEncoder* enc, SpoolModel spoolModel, float encoderPPR);

    // Główna metoda aktualizacji - wywoływana w loop()
    void update(unsigned long currentMicros);

    // Sprawdzenie czy jest nowy wynik
    bool hasNewResult();

    // Pobranie ostatniego wyniku i zresetowanie flagi
    LiftResult getLastResult();

    // Pobranie aktualnej prędkości (dla live display)
    float getCurrentVelocity() const;

    // Sprawdzenie czy trwa podniesienie
    bool isCurrentlyLifting() const;

    void setExerciseParams(float minLiftVel, float endLiftVel, float minRepDist);
    uint16_t getRepCount() const;
};

#endif
