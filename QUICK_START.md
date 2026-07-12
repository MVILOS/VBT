# VBT Auto-Resume - Szybki Start

## 🚀 Natychmiastowe uruchomienie

### 1. Synchronizuj projekt z serwerem
```bash
cd ~/Documents/VBT
./sync_to_server.sh
```

### 2. Uruchom automatyczne wznawianie (5h)
```bash
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

### 3. Sprawdź logi
```bash
tail -f auto_resume.log
```

**Gotowe!** System będzie automatycznie wznawiał sesję co 5 godzin.

---

## 📋 Codzienne komendy

### Przed rozpoczęciem pracy
```bash
./manual_resume.sh
```
↳ Synchronizuje projekt i pokazuje różnice

### Wyślij zmiany na serwer
```bash
./sync_to_server.sh
```
↳ Szybka synchronizacja rsync

### Sprawdź status auto-resume
```bash
pgrep -f auto_resume && echo "✓ Działa" || echo "✗ Nie działa"
tail -20 auto_resume.log
```

### Zatrzymaj auto-resume
```bash
pkill -f auto_resume_session
```

### Uruchom ponownie auto-resume
```bash
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

---

## 🔧 Konfiguracja (jednorazowa)

Jeśli chcesz zmienić czas wznawiania, edytuj `auto_resume_session.sh`:

```bash
SESSION_DURATION=$((5 * 60 * 60))  # Zmień 5 na inną liczbę godzin
CHECK_INTERVAL=$((10 * 60))         # Zmień 10 na inną liczbę minut
```

---

## ⚠️ Rozwiązywanie problemów

### Błąd SSH
```bash
chmod 600 ~/ssh-key-2025-12-19.key
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 "echo OK"
```

### Różne wersje lokalnie i na serwerze
```bash
./manual_resume.sh  # Pokaże różnice
```

### Pełny log
```bash
> auto_resume.log  # Wyczyść log
```

---

## 📚 Więcej informacji

- Pełna dokumentacja: `AUTO_RESUME_README.md`
- Status konfiguracji: `SETUP_COMPLETE.md`
- Wszystkie skrypty: `ls -la *.sh`

---

## 🎯 Typowy workflow

```bash
# Rano
cd ~/Documents/VBT
./manual_resume.sh

# Praca...
# (edycja kodu, testy, itp.)

# Commituj zmiany
git add .
git commit -m "Opis zmian"

# Synchronizuj z serwerem
./sync_to_server.sh

# Koniec dnia - zostaw auto-resume w tle
pgrep -f auto_resume && echo "Auto-resume działa - OK" || \
  (nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &)
```

---

## 📊 Status projektu

**Lokalnie:** `/home/diabolik/Documents/VBT`
**Serwer:** `ubuntu@130.61.232.212:~/VBT`

### Aktualne zmiany niezcommitowane:
- `platformio.ini` - zmiana board na nodemcu-32s
- `src/main.cpp` - optymalizacje i zmiany konfiguracji

### Aby commitować:
```bash
git add platformio.ini src/main.cpp
git commit -m "Update ESP32 config for NodeMCU-32S"
./sync_to_server.sh
```
