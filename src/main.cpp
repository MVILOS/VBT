#include <Arduino.h>
#include "quad_encoder.h"
#include "lift_detector.h"
#include "data_storage.h"
#include "ble_server.h"

// --- KONFIGURACJA SPRZĘTU (ESP32-C3 SuperMini) ---
// Linka nawija się pojedynczą warstwą wzdłuż szerokiego bębna, więc promień
// nawijania jest w praktyce stały. Poprzedni model warstwowy (SpoolModel)
// mieścił geometrycznie tylko ~0.17 m linki, po czym clampował się do
// promienia rdzenia - realnie i tak liczył stałą średnicę, tyle że błędną
// (42 mm). Zmierzona na urządzeniu średnica bębna z linką: 41 mm.
const float SPOOL_DIAMETER_M = 0.041;
// Enkoder: 600 PPR na kanał, dekoder pełnej kwadratury (quad_encoder.h)
// daje 4 zliczenia na impuls -> 2400 zliczeń na obrót. Poprzednia wartość
// 1200 pochodziła z firmware v1 (gdzie maskowała ją empiryczna kalibracja
// SPOOL_DIAMETER_M=0.0074) i zawyżała prędkość/dystans 2x - zweryfikowane
// analizą wideo przysiadu 140 kg (sesja 105, 15.07.2026) vs dane na
// serwerze. Test kontrolny po każdej zmianie: wyciągnięcie dokładnie
// 1.00 m linki (wolno i szybko) musi logować dystans ~1.00 m.
const float COUNTS_PER_REV = 2400.0;

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
