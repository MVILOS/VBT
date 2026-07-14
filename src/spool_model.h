#ifndef SPOOL_MODEL_H
#define SPOOL_MODEL_H

#include <Arduino.h>

// Model geometrii nawijania linki na rolkę. Linka nawija się warstwa na
// warstwę (jak taśma na szpuli), więc promień rośnie wraz z aktualnie
// nawiniętą długością L wg: r(L) = sqrt(r0^2 + L*d/pi), gdzie r0 to promień
// samego (pustego) rdzenia, a d to grubość linki. Stała, uśredniona średnica
// (poprzednie podejście) fałszowała dystans/prędkość w miarę odwijania się
// linki w trakcie powtórzenia - ten model odtwarza rzeczywistą, zmienną
// średnicę na podstawie fizycznych wymiarów rolki.
//
// Zakłada, że urządzenie startuje w pozycji spoczynkowej (linka w pełni
// nawinięta, sztanga na dole) - stąd woundLength_ inicjalizowane na
// maxWoundLength_ w konstruktorze.
class SpoolModel {
public:
    SpoolModel(float coreRadius_m, float cordThickness_m, float maxRadius_m)
        : coreRadius_(coreRadius_m),
          cordThickness_(cordThickness_m),
          maxWoundLength_(lengthAtRadius(maxRadius_m)),
          woundLength_(maxWoundLength_) {}

    // Konwertuje kroki enkodera na dystans (m, ze znakiem) przy aktualnym
    // promieniu rolki i aktualizuje stan nawinięcia. Dodatnie kroki =
    // odwijanie linki (wysuwanie, podnoszenie), ujemne = nawijanie
    // (opuszczanie).
    float stepsToDistance(float steps, float radPerStep) {
        float distance = steps * radPerStep * currentRadius();
        woundLength_ -= distance;
        if (woundLength_ < 0.0f) woundLength_ = 0.0f;
        if (woundLength_ > maxWoundLength_) woundLength_ = maxWoundLength_;
        return distance;
    }

    float currentRadius() const {
        float r2 = coreRadius_ * coreRadius_ + (woundLength_ * cordThickness_) / PI_CONST;
        return sqrtf(r2);
    }

private:
    static constexpr float PI_CONST = 3.14159265359f;

    float coreRadius_;
    float cordThickness_;
    float maxWoundLength_;
    float woundLength_;

    float lengthAtRadius(float r) const {
        return PI_CONST * (r * r - coreRadius_ * coreRadius_) / cordThickness_;
    }
};

#endif
