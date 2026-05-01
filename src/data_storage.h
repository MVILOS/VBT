#ifndef DATA_STORAGE_H
#define DATA_STORAGE_H

#include <Arduino.h>
#include <Preferences.h>
#include "lift_detector.h"

#define MAX_HISTORY 20

class DataStorage {
private:
    LiftResult history[MAX_HISTORY];
    uint8_t currentIndex;
    uint8_t count;  // Liczba zapisanych wyników (max 5)
    Preferences prefs;

    // CRC32 dla walidacji danych
    uint32_t calculateCRC32(const uint8_t* data, size_t length);

public:
    DataStorage();

    // Inicjalizacja - musi być wywołana w setup()
    void init();

    // Dodanie nowego wyniku do historii
    void addResult(const LiftResult& result);

    // Pobranie historii (zwraca faktyczną liczbę wyników)
    uint8_t getHistory(LiftResult* buffer, uint8_t maxSize);

    // Wyczyszczenie całej historii
    void clearHistory();

    // Pobranie liczby zapisanych wyników
    uint8_t getCount() const;

    // Pobranie wyniku według indeksu (0 = najnowszy)
    LiftResult getResultByIndex(uint8_t idx) const;

private:
    // Zapis/odczyt z NVRAM
    void loadFromNVRAM();
    void saveToNVRAM();
};

#endif
