# 🎯 VBT Project - Przypomnienia Sesji

## ⚡ Auto-commity WŁĄCZONE
- Każda edycja pliku = automatyczny commit
- Koniec sesji = auto-commit wszystkich zmian
- Status line pokazuje tokeny i czas sesji

## 📝 Zasady commitowania

### Po każdym bloku zmian:
1. **Sprawdź zmiany**: `git status --short`
2. **Zobacz ostatnie auto-commity**: `git log --oneline -5`
3. **Squash jeśli potrzeba**:
   ```bash
   git reset --soft HEAD~3
   git commit -m "feat: <opisowa wiadomość>"
   ```

### Format commitów:
```
<typ>: <krótki opis>

feat:     - nowa funkcjonalność
fix:      - naprawa błędu
refactor: - refaktoryzacja
perf:     - optymalizacja
docs:     - dokumentacja
test:     - testy
```

## 🔄 Synchronizacja z serwerem

### Przed rozpoczęciem pracy:
```bash
./manual_resume.sh
```

### Po zakończeniu bloku pracy:
```bash
git add .
git commit -m "feat: <opis zmian>"
./sync_to_server.sh
```

### Auto-resume działa w tle?
```bash
pgrep -f auto_resume && echo "✓ Działa" || echo "✗ Uruchom: nohup ./auto_resume_session.sh &"
```

## 💾 Backup i cofanie

### Szybki backup przed dużymi zmianami:
```bash
git branch backup-$(date +%Y%m%d-%H%M%S)
```

### Cofnij ostatnie zmiany (zachowaj pliki):
```bash
git reset --soft HEAD~1
```

### Cofnij zmiany (usuń modyfikacje):
```bash
git reset --hard HEAD~1
```

## 📊 Oszczędzanie tokenów (RTK)

### Zasady:
1. **Czytaj tylko potrzebne fragmenty** - używaj offset/limit
2. **Grep zamiast Read** - do wyszukiwania wzorców
3. **Task/Explore** - do eksploracji codebase
4. **Nie powtarzaj kontekstu** - Claude pamięta
5. **Małe commity** - łatwiej wrócić do punktu

### Monitoruj tokeny:
- Status line na dole ekranu
- Przy >150k tokenów rozważ nową sesję
- Sprawdź: `grep '"token_count"' ~/.claude/stats-cache.json`

## 🚀 Szybkie akcje

```bash
# Status projektu
git status --short

# Ostatnie 5 commitów
git log --oneline -5

# Różnice do commita
git diff

# Sync z serwerem
./sync_to_server.sh

# Auto-resume status
tail -20 auto_resume.log

# Manual resume (pełna diagnostyka)
./manual_resume.sh
```

## ⚠️ Ważne przypomnienia

1. **Testuj przed commitem** - broken code = trudny rollback
2. **Opisowe commity** - przyszłe-ty podziękuje
3. **Regularna synchronizacja** - backup na serwerze
4. **Sprawdzaj tokeny** - oszczędzaj na czytaniu całych plików
5. **Auto-resume działa** - wznawia co 5h automatycznie

## 📁 Struktura projektu VBT

```
VBT/
├── src/
│   ├── main.cpp              ← Główny firmware ESP32
│   ├── ble_server.cpp/h      ← BLE komunikacja
│   ├── data_storage.cpp/h    ← Pamięć danych
│   └── lift_detector.cpp/h   ← Detekcja powtórzeń
├── platformio.ini            ← Konfiguracja (nodemcu-32s, 80MHz)
├── .claude/
│   ├── settings.json         ← Hooki auto-commit
│   └── COMMIT_AND_TOKEN_GUIDE.md
└── *.sh                      ← Skrypty sync/resume
```

## 🔧 ESP32 VBT - Aktualna konfiguracja

```ini
Board: nodemcu-32s
CPU: 80MHz (oszczędzanie energii)
Upload: 115200 baud, /dev/ttyUSB0
Encoder: FullQuad (25, 26)
Spool: 0.04m diameter
```

## 📞 Skrypty pomocnicze

- `sync_to_server.sh` - Synchronizacja na serwer
- `manual_resume.sh` - Ręczne wznowienie + diagnostyka
- `auto_resume_session.sh` - Auto-resume co 5h
- `setup_auto_resume.sh` - Konfiguracja początkowa

---

**Dokumentacja:**
- `COMMIT_AND_TOKEN_GUIDE.md` - Pełny przewodnik
- `AUTO_RESUME_README.md` - Dokumentacja auto-resume
- `SETUP_COMPLETE.md` - Status konfiguracji
- `QUICK_START.md` - Szybki start

---

**🎯 Przed rozpoczęciem pracy:**
1. `./manual_resume.sh` - Sync i diagnostyka
2. Sprawdź status line (tokeny/czas)
3. Sprawdź auto-resume: `pgrep -f auto_resume`
4. Sprawdź ostatnie commity: `git log --oneline -5`

**🎯 Po zakończeniu bloku pracy:**
1. `git status` - Zobacz zmiany
2. `git log --oneline -5` - Sprawdź auto-commity
3. Squash jeśli potrzeba, lub zostaw auto-commity
4. `./sync_to_server.sh` - Backup na serwer
5. Commituj celowo: `git commit -m "feat: <opis>"`

**Powodzenia! 🚀**
