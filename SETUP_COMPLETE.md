# ✓ System automatycznego wznawiania gotowy

## Status synchronizacji

**Lokalny projekt:** `/home/diabolik/Documents/VBT`
**Serwer:** `ubuntu@130.61.232.212:~/VBT`
**Status:** ✓ Zsynchronizowane

### Ostatnia synchronizacja
- Projekt VBT przesłany na serwer
- Repozytorium git zainicjalizowane na serwerze
- Wszystkie pliki źródłowe zsynchronizowane

## Dostępne skrypty

### 1. `sync_to_server.sh` - Synchronizacja projektu
Synchronizuje lokalny projekt VBT z serwerem.

```bash
./sync_to_server.sh
```

**Funkcje:**
- Sprawdza połączenie SSH
- Synchronizuje pliki (rsync)
- Inicjalizuje git jeśli nie istnieje
- Pokazuje status git na serwerze

---

### 2. `manual_resume.sh` - Ręczne wznowienie pracy
Synchronizuje projekt i przygotowuje do pracy.

```bash
./manual_resume.sh "Opcjonalny commit message"
```

**Funkcje:**
- Sprawdza lokalne zmiany
- Opcjonalnie commituje zmiany
- Synchronizuje z serwerem
- Porównuje wersje lokalne vs serwer
- Pokazuje status projektu

---

### 3. `auto_resume_session.sh` - Automatyczne wznawianie
Automatycznie wznawia sesję Claude Code co 5 godzin.

```bash
# W trybie blokującym (widoczne logi)
./auto_resume_session.sh

# W tle (zalecane)
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

**Parametry (edytowalne w pliku):**
```bash
SESSION_DURATION=$((5 * 60 * 60))  # 5 godzin
CHECK_INTERVAL=$((10 * 60))         # Sprawdzaj co 10 minut
```

**Funkcje:**
- Synchronizuje z serwerem przed wznowieniem
- Wznawia sesję Claude Code
- Loguje wszystkie operacje
- Działa w pętli nieskończonej

---

### 4. `setup_auto_resume.sh` - Konfiguracja początkowa
Konfiguruje system i testuje połączenie.

```bash
./setup_auto_resume.sh
```

**Funkcje:**
- Nadaje uprawnienia wykonywania
- Testuje połączenie SSH
- Synchronizuje projekt
- Opcjonalnie tworzy usługę systemd

---

## Szybki start

### Pierwsza konfiguracja (JUŻ WYKONANA ✓)

1. Uprawnienia ustawione ✓
2. Projekt zsynchronizowany ✓
3. Git zainicjalizowany na serwerze ✓

### Teraz możesz:

#### Opcja A: Uruchom automatyczne wznawianie
```bash
cd ~/Documents/VBT
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

#### Opcja B: Synchronizuj ręcznie
```bash
./manual_resume.sh
```

#### Opcja C: Tylko sync
```bash
./sync_to_server.sh
```

---

## Monitorowanie

### Sprawdź czy auto-resume działa:
```bash
# Znajdź proces
ps aux | grep auto_resume

# Zobacz PID
pgrep -f auto_resume_session

# Logi na żywo
tail -f ~/Documents/VBT/auto_resume.log

# Ostatnie 50 linii
tail -50 ~/Documents/VBT/auto_resume.log
```

### Zarządzanie procesem:
```bash
# Zatrzymaj
pkill -f auto_resume_session

# Uruchom ponownie
cd ~/Documents/VBT
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &

# Sprawdź status
pgrep -f auto_resume && echo "Działa" || echo "Nie działa"
```

---

## Harmonogram działania auto-resume

```
┌─────────────┐
│   Start     │
└──────┬──────┘
       │
       ├──> Co 10 min: Sprawdź czy sesja aktywna
       │
       ├──> Po 5h:
       │    ├─> Synchronizuj projekt
       │    ├─> Wznów sesję Claude
       │    └─> Reset timera
       │
       └──> Pętla...
```

---

## Różnice lokalne vs serwer

### Aktualny stan:
```bash
Lokalne zmiany:
  M platformio.ini     (zmiana board na nodemcu-32s)
  M src/main.cpp      (zmiany w konfiguracji)
```

### Sprawdź różnice:
```bash
# Lokalne zmiany
git diff platformio.ini
git diff src/main.cpp

# Porównaj z serwerem
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 "cd VBT && git status"
```

### Commituj lokalne zmiany:
```bash
git add platformio.ini src/main.cpp
git commit -m "Update ESP32 config for nodemcu-32s board"
./sync_to_server.sh
```

---

## Usługa systemd (opcjonalnie)

Jeśli chcesz aby auto-resume uruchamiał się automatycznie przy starcie systemu:

### Utwórz usługę:
```bash
mkdir -p ~/.config/systemd/user

cat > ~/.config/systemd/user/claude-auto-resume.service << 'EOF'
[Unit]
Description=Claude Code Auto Resume Service
After=network.target

[Service]
Type=simple
ExecStart=/home/diabolik/Documents/VBT/auto_resume_session.sh
Restart=always
RestartSec=10
StandardOutput=append:/home/diabolik/Documents/VBT/auto_resume.log
StandardError=append:/home/diabolik/Documents/VBT/auto_resume_error.log

[Install]
WantedBy=default.target
EOF
```

### Uruchom:
```bash
systemctl --user daemon-reload
systemctl --user enable claude-auto-resume.service
systemctl --user start claude-auto-resume.service
```

### Zarządzaj:
```bash
# Status
systemctl --user status claude-auto-resume.service

# Logi
journalctl --user -u claude-auto-resume.service -f

# Zatrzymaj
systemctl --user stop claude-auto-resume.service

# Wyłącz autostart
systemctl --user disable claude-auto-resume.service
```

---

## Troubleshooting

### Problem: Błąd połączenia SSH
```bash
# Test połączenia
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 "echo OK"

# Sprawdź uprawnienia klucza
ls -la ~/ssh-key-2025-12-19.key
chmod 600 ~/ssh-key-2025-12-19.key
```

### Problem: Nie wznawia sesji
```bash
# Sprawdź czy claude jest zainstalowany
which claude
claude --version

# Alternatywnie użyj npx
npx @anthropic/claude --version
```

### Problem: Pełny dysk z logów
```bash
# Wyczyść logi
> ~/Documents/VBT/auto_resume.log

# Ogranicz rozmiar (rotacja)
tail -1000 ~/Documents/VBT/auto_resume.log > /tmp/auto_resume.log.tmp
mv /tmp/auto_resume.log.tmp ~/Documents/VBT/auto_resume.log
```

### Problem: Różne wersje lokalnie i na serwerze
```bash
# Pobierz wersję z serwera
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 "cd VBT && cat src/main.cpp" > /tmp/server_main.cpp
diff /tmp/server_main.cpp src/main.cpp

# Lub wymuś sync z lokalu na serwer
./sync_to_server.sh
```

---

## Backup

### Backup lokalny:
```bash
cd ~/Documents
tar -czf VBT_backup_$(date +%Y%m%d_%H%M%S).tar.gz VBT/
```

### Backup na serwerze:
```bash
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 \
  "cd ~ && tar -czf VBT_backup_\$(date +%Y%m%d_%H%M%S).tar.gz VBT/"
```

### Lista backupów na serwerze:
```bash
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 \
  "ls -lh ~/VBT_backup_*.tar.gz"
```

---

## Następne kroki

1. **Uruchom auto-resume w tle:**
   ```bash
   cd ~/Documents/VBT
   nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
   ```

2. **Commituj lokalne zmiany:**
   ```bash
   git add platformio.ini src/main.cpp
   git commit -m "Update board config and main.cpp optimizations"
   ./sync_to_server.sh
   ```

3. **Monitoruj działanie:**
   ```bash
   tail -f ~/Documents/VBT/auto_resume.log
   ```

---

## Pliki projektu

```
VBT/
├── src/
│   ├── main.cpp              (ESP32 firmware)
│   ├── ble_server.cpp/h      (BLE komunikacja)
│   ├── data_storage.cpp/h    (Pamięć danych)
│   └── lift_detector.cpp/h   (Detekcja powtórzeń)
├── platformio.ini            (Konfiguracja PlatformIO)
├── sync_to_server.sh         (Synchronizacja)
├── manual_resume.sh          (Ręczne wznowienie)
├── auto_resume_session.sh    (Auto wznowienie)
├── setup_auto_resume.sh      (Konfiguracja)
├── AUTO_RESUME_README.md     (Dokumentacja)
└── SETUP_COMPLETE.md         (Ten plik)
```

---

## Pomoc

- Szczegółowa dokumentacja: `AUTO_RESUME_README.md`
- GitHub issues: Zgłoś problem jeśli coś nie działa
- Logi: `tail -f auto_resume.log`

**System gotowy do użycia! 🚀**
