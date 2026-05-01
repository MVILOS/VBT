#include "data_storage.h"

DataStorage::DataStorage()
    : currentIndex(0), count(0)
{
}

void DataStorage::init() {
    prefs.begin("vbt-storage", false);  // false = read/write mode
    loadFromNVRAM();
    Serial.printf("DataStorage initialized. Loaded %d results from NVRAM\n", count);
}

void DataStorage::addResult(const LiftResult& result) {
    // Dodanie do circular buffer
    history[currentIndex] = result;
    currentIndex = (currentIndex + 1) % MAX_HISTORY;

    // Zwiększenie licznika (max 5)
    if (count < MAX_HISTORY) {
        count++;
    }

    // Zapis do NVRAM
    saveToNVRAM();

    Serial.printf("Result added to history. Total: %d\n", count);
}

uint8_t DataStorage::getHistory(LiftResult* buffer, uint8_t maxSize) {
    uint8_t numToReturn = min(count, maxSize);

    // Kopiowanie wyników w kolejności od najnowszego
    for (uint8_t i = 0; i < numToReturn; i++) {
        // Obliczenie indeksu: najnowszy wynik to (currentIndex - 1)
        int8_t idx = currentIndex - 1 - i;
        if (idx < 0) {
            idx += MAX_HISTORY;
        }
        buffer[i] = history[idx];
    }

    return numToReturn;
}

void DataStorage::clearHistory() {
    currentIndex = 0;
    count = 0;
    memset(history, 0, sizeof(history));

    // Wyczyść NVRAM
    prefs.clear();

    Serial.println("History cleared");
}

uint8_t DataStorage::getCount() const {
    return count;
}

void DataStorage::loadFromNVRAM() {
    // Struktura do zapisu z checksumą
    struct StorageBlock {
        LiftResult data[MAX_HISTORY];
        uint8_t index;
        uint8_t count;
        uint32_t checksum;
    } block;

    size_t len = prefs.getBytes("data", &block, sizeof(block));

    if (len != sizeof(block)) {
        Serial.println("NVRAM: No valid data found, starting fresh");
        clearHistory();
        return;
    }

    // Walidacja CRC32
    uint32_t calculatedCRC = calculateCRC32(
        (uint8_t*)&block,
        sizeof(block) - sizeof(uint32_t)  // Bez pola checksum
    );

    if (calculatedCRC != block.checksum) {
        Serial.println("NVRAM: CRC32 mismatch, data corrupted. Clearing.");
        clearHistory();
        return;
    }

    // Dane poprawne - załaduj
    memcpy(history, block.data, sizeof(history));
    currentIndex = block.index;
    count = block.count;

    Serial.println("NVRAM: Data loaded successfully");
}

void DataStorage::saveToNVRAM() {
    struct StorageBlock {
        LiftResult data[MAX_HISTORY];
        uint8_t index;
        uint8_t count;
        uint32_t checksum;
    } block;

    // Przygotowanie danych
    memcpy(block.data, history, sizeof(history));
    block.index = currentIndex;
    block.count = count;

    // Obliczenie CRC32
    block.checksum = calculateCRC32(
        (uint8_t*)&block,
        sizeof(block) - sizeof(uint32_t)
    );

    // Zapis do NVRAM
    prefs.putBytes("data", &block, sizeof(block));
}

LiftResult DataStorage::getResultByIndex(uint8_t idx) const {
    if (idx >= count) {
        return LiftResult{0, 0, 0, 0};
    }
    int8_t actualIdx = currentIndex - 1 - idx;
    if (actualIdx < 0) actualIdx += MAX_HISTORY;
    return history[actualIdx];
}

// Algorytm CRC32 (IEEE 802.3)
uint32_t DataStorage::calculateCRC32(const uint8_t* data, size_t length) {
    uint32_t crc = 0xFFFFFFFF;

    for (size_t i = 0; i < length; i++) {
        crc ^= data[i];
        for (uint8_t j = 0; j < 8; j++) {
            crc = (crc >> 1) ^ (0xEDB88320 & -(crc & 1));
        }
    }

    return ~crc;
}
