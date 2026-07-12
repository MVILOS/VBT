#include "quad_encoder.h"

QuadEncoder* QuadEncoder::activeInstance = nullptr;

// Tablica przejść stanu kwadratury: indeks = (poprzedni_stan<<2 | nowy_stan),
// wartość = -1/0/+1 zliczeń. Standardowa dekodowanie pełnej kwadratury (4x).
static const int8_t QUAD_TRANSITION[16] = {
    0, -1,  1,  0,
    1,  0,  0, -1,
   -1,  0,  0,  1,
    0,  1, -1,  0
};

void QuadEncoder::attach(int pinA, int pinB) {
    pinA_ = pinA;
    pinB_ = pinB;
    pinMode(pinA_, INPUT_PULLUP);
    pinMode(pinB_, INPUT_PULLUP);

    lastState_ = (digitalRead(pinA_) << 1) | digitalRead(pinB_);
    count_ = 0;

    activeInstance = this;
    attachInterrupt(digitalPinToInterrupt(pinA_), isrTrampoline, CHANGE);
    attachInterrupt(digitalPinToInterrupt(pinB_), isrTrampoline, CHANGE);
}

int64_t QuadEncoder::getCount() const {
    return count_;
}

void QuadEncoder::clearCount() {
    noInterrupts();
    count_ = 0;
    interrupts();
}

void IRAM_ATTR QuadEncoder::onChange() {
    uint8_t state = (digitalRead(pinA_) << 1) | digitalRead(pinB_);
    uint8_t idx = (lastState_ << 2) | state;
    count_ += QUAD_TRANSITION[idx];
    lastState_ = state;
}

void IRAM_ATTR QuadEncoder::isrTrampoline() {
    if (activeInstance) {
        activeInstance->onChange();
    }
}
