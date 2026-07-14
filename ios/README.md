# VBT iOS

Port natywnej aplikacji Android (`android/vbt-app`) na iOS (SwiftUI, iOS 17+), zgodnie z `../IOS_PORT_PLAN.md`.

## Wymagania

- macOS + Xcode 15.4+ jeśli chcesz pracować lokalnie. **Ten projekt jest rozwijany bez dostępu
  do Maca** — kompilacja jest weryfikowana wyłącznie przez CI (patrz niżej), a UI/BLE nigdy
  nie było odpalone na symulatorze/urządzeniu. Jeśli masz Maca, pierwszy krok to otworzyć
  projekt i przejść po ekranach ręcznie — mogą się ujawnić rzeczy, których `xcodebuild build`
  nie wyłapuje (layout, gesty, faktyczne działanie BLE).
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`) — projekt `.xcodeproj`
  nie jest trzymany w repo, generuje się z `project.yml`.
- Fizyczny iPhone (najlepiej iPhone 15 Pro) do testów BLE — symulator iOS nie obsługuje Bluetooth.

## CI (weryfikacja bez Maca)

GitHub Actions z runnerem `macos-15` (workflow `.github/workflows/ios-build.yml`) to jedyny
sposób weryfikacji kompilacji w tym repo. Uruchamia się automatycznie przy push/PR dotykającym
`ios/**`, generuje projekt przez XcodeGen i robi `xcodebuild build` (destination
`generic/platform=iOS Simulator` — nie wymaga konkretnego zainstalowanego symulatora).
Testy jednostkowe (`VBTTests/`) odpalają się dodatkowo, jeśli na danym runnerze jest
zainstalowany choć jeden uruchamialny symulator iOS — obserwowane runnery czasem go nie mają,
wtedy krok jest pomijany (build i tak weryfikuje kompilację). Sprawdzaj zakładkę Actions po
każdym pushu.

**Ograniczenie tego podejścia:** `xcodebuild build` łapie błędy typów/składni, ale NIE łapie
błędów działania w runtime (np. złe środowisko `@Environment`, crash przy starcie, zła logika
BLE) ani błędów layoutu SwiftUI. To nie zastępuje odpalenia appki — tylko wyklucza "nie
kompiluje się w ogóle".

## Generowanie projektu

```bash
cd ios/VBT
xcodegen generate
open VBT.xcodeproj
```

## Struktura

```
VBT/
├── App/                    — VBTApp.swift (composition root), RootView, MainTabView
├── Core/
│   ├── BLE/                 — CoreBluetooth: VbtBleManager, HeartRateManager, BleConstants, RepPacketParser
│   ├── Networking/          — APIClient (URLSession async/await), Endpoint, DTO
│   ├── Persistence/          — SwiftData models (TODO — patrz Faza 4 planu)
│   ├── Auth/                 — KeychainStore, UserPreferences, AuthRepository
│   └── Sync/                 — BackgroundSyncManager (TODO — Faza 4)
├── Domain/
│   ├── Models/                — ExerciseType, VelocityZone
│   └── UseCases/               — CalculatePower, Estimate1RM, GetVelocityZone
├── Features/                   — jeden folder na ekran (Login, Home, Connect, Workout, ...)
└── DesignSystem/                — Color, Typography (dark + teal, jak Android)
```

## Status implementacji

Zrealizowane (kod napisany i **kompiluje się w CI** — `xcodebuild build` zielony):
- [x] Szkielet projektu (XcodeGen) + Info.plist (BLE background mode)
- [x] Design system (kolory/typografia 1:1 z Androidem)
- [x] DTO/Codable modele (1:1 z `ApiModels.kt`)
- [x] `APIClient` (async/await, JWT bearer, obsługa wygaśnięcia sesji)
- [x] `KeychainStore` (token w Keychain, nie w plaintext jak Android DataStore) + `AuthRepository`
- [x] BLE: `BleConstants`, `RepPacketParser` (16-bajtowy pakiet rep, pokryty testami jednostkowymi 1:1 z Androidem), `VbtBleManager` + `HeartRateManager` (CoreBluetooth) — **niezweryfikowane na fizycznym sprzęcie**
- [x] Domain use case'y (power, 1RM, velocity zone) — pokryte testami jednostkowymi portowanymi 1:1 z Androida
- [x] Nawigacja (`NavigationStack`/`TabView`, routing wg roli) + `LoginScreen`/`RegisterScreen`/`HomeScreen`
- [x] `ConnectScreen` (skan/lista/połącz dla ESP32 + opcjonalny pas HR)

Do zrobienia (patrz `../IOS_PORT_PLAN.md`, Fazy 3-6):
- [ ] **Test `VbtBleManager` na fizycznym ESP32 (`VBT-Vector`) i iPhone 15 Pro — priorytet #1**,
      zanim rozbuduje się dalej WorkoutScreen na tej podstawie
- [ ] WorkoutScreen (freestyle + plan mode + live velocity + Live Activity/Dynamic Island)
- [ ] SwiftData (lub GRDB — do zdecydowania po spike'u) + offline sync
- [ ] History/SessionDetail, Plans, Athletes, Schedule, Analytics

## Uwaga

Backend (`server/backend`) i protokół BLE ESP32 (`src/ble_server.cpp`) **nie wymagają żadnych zmian** —
iOS mówi tym samym API/protokołem co Android.
