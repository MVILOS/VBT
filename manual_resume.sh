#!/bin/bash
# Ręczne wznowienie pracy z synchronizacją
# Użycie: ./manual_resume.sh [commit_message]

set -e

SERVER="ubuntu@130.61.232.212"
SSH_KEY="$HOME/ssh-key-2025-12-19.key"
COMMIT_MSG="${1:-Automatic sync before resume}"

echo "=== Ręczne wznowienie pracy ==="
echo ""

# 1. Sprawdź zmiany lokalne
echo "1. Sprawdzam zmiany lokalne..."
if [[ -n $(git status --porcelain) ]]; then
    echo "Znalezione zmiany:"
    git status --short
    echo ""

    echo "Czy chcesz commitować te zmiany? (t/n)"
    read -r answer

    if [ "$answer" = "t" ] || [ "$answer" = "T" ]; then
        git add .
        git commit -m "$COMMIT_MSG"
        echo "✓ Zmiany commitowane"
    else
        echo "⚠ Zmiany nie zostały commitowane"
    fi
else
    echo "✓ Brak zmian do commitowania"
fi
echo ""

# 2. Synchronizacja z serwerem
echo "2. Synchronizuję z serwerem..."
./sync_to_server.sh
echo ""

# 3. Porównaj wersje
echo "3. Porównuję wersje..."
echo "Lokalny ostatni commit:"
git log --oneline -1
echo ""
echo "Serwer ostatni commit:"
ssh -i "$SSH_KEY" "$SERVER" "cd VBT && git log --oneline -1"
echo ""

# 4. Sprawdź różnice w plikach roboczych
echo "4. Różnice w kluczowych plikach:"
echo ""
echo "platformio.ini:"
DIFF_INI=$(git diff platformio.ini | wc -l)
if [ "$DIFF_INI" -gt 0 ]; then
    echo "  ⚠ Lokalnie zmodyfikowany ($DIFF_INI linii różnic)"
else
    echo "  ✓ Bez zmian"
fi

echo "src/main.cpp:"
DIFF_MAIN=$(git diff src/main.cpp | wc -l)
if [ "$DIFF_MAIN" -gt 0 ]; then
    echo "  ⚠ Lokalnie zmodyfikowany ($DIFF_MAIN linii różnic)"
else
    echo "  ✓ Bez zmian"
fi
echo ""

# 5. Pokaż status projektu
echo "5. Status projektu:"
echo "Lokalnie:"
ls -lh src/*.cpp src/*.h 2>/dev/null | tail -n +2 | awk '{print "  " $9 " (" $5 ")"}'
echo ""

echo "=== Gotowe do pracy! ==="
echo ""
echo "Sugestie:"
echo "  - Lokalny projekt zsynchronizowany z serwerem"
echo "  - Możesz teraz kontynuować pracę"
echo "  - Aby uruchomić auto-resume: nohup ./auto_resume_session.sh &"
