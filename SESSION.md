# VBT Project Session State

## Architecture
- **Server**: Oracle Cloud `130.61.232.212`, SSH: `~/Downloads/Oracle_oci8/ssh-key-2025-12-19.key`
- **Backend**: FastAPI + PostgreSQL (Docker), port 8000, nginx on 80
- **Frontend**: React+Vite+TS+Tailwind+Recharts, port 3001
- **Android**: MVVM+Hilt+Compose+Room+Nordic BLE SDK, SDK at `~/android-sdk/`
- **ESP32**: PlatformIO at `/home/diabolik_zotac/Documents/vbt/`

## Paths
- Server code: `/home/diabolik_zotac/Documents/vbt/server/`
- Android: `/home/diabolik_zotac/Documents/vbt/android/vbt-app/`
- ESP32 src: `/home/diabolik_zotac/Documents/vbt/src/`
- Backend API: `server/backend/app/api/`
- Frontend pages: `server/frontend/src/pages/`

## Credentials
- Coach: `coach@vbt.pl` / `Coach123!`
- Athletes: `athlete1`/`Ath123!`, `jan.kowalski`/`Jan123!`, `anna.nowak`/`Anna123!`

## Docker (server-side, docker-compose v1)
```bash
ssh -i ~/Downloads/Oracle_oci8/ssh-key-2025-12-19.key ubuntu@130.61.232.212
cd /opt/vbt && docker-compose ps
# Update backend:
rsync -av server/backend/app/ ubuntu@130.61.232.212:/opt/vbt/backend/app/ -e "ssh -i ~/Downloads/Oracle_oci8/ssh-key-2025-12-19.key"
ssh ... "docker cp /opt/vbt/backend/app vbt_backend_1:/app/"
# Update frontend:
rsync -av server/frontend/src/ ubuntu@130.61.232.212:/opt/vbt/frontend/src/ -e "ssh -i ~/Downloads/Oracle_oci8/ssh-key-2025-12-19.key"
ssh ... "docker exec vbt_frontend_1 sh -c 'cd /app && npm run build'"
```

## Android Build & Emulator
```bash
cd /home/diabolik_zotac/Documents/vbt/android/vbt-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# Emulator AVD: VBT (android-34/google_apis/x86_64)
ANDROID_SDK_ROOT=~/android-sdk ~/android-sdk/emulator/emulator -avd VBT -no-audio -gpu swiftshader_indirect &
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/android-sdk/platform-tools/adb shell am start -n com.vbt.app/.MainActivity
```

## Key Rules
- **Haiku for execution, Sonnet for planning**
- docker-compose v1 (not `docker compose`)
- Java 17 for Android (JAVA_HOME=/usr/lib/jvm/java-17-openjdk)
- bcrypt directly (not passlib)
- Container code update: rsync to host → `docker cp` into container

## Android App - Screens
- **Login**: server auth, JWT in SharedPreferences (`PreferencesManager`)
- **Home**: BLE connect, Start Workout (freestyle/plan), nav buttons
- **Calendar/Schedule**: coach-scheduled sessions from GET /calendar, "Start Workout" button on scheduled entries
- **Workout**: live BLE velocity (2 decimal places), per-rep cards, plan exercise picker
- **Plans/History/Exercises**: local Room DB

## Android - Server API Layer
- Base URL: `http://130.61.232.212/api/`
- `data/remote/ApiService.kt` — endpoints: login, getCalendar, createSession
- `di/NetworkModule.kt` — Retrofit+OkHttp Hilt module
- `PreferencesManager.kt` — JWT token storage
- `AuthRepository.kt` — login/logout
- `WorkoutViewModel.syncToServer()` — posts session after finish

## ESP32 BLE Packet (16 bytes, LITTLE_ENDIAN)
- bytes 0-1: **meanVelocity** (uint16, m/s × 1000) ← changed from peak to mean
- bytes 2-3: distance (uint16, m × 1000)
- bytes 4-5: duration (uint16, ms)
- bytes 6-9: timestamp (uint32, ms from boot)
- bytes 10-11: repIndex (uint16)
- bytes 12-15: reserved
Android parser in `VbtBleManager.parseRepResult()` reads this layout correctly.

## ESP32 Changes
- `lift_detector.h`: `LiftResult` has `maxVelocity` (peak) + `meanVelocity` (average)
- `lift_detector.cpp`: accumulates `velocitySum`/`velocitySampleCount` during lift → mean = sum/count
- `ble_server.cpp`: sends `meanVelocity` in bytes 0-1 (not maxVelocity)

## Backend API Endpoints
- POST `/api/auth/login` → `{access_token, user}`
- GET `/api/users/athletes`, `/api/users/athletes/{id}`
- GET/POST `/api/exercises`
- GET/POST `/api/plans`, PATCH `/api/plans/{id}`
- GET/POST `/api/calendar` (GET filters by athlete for athlete role)
- POST `/api/sessions` (athlete creates session with reps array)
- GET `/api/analytics/velocity-trend?athlete_id&exercise_id&days`
- GET `/api/analytics/1rm-progress?athlete_id&exercise_id`
- GET `/api/analytics/volume?athlete_id&days`
- GET `/api/analytics/compare-athletes?athlete_ids=1,2&exercise_id&days`
- GET `/api/analytics/session-detail?session_id` ← NEW
- GET `/api/analytics/sessions-list?athlete_id&exercise_id&days` ← NEW
- GET `/api/analytics/export-csv?athlete_id&date_from&date_to` ← NEW
- GET `/api/dashboard/stats`, `/api/dashboard/recent-sessions`

## Frontend Analytics Page
New AnalyticsPage.tsx with 3 sections:
1. **Session Detail**: per-rep bar chart (color by velocity zone), metric toggle (mean/peak/power), set breakdown
2. **Compare Sessions (Overlay)**: multi-select sessions → overlay line chart per rep
3. **Export CSV**: athlete + date range → download .csv

## SQLAlchemy Known Fixes
- CalendarEntry: two FKs to users → `foreign_keys=[athlete_id]` (column object, not string)
- User.sessions: `foreign_keys="[WorkoutSession.athlete_id]"`

## TODO
- [ ] Heart rate monitor (BLE opaski) — scan for HR service UUID, save to session logs
- [ ] Athlete analytics tab in AthleteProfilePage (web)
- [ ] Athlete plan creation/modification in web (currently coach-only)
- [ ] Android: stats/analytics screen for athletes
