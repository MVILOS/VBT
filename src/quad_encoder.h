#ifndef QUAD_ENCODER_H
#define QUAD_ENCODER_H

#include <Arduino.h>

// Własny dekoder pełnej kwadratury (4x) na przerwaniach GPIO.
//
// Powód istnienia: ESP32Encoder (tryb attachFullQuad) korzysta ze
// sprzętowego PCNT, którego ESP32-C3 NIE MA (#if SOC_PCNT_SUPPORTED w tej
// bibliotece wycina całą implementację na C3 -> undefined reference przy
// linkowaniu). Zapasowa klasa biblioteki (InterruptEncoder) liczy tylko
// zbocza kanału A, czyli ma 4x gorszą rozdzielczość niż pełna kwadratura -
// to zmieniłoby kalibrację STEPS_PER_METER i pogorszyło precyzję pomiaru
// prędkości. Ten dekoder odtwarza dokładnie zachowanie pełnej kwadratury
// (4 zliczenia na jeden pełny cykl A/B) czysto programowo, więc istniejąca
// kalibracja STEPS_PER_METER pozostaje poprawna.
//
// Tylko JEDNA instancja na proces (limitacja wynikająca z sygnatury
// attachInterrupt, który wymaga zwykłego wskaźnika funkcji, nie metody).
// Do jednego enkodera na urządzeniu to wystarczające.
class QuadEncoder {
public:
    void attach(int pinA, int pinB);
    int64_t getCount() const;
    void clearCount();

private:
    static QuadEncoder* activeInstance;
    int pinA_ = -1;
    int pinB_ = -1;
    volatile int64_t count_ = 0;
    volatile uint8_t lastState_ = 0;

    void IRAM_ATTR onChange();
    static void IRAM_ATTR isrTrampoline();
};

#endif
