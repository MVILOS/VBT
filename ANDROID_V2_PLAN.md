# VBT Android App — Plan Przepisania od Nowa

**Data:** 2026-04-15  
**Cel:** Kompletna aplikacja Android kompatybilna z serwerem VBT, obsługa ról coach/athlete, BLE (ESP32 + HR opaski), zaawansowane dane prędkości.

---

## FAZA 1 — FUNDAMENT (Auth + API + Nawigacja)

### 1.1 Temat / Motyw (Theme)
- [ ] `Color.kt` — dark theme, teal akcent (#4ECDC4), tło (#0F0F0F / #1A1A1A), surface (#242424)
- [ ] `Theme.kt` — MaterialTheme dark, bez dynamicznych kolorów
- [ ] `Type.kt` — typografia (grube nagłówki jak Vitruve)

### 1.2 Auth Layer
- [ ] `PreferencesManager.kt` — DataStore: token JWT, user_id, username, role (coach/athlete)
- [ ] `AuthRepository.kt` — `login(username, password)`, `logout()`, `getStoredUser()`
- [ ] `ApiService.kt` — Retrofit interface, wszystkie endpointy serwera:
  - POST `/auth/login`
  - GET/POST `/exercises`
  - GET/POST/PUT/DELETE `/plans`, `/plans/{id}`, `/plans/{id}/assign/{athleteId}`
  - GET/POST `/calendar`, PUT/DELETE `/calendar/{id}`
  - GET/POST `/sessions`, GET `/sessions/{id}`
  - GET `/users/athletes`, GET `/users/athletes/{id}`
  - GET `/analytics/velocity-trend`, `/analytics/1rm-progress`
  - POST `/reps/{id}/velocity-trace` ← **NOWY endpoint do dodania w serwerze**
- [ ] `NetworkModule.kt` — Hilt: Retrofit + OkHttp z interceptorem JWT

### 1.3 Nawigacja
- [ ] `VbtNavGraph.kt` — role-aware routing:
  ```
  Login → Home
  Home → [Connect, Workout, Plans, Schedule, Athletes(coach), History]
  Workout → [SessionSelect(coach), ActiveWorkout]
  Athletes → AthleteProfile
  Plans → PlanEdit
  ```
- [ ] `BottomNavBar.kt` — ikony: Home | Workout | Plans | Schedule | [Athletes(coach)]

---

## FAZA 2 — EKRANY WSPÓLNE (athlete + coach)

### 2.1 LoginScreen
- [ ] `LoginScreen.kt` + `LoginViewModel.kt`
- Dark tło, logo/tytuł, pola username + password
- "Zaloguj" → POST `/auth/login` → zapis tokenu + roli → nawigacja do Home
- Obsługa błędów (wrong credentials)

### 2.2 HomeScreen
- [ ] `HomeScreen.kt` + `HomeViewModel.kt`
- Nagłówek: "Cześć, {username}" + chip roli (COACH / ATHLETE)
- Status BLE (ESP32 connected/disconnected — zielona/szara kropka)
- Duży przycisk "NOWY TRENING" (jak Vitruve START button)
- Kafelki: Ostatnia sesja, Zaplanowane dziś

### 2.3 ConnectScreen (BLE)
- [ ] `ConnectScreen.kt` + `ConnectViewModel.kt`
- Sekcja ESP32: scan → lista urządzeń → connect → status
- Sekcja HR Monitor (opcjonalna): scan po UUID 0x180D → connect → wyświetl BPM
- Oba połączenia działają równolegle i niezależnie

### 2.4 WorkoutScreen — KLUCZOWY EKRAN
- [ ] `WorkoutScreen.kt` + `WorkoutViewModel.kt`
- **Coach flow**: przed startem → `SessionSelectDialog` ("Dla kogo?": siebie / wybór zawodnika)
- **Plan mode**:
  - Lista ćwiczeń z planu (karty jak Vitruve Builder)
  - Progress dots (wypełnione = ukończona seria)
  - Tap na ćwiczenie → startuje serię
  - **EDYCJA**: długi tap / ikona ołówka na serii → dialog: zmień kg / reps
  - **DODAJ SERIĘ**: przycisk "+" na dole karty → dodaje nową serię powyżej planu (auto)
- **Freestyle mode**: picker ćwiczenia → numpad kg (styl Vitruve: +2.5 / +5 / +10 / +20 / +25)
- **Live pomiar**:
  - Duża cyfra prędkości (m/s)
  - Strefa prędkości (kolor)
  - HR (jeśli połączone)
  - Historia powtórzeń bieżącej serii
- **Pauza**: fioletowy banner "Zmień obciążenie"
- **Zaawansowane dane (coach)**: bufor VelocityPoint[] co 0.1s per powtórzenie
- **Finish**: upload sesji + rep results + velocity traces (coach) do serwera

### 2.5 HistoryScreen
- [ ] `HistoryScreen.kt` + `HistoryViewModel.kt`
- Lista sesji (athlete widzi swoje, coach widzi swoje + może filtrować po zawodniku)
- Data, ćwiczenie, liczba rep, czas trwania

### 2.6 SessionDetailScreen
- [ ] `SessionDetailScreen.kt` + `SessionDetailViewModel.kt`
- Rep-by-rep tabela: set | rep | kg | mean vel | peak vel | 1RM est.
- Wykres prędkości (jeśli są velocity traces — tylko coach)
- 1RM progress

---

## FAZA 3 — EKRANY PLANÓW

### 3.1 PlanListScreen
- [ ] `PlanListScreen.kt` + `PlanListViewModel.kt`
- Karty planów (nazwa, liczba ćwiczeń, assigned_to)
- Coach: przycisk "Nowy Plan", assign do zawodnika, "Schedule →"
- Athlete: widzi swoje plany, przycisk "Start"

### 3.2 PlanEditScreen (coach)
- [ ] `PlanEditScreen.kt` + `PlanEditViewModel.kt`
- Formularz: nazwa planu, opis, assign do zawodnika (opcja)
- Lista ćwiczeń: picker (jak Vitruve Pick Exercise), "New +" inline
- Dla każdego ćwiczenia: tabela serii (set_number, reps, kg, rest_s, vel_min-max)
- Dodaj/usuń ćwiczenie, dodaj/usuń serię

---

## FAZA 4 — EKRANY COACH-ONLY

### 4.1 AthleteListScreen
- [ ] `AthleteListScreen.kt` + `AthleteListViewModel.kt`
- Lista zawodników (avatar inicjałów, username, email, status)
- Tap → AthleteProfileScreen

### 4.2 AthleteProfileScreen
- [ ] `AthleteProfileScreen.kt` + `AthleteProfileViewModel.kt`
- Tabs: Kalendarz | Plany | Sesje
- **Kalendarz tab**: tygodniowy grid (jak web), tap dnia → dodaj trening
- **Plany tab**: przypisane plany, "Przypisz Plan", "Schedule in Calendar"
- **Sesje tab**: lista sesji zawodnika, tap → SessionDetail

### 4.3 ScheduleScreen (Training Schedule)
- [ ] `ScheduleScreen.kt` + `ScheduleViewModel.kt`
- Tygodniowy widok wszystkich zawodników
- Filtr po zawodniku (chip bar)
- Tap komórki → AddCalendarEntryDialog (wybierz zawodnika, plan, datę)

---

## FAZA 5 — ZAAWANSOWANE DANE PRĘDKOŚCI

### 5.1 Backend — nowy endpoint
- [ ] `server/backend/app/api/sessions.py` — dodać:
  - Model `RepVelocityTrace`: `rep_result_id`, `points: [{timestamp_ms, velocity_ms}]`
  - `POST /sessions/{session_id}/reps/{rep_id}/velocity-trace`
  - `GET /sessions/{session_id}/reps/{rep_id}/velocity-trace`
- [ ] `server/backend/app/models/__init__.py` — nowa tabela `rep_velocity_traces`

### 5.2 Android — zbieranie danych
- [ ] `VbtBleManager.kt` — buforowanie surowych próbek z częstotliwością 0.1s
- [ ] `WorkoutViewModel.kt` — `currentRepVelocityBuffer: MutableList<VelocityPoint>`
- [ ] Reset bufora przy każdym nowym rep, upload po zakończeniu rep (coach only)

---

## FAZA 6 — POLISH & SZCZEGÓŁY

- [ ] **Numpad kg**: własny composable (jak Vitruve: wielkie klawisze + +2.5/+5/+10/+20/+25)
- [ ] **Ikony ćwiczeń**: piktogramy (Squat, Bench, Deadlift, Clean...) lub kategoria
- [ ] **Offline mode**: Room jako cache, sync przy połączeniu
- [ ] **Permissions**: BLE scan / connect (Android 12+: BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
- [ ] **Animacje**: velocity zone color transition, rep count bounce
- [ ] **Feedback głosowy**: Text-to-Speech dla prędkości (opcjonalne)

---

## KOLEJNOŚĆ IMPLEMENTACJI (sesja po sesji)

```
✅ Sesja 1: Theme + Auth + LoginScreen + NavigationGraph + HomeScreen
✅ Sesja 2: ConnectScreen (ESP32 BLE + HR Monitor)
✅ Sesja 3: WorkoutScreen core (freestyle + live velocity)
✅ Sesja 4: WorkoutScreen plan mode (picker, edycja serii, auto-add)
✅ Sesja 5: WorkoutScreen coach extensions (SessionSelect, velocity buffer, upload)
✅ Sesja 6: PlanListScreen + PlanEditScreen
✅ Sesja 7: HistoryScreen + SessionDetailScreen
✅ Sesja 8: AthleteListScreen + AthleteProfileScreen
✅ Sesja 9: ScheduleScreen
✅ Sesja 10: Backend endpoint velocity-trace + model RepVelocityTrace
⬜ Sesja 11: HR Monitor BLE (stub w ConnectScreen — pełna implementacja do weryfikacji)
⬜ Sesja 12: Polish + kompilacja + bugfixes (po pierwszym buildzie)
```

## STATUS: Kod kompletny — wymaga kompilacji i testowania

---

## ISTNIEJĄCE PLIKI DO ZACHOWANIA / ROZSZERZENIA

| Plik | Akcja |
|------|-------|
| `data/ble/VbtBleManager.kt` | Rozszerzyć o HR + buforowanie velocity |
| `data/ble/BleConstants.kt` | Dodać UUID HR Service |
| `data/ble/RepFromDevice.kt` | Dodać `velocityPoints: List<VelocityPoint>` |
| `data/local/VbtDatabase.kt` | Dodać nowe encje (CalendarEntry, User cache) |
| `data/remote/ApiService.kt` | Przepisać — wszystkie nowe endpointy |
| `data/remote/ApiModels.kt` | Przepisać — nowe modele (User, CalendarEntry, VelocityTrace) |
| `di/NetworkModule.kt` | Rozszerzyć o JWT interceptor |
| `domain/model/VelocityZone.kt` | Zachować |
| `domain/usecase/*` | Zachować + dodać nowe |
| `ui/theme/*` | Przepisać (dark + teal) |

---

## SERWER — ZMIANY POTRZEBNE

1. **Bug fix** ✅ `calendar.py` — GET /calendar dla coach zwraca wszystkich zawodników
2. **Nowy endpoint** `POST /sessions/{session_id}/reps/{rep_id}/velocity-trace` — zapis danych 0.1s
3. **Nowy model** `RepVelocityTrace` — tabela w DB
4. **Opcjonalnie**: `GET /users/me` — zwraca aktualnego usera (coach_id, role)

---

## DEFINICJA "GOTOWE"

- [ ] Athlete może zalogować się, wybrać plan, wykonać trening (edytując kg/serie), zobaczyć historię
- [ ] Coach może zalogować się, nagrać sesję dla siebie lub zawodnika, zapisać velocity traces
- [ ] Coach może tworzyć i edytować plany treningowe
- [ ] Coach może planować treningi w kalendarzu zawodnika
- [ ] BLE ESP32 działa jak dotychczas
- [ ] HR Monitor podłącza się opcjonalnie
- [ ] Aplikacja synchronizuje dane z serwerem przez REST API
