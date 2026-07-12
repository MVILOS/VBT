#!/bin/bash
# Skrypt synchronizacji projektu VBT na serwer
# Użycie: ./sync_to_server.sh

set -e

SERVER="ubuntu@130.61.232.212"
SSH_KEY="$HOME/ssh-key-2025-12-19.key"
LOCAL_DIR="/home/diabolik/Documents/VBT"
REMOTE_DIR="VBT"

echo "=== Synchronizacja projektu VBT na serwer ==="
echo "Serwer: $SERVER"
echo "Katalog lokalny: $LOCAL_DIR"
echo "Katalog zdalny: ~/$REMOTE_DIR"
echo ""

# Sprawdź czy klucz SSH istnieje
if [ ! -f "$SSH_KEY" ]; then
    echo "BŁĄD: Nie znaleziono klucza SSH: $SSH_KEY"
    exit 1
fi

# Sprawdź połączenie
echo "Sprawdzam połączenie SSH..."
if ! ssh -i "$SSH_KEY" -o ConnectTimeout=5 "$SERVER" "echo 'Połączenie OK'" 2>/dev/null; then
    echo "BŁĄD: Nie można połączyć się z serwerem"
    exit 1
fi
echo "✓ Połączenie działa"
echo ""

# Synchronizacja za pomocą rsync
echo "Rozpoczynam synchronizację..."
rsync -avz --delete \
    --exclude='.git/' \
    --exclude='.pio/' \
    --exclude='*.pyc' \
    --exclude='__pycache__/' \
    --exclude='node_modules/' \
    --exclude='android/app/build/' \
    --exclude='android/.gradle/' \
    --exclude='.claude/' \
    --exclude='src/.Rhistory' \
    -e "ssh -i $SSH_KEY" \
    "$LOCAL_DIR/" \
    "$SERVER:$REMOTE_DIR/"

echo ""
echo "✓ Synchronizacja zakończona!"
echo ""

# Sprawdź czy git istnieje na serwerze
echo "Sprawdzam repozytorium git na serwerze..."
if ssh -i "$SSH_KEY" "$SERVER" "cd $REMOTE_DIR && git rev-parse --git-dir > /dev/null 2>&1"; then
    echo ""
    echo "Status git na serwerze:"
    ssh -i "$SSH_KEY" "$SERVER" "cd $REMOTE_DIR && git status --short"

    echo ""
    echo "Ostatnie 3 commity na serwerze:"
    ssh -i "$SSH_KEY" "$SERVER" "cd $REMOTE_DIR && git log --oneline -3"
else
    echo "⚠ Brak repozytorium git na serwerze. Inicjalizuję..."
    ssh -i "$SSH_KEY" "$SERVER" "cd $REMOTE_DIR && \
        git init && \
        git config user.email 'vbt@server.local' && \
        git config user.name 'VBT Server' && \
        git add . && \
        git commit -m 'Initial sync from local'"
    echo "✓ Repozytorium git utworzone i zainicjalizowane"
fi
