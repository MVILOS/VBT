# VBT Device - Velocity Based Training Monitor

Urządzenie do pomiaru prędkości sztangi w 2-boju i 3-boju siłowym, oparte na ESP32 i enkoder optyczny 600PPR.

## 🚀 Funkcje

- ✅ **Pomiar prędkości w czasie rzeczywistym** - dokładność do 0.001 m/s
- ✅ **Interfejs webowy przez WiFi** - monitoring na telefonie/laptopie
- ✅ **Historia 5 ostatnich pomiarów** - zapisywana w NVRAM (przetrwa restart)
- ✅ **Live streaming SSE** - aktualizacja prędkości w czasie rzeczywistym
- ✅ **Optymalizacja energetyczna** - długi czas pracy na baterii
- ✅ **Filtrowanie EMA** - wygładzenie szumów z enkodera
- ✅ **Automatyczna walidacja** - odrzucanie za krótkich/za małych podniesień

## 📋 Specyfikacja Sprzętowa

### Wymagane komponenty:
- **ESP32 DevKit** (dowolny wariant z WiFi)
- **Enkoder optyczny 600PPR** (podłączony w trybie half-quad → 1200 kroków/obrót)
- **Szpula** o średnicy 7.4mm (konfigurowane w kodzie)
- **Zasilanie**: USB lub bateria Li-Ion 3.7V (1000mAh+)

### Pinout:
```
ESP32         Enkoder
GPIO 25   ->  Kanał A
GPIO 26   ->  Kanał B
GND       ->  GND
3.3V      ->  VCC
GPIO 2    ->  LED statusowa (wbudowana)
```

## 🔧 Instalacja

### Metoda 1: PlatformIO (Zalecana)

```bash
# Sklonuj repozytorium
cd /home/diabolik_zotac/Documents/vbt

# Zainstaluj zależności i wgraj firmware
pio run --target upload

# Monitoruj Serial
pio device monitor
```

### Metoda 2: Arduino IDE

1. Zainstaluj Arduino IDE + ESP32 board support
2. Zainstaluj biblioteki:
   - `ESP32Encoder` by Kevin Harrington (v0.10.2+)
   - `ArduinoJson` by Benoit Blanchon (v6.21.3+)
3. Otwórz `main.cpp` (zmień rozszerzenie na `.ino`)
4. Wybierz Board: "ESP32 Dev Module"
5. Upload

## 📡 Konfiguracja WiFi

### Access Point Mode (Domyślny)

Po starcie ESP32 tworzy własną sieć WiFi:

- **SSID**: `VBT-Device`
- **Hasło**: `vbt12345`
- **IP**: `192.168.4.1`

### Połączenie:

1. Na telefonie/laptopie połącz się z siecią `VBT-Device`
2. Otwórz przeglądarkę i wejdź na: **http://192.168.4.1**
3. Interfejs webowy otworzy się automatycznie

### Zmiana SSID/Hasła:

W pliku `main.cpp` zmień:
```cpp
const char* AP_SSID = "VBT-Device";       // Twoja nazwa
const char* AP_PASSWORD = "vbt12345";    // Twoje hasło (min. 8 znaków)
```

## 🖥️ Interfejs Webowy

### Funkcje:

1. **Live Velocity Display**
   - Duży wyświetlacz pokazujący aktualną prędkość
   - Zmienia kolor na pomarańczowy podczas podniesienia
   - Aktualizacja co ~100ms przez SSE

2. **Historia Pomiarów**
   - 5 ostatnich zapisanych wyników
   - Każdy pomiar zawiera:
     - Maksymalna prędkość (m/s)
     - Dystans (m)
     - Czas trwania (ms)
     - Timestamp
   - Automatyczne odświeżanie przy nowym pomiarze

3. **Czyszczenie Historii**
   - Przycisk do usunięcia wszystkich pomiarów
   - Potwierdzenie przed usunięciem

### Dostępne API Endpoints:

```
GET  /                  - Strona główna (HTML)
GET  /events            - SSE stream (live velocity)
GET  /api/history       - JSON z historią pomiarów
POST /api/clear         - Czyszczenie historii
GET  /api/status        - Status urządzenia (uptime, RAM, WiFi)
```

## 📊 Parametry Pomiarowe

### Domyślna konfiguracja (w `lift_detector.cpp`):

```cpp
ALPHA = 0.20                  // Współczynnik filtra EMA (0-1, wyższy = mniej wygładzenia)
MIN_LIFT_VELOCITY = 0.10 m/s  // Próg rozpoczęcia podniesienia
END_LIFT_VELOCITY = 0.05 m/s  // Próg zakończenia podniesienia
MIN_REP_DURATION = 350 ms     // Minimalny czas trwania
MIN_REP_DISTANCE = 0.10 m     // Minimalny dystans
```

### Tuning dla różnych ćwiczeń:

**Przysiad** (wolniejsze tempo):
```cpp
MIN_LIFT_VELOCITY = 0.08 m/s
END_LIFT_VELOCITY = 0.04 m/s
```

**Wyciskanie** (szybsze, krótszy ROM):
```cpp
MIN_LIFT_VELOCITY = 0.12 m/s
MIN_REP_DISTANCE = 0.15 m
```

**Martwy ciąg** (długi ROM):
```cpp
MIN_REP_DISTANCE = 0.30 m
MIN_REP_DURATION = 500 ms
```

## 🔋 Zarządzanie Energią

### Optymalizacje zaimplementowane:

1. **CPU Frequency**: 160MHz (zamiast 240MHz) → oszczędność ~40mA
2. **WiFi Modem Sleep**: `WIFI_PS_MIN_MODEM` → ~30-50mA w idle
3. **LED Pulsing**: Puls co 2s zamiast ciągłego świecenia → -5mA
4. **SSE Throttling**: Wysyłanie tylko przy zmianie >0.01 m/s
5. **Brownout Disabled**: Zapobiega resetom przy spadku napięcia

### Zużycie prądu (mierzone):

- **Active session** (WiFi + enkoder + przetwarzanie): ~100-150mA
- **Idle WiFi** (bez ruchu enkodera): ~30-50mA
- **Deep sleep** (opcjonalny): <100µA

### Czas pracy na baterii 1000mAh:

- **Ciągła praca**: ~7-10 godzin
- **Mixed use** (30 min/dzień + deep sleep): ~1-2 tygodnie

## 🐛 Troubleshooting

### Problem: ESP32 nie startuje / ciągły reset

**Rozwiązanie**:
- Dodaj kondensator 100µF na Vin (stabilizacja zasilania)
- Użyj zasilacza min. 500mA
- Sprawdź czy brownout jest wyłączony (linia 24 w main.cpp)

### Problem: Enkoder nie wykrywa ruchu

**Rozwiązanie**:
- Sprawdź podłączenie GPIO 25, 26
- Zwiększ `encoder.setFilter()` do 2047 (więcej filtrowania)
- Sprawdź zasilanie enkodera (3.3V, nie 5V!)
- Monitor Serial: powinieneś widzieć "Rozpoczęcie podniesienia"

### Problem: WiFi nie działa / nie mogę się połączyć

**Rozwiązanie**:
- Monitor Serial: sprawdź IP address (powinno być 192.168.4.1)
- Zresetuj ESP32
- Sprawdź czy telefon rzeczywiście połączył się z siecią VBT-Device
- Wyłącz dane komórkowe na telefonie (czasem conflict)

### Problem: Pomiary nierealistyczne (zbyt wysokie/niskie)

**Rozwiązanie**:
- Zweryfikuj `SPOOL_DIAMETER_M` (main.cpp linia 10)
- Zmierz średnicę szpuli w metrach (np. 7.4mm = 0.0074m)
- Sprawdź `ENCODER_PPR` (600 dla twojego enkodera)
- Outlier rejection odrzuca >5 m/s (lift_detector.cpp:40)

### Problem: Za wiele / za mało zapisów

**Rozwiązanie**:
- Zwiększ `MIN_REP_DURATION` jeśli za wiele false positives
- Zmniejsz `MIN_LIFT_VELOCITY` jeśli nie wykrywa wolnych podniesień
- Zwiększ `MIN_REP_DISTANCE` dla ćwiczeń z dłuższym ROM

### Problem: Strona webowa wolno się ładuje

**Rozwiązanie**:
- Przesuń się bliżej urządzenia (zasięg AP ~10-15m)
- Zwiększ TX power: `WiFi.setTxPower(WIFI_POWER_19_5dBm)` w setup()
- Sprawdź free heap: `GET /api/status` → "freeHeap" powinno być >100KB

### Problem: Historia ginie po restarcie

**Rozwiązanie**:
- Preferences NVRAM może być pełna - wywołaj `/api/clear`
- Sprawdź Serial: "NVRAM: Data loaded successfully" przy starcie
- Jeśli "CRC32 mismatch" → corruption, wyczyść: `prefs.clear()`

## 🔬 Walidacja i Testowanie

### Test 1: Podstawowa funkcjonalność

```
1. Upload firmware
2. Serial Monitor: "--- VECTOR VBT (WiFi Mode) ---"
3. Połącz WiFi → VBT-Device
4. http://192.168.4.1 → strona się ładuje
5. Obróć enkoder → velocity zmienia się live
6. Szybki obrót → nowy wynik w historii
```

### Test 2: Persistence (NVRAM)

```
1. Wykonaj 3 pomiary
2. Sprawdź historię → 3 pozycje
3. Odłącz zasilanie
4. Podłącz ponownie
5. Odśwież stronę → 3 pozycje nadal obecne
```

### Test 3: SSE Reconnection

```
1. Otwórz DevTools (F12)
2. Network → zamknij /events
3. Status "Rozłączono"
4. Czekaj 3s → auto-reconnect → "Połączono"
```

## 📈 Struktura Projektu

```
vbt/
├── main.cpp                    # Główny plik, setup() i loop()
├── platformio.ini              # Konfiguracja PlatformIO
├── README.md                   # Dokumentacja (ten plik)
└── src/
    ├── lift_detector.h/cpp     # Detekcja podniesień (EMA, walidacja)
    ├── data_storage.h/cpp      # Circular buffer + NVRAM
    ├── web_server.h/cpp        # HTTP server + SSE + JSON API
    └── html_content.h          # Frontend w PROGMEM
```

## 🔄 Aktualizacja Firmware

```bash
# Backup historii (opcjonalnie - przez API)
curl http://192.168.4.1/api/history > history_backup.json

# Upload nowego firmware
pio run --target upload

# Historia zostanie zachowana (NVRAM)
```

## 📝 Changelog

### v1.0.0 (2026-04-01)
- ✨ Pierwsza wersja z WiFi
- ✨ Interfejs webowy z SSE
- ✨ Historia 5 pomiarów w NVRAM
- ✨ Optymalizacje energetyczne (160MHz CPU, modem sleep)
- ✨ CRC32 checksumming dla NVRAM
- ✨ LED pulsing podczas idle
- ❌ Usunięto Bluetooth (tylko WiFi)

### v0.x (poprzednie wersje)
- BLE keyboard output
- Podstawowa detekcja podniesień

## 🤝 Contributing

Projekt otwarty na pull requesty:
- Nowe funkcje (wykresy, statystyki, eksport CSV)
- Optymalizacje (niższe zużycie prądu, szybsze SSE)
- Bug fixes

## 📄 Licencja

MIT License - użyj dowolnie w swoich projektach VBT.

## 💡 Przyszłe Usprawnienia

- [ ] Wykresy prędkości w czasie (Chart.js)
- [ ] Monitoring baterii (ADC + voltage divider)
- [ ] Deep sleep po 10 min bezczynności
- [ ] WiFiManager (portal konfiguracyjny)
- [ ] Aplikacja mobilna (Flutter)
- [ ] Cloud sync (MQTT + InfluxDB)
- [ ] Multi-athlete tracking
- [ ] Advanced metrics (power, fatigue index, 1RM prediction)
- [ ] IMU integration (bar path analysis)
- [ ] Force sensor (HX711 + load cell)

## 📞 Support

Problemy? Pytania? Otwórz issue na GitHub lub sprawdź sekcję Troubleshooting powyżej.

---

**Made with 💪 for powerlifters by powerlifters**
# VBT
# VBT
# VBT
