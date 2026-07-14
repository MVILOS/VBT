# VBT iOS

Port natywnej aplikacji Android (`android/vbt-app`) na iOS (SwiftUI, iOS 17+), zgodnie z `../IOS_PORT_PLAN.md`.

## Wymagania

- macOS + Xcode 15.4+ (ten kod był pisany na Linuksie bez dostępu do Xcode/Swift toolchain —
  **nie był jeszcze kompilowany**. Pierwszy krok na Macu to zbudowanie projektu i naprawienie
  ewentualnych błędów kompilacji).
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`) — projekt `.xcodeproj`
  nie jest trzymany w repo, generuje się z `project.yml`.
- Fizyczny iPhone (najlepiej iPhone 15 Pro) do testów BLE — symulator iOS nie obsługuje Bluetooth.

## Generowanie projektu

```bash
cd ios/VBT
xcodegen generate
open VBT.xcodeproj
```

## Struktura

```
VBT/
├── App/                    — VBTApp.swift, RootView (routing wg stanu auth)
├── Core/
│   ├── BLE/                 — CoreBluetooth: VbtBleManager, BleConstants, RepPacketParser
│   ├── Networking/          — APIClient (URLSession async/await), Endpoint, DTO
│   ├── Persistence/          — SwiftData models (TODO — patrz Faza 4 planu)
│   ├── Auth/                 — KeychainStore, AuthRepository
│   └── Sync/                 — BackgroundSyncManager (TODO — Faza 4)
├── Domain/
│   ├── Models/                — ExerciseType, VelocityZone
│   └── UseCases/               — CalculatePower, Estimate1RM, GetVelocityZone
├── Features/                   — jeden folder na ekran (Login, Home, Connect, Workout, ...)
└── DesignSystem/                — Color, Typography (dark + teal, jak Android)
```

## Status implementacji

Zrealizowane (kod napisany, **niekompilowany — wymaga weryfikacji na Macu**):
- [x] Szkielet projektu (XcodeGen) + Info.plist (BLE background mode)
- [x] Design system (kolory/typografia 1:1 z Androidem)
- [x] DTO/Codable modele (1:1 z `ApiModels.kt`)
- [x] `APIClient` (async/await, JWT bearer)
- [x] `KeychainStore` + `AuthRepository`
- [x] BLE: `BleConstants`, `RepPacketParser` (16-bajtowy pakiet rep — parser pokryty testami jednostkowymi), `VbtBleManager` (CoreBluetooth)
- [x] Domain use case'y (power, 1RM, velocity zone)
- [x] Nawigacja (`NavigationStack`, routing wg roli) + `LoginScreen`/`HomeScreen` jako pierwszy pionowy przekrój

Do zrobienia (patrz `../IOS_PORT_PLAN.md`, Fazy 2-6):
- [ ] Test `VbtBleManager` na fizycznym ESP32 (`VBT-Vector`) — **priorytet #1 przed dalszą pracą**
- [ ] ConnectScreen (UI do skanowania/łączenia)
- [ ] WorkoutScreen (freestyle + plan mode + live velocity + Live Activity/Dynamic Island)
- [ ] SwiftData (lub GRDB — do zdecydowania po spike'u) + offline sync
- [ ] History/SessionDetail, Plans, Athletes, Schedule, Analytics

## Uwaga

Backend (`server/backend`) i protokół BLE ESP32 (`src/ble_server.cpp`) **nie wymagają żadnych zmian** —
iOS mówi tym samym API/protokołem co Android.
