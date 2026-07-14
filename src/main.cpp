#include <Arduino.h>
#include "quad_encoder.h"
#include "lift_detector.h"
#include "data_storage.h"
#include "ble_server.h"

// --- KONFIGURACJA SPRZĘTU (ESP32-C3 SuperMini) ---
// Rolka fizycznie ma ok. 41.5mm "pustej" średnicy, ale przez nawijające się
// na siebie zwoje linki rośnie w trakcie użycia do ok. 44mm - to źródło
// narastającego błędu pomiaru, bo poniższa stała jest wartością stałą, a nie
// dynamicznym modelem. Użyto wartości średniej z zakresu 41.5-44mm, żeby
// zminimalizować maksymalny błąd w obie strony (dawne 0.04 = 40mm było
// zwyczajnie błędne, poza całym zakresem rzeczywistej średnicy).
// Pełna korekta wymagałaby dynamicznego modelu narastania średnicy zależnego
// od nawiniętej długości linki (grubość linki + średnica rdzenia rolki).
const float SPOOL_DIAMETER_M = 0.04275;
const float PI_VAL = 3.14159265359;
const float SPOOL_CIRCUMFERENCE = SPOOL_DIAMETER_M * PI_VAL;
const float ENCODER_PPR = 1200.0;
const float STEPS_PER_METER = ENCODER_PPR / SPOOL_CIRCUMFERENCE;

// UWAGA: piny przeniesione z klasycznego ESP32 (25/26, LED=2) na ESP32-C3,
// który ma tylko GPIO 0-21. Kanały A/B enkodera wg zgłoszenia: GPIO 5 i 6
// ("chyba" - do potwierdzenia fizycznie po podłączeniu). LED_PIN=8 to
// standardowe położenie wbudowanej diody na klonach ESP32-C3 SuperMini,
// zwykle podłączonej w logice ODWRÓCONEJ (aktywna stanem LOW) - stąd
// LED_ACTIVE_LOW poniżej. Zweryfikuj oba na fizycznej płytce.
const int ENCODER_PIN_A = 4;
const int ENCODER_PIN_B = 5;
const int LED_PIN = 8;
const bool LED_ACTIVE_LOW = true;

inline void ledWrite(bool on) {
    digitalWrite(LED_PIN, (on != LED_ACTIVE_LOW) ? HIGH : LOW);
}

// Obiekty
QuadEncoder encoder;
LiftDetector* detector;
DataStorage* storage;
VBTBleServer* bleServer;

void setup() {
    // 1. Serial i podstawowe opóźnienie
    Serial.begin(115200);
    delay(500);  // Stabilizacja zasilania

    Serial.println("Starting VBT...");

    // 2. Konfiguracja Diody Statusowej
    pinMode(LED_PIN, OUTPUT);

    // Mrugnij 3 razy na start
    for(int i = 0; i < 3; i++) {
        ledWrite(true);
        delay(200);
        ledWrite(false);
        delay(200);
    }

    // 3. Konfiguracja Enkodera (własny dekoder kwadratury - patrz quad_encoder.h,
    // ESP32-C3 nie ma sprzętowego PCNT wymaganego przez ESP32Encoder)
    encoder.attach(ENCODER_PIN_A, ENCODER_PIN_B);
    encoder.clearCount();

    // 4. Inicjalizacja modułów
    detector = new LiftDetector(&encoder, STEPS_PER_METER);
    storage = new DataStorage();
    storage->init();

    // 5. Optymalizacja CPU PRZED BLE - redukcja z 240MHz do 80MHz dla oszczędności prądu
    setCpuFrequencyMhz(80);
    Serial.printf("CPU Frequency: %d MHz\n", getCpuFrequencyMhz());
    delay(500);  // Stabilizacja po zmianie częstotliwości

    // 6. Inicjalizacja BLE (zamiast WiFi)
    Serial.println("Initializing BLE...");
    bleServer = new VBTBleServer(detector, storage);
    bleServer->begin();
    Serial.println("BLE ready");

    // Zapal diodę na stałe po starcie
    ledWrite(true);

    delay(500);
    Serial.println("--- VECTOR VBT v2.0.0 (BLE Mode) ---");
}

void loop() {
    unsigned long currentMicros = micros();

    // LED pulsing - energooszczędny sposób
    static unsigned long ledTimer = 0;
    static bool ledState = false;

    if (millis() - ledTimer > 2000) {
        ledState = !ledState;
        if (ledState) {
            ledWrite(true);
            delay(50);
            ledWrite(false);
        }
        ledTimer = millis();
    }

    // Podczas podniesienia - dioda świeci
    if (detector->isCurrentlyLifting()) {
        ledWrite(true);
    } else {
        if (millis() - ledTimer > 50) {
            ledWrite(false);
        }
    }

    // Aktualizacja detektora podniesień
    detector->update(currentMicros);

    // Obsługa nowego wyniku
    if (detector->hasNewResult()) {
        LiftResult result = detector->getLastResult();

        // Dodanie do historii
        storage->addResult(result);

        // Powiadomienie BLE serwera o nowym wyniku
        bleServer->notifyNewResult();
    }

    // Aktualizacja BLE (wysyłanie notyfikacji)
    bleServer->update();
}
