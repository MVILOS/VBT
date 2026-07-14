# VBT iOS — Analiza i Plan Portu (Android → iOS, target: iPhone 15 Pro)

**Data:** 2026-07-14
**Cel:** Natywna aplikacja iOS (Swift/SwiftUI) funkcjonalnie równoważna `android/vbt-app`, komunikująca się z tym samym backendem (`server/backend`, FastAPI + JWT) i tym samym urządzeniem ESP32 przez BLE (protokół bez zmian).

---

## 1. Analiza źródła (Android)

### Stack obecny
- **UI**: Jetpack Compose, Material3, dark theme (teal `#4ECDC4`)
- **DI**: Hilt
- **Baza lokalna**: Room (`VbtDatabase`) — encje: `ExerciseDefinition`, `TrainingPlan`, `PlanExercise`, `PlanSet`, `WorkoutSession`, `SessionSet`, `RepResult`
- **Sieć**: Retrofit + OkHttp + Gson, JWT interceptor, `DataStore` (`PreferencesManager`) na token/rolę
- **BLE**: Nordic Android BLE library (`no.nordicsemi.android:ble:2.7.0`)
- **Sync**: WorkManager (`SessionSyncWorker`) — offline-first, kolejkowanie uploadu sesji
- **Foreground service**: `WorkoutForegroundService` — utrzymuje połączenie BLE w tle podczas treningu
- **Nawigacja**: Navigation-Compose, routing zależny od roli (coach/athlete)
- **11 ekranów**: Login/Register, Home, Connect (BLE), Workout (kluczowy — freestyle + plan mode + live velocity), History, SessionDetail, PlanList, PlanEdit, AthleteList, AthleteProfile, Schedule/Calendar, Analytics

### Protokół BLE (do zachowania 1:1)
Usługa `0000FF00-...`, charakterystyki:
- `FF01` — live velocity (notify)
- `FF02` — rep result, 16 bajtów little-endian: meanVel(u16,/1000), distance(u16,/1000), duration(u16,ms), timestamp(u32), repIndex(u16), peakVel(u16,/1000), reserved
- `FF03` — device status (notify)
- `FF04` — command (write): RESET_REPS=0x01, CLEAR_HISTORY=0x02, SET_EXERCISE_PARAMS=0x03, PING=0x04
- Nazwa urządzenia: `VBT-Vector`
- Dodatkowo: BLE Heart Rate Service standardowy (`0x180D`) — opcjonalna opaska HR

**Ten protokół jest niezależny od platformy — nic tu się nie zmienia.** To jedyna warstwa, gdzie liczy się bitowa zgodność.

### Backend (FastAPI)
REST + JWT, endpointy: `auth`, `exercises`, `plans`, `calendar`, `sessions`, `users`, `analytics`, `admin`, `dashboard`. Brak zmian po stronie serwera — iOS używa tego samego API.

---

## 2. Mapowanie architektury Android → iOS

| Android | iOS (natywnie) | Uwagi |
|---|---|---|
| Kotlin | Swift 5.10+ | |
| Jetpack Compose | **SwiftUI** | 1:1 filozofia deklaratywna, łatwe przeniesienie logiki ekranów |
| Hilt (DI) | **swift-dependencies** (Point-Free) lub ręczny `Environment`/`@Observable` | Hilt nie ma odpowiednika 1:1, ale wzorzec repo→viewmodel przenosi się łatwo |
| ViewModel + StateFlow | `@Observable` ViewModel (iOS 17+) + `AsyncStream`/`Combine` | iOS 17 min → można używać nowego `@Observable` zamiast `ObservableObject` |
| Room | **SwiftData** (iOS 17+) | Bezpośredni odpowiednik: `@Model` zamiast `@Entity`, ORM z podobnym query API. Alternatywa: GRDB (SQLite) jeśli potrzeba więcej kontroli nad migracjami |
| Retrofit/OkHttp/Gson | **URLSession + Codable** (async/await) | Brak potrzeby Alamofire — natywny async/await w pełni wystarcza |
| DataStore (prefs) | **Keychain** (token JWT — nigdy UserDefaults!) + `UserDefaults`/`@AppStorage` (nie-wrażliwe: rola, username) | Ważna poprawka bezpieczeństwa względem Androida — token JWT MUSI iść do Keychain, nie do plaintext prefs |
| Nordic BLE (Android) | **CoreBluetooth** natywnie | Apple nie ma potrzeby biblioteki pośredniej — CoreBluetooth jest niskopoziomowe ale stabilne; ewent. wrapper jak `Bluejay`/`SwiftyBluetooth` dla wygody |
| WorkManager (sync) | **BGTaskScheduler** (`BGAppRefreshTask`/`BGProcessingTask`) | iOS jest dużo bardziej restrykcyjny co do tła — patrz sekcja 4 |
| Foreground Service (BLE w tle) | **Background Modes**: `bluetooth-central` + ewentualnie `processing` | iOS nie ma "foreground service"; ciągłość BLE w tle wymaga deklaracji w Info.plist i ograniczonego zestawu operacji (patrz sekcja 4) |
| Navigation-Compose | **NavigationStack** (iOS 16+) z `NavigationPath` | Routing zależny od roli — enum-based `NavigationDestination` |
| Material3 dark theme | Custom SwiftUI `ColorScheme` + `Color` assets (Asset Catalog) | Odwzorować identyczną paletę (teal `#4ECDC4`, `#0F0F0F`/`#1A1A1A`/`#242424`) |

### Struktura projektu (proponowana)
```
ios/VBT/
├── VBT.xcodeproj
├── VBT/
│   ├── App/                    (VBTApp.swift, entry point)
│   ├── Core/
│   │   ├── BLE/                (VbtBleManager, BleConstants, RepPacketParser)
│   │   ├── Networking/         (APIClient, Endpoints, DTO/Codable models)
│   │   ├── Persistence/        (SwiftData models + container)
│   │   ├── Auth/                (AuthRepository, KeychainStore)
│   │   └── Sync/                (BackgroundSyncManager)
│   ├── Domain/
│   │   ├── Models/              (ExerciseType, VelocityZone)
│   │   └── UseCases/             (CalculatePower, Estimate1RM, GetVelocityZone)
│   ├── Features/
│   │   ├── Login/
│   │   ├── Home/
│   │   ├── Connect/
│   │   ├── Workout/              (najbardziej złożony moduł)
│   │   ├── History/
│   │   ├── Plans/
│   │   ├── Athletes/
│   │   ├── Schedule/
│   │   └── Analytics/
│   └── DesignSystem/             (Color, Typography, komponenty: numpad kg, velocity gauge)
└── VBTTests/
```

---

## 3. Specyfika iPhone 15 Pro — co warto wykorzystać

iPhone 15 Pro to solidny target (iOS 17 minimum realistycznie, można ustawić deployment target 17.0):

- **Dynamic Island** — podczas aktywnego treningu: live activity pokazujący bieżącą prędkość / rep count na Dynamic Island i lock screen (`ActivityKit` / `Live Activities`). To naturalny odpowiednik Androidowego `WorkoutForegroundService` z notyfikacją — i lepszy UX niż Android.
- **ProMotion 120Hz** — płynna animacja dużej cyfry prędkości i przejść stref kolorów (SwiftUI robi to "for free", ale warto testować na urządzeniu, nie tylko symulatorze).
- **Action Button** — opcjonalnie: skrót do szybkiego łączenia z ostatnim znanym urządzeniem BLE / start-stop seta (nice-to-have, nie blocker).
- **A17 Pro / Neural Engine** — brak realnego zastosowania w tej apce (brak ML w obecnym zakresie), pomijamy.
- **USB-C** — bez znaczenia dla BLE, nie wpływa na architekturę.
- Deployment target: **iOS 17.0** pozwala używać `@Observable`, `SwiftData`, `NavigationStack` bez fallbacków — nie ma sensu wspierać starszych iOS-ów dla nowego projektu w 2026.

---

## 4. Krytyczne różnice platformowe (ryzyka do zaadresowania wcześnie)

1. **BLE w tle jest dużo bardziej ograniczone na iOS.**
   Android: foreground service + trwałe połączenie GATT bez większych restrykcji.
   iOS: wymaga `UIBackgroundModes: bluetooth-central` w Info.plist; system może zawiesić appkę i tylko obudzić ją na zdarzenie z peryferala (CBCentralManager state restoration). Trzeba zaimplementować `centralManager(_:willRestoreState:)` i testować realny scenariusz "trening z zablokowanym ekranem" na fizycznym iPhone 15 Pro, nie na symulatorze (symulator nie ma BLE).

2. **Sync w tle**: WorkManager → BGTaskScheduler nie gwarantuje uruchomienia w konkretnym czasie (heurystyka systemu). Trzeba zaprojektować sync tak, by kluczowy upload (koniec sesji) odbywał się **na pierwszym planie zaraz po zakończeniu treningu**, a BGTaskScheduler był tylko dodatkowym "dobiciem" zaległej kolejki, nie jedynym mechanizmem — inaczej dane będą się gubić/opóźniać.

3. **Uprawnienia BLE**: `NSBluetoothAlwaysUsageDescription` w Info.plist (odpowiednik `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`). Brak potrzeby lokalizacji na iOS (Android czasem tego wymagał na starszych wersjach) — to uproszczenie względem Androida.

4. **Keychain vs DataStore** — jak wyżej, trzeba to zrobić poprawnie od początku (Access Control: `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`).

5. **SwiftData dojrzałość** — SwiftData (od iOS 17) bywa kapryśne przy złożonych relacjach i migracjach schematu w porównaniu do dojrzałego Room. Jeśli plan treningowy ma zagnieżdżone relacje (Plan→Exercise→Set→SessionSet→RepResult, tak jak w Room), warto zrobić szybki spike (1 dzień) testujący SwiftData na tym dokładnym grafie relacji, zanim zacznie się budować na nim resztę appki. Fallback: **GRDB.swift** (SQLite) — więcej boilerplate'u, ale przewidywalne zachowanie i dojrzałe migracje.

6. **Reużycie logiki domenowej**: `CalculatePowerUseCase`, `Estimate1RMUseCase`, `GetVelocityZoneUseCase`, `RepPacketParser` to czysta logika bez zależności od Androida — do przepisania 1:1 na Swift (mechaniczne tłumaczenie, niskie ryzyko, można zrobić najpierw i pokryć testami jednostkowymi, które będą działać identycznie jak w Kotlinie).

---

## 5. Fazowanie implementacji

### Faza 0 — Setup i spike'i ryzyka (przed właściwą pracą)
- Nowy projekt Xcode (SwiftUI, iOS 17 deployment target), struktura folderów jak w sekcji 2
- Spike: SwiftData z grafem relacji Plan/Session/Rep (walidacja podejścia z p.5 wyżej)
- Spike: CoreBluetooth connect + notify na realnym ESP32 (`VBT-Vector`), odczyt i parsowanie pakietu FF02 na żywym urządzeniu — to najważniejszy dzień całego portu, bo tu leży całe ryzyko sprzętowe
- Design system: paleta kolorów, typografia, dark theme jako `Color`/`Font` assets

### Faza 1 — Fundament (Auth + API + Nawigacja)
- `APIClient` (URLSession async/await) + `Endpoint` enum, mapowanie 1:1 z `ApiService.kt`
- `KeychainStore` (token JWT), `AuthRepository`
- `NavigationStack` z role-aware routingiem (coach/athlete), odpowiednik `VbtNavGraph`
- LoginScreen/RegisterScreen + HomeScreen

### Faza 2 — BLE core
- `VbtBleManager` (CoreBluetooth), `BleConstants`, `RepPacketParser` (przepisanie 1:1 logiki z bajtowym parsowaniem — testy jednostkowe identyczne jak w Kotlinie)
- `HeartRateManager` (standard BLE HR service)
- ConnectScreen (skanowanie, lista urządzeń, status połączenia)

### Faza 3 — Workout (najbardziej złożony ekran)
- Freestyle mode (numpad kg jak w Vitruve: +2.5/+5/+10/+20/+25)
- Plan mode (lista ćwiczeń, progress dots, edycja serii, dodawanie serii)
- Live velocity (duża cyfra, strefa kolorystyczna, animacje 120Hz)
- Live Activity / Dynamic Island podczas treningu (odpowiednik foreground service + notyfikacji z Androida, ale lepszy UX)
- SessionSelect dla coacha (dla siebie / dla zawodnika)
- Bufor VelocityPoint (co 0.1s) + upload po sesji

### Faza 4 — Dane lokalne + sync
- SwiftData model (lub GRDB, zależnie od wyniku spike'u z Fazy 0)
- `SyncManager`: upload na pierwszym planie po zakończeniu sesji + BGTaskScheduler jako fallback dla zaległej kolejki (offline-first, jak `SessionSyncWorker`)

### Faza 5 — Pozostałe ekrany
- HistoryScreen + SessionDetailScreen (tabela rep-by-rep, wykres prędkości — `Swift Charts`, natywny odpowiednik dla wykresów, iOS 16+)
- PlanListScreen + PlanEditScreen
- AthleteListScreen + AthleteProfileScreen (taby: Kalendarz/Plany/Sesje)
- ScheduleScreen (widok tygodniowy)
- AnalyticsScreen

### Faza 6 — Polish pod iPhone 15 Pro
- Dynamic Island Live Activity dopracowana (kompaktowy/rozwinięty widok)
- Testy na fizycznym iPhone 15 Pro: BLE w tle z zablokowanym ekranem, ProMotion, Action Button (opcjonalnie)
- Haptics (`UIFeedbackGenerator`) przy wykryciu powtórzenia — czego Android nie miał, ale to tania i wartościowa dodatkowa jakość na iOS
- VoiceOver/accessibility pass (Apple częściej to weryfikuje przy review App Store)
- App Store: ikony, screenshoty, prywatność (BLE + dane treningowe → wymagana deklaracja w App Privacy)

---

## 6. Rekomendacje techniczne (decyzje do podjęcia na starcie)

- **Minimalny target: iOS 17.0** — pozwala używać `@Observable`, `SwiftData`, `NavigationStack`, `Swift Charts` bez kompromisów.
- **Bez frameworków side-party tam, gdzie Apple ma natywny odpowiednik** — URLSession zamiast Alamofire, CoreBluetooth bezpośrednio (ew. cienki wrapper), SwiftData zamiast Realm — mniejszy dependency footprint, łatwiejsze utrzymanie.
- **DI**: nie warto ciągnąć ciężkiego frameworka jak w Hilt — prosty `Environment`/`@Observable` + protokoły + manualny "composition root" w `VBTApp.swift` w zupełności wystarczy przy tej skali projektu (11 ekranów).
- **Współdzielenie logiki BLE między Android a iOS w przyszłości**: gdyby kiedyś chcieć KMP (Kotlin Multiplatform) dla `RepPacketParser`/use case'ów — to osobna, większa decyzja architektoniczna, nie rekomendowana na start (dodaje złożoność bez pilnej potrzeby, mając już działający port).

---

## 7. Szacowanie (orientacyjne, dla jednego dewelopera)

| Faza | Nakład |
|---|---|
| 0 — Setup + spike'i | 2-3 dni (spike BLE na realnym sprzęcie jest krytyczny) |
| 1 — Fundament | 3-4 dni |
| 2 — BLE core | 3-4 dni |
| 3 — Workout | 6-8 dni (najbardziej złożony ekran) |
| 4 — Dane lokalne + sync | 3-4 dni |
| 5 — Pozostałe ekrany | 6-8 dni |
| 6 — Polish + iPhone 15 Pro specyfika | 3-5 dni |
| **Razem** | **~26-36 dni roboczych** |

To odpowiada z grubsza nakładowi widocznemu w `ANDROID_V2_PLAN.md` (10+ sesji) — sensowne, bo to ten sam zakres funkcjonalny, inna platforma.

---

## 8. Następny krok

Rekomendowany start: **Faza 0**, a w niej najpierw spike CoreBluetooth ↔ ESP32 (`VBT-Vector`) na fizycznym iPhone 15 Pro. To jedyny punkt w całym planie, gdzie realny sprzęt może ujawnić niespodzianki (np. zachowanie iOS-owego BLE stacku przy częstych notify co 0.1s) — wszystko inne to standardowa, dobrze przetestowana ścieżka SwiftUI.
