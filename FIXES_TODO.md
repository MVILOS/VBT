# VBT - Lista poprawek do zrobienia

**Data testu:** 2026-04-25  
**Testowane przez:** Claude Code (testy na telefonie + API)

---

## BUGI DO NAPRAWIENIA

### BUG 0 — Schedule → "Rozpocznij trening" otwiera nowy trening zamiast zaplanowanego ✅ NAPRAWIONO i ZWERYFIKOWANO na telefonie (2026-04-26)
**Pliki:** `VbtNavGraph.kt`, `ScheduleScreen.kt`, `WorkoutViewModel.kt`, `WorkoutScreen.kt`

**Przyczyna #1 (timing):** `planId` odczytywany async w ViewModel → IDLE pokazywało przycisk zanim plan się załadował

**Przyczyna #2 (główna):** Wpisy w kalendarzu mają `plan_id = null` → nawigacja szła do plain `"workout"` (bez planId) → IDLE mode → użytkownik musiał kliknąć ponownie

**Naprawa:**
- `VbtNavGraph.kt`: Nowa trasa `WORKOUT_FROM_SCHEDULE` z parametrami `calendarEntryId` + `athleteId`. Schedule zawsze używa tej trasy
- `ScheduleScreen.kt`: `onStartWorkout` przekazuje `(planId, calendarEntryId, athleteId)` zamiast tylko `planId`
- `WorkoutViewModel.kt`: Gdy `calendarEntryId > 0` → pomiń IDLE i SESSION_SELECT:
  - pre-select atlet z `athleteId`
  - jeśli `planId > 0` → załaduj plan → ACTIVE mode
  - jeśli brak planu → przeskocz od razu do EXERCISE_PICKER (bez klikania)
- `WorkoutScreen.kt`: IDLE + `isLoading=true` → spinner zamiast przycisku

---

### BUG 1 — `/dashboard/recent-sessions` zwraca `athlete_name` zamiast `athlete_id` ✅ NAPRAWIONO (lokalnie, wymaga deploy)
**Plik:** `server/backend/app/api/dashboard.py`, linia ~74  
**Problem:** Response zawierało pole `athlete_name` (string), ale brakowało `athlete_id` (int).  
**Status:** Plik lokalny naprawiony — `athlete_id` dodane do dict. Wymaga wdrożenia na serwer (scp/rsync do /opt/vbt i docker compose restart backend).
```python
result.append({
    "id": s.id,
    "athlete_id": s.athlete_id,          # ← DODANE
    "athlete_name": athlete.username if athlete else "Unknown",
    "started_at": s.started_at.isoformat(),
    "duration_seconds": s.duration_seconds,
    "reps_count": len(s.reps),
})
```

---

### BUG 2 — Analytics endpoints nie działają dla coacha bez `?athlete_id=X`
**Plik:** `server/backend/app/api/analytics.py`, funkcja `resolve_athlete_id()`  
**Problem:** Gdy coach wywołuje `/api/analytics/1rm-progress` bez parametru `athlete_id`,  
funkcja zwraca `coach.id` (id=1) zamiast błędu lub danych wszystkich atletów.  
Coach nie ma własnych sesji → endpointy zwracają puste listy.  
**Naprawa (opcja A):** Zwrócić 400 Bad Request gdy coach nie poda `athlete_id`:
```python
def resolve_athlete_id(athlete_id: Optional[int], current_user: User) -> int:
    if current_user.role == "athlete":
        return current_user.id
    if athlete_id is None:
        raise HTTPException(status_code=400, detail="athlete_id required for coach role")
    return athlete_id
```
**Naprawa (opcja B - lepsza UX):** Zwrócić zagregowane dane wszystkich podwładnych atletów.

---

### BUG 3 — Duplikaty ćwiczeń w bazie danych
**Endpoint:** `GET /api/exercises`  
**Problem:** W bazie są duplikaty:
- "Power Clean" — id=5 (created_by=1) i id=7 (created_by=3)
- "Power Snach" — id=8 (created_by=3) i id=9 (created_by=1)
- "Snach" — id=6 (literówka — powinno być "Snatch")  
**Naprawa:** Dodać unique constraint na `(name, created_by)` lub globalnie na `name` dla ćwiczeń systemowych. Poprawić literówkę "Snach" → "Snatch".

---

## ANDROID APP — DO SPRAWDZENIA

### CHECK 1 — `HomeScreen` nie używa `/dashboard/recent-sessions`
Zweryfikowano że aplikacja Android aktualnie **nie wywołuje** endpointu `/dashboard/recent-sessions`.  
Jeśli w przyszłości zostanie dodane — upewnić się że kod czyta `athlete_name`, nie `athlete_id`.

### CHECK 2 — Analytics wywołania muszą przekazywać `athlete_id`
Gdy użytkownik jest coachem, wszystkie wywołania analytics muszą zawierać `?athlete_id=X`.  
Sprawdzić w: `ScheduleViewModel.kt`, `AthleteProfileViewModel.kt`, `HistoryViewModel.kt`.

---

## WERYFIKACJA SPÓJNOŚCI — WYNIKI OK

| Endpoint | Status | Uwagi |
|----------|--------|-------|
| GET /health | ✅ | Server działa |
| POST /auth/register | ✅ | Rejestracja działa |
| POST /auth/login | ✅ | JWT token OK |
| GET /auth/me | ✅ | Token walidacja OK |
| GET /exercises | ✅ | 9 ćwiczeń (ale duplikaty - patrz Bug 3) |
| GET /plans | ✅ | 3 plany, exercises zagnieżdżone |
| GET /calendar | ✅ | 82 wpisy |
| GET /users/athletes | ✅ | 4 atletów, coach_id poprawne |
| POST /sessions (symulacja BLE) | ✅ | Session 72 (15 repów), Session 73 (9 repów) |
| GET /sessions | ✅ | Lista sesji poprawna |
| GET /sessions/{id} | ✅ | Dane rep-by-rep spójne |
| GET /analytics/session-detail | ✅ | Labels S1R1..S3R5 poprawne |
| GET /analytics/1rm-progress | ⚠️ | Wymaga athlete_id dla coacha |
| GET /analytics/velocity-trend | ⚠️ | Wymaga athlete_id dla coacha |
| GET /analytics/volume | ⚠️ | Wymaga athlete_id dla coacha |
| GET /dashboard/stats | ✅ | sessions_this_week aktualizuje się po insercie |
| GET /dashboard/recent-sessions | ⚠️ | Brak athlete_id w response |

---

## DANE TESTOWE UTWORZONE

- User: `test_athlete` / `Test1234!` (id=6, role=athlete)
- Session 72: Squat 3×5 @100kg, athlete_id=6
- Session 73: Deadlift 3×3 @120kg, athlete_id=2 (athlete1)

---

## TOKEN COACH W APLIKACJI

Token wygasa: **2026-04-26 20:51:52** (jutro wieczór)  
Po wygaśnięciu → aplikacja będzie zwracać 401, trzeba zalogować się ponownie w UI.  
Credentials coach: `coach` / hasło nieznane (zresetować przez API jeśli potrzeba)
