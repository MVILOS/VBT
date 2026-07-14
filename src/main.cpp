#include <Arduino.h>
#include "quad_encoder.h"
#include "spool_model.h"
#include "lift_detector.h"
#include "data_storage.h"
#include "ble_server.h"

// --- KONFIGURACJA SPRZĘTU (ESP32-C3 SuperMini) ---
// Rolka nawija linkę warstwa na warstwę, więc jej promień rośnie w trakcie
// użycia (nawijające się na siebie zwoje) - stała średnica fałszowałaby
// pomiar dystansu/prędkości w miarę odwijania linki w obrębie powtórzenia.
// Zamiast stałej, LiftDetector używa dynamicznego modelu (SpoolModel,
// zob. spool_model.h) opartego o fizyczne wymiary rolki zmierzone na
// urządzeniu:
const float CORE_DIAMETER_M = 0.042;      // średnica pustego rdzenia rolki
const float CORD_THICKNESS_M = 0.001;     // grubość linki
const float MAX_SPOOL_DIAMETER_M = 0.0445; // najwyższy zmierzony szczyt (pełne nawinięcie)
const float ENCODER_PPR = 1200.0;

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
    SpoolModel spoolModel(CORE_DIAMETER_M / 2.0f, CORD_THICKNESS_M, MAX_SPOOL_DIAMETER_M / 2.0f);
    detector = new LiftDetector(&encoder, spoolModel, ENCODER_PPR);
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
