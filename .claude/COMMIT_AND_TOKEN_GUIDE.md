# 📝 Automatyczne Commity i Oszczędzanie Tokenów

## ✅ Włączone funkcje

### 1. Auto-commit po każdej zmianie pliku
**Hook PostToolUse** - automatycznie commituje po każdej edycji pliku.

**Jak to działa:**
- Po użyciu `Edit`, `Write`, lub `NotebookEdit` automatycznie:
  - `git add -A`
  - `git commit -m "Auto-commit: <data/czas> - Modified files"`
- Działa w tle (async: true) - nie blokuje pracy

**Wyłączanie:**
Usuń sekcję `"PostToolUse"` z `.claude/settings.json`

### 2. Auto-commit przy zamknięciu sesji
**Hook SessionEnd** - zapisuje wszystkie zmiany przed zakończeniem.

**Jak to działa:**
- Przy zamykaniu sesji Claude Code:
  - Sprawdza czy są niezapisane zmiany
  - Commituje wszystko z timestampem

### 3. Status Line - zużycie tokenów i czas sesji
**W dolnej części ekranu** widoczny status:
- 📊 Tokens: liczba użytych tokenów
- ⏱️ Session: czas trwania sesji w sekundach

---

## 🎯 Strategia RTK (Reduce Token Knowledge)

### Zasady oszczędzania tokenów:

#### 1. **Czytaj tylko to co potrzebne**
```bash
# ❌ ZŁE - czyta cały plik
Read: /path/to/large_file.cpp

# ✅ DOBRE - czyta tylko potrzebny fragment
Read: /path/to/large_file.cpp (offset: 100, limit: 50)
```

#### 2. **Używaj Grep zamiast Read dla wyszukiwania**
```bash
# ❌ ZŁE - czyta wszystkie pliki żeby znaleźć wzorzec
Read: src/file1.cpp, src/file2.cpp, src/file3.cpp

# ✅ DOBRE - szuka bezpośrednio
Grep: pattern="funkcja_nazwa" path="src/" glob="*.cpp"
```

#### 3. **Używaj Task/Explore dla eksploracji**
```bash
# ❌ ZŁE - czyta wiele plików bezpośrednio
Read: wszystkie pliki w projekcie

# ✅ DOBRE - agent Explore zrobi to efektywniej
Task (Explore): "Znajdź gdzie jest obsługa BLE"
```

#### 4. **Nie powtarzaj kontekstu**
```markdown
❌ ZŁE:
"Jak mówiłem wcześniej, funkcja X robi Y, a teraz chcę..."

✅ DOBRE:
"Dodaj obsługę błędów do funkcji X"
(Claude pamięta kontekst z poprzednich wiadomości)
```

#### 5. **Commituj małe, atomowe zmiany**
```bash
# ❌ ZŁE - jeden wielki commit
git commit -m "Zmiany w projekcie"

# ✅ DOBRE - małe, opisowe commity
git commit -m "Add BLE connection timeout handling"
git commit -m "Optimize encoder reading frequency"
git commit -m "Fix memory leak in data storage"
```

---

## 📋 Instrukcje commitowania

### Zasady commitów:

1. **Po każdej logicznej zmianie** (hook robi to automatycznie)
2. **Opisowe wiadomości** zamiast "fix", "update"
3. **Atomowe commity** - jedna funkcjonalność = jeden commit
4. **Przed testowaniem** nowej funkcji

### Format commitów:
```
<typ>: <krótki opis>

<opcjonalnie dłuższy opis>
```

**Typy:**
- `feat:` - nowa funkcjonalność
- `fix:` - naprawa błędu
- `refactor:` - refaktoryzacja kodu
- `docs:` - dokumentacja
- `test:` - testy
- `chore:` - czynności pomocnicze
- `perf:` - optymalizacja wydajności

### Przykłady dobrych commitów:
```bash
git commit -m "feat: Add BLE connection retry mechanism"
git commit -m "fix: Resolve encoder overflow on long sessions"
git commit -m "refactor: Extract velocity calculation to separate function"
git commit -m "perf: Reduce CPU frequency to 80MHz for power saving"
git commit -m "docs: Update README with installation instructions"
```

### Przykłady złych commitów:
```bash
❌ git commit -m "fix"
❌ git commit -m "update"
❌ git commit -m "changes"
❌ git commit -m "WIP"
❌ git commit -m "asdf"
```

---

## 🔄 Workflow z auto-commitami

### Standardowa praca:

1. **Edytujesz plik** → Auto-commit ✓
2. **Edytujesz kolejny plik** → Auto-commit ✓
3. **Kończysz funkcjonalność** → Ręczny commit z opisem:
   ```bash
   git reset --soft HEAD~3  # Cofnij 3 auto-commity
   git commit -m "feat: Add complete BLE auto-reconnect feature"
   ```

### Praca z większymi zmianami:

1. **Duża zmiana** - wyłącz auto-commit tymczasowo
2. **Testuj często** - commituj przed każdym testem
3. **Wycofywanie** - łatwo wrócić do poprzedniego stanu:
   ```bash
   git log --oneline -10  # Zobacz ostatnie commity
   git reset --hard HEAD~2  # Cofnij 2 commity
   ```

---

## 📊 Monitorowanie zużycia tokenów

### Sprawdź statystyki:
```bash
# Pełne statystyki
cat ~/.claude/stats-cache.json | jq '.'

# Tokeny w bieżącej sesji
grep -o '"token_count":[0-9]*' ~/.claude/stats-cache.json | tail -1

# Historia wszystkich sesji
cat ~/.claude/history.jsonl | wc -l
```

### Optymalizacja:
```bash
# Wyczyść starą historię (oszczędza tokeny przy wznowieniu)
find ~/.claude/projects/ -name "*.md" -mtime +30 -delete

# Kompresja historii
claude compact  # Jeśli dostępne
```

---

## 🛠️ Konfiguracja projektu VBT

### Aktualne ustawienia (.claude/settings.json):
```json
{
  "hooks": {
    "PostToolUse": [/* auto-commit po edycji */],
    "SessionEnd": [/* auto-commit przy zamknięciu */]
  },
  "statusLine": {
    "command": "/* pokazuje tokeny i czas */"
  }
}
```

### Dostosowanie:

#### Wyłącz auto-commit:
Usuń hook z settings.json lub ustaw:
```json
"async": false  // Zmień na false żeby widzieć commity
```

#### Zmień format auto-commita:
```json
"command": "git add -A && git commit -m \"feat: Auto-save - $(git diff --name-only --cached | head -1)\""
```

#### Dodaj hook pre-commit (walidacja):
```json
"PreToolUse": [{
  "matcher": "Write|Edit",
  "hooks": [{
    "type": "prompt",
    "prompt": "Check if this edit might break existing functionality"
  }]
}]
```

---

## 📚 Dodatkowe zasoby

### Komendy pomocnicze:

```bash
# Zobacz historię commitów
git log --oneline --graph --all --decorate -20

# Cofnij ostatni commit (zachowaj zmiany)
git reset --soft HEAD~1

# Cofnij ostatni commit (usuń zmiany)
git reset --hard HEAD~1

# Squash ostatnich N commitów
git rebase -i HEAD~N

# Edytuj ostatni commit message
git commit --amend -m "Nowy opis"

# Zobacz szczegóły commita
git show <commit-hash>

# Wróć do konkretnego commita
git checkout <commit-hash>

# Stwórz branch z obecnego stanu
git checkout -b feature/nazwa-brancha
```

### Aliasy git (dodaj do ~/.gitconfig):
```ini
[alias]
    st = status --short
    co = commit -m
    ac = !git add -A && git commit -m
    lg = log --oneline --graph --all -20
    undo = reset --soft HEAD~1
    save = !git add -A && git commit -m "SAVEPOINT"
    wip = !git add -A && git commit -m "WIP"
```

Użycie:
```bash
git ac "feat: Add new feature"  # Add all + commit
git lg                           # Pretty log
git undo                         # Undo last commit
git save                         # Quick savepoint
```

---

## ⚠️ Ważne przypomnienia

1. **Auto-commity są lokalne** - nie są pushowane automatycznie
2. **Przed push** - rozważ squash auto-commitów: `git rebase -i`
3. **Backup** - regularnie sync z serwerem: `./sync_to_server.sh`
4. **Tokeny** - sprawdzaj status line, przy 150k+ rozważ restart sesji
5. **Historia** - commity pozwalają wrócić do dowolnego punktu

---

## 🎓 Best practices

### DO:
✅ Commituj po każdej funkcjonalności
✅ Pisz opisowe commity
✅ Testuj przed commitem
✅ Regularnie syncuj z serwerem
✅ Sprawdzaj zużycie tokenów

### DON'T:
❌ Nie commituj broken code (bez testu)
❌ Nie używaj niejasnych opisów
❌ Nie czekaj z commitem do końca dnia
❌ Nie ignoruj status line
❌ Nie trzymaj wszystkiego w jednym branchu

---

**System automatycznego commitowania i monitorowania tokenów jest aktywny! 🚀**
