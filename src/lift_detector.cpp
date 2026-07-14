#include "lift_detector.h"

LiftDetector::LiftDetector(QuadEncoder* enc, SpoolModel spoolModel, float encoderPPR)
    : encoder(enc),
      spoolModel(spoolModel),
      RAD_PER_STEP(2.0f * PI / encoderPPR),
      ALPHA(0.20f),
      MIN_REP_DURATION(350),
      isLifting(false),
      maxVelocitySession(0.0f),
      velocitySum(0.0f),
      velocitySampleCount(0),
      liftStartTime(0),
      startDistance(0.0f),
      cumulativeDistance(0.0f),
      lastRepEndTime(0),
      belowEndThresholdCount(0),
      lastTimeMicros(0),
      lastPosition(0),
      currentVelocityEMA(0.0f),
      newResultAvailable(false),
      repCount(0)
{
    MIN_LIFT_VELOCITY = 0.10f;
    END_LIFT_VELOCITY = 0.05f;
    MIN_REP_DISTANCE = 0.10f;
    lastTimeMicros = micros();
}

void LiftDetector::update(unsigned long currentMicros) {
    // Aktualizacja co 20ms (20000 mikrosekund)
    if (currentMicros - lastTimeMicros < 20000) {
        return;
    }

    float dt_s = (currentMicros - lastTimeMicros) / 1000000.0f;
    lastTimeMicros = currentMicros;

    int64_t currentPositionRaw = encoder->getCount();
    float deltaSteps = (float)(currentPositionRaw - lastPosition);
    // Konwersja kroków na dystans przez dynamiczny model rolki (zmienny
    // promień w miarę odwijania/nawijania linki) - patrz spool_model.h
    float distanceDelta = spoolModel.stepsToDistance(deltaSteps, RAD_PER_STEP);
    float rawVelocityMs = distanceDelta / dt_s;
    cumulativeDistance += distanceDelta;

    // Filtrowanie EMA (Exponential Moving Average)
    currentVelocityEMA = (ALPHA * rawVelocityMs) + ((1.0f - ALPHA) * currentVelocityEMA);

    // Outlier rejection - ignoruj fizycznie niemożliwe wartości (>5 m/s)
    if (abs(rawVelocityMs) > 5.0f) {
        lastPosition = currentPositionRaw;
        return;
    }

    if (!isLifting) {
        // Detekcja rozpoczęcia podniesienia - zablokowana na POST_REP_LOCKOUT_MS
        // po poprzednim powtórzeniu, żeby odbicie sztangi od podłoża/stojaka
        // (gwałtowny, krótki skok prędkości w górę zaraz po odłożeniu) nie
        // zostało błędnie zaliczone jako nowe powtórzenie
        bool lockoutActive = (millis() - lastRepEndTime) < POST_REP_LOCKOUT_MS;
        if (!lockoutActive && currentVelocityEMA > MIN_LIFT_VELOCITY) {
            isLifting = true;
            maxVelocitySession = 0.0f;
            velocitySum = 0.0f;
            velocitySampleCount = 0;
            belowEndThresholdCount = 0;
            liftStartTime = millis();
            startPosition = currentPositionRaw;
            Serial.println("Rozpoczęcie podniesienia");
        }
    } else {
        // Podczas podniesienia - śledzenie maksymalnej prędkości
        if (currentVelocityEMA > maxVelocitySession) {
            maxVelocitySession = currentVelocityEMA;
        }
        // Akumulacja próbek do obliczenia średniej
        if (currentVelocityEMA > 0.0f) {
            velocitySum += currentVelocityEMA;
            velocitySampleCount++;
        }

        // Detekcja zakończenia podniesienia - wymaga END_CONFIRM_SAMPLES kolejnych
        // próbek poniżej progu, żeby chwilowy spadek prędkości w sticking poincie
        // (typowe w ciężkich przysiadach) nie ucinał powtórzenia w połowie ruchu
        // i nie zaniżał sztucznie średniej prędkości
        if (currentVelocityEMA < END_LIFT_VELOCITY) {
            belowEndThresholdCount++;
        } else {
            belowEndThresholdCount = 0;
        }

        if (belowEndThresholdCount >= END_CONFIRM_SAMPLES) {
            unsigned long duration = millis() - liftStartTime;
            float liftDistance = (float)(currentPositionRaw - startPosition) / STEPS_PER_METER;

            // Walidacja podniesienia
            if (duration > MIN_REP_DURATION && liftDistance >= MIN_REP_DISTANCE) {
                // Zapisanie wyniku
                lastResult.maxVelocity = maxVelocitySession;
                lastResult.meanVelocity = (velocitySampleCount > 0) ? (velocitySum / velocitySampleCount) : maxVelocitySession;
                lastResult.distance = liftDistance;
                lastResult.timestamp = millis();
                lastResult.duration = duration;
                newResultAvailable = true;
                repCount++;

                float meanVel = (velocitySampleCount > 0) ? (velocitySum / velocitySampleCount) : maxVelocitySession;
                Serial.printf("WYNIK: peak=%.2f m/s mean=%.2f m/s (Dystans: %.3f m, Czas: %lu ms)\n",
                             maxVelocitySession, meanVel, liftDistance, duration);
            } else {
                Serial.println("Podniesienie odrzucone (za krótkie lub za mały dystans)");
            }

            isLifting = false;
            belowEndThresholdCount = 0;
            lastRepEndTime = millis();
        }
    }

    lastPosition = currentPositionRaw;
}

bool LiftDetector::hasNewResult() {
    return newResultAvailable;
}

LiftResult LiftDetector::getLastResult() {
    newResultAvailable = false;  // Reset flagi
    return lastResult;
}

float LiftDetector::getCurrentVelocity() const {
    return currentVelocityEMA;
}

bool LiftDetector::isCurrentlyLifting() const {
    return isLifting;
}

void LiftDetector::setExerciseParams(float minLiftVel, float endLiftVel, float minRepDist) {
    MIN_LIFT_VELOCITY = minLiftVel;
    END_LIFT_VELOCITY = endLiftVel;
    MIN_REP_DISTANCE = minRepDist;
    Serial.printf("Exercise params updated: minVel=%.2f, endVel=%.2f, minDist=%.2f\n", minLiftVel, endLiftVel, minRepDist);
}

uint16_t LiftDetector::getRepCount() const {
    return repCount;
}
