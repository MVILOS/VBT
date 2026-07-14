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

// Programowy filtr glitchy - odtwarza sprzętowy setFilter(1023) z poprzedniego
// firmware (ESP32Encoder/PCNT, ~12.8us przy 80MHz APB). Bez tego progu
// elektryczny szum na liniach A/B (zbocza nie są idealnie skokowe) liczy się
// jako dodatkowe, fałszywe kroki i zawyża obliczaną prędkość. Wartość musi
// być mniejsza niż odstęp między realnymi zboczami przy maksymalnej
// spodziewanej prędkości sztangi (kilka m/s), stąd tylko kilkanaście us.
static const unsigned long MIN_EDGE_INTERVAL_US = 15;

void QuadEncoder::attach(int pinA, int pinB) {
    pinA_ = pinA;
    pinB_ = pinB;
    pinMode(pinA_, INPUT_PULLUP);
    pinMode(pinB_, INPUT_PULLUP);

    lastState_ = (digitalRead(pinA_) << 1) | digitalRead(pinB_);
    lastEdgeMicros_ = micros();
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
    unsigned long now = micros();
    if (now - lastEdgeMicros_ < MIN_EDGE_INTERVAL_US) {
        // Zbyt szybko po poprzednim zboczu - traktuj jako zakłócenie i
        // odrzuć, nie aktualizując stanu (patrz komentarz przy
        // MIN_EDGE_INTERVAL_US wyżej).
        return;
    }
    lastEdgeMicros_ = now;

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
