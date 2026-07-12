# Automatyczne wznawianie sesji Claude Code

## Przegląd

System składa się z 3 skryptów zapewniających automatyczną synchronizację projektu VBT z serwerem oraz wznawianie sesji Claude Code po upływie 5 godzin.

## Skrypty

### 1. `sync_to_server.sh`
Synchronizuje projekt VBT z serwerem za pomocą rsync.

**Funkcje:**
- Sprawdza połączenie SSH
- Synchronizuje pliki (pomija .git, .pio, build artifacts)
- Pokazuje status git na serwerze
- Wyświetla ostatnie commity

**Użycie:**
```bash
./sync_to_server.sh
```

### 2. `auto_resume_session.sh`
Automatycznie wznawia sesję Claude Code co 5 godzin.

**Funkcje:**
- Synchronizuje projekt przed wznowieniem
- Sprawdza czy sesja Claude jest aktywna
- Loguje wszystkie operacje do `auto_resume.log`
- Działa w pętli nieskończonej

**Użycie:**
```bash
# W trybie blokującym (widoczne logi)
./auto_resume_session.sh

# W tle (zalecane)
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &

# Sprawdź PID procesu
pgrep -f auto_resume_session

# Zabij proces
pkill -f auto_resume_session
```

### 3. `setup_auto_resume.sh`
Konfiguruje i testuje system automatycznego wznawiania.

**Funkcje:**
- Nadaje uprawnienia wykonywania
- Testuje synchronizację z serwerem
- Opcjonalnie tworzy usługę systemd

**Użycie:**
```bash
./setup_auto_resume.sh
```

## Szybki start

### Pierwsza konfiguracja

1. **Uruchom setup:**
```bash
cd ~/Documents/VBT
./setup_auto_resume.sh
```

2. **Test synchronizacji:**
```bash
./sync_to_server.sh
```

3. **Uruchom auto-resume w tle:**
```bash
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

4. **Sprawdź logi:**
```bash
tail -f auto_resume.log
```

### Użycie z systemd (opcjonalne)

Jeśli wybrałeś opcję systemd podczas setup:

```bash
# Przeładuj konfigurację
systemctl --user daemon-reload

# Włącz autostart
systemctl --user enable claude-auto-resume.service

# Uruchom usługę
systemctl --user start claude-auto-resume.service

# Sprawdź status
systemctl --user status claude-auto-resume.service

# Zobacz logi
journalctl --user -u claude-auto-resume.service -f

# Zatrzymaj usługę
systemctl --user stop claude-auto-resume.service
```

## Harmonogram działania

1. **Start:** Uruchomienie skryptu auto_resume_session.sh
2. **Co 10 minut:** Sprawdzenie czy sesja jest aktywna
3. **Po 5 godzinach:**
   - Synchronizacja projektu z serwerem
   - Wznowienie sesji Claude Code
   - Reset timera na kolejne 5h

## Monitorowanie

### Sprawdzenie czy działa:
```bash
# Procesy
ps aux | grep auto_resume

# Logi na żywo
tail -f ~/Documents/VBT/auto_resume.log

# Ostatnie 20 linii logu
tail -20 ~/Documents/VBT/auto_resume.log
```

### Restart systemu:
```bash
# Zabij stary proces
pkill -f auto_resume_session

# Uruchom nowy
cd ~/Documents/VBT
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &
```

## Konfiguracja

### Zmiana parametrów w auto_resume_session.sh:

```bash
SESSION_DURATION=$((5 * 60 * 60))  # Zmień 5 na inną liczbę godzin
CHECK_INTERVAL=$((10 * 60))         # Zmień 10 na inną liczbę minut
```

### Dodanie wykluczeń w rsync:

Edytuj sekcję `--exclude` w obu skryptach:
```bash
--exclude='twój_katalog/' \
--exclude='*.rozszerzenie' \
```

## Rozwiązywanie problemów

### Błąd: "Nie znaleziono klucza SSH"
Sprawdź czy klucz istnieje:
```bash
ls -la ~/ssh-key-2025-12-19.key
```

### Błąd: "Nie można połączyć się z serwerem"
Test połączenia:
```bash
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 "echo OK"
```

### Błąd: "Nie znaleziono komendy 'claude'"
Zainstaluj Claude Code CLI:
```bash
npm install -g @anthropic/claude
# lub
npx @anthropic/claude
```

### Sesja nie wznawia się automatycznie
Sprawdź logi:
```bash
tail -50 ~/Documents/VBT/auto_resume.log
```

## Bezpieczeństwo

- **Klucz SSH:** Upewnij się że `~/ssh-key-2025-12-19.key` ma uprawnienia 600
  ```bash
  chmod 600 ~/ssh-key-2025-12-19.key
  ```

- **Logi:** Regularnie czyść pliki logów
  ```bash
  > ~/Documents/VBT/auto_resume.log
  ```

## Backup

Przed synchronizacją, rozważ utworzenie kopii zapasowej na serwerze:
```bash
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 \
  "cd ~ && tar -czf VBT_backup_$(date +%Y%m%d_%H%M%S).tar.gz VBT/"
```

## Sprawdzenie różnic lokalny vs serwer

```bash
# Porównaj pliki
ssh -i ~/ssh-key-2025-12-19.key ubuntu@130.61.232.212 \
  "cd VBT && find . -type f -name '*.cpp' -o -name '*.h' -o -name '*.ini'" \
  > server_files.txt

find . -type f \( -name '*.cpp' -o -name '*.h' -o -name '*.ini' \) > local_files.txt

diff local_files.txt server_files.txt
```
