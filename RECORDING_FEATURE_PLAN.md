# Nagrywanie podejścia z wypalonymi parametrami (OVR-style) — Android

**Cel:** podczas treningu/pomiaru nagrać wideo podejścia telefonem i wypalić w plik MP4
parametry VBT (prędkość, powtórzenia, moc, ciężar, strefa), gotowy do udostępnienia.
Zapis lokalny + eksport do galerii. Bez zmian na serwerze.

## Decyzje
- **Platforma:** tylko Android (Kotlin / Jetpack Compose).
- **Nakładka:** wypalona na stałe w pliku wideo (nie tylko podgląd na żywo).
- **Zapis:** lokalnie + galeria (MediaStore). Backend nietknięty.
- **Silnik wypalania:** AndroidX **Media3 Transformer** + `OverlayEffect` z dynamiczną
  `BitmapOverlay` (`getBitmap(presentationTimeUs)`) — zamiast ręcznego MediaCodec/OpenGL.
- **Kamera:** CameraX `VideoCapture` (FHD, wideo-only, bez audio → mniej uprawnień).
- **Synchronizacja czasu:** nie używamy zegara czujnika. W momencie startu nagrania
  bierzemy `t0 = SystemClock.elapsedRealtime()`; każde zdarzenie z BLE (nowe powtórzenie,
  próbka prędkości live) dostaje `t = elapsedRealtime() - t0`. Przy wypalaniu dla każdej
  klatki o czasie `pt` renderujemy stan nakładki wg ostatniego zdarzenia z `t <= pt`.

## Architektura (nowy pakiet `com.vbt.app.recording`)
1. **RecordingModels.kt** — `OverlaySnapshot` (repCount, ostatnia prędkość/moc, ciężar,
   nazwa ćwiczenia/zawodnika, strefa, prędkość live, timestampMs) + `OverlayTimeline`
   (posortowana lista snapshotów + `snapshotAt(timeUs)`).
2. **OverlayRenderer.kt** — czysta funkcja `render(snapshot, width, height): Bitmap`
   rysująca Canvas z designem nakładki (dolny pasek: duża prędkość + kolor strefy,
   licznik powtórzeń, moc; góra-lewo: ćwiczenie + ciężar + zawodnik). Motyw VbtTeal.
3. **MetricsOverlay.kt** — `BitmapOverlay` z Media3, `getBitmap(presentationTimeUs)`
   woła `OverlayRenderer.render(timeline.snapshotAt(pt))`.
4. **VideoOverlayProcessor.kt** — buduje `Transformer` + `EditedMediaItem` z `OverlayEffect`,
   uruchamia eksport surowego MP4 → MP4 z wypaloną nakładką, zwraca postęp/wynik.
5. **GallerySaver.kt** — zapis gotowego pliku do `MediaStore.Video` (Movies/VBT).
6. **SetRecorder.kt** — opakowanie CameraX `Recorder`/`VideoCapture`: start/stop, plik + t0.

## UI / integracja
- **RecordingViewModel** (Hilt) — wstrzykuje singletony `VbtBleManager` + `HeartRateManager`,
  czyta na żywo `liveVelocity` i `repResult` (te same strumienie co trening — SharedFlow/
  StateFlow obsługują wielu subskrybentów, więc sesja treningowa działa równolegle).
  Nazwę ćwiczenia, ciężar i zawodnika dostaje przez argumenty nawigacji z WorkoutScreen.
  Buduje `OverlayTimeline` w trakcie nagrywania; po stopie odpala `VideoOverlayProcessor`,
  potem `GallerySaver`, wystawia postęp.
- **RecordingScreen** — pełnoekranowy podgląd CameraX (`PreviewView` w `AndroidView`) +
  compose'owa nakładka na żywo (ten sam układ co wypalana) + przycisk REC + pasek postępu
  przetwarzania. Ekran nie gaśnie.
- **Nawigacja** — nowa trasa `record?exercise=&load=&athlete=`; przycisk "Nagraj" (ikona
  kamery) w trybie ACTIVE na WorkoutScreen otwiera ją z bieżącym kontekstem.

## Zależności / manifest
- `libs.versions.toml` + `build.gradle.kts`: CameraX (core/camera2/lifecycle/video/view),
  Media3 (transformer/effect/common), `accompanist-permissions` lub własny launcher uprawnień.
- Manifest: `android.permission.CAMERA`, `<uses-feature camera>`. Audio pomijamy.

## Kroki realizacji
1. Zależności + manifest.
2. `recording/` — modele, renderer, overlay, processor, gallery saver, recorder.
3. RecordingViewModel + RecordingScreen.
4. Wpięcie nawigacji + przycisk w WorkoutScreen.
5. Build (`./gradlew :app:assembleDebug`) + korekty. Walidacja na urządzeniu (kamera,
   BLE i wypalanie wymagają realnego sprzętu — jak przy firmware/BLE).

## Ryzyka
- Media3 Transformer wymaga zgodnej wersji (1.4.x) z compileSdk 34 — do weryfikacji w buildzie.
- Wydajność wypalania na słabszych telefonach (Transformer działa w tle, pokazujemy postęp).
- Rozdzielczość nakładki musi zgadzać się z rozdzielczością nagrania (renderujemy w rozmiarze
  klatki zwróconym przez Transformer / ustawionym w CameraX).
