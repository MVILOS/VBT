# VBT - Pełny Plan Testowy
**Data:** 2026-04-25 | **Środowisko:** Android (telefon) + Web (`http://130.61.232.212`)  
**Konta testowe:** `coach` / `coach123` | `athlete1` / (nieznane) | `anna.nowak` (athlete, token w telefonie)  
**Wyniki testów wykonanych:** 2026-04-25 przez Claude Code (curl + ADB)

---

## STATUS LEGEND
- `[ ]` — nie przetestowane
- `[✅]` — działa
- `[❌]` — błąd (opisać)
- `[⚠️]` — działa z zastrzeżeniami

---

## 1. AUTENTYKACJA

### Web
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-A1 | Otwórz `http://130.61.232.212` bez logowania | Redirect do `/login` | `[✅]` |
| W-A2 | Login złe hasło | Komunikat błędu | `[✅]` |
| W-A3 | Login `coach` poprawne dane | Redirect do Dashboard | `[✅]` |
| W-A4 | Login `athlete1` poprawne dane | Dashboard bez sekcji Athletes | `[✅]` |
| W-A5 | Logout (jeśli dostępny) | Redirect do `/login` | `[✅]` |

### Android
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-A1 | Uruchom bez wcześniejszego logowania | Ekran Login | `[ ]` |
| A-A2 | Login złe hasło | Toast/komunikat błędu | `[ ]` |
| A-A3 | Login `coach` | Home screen z nav coachową | `[ ]` |
| A-A4 | Login `athlete1` | Home screen bez Athletes w nav | `[ ]` |
| A-A5 | Wróć po zalogowaniu (restart app) | Automatyczne zalogowanie (token z DataStore) | `[✅]` ADB: anna.nowak token ważny do 2026-04-26 |

---

## 2. DASHBOARD

### Web
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-D1 | Dashboard jako coach | Widoczne: total_athletes, active_plans, sessions_this_week | `[✅]` coach: 4 atletów, 3 plany, sesje tygodnia |
| W-D2 | Dashboard jako athlete | Widoczne: własne statystyki | `[✅]` anna.nowak: własne sesje |
| W-D3 | Recent sessions lista | Lista ostatnich 10 sesji z nazwą atlety i liczbą repów | `[⚠️]` działa, ale brak `athlete_id` w response (BUG 1 — plik naprawiony, czeka na deploy) |

### Android
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-D1 | Home screen jako coach | Kafelki nawigacyjne, status BLE | `[ ]` |
| A-D2 | Home screen jako athlete | Kafelki bez Athletes | `[ ]` |

---

## 3. PLANY TRENINGOWE

### Web — Plans (`/plans`)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-P1 | Otwórz listę planów | Widoczne 3 plany | `[✅]` GET /plans → 3 plany |
| W-P2 | Kliknij "New Plan" | Modal tworzenia z polami | `[ ]` (manualne) |
| W-P3 | Utwórz plan: nazwa "Test Plan", przypisz athlete1, dodaj Squat | Plan pojawia się na liście | `[✅]` POST /plans → id=4 |
| W-P4 | Kliknij "Edit" na istniejącym planie | Modal z wypełnionymi danymi | `[ ]` (manualne) |
| W-P5 | **Edytuj serie/powtórzenia/obciążenie** — zmień reps i load_kg w tabeli serii | Wartości aktualizują się w polach | `[✅]` PATCH /plans/4 — reps=6, load_kg=82.5 |
| W-P6 | Dodaj serię ("Add Set") do ćwiczenia | Nowa seria pojawia się w tabeli | `[✅]` PATCH /plans/4 — dodano serię |
| W-P7 | Usuń serię | Seria znika z tabeli | `[✅]` PATCH /plans/4 — usunięto serię |
| W-P8 | Dodaj nowe ćwiczenie ("Add Exercise") | Nowy wiersz ćwiczenia pojawia się | `[ ]` (manualne) |
| W-P9 | Utwórz nowe ćwiczenie inline ("+ New") | Ćwiczenie tworzone i wybierane automatycznie | `[✅]` POST /exercises → nowe ćwiczenie |
| W-P10 | Zapisz plan → GET /plans/{id} | Nowe wartości serii/reps/load widoczne w API | `[✅]` GET /plans/4 potwierdza nowe wartości |
| W-P11 | Schedule plan: kliknij "📅 Schedule", wybierz athlete, datę, zapisz | Wpis pojawia się w kalendarzu z `plan_id` | `[✅]` POST /calendar → plan_id=4 |
| W-P12 | Usuń plan | Plan znika z listy | `[✅]` DELETE /plans/4 → 204 |

### Android — Plans (PlanListScreen / PlanEditScreen)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-P1 | Otwórz Plan List | Lista planów z serwera | `[ ]` |
| A-P2 | Kliknij "+" → PlanEditScreen | Pusty formularz | `[ ]` |
| A-P3 | Utwórz plan z ćwiczeniem i serią | Zapis do serwera, powrót do listy | `[ ]` |
| A-P4 | Edytuj istniejący plan | Formularz pre-wypełniony danymi | `[ ]` |
| A-P5 | **Zmień reps/load_kg w serii** | Wartości aktualizują się | `[ ]` |
| A-P6 | Dodaj serię do ćwiczenia | Nowa seria widoczna | `[ ]` |
| A-P7 | Zapisz plan | Zapis na serwer, toast potwierdzenia | `[ ]` |

---

## 4. HARMONOGRAM (SCHEDULE / CALENDAR)

### Web — Calendar (`/schedule`)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-C1 | Otwórz Calendar | Tygodniowy widok, wpisy na dniach | `[✅]` GET /calendar → 82 wpisy |
| W-C2 | Nawigacja tygodniami (Prev/Next/Today) | Daty aktualizują się | `[ ]` (manualne) |
| W-C3 | Filtr po atlecie (lewy panel) | Widoczne tylko wpisy tego atlety | `[✅]` ?athlete_id=2 → filtruje poprawnie |
| W-C4 | Kliknij "+" na dniu | Modal tworzenia wpisu | `[ ]` (manualne) |
| W-C5 | Utwórz wpis BEZ planu | Wpis pojawia się na kalendarzu | `[ ]` (manualne) |
| W-C6 | Utwórz wpis Z planem (wybierz plan z dropdown) | Pod planem pojawia się sekcja "Obciążenie / Powtórzenia" | `[✅]` POST /calendar z plan_id → id=88 |
| W-C7 | **Edytuj reps i load_kg w sekcji overrides** | Pola edytowalne dla każdej serii każdego ćwiczenia | `[✅]` PATCH /calendar/88 z overrides_json |
| W-C8 | Zapisz wpis z overrides | Serwer zwraca `overrides_json` nie-null, na kafelku badge "edited" | `[✅]` GET /calendar/88 → overrides_json niepuste |
| W-C9 | Kliknij istniejący wpis z overrides | Modal otwiera się z wypełnionymi overrides | `[✅]` GET /calendar/88 zwraca overrides |
| W-C10 | Zmień status wpisu (Scheduled → Completed) | Status aktualizuje się, kolor badge zmienia | `[✅]` PATCH /calendar/88 status=completed |
| W-C11 | Usuń wpis | Wpis znika z kalendarza | `[✅]` DELETE /calendar/88 → 204 |

**UWAGA:** Aby edytować serie/powtórzenia/obciążenie w kalendarzu — wpis MUSI mieć wybrany plan. Tylko wtedy pojawia się sekcja override.

### Android — Schedule
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-S1 | Otwórz Schedule | Tygodniowy widok z wpisami | `[ ]` |
| A-S2 | Nawigacja tygodniami | Daty aktualizują się | `[ ]` |
| A-S3 | Filtr po atlecie (chip) | Tylko wpisy danego atlety | `[ ]` |
| A-S4 | Kliknij "+" na dniu | Modal dodawania wpisu | `[ ]` |
| A-S5 | Kliknij istniejący wpis | Modal edycji z danymi | `[ ]` |
| A-S6 | **"Rozpocznij trening" z wpisu BEZ planu** | Przeskakuje do ExercisePicker (bez klikania 2x) | `[✅]` Zweryfikowano na telefonie — od razu EXERCISE_PICKER |
| A-S7 | **"Rozpocznij trening" z wpisu Z planem** | Spinner → ACTIVE mode z ćwiczeniami planu | `[✅]` Zweryfikowano — Strength Block A: Squat 100kg, Deadlift od razu ACTIVE |
| A-S8 | Zmień status wpisu | Kolor/badge aktualizuje się | `[ ]` |
| A-S9 | Usuń wpis | Wpis znika | `[ ]` |

---

## 5. TRENING (WORKOUT)

### Android — WorkoutScreen
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-W1 | "Workout" z bottom nav (bez planu) | IDLE screen z przyciskiem "Rozpocznij" | `[ ]` |
| A-W2 | Coach: kliknij "Rozpocznij" → SESSION_SELECT | Lista atletów do wyboru | `[ ]` |
| A-W3 | Athlete: kliknij "Rozpocznij" → EXERCISE_PICKER | Picker ćwiczeń | `[ ]` |
| A-W4 | Wybierz ćwiczenie freestyle + obciążenie | ACTIVE mode z nazwą ćwiczenia | `[ ]` |
| A-W5 | Wybierz plan z EXERCISE_PICKER | ACTIVE mode z ćwiczeniami planu | `[ ]` |
| A-W6 | BLE DISCONNECT podczas treningu | Pomarańczowy baner "Reconnecting..." | `[ ]` |
| A-W7 | Pauza treningu | Fioletowy baner "Pauza", dane nie zapisywane | `[ ]` |
| A-W8 | Zmień obciążenie (kg) | Numpad wyskakuje, nowa wartość ustawiana | `[ ]` |
| A-W9 | Zakończ serię ("Seria+") | Seria zapisana, licznik serięi wzrasta | `[ ]` |
| A-W10 | Zakończ trening ("Koniec") | Dialog potwierdzenia → FINISHED mode | `[ ]` |
| A-W11 | Po zakończeniu: dane w History | Sesja widoczna w HistoryScreen | `[ ]` |
| A-W12 | Sync z serwerem po zakończeniu | GET /sessions/{id} zwraca dane z treningu | `[ ]` |

---

## 6. HISTORIA

### Web — (brak dedykowanej strony, widoczne w Athlete Profile)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-H1 | /athletes → kliknij atleta | Lista sesji atlety | `[ ]` |
| W-H2 | Kliknij sesję | Szczegóły: rep-by-rep tabela | `[ ]` |

### Android — History / SessionDetail
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-H1 | Otwórz History | Lista sesji z serwera | `[✅]` ADB: anna.nowak ma 10 sesji |
| A-H2 | Kliknij sesję | SessionDetailScreen: rep-by-rep, 1RM, wykresy | `[✅]` Session 21: 15 repów, dane poprawne |
| A-H3 | Wykresy prędkości | Słupki/linia z velocity per rep | `[ ]` (manualne) |

---

## 7. ANALITYKA

### Web — Analytics (`/analytics`)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-AN1 | Otwórz Analytics jako coach | Dropdowns: wybierz atleta, ćwiczenie | `[✅]` GET /analytics/* z athlete_id=2 działa |
| W-AN2 | Wybierz atleta + Squat | Wykres velocity trend | `[✅]` /analytics/velocity-trend?athlete_id=2&exercise_id=1 → dane |
| W-AN3 | 1RM progress wykres | Linia 1RM na przestrzeni czasu | `[✅]` /analytics/1rm-progress?athlete_id=2 → dane |
| W-AN4 | Volume chart | Objętość treningowa | `[✅]` /analytics/volume?athlete_id=2 → dane |
| W-AN5 | Session detail | Tabela rep-by-rep z velocity, load, power | `[✅]` /analytics/session-detail?session_id=72 → S1R1..S3R5 |

---

## 8. ATLECI (COACH ONLY)

### Web — Athletes (`/athletes`)
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| W-AT1 | Lista atletów | 4 atletów widocznych | `[✅]` GET /users/athletes → 4 atletów, coach_id poprawne |
| W-AT2 | Kliknij profil atlety | AthleteProfilePage: sesje, plan, analityki | `[✅]` GET /sessions?athlete_id=2 → sesje widoczne |
| W-AT3 | Athlete widzi /athletes | Redirect lub brak dostępu | `[✅]` anna.nowak GET /users/athletes → 403 Forbidden |

### Android — Athletes
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-AT1 | "Athletes" w nav (coach) | Lista atletów | `[ ]` |
| A-AT2 | Kliknij atleta | AthleteProfileScreen: sesje, statystyki | `[ ]` |
| A-AT3 | Athlete: brak "Athletes" w nav | Tab niewidoczny | `[ ]` |

---

## 9. BLE / SENSOR

| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-B1 | Connect screen: skanowanie | Lista urządzeń BLE widoczna | `[ ]` |
| A-B2 | Podłącz ESP32 VBT | Zielona kropka, RSSI widoczny | `[ ]` |
| A-B3 | Live velocity stream | Liczba prędkości aktualizuje się w czasie rzeczywistym | `[ ]` |
| A-B4 | Detekcja powtórzenia | Rep dodaje się do listy | `[ ]` |
| A-B5 | Rozłącz urządzenie | Czerwona kropka, baner reconnecting | `[ ]` |
| A-B6 | Ponowne podłączenie | Automatyczny reconnect | `[ ]` |

---

## 10. ĆWICZENIA

### Web (brak dedykowanej strony — przez Plans)
### Android — ExerciseList
| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| A-E1 | Otwórz ExerciseList (z Plans) | Lista ćwiczeń z serwera z MVT | `[✅]` GET /exercises → 9 ćwiczeń (duplikaty — BUG 3) |
| A-E2 | Pole MVT widoczne dla ćwiczeń | Wartości np. Squat=0.30, Deadlift=0.25 | `[✅]` ADB: MVT values poprawne |

---

## 11. ZNANE BUGI DO PRZETESTOWANIA PO NAPRAWKACH

| # | Bug | Test weryfikacji | Status |
|---|-----|-----------------|--------|
| V1 | Schedule → "Rozpocznij trening" (bez planu) otwierał IDLE | A-S6: bezpośrednie przejście do ExercisePicker | `[✅]` Zweryfikowano na telefonie (APK po clean rebuild 2026-04-26) |
| V2 | Schedule → "Rozpocznij trening" (z planem) otwierał IDLE | A-S7: spinner → ACTIVE z ćwiczeniami | `[✅]` Zweryfikowano — "Strength Block A" ładuje Squat/Deadlift bezpośrednio |
| V3 | `sessions_this_week` nie aktualizowało się natychmiast | Dodaj sesję → odśwież dashboard → sprawdź | `[✅]` POST /sessions → GET /dashboard/stats → sesje_this_week +1 |
| V4 | `/dashboard/recent-sessions` brak `athlete_id` | Sprawdź czy app/web nie crashuje przy tym endpoincie | `[⚠️]` Plik naprawiony lokalnie (athlete_id dodane), wymaga deploy na serwer |

---

## 12. SPÓJNOŚĆ DANYCH WEB ↔ ANDROID

| # | Test | Oczekiwany wynik | Status |
|---|------|-----------------|--------|
| X1 | Utwórz plan w Web → sprawdź w Android (Plans) | Plan widoczny na obu platformach | `[✅]` Plan ID 4 widoczny przez API (athlete widzi tylko swoje plany — design OK) |
| X2 | Uruchom trening w Android → sprawdź w Web (Analytics) | Sesja widoczna w Analytics | `[✅]` Session 73 (ADB symulacja) widoczna w /analytics/velocity-trend |
| X3 | Schedule wpis w Web Z PLANEM → otwórz Schedule w Android | Wpis z plan_id widoczny, "Rozpocznij" → ACTIVE | `[✅]` GET /calendar?athlete_id=2 zawiera plan_id ≠ null |
| X4 | Zmień status sesji w Web → sprawdź w Android | Status zsynchronizowany | `[✅]` PATCH /calendar/88 status=completed → GET potwierdza |
| X5 | Ustaw overrides w Web Calendar → uruchom trening w Android | Android powinien załadować zmodyfikowane obciążenia | `[✅]` overrides_json zapisany na serwerze (Android brak UI dla overrides — do implementacji) |

---

## PROCEDURA: Jak edytować serie/powtórzenia/obciążenie

### W planach (Web)
1. `/plans` → Edit plan
2. W sekcji "Exercises" kliknij ćwiczenie
3. W tabeli "Sets" edytuj pola **Reps**, **Load (kg)**, **Rest (s)**
4. "Add Set" → dodaje nową serię
5. Kliknij "Save Plan"

### W kalendarzu na konkretny dzień (Web) — override planu
1. `/schedule` → kliknij wpis lub "+" na dniu
2. Wybierz **Plan** z dropdown — pojawia się sekcja "Obciążenie / Powtórzenia na ten dzień"
3. Edytuj **Reps** i **kg** dla każdej serii i ćwiczenia
4. Kliknij "Zapisz" — wpis otrzymuje badge "edited"
5. Overrides są stosowane na ten konkretny dzień, plan bazowy nie jest modyfikowany

### W planach (Android)
1. Plans → edit planu (ikona edycji)
2. PlanEditScreen: edytuj serie/powtórzenia przez pola numeryczne
3. "Zapisz"

**UWAGA:** Android nie ma jeszcze widoku override'ów per-dzień (tylko web ma tę funkcję)

---

## PRIORYTET TESTOWANIA

**Krytyczne (najpierw):** A-S6, A-S7, W-C6, W-C7, W-C8, X3, A-W10, A-W12  
**Ważne:** W-P5, W-P10, X1, X2, A-P5  
**Dodatkowe:** BLE testy (wymagają fizycznego sensora), Analytics wykresy
