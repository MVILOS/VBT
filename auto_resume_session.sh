#!/bin/bash
# Automatyczne wznawianie sesji Claude Code po odnowieniu (5h)
# Uruchom w tle: nohup ./auto_resume_session.sh &

set -e

SERVER="ubuntu@130.61.232.212"
SSH_KEY="$HOME/.ssh/ssh-key-2025-12-19.key"
REMOTE_DIR="VBT"
LOG_FILE="$HOME/Documents/VBT/auto_resume.log"
SESSION_DURATION=$((5 * 60 * 60))  # 5 godzin w sekundach
CHECK_INTERVAL=$((10 * 60))  # Sprawdzaj co 10 minut

# Funkcja logowania
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Funkcja synchronizacji z serwerem
sync_project() {
    log "Synchronizuję projekt z serwerem..."
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
        "$HOME/Documents/VBT/" \
        "$SERVER:$REMOTE_DIR/" >> "$LOG_FILE" 2>&1
    log "✓ Synchronizacja zakończona"
}

# Funkcja sprawdzająca czy sesja Claude jest aktywna
check_claude_session() {
    # Sprawdź czy proces claude jest uruchomiony
    if pgrep -f "claude.*VBT" > /dev/null; then
        return 0  # Sesja aktywna
    else
        return 1  # Sesja nieaktywna
    fi
}

# Funkcja wznawiająca sesję
resume_session() {
    log "Wznawiam sesję Claude Code..."

    # Synchronizuj projekt przed wznowieniem
    sync_project

    # Przejdź do katalogu projektu
    cd "$HOME/Documents/VBT"

    # Wznów sesję Claude (zakładając że claude jest w PATH)
    # Możesz dostosować tę komendę do swojego środowiska
    log "Uruchamiam: claude resume"

    # Opcja 1: Jeśli masz alias lub skrypt claude
    if command -v claude &> /dev/null; then
        claude resume >> "$LOG_FILE" 2>&1 &
    # Opcja 2: Jeśli używasz npx
    elif command -v npx &> /dev/null; then
        npx @anthropic/claude resume >> "$LOG_FILE" 2>&1 &
    else
        log "BŁĄD: Nie znaleziono komendy 'claude'. Zainstaluj Claude Code CLI."
        return 1
    fi

    log "✓ Sesja wznowiona"
}

# Główna pętla
log "=== Start automatycznego wznawiania sesji Claude ==="
log "Czas trwania sesji: $SESSION_DURATION sekund (5h)"
log "Interwał sprawdzania: $CHECK_INTERVAL sekund (10min)"
log ""

START_TIME=$(date +%s)
NEXT_RESUME_TIME=$((START_TIME + SESSION_DURATION))

while true; do
    CURRENT_TIME=$(date +%s)
    TIME_TO_RESUME=$((NEXT_RESUME_TIME - CURRENT_TIME))

    if [ $TIME_TO_RESUME -le 0 ]; then
        log "Upłynęło 5h - czas na wznowienie sesji"

        # Synchronizuj i wznów
        sync_project
        resume_session

        # Ustaw następny czas wznowienia
        NEXT_RESUME_TIME=$((CURRENT_TIME + SESSION_DURATION))
        log "Następne wznowienie za 5h o $(date -d @$NEXT_RESUME_TIME '+%Y-%m-%d %H:%M:%S')"
    else
        HOURS=$((TIME_TO_RESUME / 3600))
        MINUTES=$(((TIME_TO_RESUME % 3600) / 60))
        log "Do następnego wznowienia: ${HOURS}h ${MINUTES}min"
    fi

    # Sprawdź czy sesja jest aktywna
    if check_claude_session; then
        log "Sesja Claude jest aktywna"
    else
        log "UWAGA: Sesja Claude nie jest aktywna"
    fi

    # Czekaj przed następnym sprawdzeniem
    sleep $CHECK_INTERVAL
done
