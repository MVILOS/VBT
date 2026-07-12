# ✅ Zainstalowane funkcje - VBT Project

## 📦 Zainstalowane pluginy

### 1. **commit-commands** (włączony)
Plugin do zaawansowanego zarządzania commitami.

**Użycie:**
```bash
/commit                    # Commit z AI-wygenerowaną wiadomością
/commit "custom message"   # Commit z własną wiadomością
```

### 2. **hookify** (włączony)
Plugin do tworzenia custom hooków bez edytowania JSON.

**Użycie:**
```bash
/hookify Warn me when I use rm -rf commands
/hookify:list              # Lista wszystkich hooków
/hookify:configure         # Konfiguracja interaktywna
```

---

## ⚙️ Skonfigurowane hooki

### 1. **PostToolUse** - Auto-commit po edycji
**Trigger:** `Edit`, `Write`, `NotebookEdit`

**Akcja:**
```bash
git add -A
git commit -m "Auto-commit: <timestamp> - Modified files"
```

**Parametry:**
- `async: true` - Działa w tle, nie blokuje
- `statusMessage: "Auto-committing changes..."`

**Jak wyłączyć:**
Usuń sekcję `PostToolUse` z `.claude/settings.json`

### 2. **SessionEnd** - Auto-commit przy zamknięciu
**Trigger:** Zamknięcie sesji Claude Code

**Akcja:**
```bash
git add -A
git commit -m "Session end auto-commit: <timestamp>"
```

**Parametry:**
- `async: false` - Synchroniczny, czeka na zakończenie
- `statusMessage: "Saving session changes..."`

---

## 📊 Status Line

### Konfiguracja
**Lokalizacja:** `.claude/settings.json`

**Komenda:**
```bash
echo "📊 Tokens: $(tokens) | ⏱️ Session: $(duration) s"
```

**Wyświetla:**
- 📊 Liczba użytych tokenów w sesji
- ⏱️ Czas trwania sesji w sekundach

**Padding:** 2 (odstęp od krawędzi)

---

## 🔄 Automatyczne wznawianie sesji

### Skrypty

#### 1. `sync_to_server.sh`
Synchronizuje projekt z serwerem przez rsync.

**Funkcje:**
- Test połączenia SSH
- Synchronizacja plików (wykluczenia: .git, .pio, build)
- Inicjalizacja git jeśli nie istnieje
- Status git na serwerze

**Użycie:**
```bash
./sync_to_server.sh
```

#### 2. `manual_resume.sh`
Ręczne wznowienie pracy z pełną diagnostyką.

**Funkcje:**
- Sprawdzenie lokalnych zmian
- Opcjonalne commitowanie
- Synchronizacja z serwerem
- Porównanie wersji lokalnej vs serwer
- Status projektu

**Użycie:**
```bash
./manual_resume.sh ["commit message"]
```

#### 3. `auto_resume_session.sh`
Automatyczne wznawianie sesji co 5 godzin.

**Funkcje:**
- Synchronizacja przed wznowieniem
- Sprawdzanie aktywności sesji co 10 min
- Wznawianie Claude Code po 5h
- Logowanie wszystkich operacji

**Użycie:**
```bash
# W tle (zalecane)
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &

# Pierwszym planie (widoczne logi)
./auto_resume_session.sh
```

**Parametry (edytowalne):**
- `SESSION_DURATION=$((5 * 60 * 60))` - 5 godzin
- `CHECK_INTERVAL=$((10 * 60))` - 10 minut

#### 4. `setup_auto_resume.sh`
Konfiguracja początkowa systemu.

**Funkcje:**
- Nadanie uprawnień wykonywania
- Test połączenia SSH
- Pierwsza synchronizacja
- Opcjonalne utworzenie usługi systemd

**Użycie:**
```bash
./setup_auto_resume.sh
```

---

## 📁 Struktura konfiguracji

```
VBT/
├── .claude/
│   ├── settings.json                    ← Hooki i status line
│   ├── COMMIT_AND_TOKEN_GUIDE.md        ← Pełny przewodnik
│   └── PROJECT_REMINDERS.md             ← Przypomnienia sesji
│
├── sync_to_server.sh                    ← Synchronizacja
├── manual_resume.sh                     ← Ręczne wznowienie
├── auto_resume_session.sh               ← Auto-resume 5h
├── setup_auto_resume.sh                 ← Setup
│
├── AUTO_RESUME_README.md                ← Dokumentacja auto-resume
├── SETUP_COMPLETE.md                    ← Status konfiguracji
├── QUICK_START.md                       ← Szybki start
└── INSTALLED_FEATURES.md                ← Ten plik
```

---

## 🎯 Workflow z nowymi funkcjami

### Standardowa praca:

1. **Start sesji:**
   ```bash
   ./manual_resume.sh  # Sync + diagnostyka
   ```

2. **Edycja plików:**
   - Claude edytuje plik → **Auto-commit** ✓
   - Status line pokazuje tokeny/czas

3. **Po bloku zmian:**
   ```bash
   git log --oneline -5        # Zobacz auto-commity
   git reset --soft HEAD~3     # Squash jeśli potrzeba
   git commit -m "feat: opis"  # Celowy commit
   ./sync_to_server.sh         # Backup
   ```

4. **Koniec sesji:**
   - Zamknięcie → **Auto-commit** ✓
   - Auto-resume wznowi za 5h (jeśli działa w tle)

---

## 🛠️ Zarządzanie

### Sprawdź status:

```bash
# Auto-resume działa?
pgrep -f auto_resume && echo "✓ Działa" || echo "✗ Nie działa"

# Logi auto-resume
tail -20 auto_resume.log

# Ostatnie auto-commity
git log --oneline -10

# Zużycie tokenów
grep '"token_count"' ~/.claude/stats-cache.json

# Zainstalowane pluginy
claude plugin list
```

### Zarządzanie auto-resume:

```bash
# Uruchom
nohup ./auto_resume_session.sh > auto_resume.log 2>&1 &

# Zatrzymaj
pkill -f auto_resume_session

# Status
ps aux | grep auto_resume
```

### Zarządzanie hookami:

```bash
# Wyłącz wszystkie hooki
# Edytuj .claude/settings.json i usuń sekcję "hooks"

# Tymczasowo wyłącz auto-commit
# Zmień "async": true na "async": false w PostToolUse

# Lista hooków (przez hookify)
/hookify:list
```

---

## 📚 Dokumentacja

### Pliki pomocnicze:

1. **QUICK_START.md** - Szybki start (5 minut)
2. **AUTO_RESUME_README.md** - Pełna dok auto-resume
3. **COMMIT_AND_TOKEN_GUIDE.md** - Przewodnik commitów i RTK
4. **SETUP_COMPLETE.md** - Status i konfiguracja
5. **PROJECT_REMINDERS.md** - Przypomnienia na sesję
6. **INSTALLED_FEATURES.md** - Ten plik

### Online resources:

- Hookify docs: `~/.claude/plugins/marketplaces/claude-plugins-official/plugins/hookify/README.md`
- Commit commands: `~/.claude/plugins/marketplaces/claude-plugins-official/plugins/commit-commands/`

---

## ⚠️ Troubleshooting

### Auto-commity nie działają:
```bash
# Sprawdź konfigurację
cat .claude/settings.json

# Sprawdź git
git status
git rev-parse --git-dir

# Sprawdź logi
tail -50 ~/.claude/debug/*.log
```

### Status line nie pokazuje się:
```bash
# Sprawdź komendę ręcznie
bash -c "echo \"📊 Tokens: \$(grep -o '\"token_count\":[0-9]*' ~/.claude/stats-cache.json 2>/dev/null | tail -1 | cut -d: -f2 || echo '?') | ⏱️ Session: \$(($(date +%s) - \$(stat -c %Y ~/.claude/history.jsonl 2>/dev/null || echo \$(date +%s)))) s\""

# Przeładuj sesję
# Zamknij i otwórz ponownie Claude Code
```

### Auto-resume nie wznawia:
```bash
# Sprawdź logi
tail -50 auto_resume.log

# Sprawdź czy claude jest w PATH
which claude
claude --version

# Test ręczny
./auto_resume_session.sh
```

---

## 🎉 Podsumowanie

✅ **Zainstalowano:**
- 2 pluginy (commit-commands, hookify)
- 2 hooki (PostToolUse, SessionEnd)
- Status line (tokeny + czas)
- 4 skrypty automatyzacji
- Pełna dokumentacja

✅ **Włączono:**
- Auto-commit po każdej edycji
- Auto-commit przy zamknięciu sesji
- Monitoring tokenów w czasie rzeczywistym
- Auto-resume co 5h (jeśli uruchomiony)

✅ **Dostępne:**
- Ręczna synchronizacja: `./manual_resume.sh`
- Szybki sync: `./sync_to_server.sh`
- Komendy hookify: `/hookify`, `/hookify:list`
- Komendy commit: `/commit`

**System gotowy do pracy! 🚀**

---

**Następne kroki:**
1. Przeczytaj `QUICK_START.md`
2. Uruchom `./manual_resume.sh`
3. Sprawdź status line na dole ekranu
4. Edytuj plik i zobacz auto-commit
5. Opcjonalnie uruchom auto-resume w tle

**Dokumentacja pełna:** Zobacz wszystkie pliki `*.md` w projekcie.
